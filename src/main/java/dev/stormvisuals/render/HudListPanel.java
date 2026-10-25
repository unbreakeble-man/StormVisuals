package dev.stormvisuals.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.item.ItemStack;

/** Compact header and right-aligned values for the three draggable HUD lists. */
public final class HudListPanel {
    public record Row(String key, String name, String value, boolean warning, Identifier sprite,
                      ItemStack item, int order, float nameWidth, float valueWidth, Identifier texture) {
        public Row(String name, String value, boolean warning) {
            this(name, name, value, warning, null, ItemStack.EMPTY);
        }
        public Row(String key, String name, String value, boolean warning, Identifier sprite, ItemStack item) {
            this(key, name, value, warning, sprite, item, 0);
        }
        public Row(String key, String name, String value, boolean warning, Identifier sprite, ItemStack item, int order) {
            this(key, name, value, warning, sprite, item, order, MtsdfFont.width(name, 11), MtsdfFont.width(value, 10), null);
        }
        public static Row textured(String key, String name, String value, boolean warning, Identifier texture, int order) {
            return new Row(key, name, value, warning, null, ItemStack.EMPTY, order,
                    MtsdfFont.width(name, 11), MtsdfFont.width(value, 10), texture);
        }
        boolean hasIcon() { return texture != null || sprite != null || !item.isEmpty(); }
    }

    private static final class AnimatedRow {
        Row row;
        boolean present;
        float visibility, y = Float.NaN, badgeWidth, warning;

        AnimatedRow(Row row) {
            this.row = row;
            badgeWidth = row.valueWidth + 12;
            warning = row.warning ? 1 : 0;
        }
    }
    private final String title, empty;
    private final Identifier icon;
    private final HudDragging.Position position;
    private final BackdropBlurRenderer blur = new BackdropBlurRenderer();
    private final List<AnimatedRow> rows = new ArrayList<>();
    private final float minimumWidth, emptyWidth;
    private float width, height = 44, visibility, emptyVisibility;
    private boolean hasRows;
    private long frameTime;
    private Object world;

    public HudListPanel(String title, String empty, Identifier icon, HudDragging.Position position) {
        this.title = title;
        this.empty = empty;
        this.icon = icon;
        this.position = position;
        position.panel(this);
        minimumWidth = Math.max(142, MtsdfFont.width(title, 13) + 60);
        emptyWidth = Math.max(minimumWidth, MtsdfFont.width(empty, 11) + 24);
        width = minimumWidth;
    }

