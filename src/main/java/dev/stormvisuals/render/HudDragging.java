package dev.stormvisuals.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import dev.stormvisuals.gui.HudSettingsScreen;
import java.util.ArrayList;
import java.util.List;

/** Chat-only dragging of the built-in HUD blocks, in scaled GUI coordinates. */
public final class HudDragging {
    public static final Position WATERMARK = new Position();
    public static final Position POTIONS = new Position();
    public static final Position COOLDOWNS = new Position();
    public static final Position HOTKEYS = new Position();
    public static final Position QUESTS = new Position();
    public static final Position AUCTION_TIMER = new Position();
    private static final Position[] POSITIONS = {WATERMARK, POTIONS, COOLDOWNS, HOTKEYS, QUESTS, AUCTION_TIMER};
    private static Position dragged;
    private static double offsetX;
    private static double offsetY;
    private static float axisX, axisY, targetX, targetY;
    private static long axisTime;

    private HudDragging() {}

    public static void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            finish();
            for (Position position : POSITIONS) position.hide();
            axisX = axisY = targetX = targetY = 0;
        });
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
        private boolean moved, visible, centerX, centerY;
        private float scale = 1, cornerRadius = -1;
        private final List<String> favorites = new ArrayList<>();
        private HudListPanel panel;

        public void layout(float defaultX, float defaultY, float width, float height, int screenWidth, int screenHeight) {
            this.width = width;
            this.height = height;
            x = Math.clamp(centerX ? (screenWidth - width) / 2 : moved ? anchorX * screenWidth : defaultX, 0, Math.max(0, screenWidth - width));
            y = Math.clamp(centerY ? (screenHeight - height) / 2 : moved ? anchorY * screenHeight : defaultY, 0, Math.max(0, screenHeight - height));
            visible = true;
        }

        public float x() { return x; }
        public float y() { return y; }
        public float width() { return width; }
        public float height() { return height; }
        public float scale() { return scale; }
        public void scale(float value) { scale = Math.clamp(value, .5f, 2.5f); }
        public float radius(float defaultRadius) { return cornerRadius < 0 ? defaultRadius : cornerRadius; }
        public void setRadius(float value) { cornerRadius = Math.clamp(value, 0, 22); }
        public boolean favorite(String key) { return favorites.contains(key); }
        public List<String> favorites() { return List.copyOf(favorites); }
        public void toggleFavorite(String key) {
            if (!favorites.remove(key) && favorites.size() < 256) favorites.add(key);
        }
        void panel(HudListPanel panel) { this.panel = panel; }
        public List<HudListPanel.Row> rows() { return panel == null ? List.of() : panel.currentRows(); }
        public void hide() { visible = false; if (dragged == this) finish(); }

        public dev.stormvisuals.config.ConfigState.Position snapshot() {
            var state = new dev.stormvisuals.config.ConfigState.Position();
            state.moved = moved; state.x = anchorX; state.y = anchorY;
            state.centerX = centerX; state.centerY = centerY;
            state.scale = scale; state.cornerRadius = cornerRadius;
            state.favorites = new ArrayList<>(favorites);
            return state;
        }

        public void restore(dev.stormvisuals.config.ConfigState.Position state) {
            state.validate();
            moved = state.moved; anchorX = state.x; anchorY = state.y;
            centerX = state.centerX; centerY = state.centerY;
            scale = state.scale; cornerRadius = state.cornerRadius;
            favorites.clear(); favorites.addAll(state.favorites);
            visible = false;
        }

        private boolean contains(double px, double py) {
            return visible && px >= x && px <= x + width && py >= y && py <= y + height;
        }
    }

    /** True when this click belongs to a HUD block and must not reach vanilla chat. */
    public static boolean click(MouseButtonEvent event) {
        Minecraft client = Minecraft.getInstance();
        if ((event.button() != 0 && event.button() != 1) || client.options.hideGui || !(client.screen instanceof ChatScreen)) return false;
        Position hit = hit(event.x(), event.y());
        if (hit == null) return false;
        if (event.button() == 1) {
            if (hit == AUCTION_TIMER) return true;
            finish();
            client.setScreen(new HudSettingsScreen(client.screen, hit));
            return true;
        }
        dragged = hit;
        offsetX = event.x() - hit.x;
        offsetY = event.y() - hit.y;
        return true;
    }

    public static Position hit(double x, double y) {
        Position hit = null;
        for (int i = POSITIONS.length - 1; i >= 0; i--)
            if (POSITIONS[i].contains(x, y)) { hit = POSITIONS[i]; break; }
        return hit;
    }

    public static boolean drag(MouseButtonEvent event, int screenWidth, int screenHeight) {
        if (dragged == null || event.button() != 0) return false;
        float rawX = (float) (event.x() - offsetX);
        float rawY = (float) (event.y() - offsetY);
        float distanceX = Math.abs(rawX + dragged.width / 2 - screenWidth / 2f);
        float distanceY = Math.abs(rawY + dragged.height / 2 - screenHeight / 2f);
        float pixels = Minecraft.getInstance().getMainRenderTarget().width / (float) Math.max(1, screenWidth);
        dragged.centerX = distanceX * pixels <= (dragged.centerX ? 16 : 9);
        dragged.centerY = distanceY * pixels <= (dragged.centerY ? 16 : 9);
        targetX = Math.clamp(1 - distanceX * pixels / 32, 0, 1);
        targetY = Math.clamp(1 - distanceY * pixels / 32, 0, 1);
        dragged.x = Math.clamp(dragged.centerX ? (screenWidth - dragged.width) / 2 : rawX, 0, Math.max(0, screenWidth - dragged.width));
        dragged.y = Math.clamp(dragged.centerY ? (screenHeight - dragged.height) / 2 : rawY, 0, Math.max(0, screenHeight - dragged.height));
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
        targetX = targetY = 0;
    }

    public static void renderGuides(GuiGraphics graphics) {
        long now = System.nanoTime();
        float blend = axisTime == 0 ? 1 : 1 - (float) Math.exp(-18 * (now - axisTime) / 1_000_000_000.0);
        axisTime = now;
        if (!(Minecraft.getInstance().screen instanceof ChatScreen)) { axisX = axisY = 0; return; }
        axisX += (targetX - axisX) * blend;
        axisY += (targetY - axisY) * blend;
        int x = graphics.guiWidth() / 2, y = graphics.guiHeight() / 2;
        if (axisX > .01f) graphics.fill(x, 0, x + 1, graphics.guiHeight(), Math.round(axisX * 150) << 24 | 0x78ADFF);
        if (axisY > .01f) graphics.fill(0, y, graphics.guiWidth(), y + 1, Math.round(axisY * 150) << 24 | 0x78ADFF);
    }
}
