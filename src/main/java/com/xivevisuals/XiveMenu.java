package com.xivevisuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Меню настроек: открывается и закрывается правым Shift. */
public class XiveMenu extends Screen {
    private static final int PER_PAGE = 12;   // 3 столбца x 4 ряда
    private static int page = 0;
    private int top, navY;

    public XiveMenu() {
        super(Text.literal("XiveVisuals"));
    }

    private static Text label(XiveConfig.Module m) {
        return Text.literal((m.on ? "§a" : "§c") + m.title);
    }

    private static int pages() {
        return (XiveConfig.MODULES.length + PER_PAGE - 1) / PER_PAGE;
    }

    @Override
    protected void init() {
        int cw = 104, gap = 4;
        int total = 3 * cw + 2 * gap;
        int left = width / 2 - total / 2;
        top = Math.max(28, height / 2 - 90);

        // кнопки функций (наведите курсор — появится описание)
        int start = page * PER_PAGE;
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = start + i;
            if (idx >= XiveConfig.MODULES.length) break;
            XiveConfig.Module m = XiveConfig.MODULES[idx];
            addDrawableChild(ButtonWidget.builder(label(m), b -> {
                    m.on = !m.on;
                    XiveConfig.save();
                    b.setMessage(label(m));
                })
                .dimensions(left + (i % 3) * (cw + gap), top + (i / 3) * 24, cw, 20)
                .tooltip(Tooltip.of(Text.literal(m.desc)))
                .build());
        }

        // листание страниц
        navY = top + 4 * 24 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                page = (page - 1 + pages()) % pages();
                clearAndInit();
            }).dimensions(left, navY, 40, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                page = (page + 1) % pages();
                clearAndInit();
            }).dimensions(left + total - 40, navY, 40, 20).build());

        // палитра неба и частицы
        int half = (total - gap) / 2;
        int y2 = navY + 24;
        addDrawableChild(ButtonWidget.builder(Text.literal("Небо: " + XiveConfig.palette().name()), b -> {
                XiveConfig.palette = (XiveConfig.palette + 1) % XiveConfig.PALETTES.length;
                XiveConfig.save();
                b.setMessage(Text.literal("Небо: " + XiveConfig.palette().name()));
            }).dimensions(left, y2, half, 20)
              .tooltip(Tooltip.of(Text.literal("Меняет цвета космического неба: туманность, аврора, космос, закат.")))
              .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Частицы: " + XiveConfig.particleLevel + "/" + (XiveConfig.PARTICLE_LEVELS.length - 1)), b -> {
                XiveConfig.particleLevel = (XiveConfig.particleLevel + 1) % XiveConfig.PARTICLE_LEVELS.length;
                XiveConfig.save();
                b.setMessage(Text.literal("Частицы: " + XiveConfig.particleLevel + "/" + (XiveConfig.PARTICLE_LEVELS.length - 1)));
            }).dimensions(left + half + gap, y2, half, 20)
              .tooltip(Tooltip.of(Text.literal("Сколько светящихся огоньков летает вокруг вас. 0 — выключены.")))
              .build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Закрыть"), b -> close())
            .dimensions(width / 2 - 50, y2 + 24, 100, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "XiveVisuals", width / 2, top - 18, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, "Стр. " + (page + 1) + "/" + pages(), width / 2, navY + 6, 0xFFAAAAAA);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
