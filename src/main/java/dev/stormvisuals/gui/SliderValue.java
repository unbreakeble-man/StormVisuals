package dev.stormvisuals.gui;

import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.RoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphics;

/** Shared value field for all module sliders. */
final class SliderValue {
    static final int WIDTH = 62;
    static final int HEIGHT = 24;

    private SliderValue() {}

    static void draw(GuiGraphics graphics, String text, float right, float top, float opacity) {
        float alpha = Math.clamp(opacity, 0, 1);
        int background = Math.round(255 * alpha) << 24 | 0x202124;
        int foreground = Math.round(230 * alpha) << 24 | 0xFFFFFF;
        RoundedRectRenderer.draw(graphics, new RoundedRectRenderer.Rect(
                right - WIDTH, top, WIDTH, HEIGHT, 6, 6, 6, 6, background));
        MtsdfFont.draw(graphics, text, right - (WIDTH + MtsdfFont.width(text, 11)) / 2,
                top + 7, 11, foreground);
    }
}
