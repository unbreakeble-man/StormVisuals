package dev.stormvisuals.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

/** Chat-only dragging of the built-in HUD blocks, in scaled GUI coordinates. */
public final class HudDragging {
    public static final Position WATERMARK = new Position();
    public static final Position POTIONS = new Position();
    private static Position dragged;
    private static double offsetX;
    private static double offsetY;

    private HudDragging() {}

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen)) return;
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> !click(event));
            ScreenMouseEvents.allowMouseDrag(screen).register((ignored, event, dx, dy) ->
                    !drag(event, screen.width, screen.height));
            ScreenMouseEvents.allowMouseRelease(screen).register((ignored, event) -> !release(event));
            ScreenEvents.remove(screen).register(ignored -> finish());
        });
    }

    public static final class Position {
        private float x, y, width, height;
        private float anchorX, anchorY;
        private boolean moved, visible;

        public void layout(float defaultX, float defaultY, float width, float height, int screenWidth, int screenHeight) {
            this.width = width;
            this.height = height;
            x = Math.clamp(moved ? anchorX * screenWidth : defaultX, 0, Math.max(0, screenWidth - width));
            y = Math.clamp(moved ? anchorY * screenHeight : defaultY, 0, Math.max(0, screenHeight - height));
            visible = true;
        }

        public float x() { return x; }
        public float y() { return y; }
        public void hide() { visible = false; }

        public dev.stormvisuals.config.ConfigState.Position snapshot() {
            var state = new dev.stormvisuals.config.ConfigState.Position();
            state.moved = moved; state.x = anchorX; state.y = anchorY;
            return state;
        }

        public void restore(dev.stormvisuals.config.ConfigState.Position state) {
            state.validate();
            moved = state.moved; anchorX = state.x; anchorY = state.y;
            visible = false;
        }

        private boolean contains(double px, double py) {
            return visible && px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    /** True when this click belongs to a HUD block and must not reach vanilla chat. */
    public static boolean click(MouseButtonEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (event.button() != 0 || client.options.hideGui || !(client.screen instanceof ChatScreen)) return false;
        Position hit = POTIONS.contains(event.x(), event.y()) ? POTIONS
                : WATERMARK.contains(event.x(), event.y()) ? WATERMARK : null;
        if (hit == null) return false;
        dragged = hit;
        offsetX = event.x() - hit.x;
        offsetY = event.y() - hit.y;
        return true;
    }

    public static boolean drag(MouseButtonEvent event, int screenWidth, int screenHeight) {
        if (dragged == null || event.button() != 0) return false;
        dragged.x = (float) Math.clamp(event.x() - offsetX, 0, Math.max(0, screenWidth - dragged.width));
        dragged.y = (float) Math.clamp(event.y() - offsetY, 0, Math.max(0, screenHeight - dragged.height));
        dragged.anchorX = dragged.x / Math.max(1, screenWidth);
        dragged.anchorY = dragged.y / Math.max(1, screenHeight);
        dragged.moved = true;
        return true;
    }

    public static boolean release(MouseButtonEvent event) {
        if (event.button() != 0 || dragged == null) return false;
        finish();
        return true;
    }

    public static void finish() {
        if (dragged != null) dev.stormvisuals.config.ConfigManager.changed();
        dragged = null;
    }
}
