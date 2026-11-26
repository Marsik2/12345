package com.xivevisuals;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;

public class XiveVisualsClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        XiveConfig.load();

        DimensionRenderingRegistry.registerSkyRenderer(World.OVERWORLD, ctx -> XiveSky.render(ctx));
        HudRenderCallback.EVENT.register((ctx, tc) -> XiveHud.render(ctx, tc));
        ClientReceiveMessageEvents.ALLOW_GAME.register((msg, overlay) -> XiveFeatures.allowChat(msg, overlay));
        ClientReceiveMessageEvents.MODIFY_GAME.register((msg, overlay) -> XiveFeatures.onChat(msg, overlay));
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && entity instanceof LivingEntity) XiveFeatures.onHit();
            return ActionResult.PASS;
        });

        String cat = "key.category.xivevisuals";
        KeyBinding paletteKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.xivevisuals.palette", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, cat));
        KeyBinding particleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.xivevisuals.particles", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, cat));
        KeyBinding hudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.xivevisuals.hud", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_H, cat));
        KeyBinding menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.xivevisuals.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, cat));
        XiveFeatures.zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.xivevisuals.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, cat));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (paletteKey.wasPressed()) {
                XiveConfig.palette = (XiveConfig.palette + 1) % XiveConfig.PALETTES.length;
                XiveConfig.save();
                msg(client, "Палитра: " + XiveConfig.palette().name());
            }
            while (particleKey.wasPressed()) {
                XiveConfig.particleLevel = (XiveConfig.particleLevel + 1) % XiveConfig.PARTICLE_LEVELS.length;
                XiveConfig.save();
                msg(client, "Частицы: " + XiveConfig.particleLevel + "/" + (XiveConfig.PARTICLE_LEVELS.length - 1));
            }
            while (hudKey.wasPressed()) {
                XiveConfig.HUD.on = !XiveConfig.HUD.on;
                XiveConfig.save();
                msg(client, "HUD: " + (XiveConfig.HUD.on ? "включён" : "выключен"));
            }
            while (menuKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new XiveMenu());
            }
            XiveFeatures.tick(client);
            spawnParticles(client);
        });

        // /xive — список функций, /xive <функция> — вкл/выкл, /xive wp ... — метки
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            var root = ClientCommandManager.literal("xive").executes(c -> { help(c.getSource()); return 1; });
            for (XiveConfig.Module m : XiveConfig.MODULES) {
                var lit = ClientCommandManager.literal(m.id).executes(c -> {
                    m.on = !m.on;
                    XiveConfig.save();
                    c.getSource().sendFeedback(Text.literal("§b" + m.title + ": " + (m.on ? "§aвключено" : "§cвыключено")));
                    return 1;
                });
                if (m.id.equals("timer")) {
                    lit.then(ClientCommandManager.argument("sec", IntegerArgumentType.integer(1, 3600)).executes(c -> {
                        int sec = IntegerArgumentType.getInteger(c, "sec");
                        XiveFeatures.TIMERS.add(new long[]{System.currentTimeMillis() + sec * 1000L});
                        c.getSource().sendFeedback(Text.literal("§aТаймер на " + sec + " с запущен"));
                        return 1;
                    }));
                    lit.then(ClientCommandManager.literal("clear").executes(c -> {
                        XiveFeatures.TIMERS.clear();
                        c.getSource().sendFeedback(Text.literal("§eВсе таймеры сброшены"));
                        return 1;
                    }));
                }
                if (m.id.equals("chatfilter")) {
                    lit.then(ClientCommandManager.literal("add").then(
                        ClientCommandManager.argument("word", StringArgumentType.word()).executes(c -> {
                            String w = StringArgumentType.getString(c, "word").toLowerCase();
                            if (!XiveConfig.FILTER_WORDS.contains(w)) XiveConfig.FILTER_WORDS.add(w);
                            XiveConfig.save();
                            c.getSource().sendFeedback(Text.literal("§aСлово «" + w + "» добавлено в фильтр"));
                            return 1;
                        })));
                    lit.then(ClientCommandManager.literal("del").then(
                        ClientCommandManager.argument("word", StringArgumentType.word()).executes(c -> {
                            String w = StringArgumentType.getString(c, "word").toLowerCase();
                            boolean ok = XiveConfig.FILTER_WORDS.remove(w);
                            XiveConfig.save();
                            c.getSource().sendFeedback(Text.literal(ok ? "§eСлово «" + w + "» убрано" : "§cТакого слова нет"));
                            return 1;
                        })));
                    lit.then(ClientCommandManager.literal("list").executes(c -> {
                        c.getSource().sendFeedback(Text.literal("§7Фильтр: §f" + (XiveConfig.FILTER_WORDS.isEmpty() ? "пусто" : String.join(", ", XiveConfig.FILTER_WORDS))));
                        return 1;
                    }));
                }
                root.then(lit);
            }

            var wp = ClientCommandManager.literal("wp");
            wp.then(ClientCommandManager.literal("add").then(
                ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player == null) return 0;
                    String n = StringArgumentType.getString(c, "name");
                    XiveConfig.WAYPOINTS_MAP.put(n, new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
                    XiveConfig.save();
                    c.getSource().sendFeedback(Text.literal("§aМетка «" + n + "» сохранена"));
                    return 1;
                })));
            wp.then(ClientCommandManager.literal("del").then(
                ClientCommandManager.argument("name", StringArgumentType.word()).executes(c -> {
                    String n = StringArgumentType.getString(c, "name");
                    boolean ok = XiveConfig.WAYPOINTS_MAP.remove(n) != null;
                    XiveConfig.save();
                    c.getSource().sendFeedback(Text.literal(ok ? "§eМетка «" + n + "» удалена" : "§cТакой метки нет"));
                    return 1;
                })));
            wp.then(ClientCommandManager.literal("list").executes(c -> {
                if (XiveConfig.WAYPOINTS_MAP.isEmpty()) {
                    c.getSource().sendFeedback(Text.literal("§7Меток пока нет. /xive wp add <имя>"));
                }
                XiveConfig.WAYPOINTS_MAP.forEach((n, p) ->
                    c.getSource().sendFeedback(Text.literal("§b" + n + " §7— §f" + p[0] + " " + p[1] + " " + p[2])));
                return 1;
            }));
            root.then(wp);
            dispatcher.register(root);
        });
    }

    private static void help(FabricClientCommandSource src) {
        src.sendFeedback(Text.literal("§b§lXiveVisuals §7— функции (§f/xive <название>§7 — вкл/выкл):"));
        for (XiveConfig.Module m : XiveConfig.MODULES) {
            src.sendFeedback(Text.literal((m.on ? "§a[ВКЛ] " : "§c[ВЫКЛ] ") + "§f" + m.id + " §7— " + m.desc));
        }
        src.sendFeedback(Text.literal("§7Клавиши: §fV§7 — палитра неба, §fB§7 — частицы, §fH§7 — HUD, §fC§7 — приближение, §fПравый Shift§7 — меню."));
    }

    private static void msg(MinecraftClient c, String s) {
        if (c.player != null) c.player.sendMessage(Text.literal(s), true);
    }

    private static void spawnParticles(MinecraftClient c) {
        if (c.world == null || c.player == null || c.isPaused()) return;
        int n = XiveConfig.particlesPerTick();
        Random r = c.world.random;
        for (int i = 0; i < n; i++) {
            double x = c.player.getX() + (r.nextDouble() - 0.5) * 36;
            double y = c.player.getY() + (r.nextDouble() - 0.3) * 12;
            double z = c.player.getZ() + (r.nextDouble() - 0.5) * 36;
            c.world.addParticle(r.nextInt(3) == 0 ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                x, y, z, (r.nextDouble() - 0.5) * 0.012, 0.004 + r.nextDouble() * 0.012, (r.nextDouble() - 0.5) * 0.012);
        }
    }
}
