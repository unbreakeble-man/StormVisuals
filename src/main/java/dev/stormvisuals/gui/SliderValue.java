package dev.stormvisuals.gui;

import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.RoundedRectRenderer;
import net.minecraft.client.gui.GuiGraphics;

/** Shared value field for all module sliders. */
final class SliderValue {
    static final int HEIGHT = 24;

    private SliderValue() {}

    static float width(String text) {
        return Math.max(24, (float) Math.ceil(MtsdfFont.width(text, 11)) + 18);
    }

    static void draw(GuiGraphics graphics, String text, float right, float top, float opacity) {
        draw(graphics, text, right, top, opacity, 0);
    }

    static void draw(GuiGraphics graphics, String text, float right, float top, float opacity, float hover) {
        float alpha = Math.clamp(opacity, 0, 1);
        int glow = Math.round(Math.clamp(hover, 0, 1) * 12);
        int background = Math.round(255 * alpha) << 24 | (32 + glow) << 16 | (33 + glow) << 8 | (36 + glow);
        int foreground = Math.round(230 * alpha) << 24 | 0xFFFFFF;
        float width = width(text);
        RoundedRectRenderer.draw(graphics, new RoundedRectRenderer.Rect(
                right - width, top, width, HEIGHT, 6, 6, 6, 6, background));
        MtsdfFont.draw(graphics, text, right - (width + MtsdfFont.width(text, 11)) / 2,
                top + 7, 11, foreground);
    }
}