    public void initialize() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset();
            blur.close();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> blur.close());
    }

    public void rows(List<Row> rows) {
        Object level = Minecraft.getInstance().level;
        if (world != level) { reset(); world = level; }
        hasRows = !rows.isEmpty();
        for (AnimatedRow animated : this.rows) animated.present = false;
        for (Row row : rows) {
            AnimatedRow match = null;
            for (AnimatedRow animated : this.rows)
                if (animated.row.key.equals(row.key)) { match = animated; break; }
            if (match == null) { match = new AnimatedRow(row); this.rows.add(match); }
            match.row = row;
            match.present = true;
        }
        this.rows.sort(Comparator.comparing((AnimatedRow animated) -> !position.favorite(animated.row.key))
                .thenComparingInt(animated -> animated.row.order)
                .thenComparing(animated -> animated.row.name));
    }

    public List<Row> currentRows() {
        return rows.stream().filter(animated -> animated.present).map(animated -> animated.row).toList();
    }

    public void render(GuiGraphics graphics, float defaultY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null || world != client.level) {
            reset();
            return;
        }
        long now = System.nanoTime();
        float dt = frameTime == 0 ? 0 : Math.max(0, (now - frameTime) / 1_000_000_000f);
        frameTime = now;
        boolean editing = client.screen instanceof ChatScreen || client.screen instanceof dev.stormvisuals.gui.HudSettingsScreen;
        visibility = approach(visibility, hasRows || editing ? 1 : 0, 15, dt);
        float contentHeight = 0, targetWidth = minimumWidth;
        for (AnimatedRow animated : rows) {
            animated.visibility = approach(animated.visibility, animated.present ? 1 : 0, 18, dt);
            animated.badgeWidth = approach(animated.badgeWidth, animated.row.valueWidth + 12, 22, dt);
            animated.warning = approach(animated.warning, animated.row.warning ? 1 : 0, 12, dt);
            float targetY = 37 + contentHeight;
            if (Float.isNaN(animated.y)) animated.y = targetY;
            animated.y = approach(animated.y, targetY, 20, dt);
            contentHeight += 25 * animated.visibility;
            targetWidth = Math.max(targetWidth, 54 + (animated.row.hasIcon() ? 23 : 0)
                    + animated.row.nameWidth + Math.max(animated.row.valueWidth, animated.badgeWidth - 12));
        }
        rows.removeIf(animated -> !animated.present && animated.visibility == 0);
        emptyVisibility = approach(emptyVisibility, editing && !hasRows && rows.isEmpty() ? 1 : 0, 15, dt);
        if (emptyVisibility > 0) targetWidth = Math.max(targetWidth, emptyWidth);
        width = approach(width, targetWidth, 18, dt);
        height = approach(height, 44 + Math.max(contentHeight, 25 * emptyVisibility), 20, dt);
        if (visibility == 0) { position.hide(); return; }
        float pixels = client.getMainRenderTarget().width / (float) graphics.guiWidth();
        float scale = Math.min(1.1f * position.scale() / pixels, Math.min(graphics.guiWidth() / width, graphics.guiHeight() / height))
                * (.96f + .04f * visibility);
        float radius = position.radius(10);
        position.layout(24 / pixels, graphics.guiHeight() * defaultY, width * scale, height * scale,
                graphics.guiWidth(), graphics.guiHeight());
        float x = position.x(), y = position.y();
        blur.prepare(client.getMainRenderTarget(), x * pixels, y * pixels,
                width * scale * pixels, height * scale * pixels, now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        for (int i = 6; i >= 1; i--) {
            float spread = i * 1.25f;
            rect(graphics, -spread, 3 - spread, width + spread * 2, height + spread * 2, radius + spread, alpha(0x03000000, visibility));
        }
        blur.draw(graphics, (int) Math.ceil(width), (int) Math.ceil(height), radius, visibility);
        rect(graphics, 0, 0, width, height, radius, alpha(0xCC080A0C, visibility));
        MtsdfFont.draw(graphics, title, 12, 10, 13, alpha(0xFFFFFFFF, visibility));
        PngIconRenderer.draw(graphics, icon, Math.round(width) - 27, 9, 16, alpha(0xFFFFFFFF, visibility));
        graphics.enableScissor(8, 33, (int) Math.ceil(width - 8), (int) Math.ceil(height - 8));
        if (emptyVisibility > 0) MtsdfFont.draw(graphics, empty, 12, 43, 11, alpha(0x80FFFFFF, visibility * emptyVisibility));
        for (AnimatedRow animated : rows) {
            Row row = animated.row;
            float opacity = visibility * animated.visibility;
            if (opacity < .005f) continue;
            float top = animated.y;
            graphics.pose().pushMatrix();
            graphics.pose().translate(0, -(1 - animated.visibility) * 7);
            if (row.texture != null)
                PngIconRenderer.draw(graphics, row.texture, 12, Math.round(top + 3), 16, alpha(0xFFFFFFFF, opacity));
            else if (row.sprite != null)
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite, 12, Math.round(top + 3), 16, 16, alpha(0xFFFFFFFF, opacity));
            else if (!row.item.isEmpty()) {
                // Vanilla GUI item models have no alpha parameter; scale them in with the row.
                graphics.pose().pushMatrix();
                graphics.pose().translate(20, top + 11);
                float iconScale = (float) Math.sqrt(opacity);
                graphics.pose().scale(iconScale, iconScale);
                graphics.renderItem(row.item, -8, -8);
                graphics.pose().popMatrix();
            }
            float badgeWidth = animated.badgeWidth;
            float badgeX = width - 12 - badgeWidth;
            float nameX = row.hasIcon() ? 35 : 12;
            // The layout already reserves the 18px gap. Do not crop glyphs at its
            // left edge: rounding scaled scissors can erase a thin trailing I.
            graphics.enableScissor((int) Math.floor(nameX - 2), (int) Math.floor(top),
                    Math.max((int) Math.ceil(nameX), (int) Math.ceil(badgeX - 4)), (int) Math.ceil(top + 24));
            MtsdfFont.draw(graphics, row.name, nameX, top + 5, 11, alpha(0xEFFFFFFF, opacity));
            graphics.disableScissor();
            rect(graphics, badgeX, top + 1, badgeWidth, 20, 5,
                    alpha(mix(0x14FFFFFF, 0x24EDBA7C, animated.warning), opacity));
            MtsdfFont.draw(graphics, row.value, badgeX + (badgeWidth - row.valueWidth) / 2, top + 6, 10,
                    alpha(mix(0xDCFFFFFF, 0xFFEDBA7C, animated.warning), opacity));
            graphics.pose().popMatrix();
        }
        graphics.disableScissor();
        graphics.pose().popMatrix();
    }

    private void reset() {
        rows.clear();
        hasRows = false;
        visibility = emptyVisibility = 0;
        width = minimumWidth;
        height = 44;
        frameTime = 0;
        world = null;
        position.hide();
    }

    private static float approach(float value, float target, float speed, float dt) {
        float result = target + (value - target) * (float) Math.exp(-speed * dt);
        return Math.abs(result - target) < .001f ? target : result;
    }

    private static int alpha(int color, float opacity) {
        return Math.round((color >>> 24) * Math.clamp(opacity, 0, 1)) << 24 | color & 0xFFFFFF;
    }

    private static int mix(int from, int to, float progress) {
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            color |= Math.round((from >>> shift & 255) * (1 - progress) + (to >>> shift & 255) * progress) << shift;
        return color;
    }

    private static void rect(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        RoundedRectRenderer.draw(g, new RoundedRectRenderer.Rect(x, y, w, h, radius, radius, radius, radius, color));
    }
}
