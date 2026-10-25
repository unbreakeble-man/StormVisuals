package dev.stormvisuals.gui;

import dev.stormvisuals.render.MtsdfFont;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** Existing StormVisuals palette, font and spring animation in a keyboard-accessible widget. */
public class StormButton extends AbstractButton {
    private final Runnable action;
    private final ClickGuiScreen.Motion hover = new ClickGuiScreen.Motion(0, 24);
    public StormButton(int x, int y, int width, int height, String text, Runnable action) {
        super(x, y, width, height, Component.literal(text));
        this.action = action;
    }
    @Override public void onPress(InputWithModifiers input) { if (active) action.run(); }
    @Override protected void renderContents(GuiGraphics g, int mouseX, int mouseY, float delta) {
        hover.to(active && isHoveredOrFocused() ? 1 : 0);
        float h = hover.update(System.nanoTime());
        int alphaByte = Math.round((active ? 48 + h * 45 : 18) * alpha);
        CrosshairControls.box(g, getX(), getY(), getWidth(), getHeight(), Math.min(8, getHeight() / 3f), alphaByte << 24 | 0x408CFF);
        float size = Math.min(12, getHeight() * .45f);
        String text = getMessage().getString();
        size = Math.min(size, (getWidth() - 8) * size / Math.max(1, MtsdfFont.width(text, size)));
        MtsdfFont.draw(g, text, getX() + (getWidth() - MtsdfFont.width(text, size)) / 2,
                getY() + (getHeight() - size) / 2, size, Math.round((active ? 255 : 110) * alpha) << 24 | 0xFFFFFF);
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
}
