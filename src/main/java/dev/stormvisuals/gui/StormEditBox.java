package dev.stormvisuals.gui;

import dev.stormvisuals.render.MtsdfFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Native editing/clipboard/navigation with the same MTSDF typography as the mod. */
final class StormEditBox extends EditBox {
    private final String placeholder;
    private int anchor;
    private float offset;
    private final ClickGuiScreen.Motion glow = new ClickGuiScreen.Motion(0, 24);
    StormEditBox(int x, int y, int width, int height, String placeholder) {
        super(Minecraft.getInstance().font, x, y, width, height, Component.literal(placeholder));
        this.placeholder = placeholder;
        setBordered(false);
    }
    @Override public void setHighlightPos(int position) {
        super.setHighlightPos(position);
        anchor = Math.clamp(position, 0, getValue().length());
    }
    @Override public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
        glow.to(isFocused() || isMouseOver(mouseX, mouseY) ? 1 : 0);
        float h = glow.update(System.nanoTime());
        CrosshairControls.box(g, getX(), getY(), getWidth(), getHeight(), 8, Math.round(14 + h * 22) << 24 | 0x408CFF);
        String value = getValue();
        int cursor = Math.clamp(getCursorPosition(), 0, value.length());
        float caret = MtsdfFont.width(value.substring(0, cursor), 12);
        if (caret - offset > getWidth() - 22) offset = caret - getWidth() + 22;
        if (caret - offset < 0) offset = caret;
        offset = Math.max(0, Math.min(offset, MtsdfFont.width(value, 12)));
        float tx = getX() + 10 - offset, ty = getY() + (getHeight() - 12) / 2f;
        g.enableScissor(getX() + 8, getY() + 2, getRight() - 8, getBottom() - 2);
        if (isFocused() && anchor != cursor) {
            int start = Math.min(anchor, cursor), end = Math.max(anchor, cursor);
            float sx = tx + MtsdfFont.width(value.substring(0, start), 12);
            CrosshairControls.box(g, sx, ty - 2, MtsdfFont.width(value.substring(start, end), 12), 16, 2, 0x70408CFF);
        }
        MtsdfFont.draw(g, value.isEmpty() && !isFocused() ? placeholder : value, tx, ty, 12,
                value.isEmpty() ? 0xFF8693AB : 0xFFFFFFFF);
        if (isFocused() && System.nanoTime() / 450_000_000L % 2 == 0)
            CrosshairControls.box(g, tx + caret, ty - 1, 1, 14, 0, 0xFFFFFFFF);
        g.disableScissor();
    }
    @Override public void onClick(MouseButtonEvent e, boolean doubleClick) {
        if (doubleClick) { moveCursorToEnd(false); setHighlightPos(0); return; }
        moveCursorTo(clickedPosition(e), e.hasShiftDown());
    }
    @Override protected void onDrag(MouseButtonEvent e, double dx, double dy) { moveCursorTo(clickedPosition(e), true); }
    private int clickedPosition(MouseButtonEvent e) {
        float local = (float) e.x() - getX() - 10 + offset;
        String value = getValue();
        int position = 0;
        while (position < value.length() && MtsdfFont.width(value.substring(0, position + 1), 12) < local) position++;
        return position;
    }
}
