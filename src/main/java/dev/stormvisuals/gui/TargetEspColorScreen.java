package dev.stormvisuals.gui;

import dev.stormvisuals.module.TargetESP;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Reuses the existing HSV and HEX picker without changing crosshair settings. */
final class TargetEspColorScreen extends Screen {
    private final Screen parent;
    private final CrosshairControls controls = new CrosshairControls(() -> {}, () -> {}, () -> {});
    private float x, y, scale;

    TargetEspColorScreen(Screen parent) {
        super(Component.literal("Цвет TargetESP"));
        this.parent = parent;
        controls.color("Цвет душ", TargetESP::color, TargetESP::setColor);
        controls.position(12, 44, 286);
        controls.click(20, 50, 0);
    }

    private void dimensions() {
        scale = Math.max(0.01F, Math.min(1, Math.min((width - 16) / 310F, (height - 16) / 254F)));
        x = (width - 310 * scale) / 2;
        y = (height - 254 * scale) / 2;
        controls.position(12, 44, 286);
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int mx, int my, float delta) {}

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        dimensions();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        CrosshairControls.box(g, 0, 0, 310, 254, 12, 0xF0121419);
        CrosshairControls.text(g, "Цвет TargetESP", 12, 15, 15, 0xFFFFFFFF);
        controls.render(g, 12, 44, 286, (mx - x) / scale, (my - y) / scale, 1);
        CrosshairControls.box(g, 12, 218, 286, 26, 6, 0x38408CFF);
        CrosshairControls.text(g, "Готово", 135, 227, 11, 0xFFFFFFFF);
        g.pose().popMatrix();
    }

    @Override public boolean mouseClicked(MouseButtonEvent e, boolean twice) {
        dimensions();
        float mx = (float) ((e.x() - x) / scale), my = (float) ((e.y() - y) / scale);
        if (e.button() == 0 && mx >= 12 && mx < 298 && my >= 218 && my < 244) onClose();
        else controls.click(mx, my, e.button());
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        dimensions();
        controls.drag((float) ((e.x() - x) / scale), (float) ((e.y() - y) / scale));
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent e) { controls.release(); return true; }
    @Override public boolean keyPressed(KeyEvent e) { return controls.key(e) || super.keyPressed(e); }
    @Override public boolean charTyped(CharacterEvent e) { return controls.character(e); }
    @Override public void onClose() { controls.finish(); minecraft.setScreen(parent); }
    @Override public void removed() { controls.finish(); controls.cancel(); }
}
