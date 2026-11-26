package com.xivevisuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import net.minecraft.entity.effect.StatusEffectInstance;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Логика функций: спринт, приближение, счётчики, чат. */
public final class XiveFeatures {
    public static KeyBinding zoomKey;

    public static double speed;
    public static int combo, hits, deaths;
    public static final long SESSION_START = System.currentTimeMillis();

    public static float lastDamage;
    public static long lastDamageTime, lastCombat;
    public static boolean hasDeath;
    public static int deathX, deathY, deathZ;
    public static final List<long[]> TIMERS = new ArrayList<>();
    private static float lastHp = -1;

    private static double lastX, lastZ;
    private static boolean wasDead, forcedSprint, zoomed;
    private static int savedFov, prevHurt;
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public static void tick(MinecraftClient mc) {
        updateZoom(mc);
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null) return;

        // скорость
        double now = Math.hypot(p.getX() - lastX, p.getZ() - lastZ) * 20;
        speed = speed * 0.6 + now * 0.4;
        lastX = p.getX(); lastZ = p.getZ();

        // урон и боевой таймер
        long nowMs = System.currentTimeMillis();
        float hp = p.getHealth() + p.getAbsorptionAmount();
        if (lastHp >= 0 && hp > 0 && hp < lastHp - 0.01f) {
            lastDamage = lastHp - hp;
            lastDamageTime = nowMs;
            lastCombat = nowMs;
        }
        lastHp = hp;

        // звук за 5 секунд до конца эффекта
        if (XiveConfig.EFFECTSOUND.on) {
            for (StatusEffectInstance e : p.getStatusEffects()) {
                if (!e.isInfinite() && e.getDuration() == 100) {
                    mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1f));
                }
            }
        }

        // таймеры
        TIMERS.removeIf(t -> {
            if (System.currentTimeMillis() < t[0]) return false;
            mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1f));
            p.sendMessage(Text.literal("§bТаймер закончился"), true);
            return true;
        });

        // комбо сбрасывается при получении урона
        if (p.hurtTime > 0 && prevHurt == 0) combo = 0;
        prevHurt = p.hurtTime;

        // смерть
        boolean dead = p.getHealth() <= 0;
        if (dead && !wasDead) {
            deaths++;
            hasDeath = true;
            deathX = p.getBlockX(); deathY = p.getBlockY(); deathZ = p.getBlockZ();
            combo = 0;
            if (XiveConfig.DEATHPOS.on) {
                String dim = mc.world.getRegistryKey().getValue().getPath();
                p.sendMessage(Text.literal("§c☠ Вы погибли: §fX " + p.getBlockX() + " Y " + p.getBlockY()
                    + " Z " + p.getBlockZ() + " §7(" + dim + ")"), false);
            }
        }
        wasDead = dead;

        // авто-спринт (то же, что «переключаемый спринт» в настройках)
        boolean want = XiveConfig.SPRINT.on && mc.currentScreen == null
            && mc.options.forwardKey.isPressed() && !p.isSneaking() && !p.isUsingItem()
            && p.getHungerManager().getFoodLevel() > 6;
        if (want) {
            mc.options.sprintKey.setPressed(true);
            forcedSprint = true;
        } else if (forcedSprint) {
            mc.options.sprintKey.setPressed(false);
            forcedSprint = false;
        }
    }

    private static void updateZoom(MinecraftClient mc) {
        boolean held = XiveConfig.ZOOM.on && zoomKey != null && zoomKey.isPressed() && mc.currentScreen == null;
        if (held && !zoomed) {
            savedFov = mc.options.getFov().getValue();
            mc.options.getFov().setValue(30);
            zoomed = true;
        } else if (!held && zoomed) {
            mc.options.getFov().setValue(savedFov);
            zoomed = false;
        }
    }

    /** Вызывается, когда игрок бьёт существо. */
    public static void onHit() {
        hits++;
        lastCombat = System.currentTimeMillis();
        combo++;
        if (XiveConfig.HITSOUND.on) {
            MinecraftClient.getInstance().getSoundManager()
                .play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.5f, 0.5f));
        }
    }

    /** Фильтр чата: false — сообщение скрывается. */
    public static boolean allowChat(Text msg, boolean overlay) {
        if (overlay || !XiveConfig.CHATFILTER.on || XiveConfig.FILTER_WORDS.isEmpty()) return true;
        String s = msg.getString().toLowerCase();
        for (String w : XiveConfig.FILTER_WORDS) {
            if (!w.isEmpty() && s.contains(w.toLowerCase())) return false;
        }
        return true;
    }

    /** Обработка сообщений чата: время и звук при упоминании ника. */
    public static Text onChat(Text msg, boolean overlay) {
        if (overlay) return msg;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (XiveConfig.MENTION.on && mc.player != null) {
            String me = mc.player.getName().getString();
            String s = msg.getString();
            if (s.contains(me) && !s.startsWith("<" + me + ">")) {
                mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 1f));
            }
        }
        if (XiveConfig.CHATTIME.on) {
            return Text.literal("§7[" + LocalTime.now().format(HM) + "] §r").append(msg);
        }
        return msg;
    }

    private XiveFeatures() {}
}
