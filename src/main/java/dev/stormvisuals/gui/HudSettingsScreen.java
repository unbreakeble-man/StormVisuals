package dev.stormvisuals.gui;

import dev.stormvisuals.config.ConfigManager;
import dev.stormvisuals.render.HudDragging;
import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.PngIconRenderer;
import dev.stormvisuals.render.BackdropBlurRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** A chat-backed popup: all input belongs to the popup until it is closed. */
public final class HudSettingsScreen extends Screen {
    static final Identifier CLOSE_ICON = Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/close.png");
    private record Choice(String key, String label) {}
    private static final int WIDTH = 208, SEARCH_TOP = 116, LIST_TOP = 138, ROW_HEIGHT = 20, VISIBLE_ROWS = 4;
    private final Screen parent;
    private final HudDragging.Position position;
    private final CrosshairControls controls = new CrosshairControls(() -> {}, () -> {}, ConfigManager::changed);
    private final BackdropBlurRenderer backdrop = new BackdropBlurRenderer();
    private final ClickGuiScreen.Motion animation = new ClickGuiScreen.Motion(0, 26);
    private final ClickGuiScreen.Motion closeHover = new ClickGuiScreen.Motion(0, 26);
    private final ClickGuiScreen.Motion searchHover = new ClickGuiScreen.Motion(0, 26);
    private final ClickGuiScreen.Motion scrollHover = new ClickGuiScreen.Motion(0, 26);
    private final Map<String, ClickGuiScreen.Motion> rowHover = new HashMap<>(), rowSelection = new HashMap<>();
    private final List<Choice> choices = new ArrayList<>(), filtered = new ArrayList<>();
    private String query = "";
    private boolean searchFocused, replaceSearch, closing;
    private int scroll;
    private float x, y, scale, popupHeight, alpha;

    public HudSettingsScreen(Screen parent, HudDragging.Position position) {
        super(Component.literal(name(position)));
        this.parent = parent;
        this.position = position;
        controls.compact();
        controls.number("Размер", 50, 250, 5, () -> position.scale() * 100, value -> position.scale(value / 100));
        controls.number("Скругление", 0, 22, 1, () -> position.radius(position == HudDragging.WATERMARK ? 13 : 10), position::setRadius);
        animation.to(1);
    }

    private static String name(HudDragging.Position position) {
        return position == HudDragging.POTIONS ? "Potions" : position == HudDragging.COOLDOWNS ? "Cooldowns"
                : position == HudDragging.HOTKEYS ? "Hotkeys" : position == HudDragging.QUESTS ? "Квесты Funtime" : "Watermark";
    }

    @Override
    protected void init() {
        controls.finish();
        if (parent.width != width || parent.height != height) parent.resize(width, height);
        var entries = new LinkedHashMap<String, String>();
        if (position == HudDragging.POTIONS) {
            for (var effect : BuiltInRegistries.MOB_EFFECT)
                entries.put(BuiltInRegistries.MOB_EFFECT.getKey(effect).toString(), effect.getDisplayName().getString());
        } else if (position == HudDragging.HOTKEYS) {
            for (var binding : ClickGuiScreen.hudBindings())
                entries.put(binding.getName(), binding.getName() + " [" + binding.getTranslatedKeyMessage().getString() + "]");
        } else if (position == HudDragging.COOLDOWNS && minecraft.player != null) {
            var inventory = minecraft.player.getInventory();
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                var stack = inventory.getItem(i);
                if (!stack.isEmpty()) entries.put(minecraft.player.getCooldowns().getCooldownGroup(stack).toString(), stack.getHoverName().getString());
            }
            for (var row : position.rows()) entries.put(row.key(), row.name());
        } else if (position == HudDragging.WATERMARK) {
            entries.put("fps", "FPS"); entries.put("ping", "Ping"); entries.put("tps", "TPS");
        }
        for (String key : position.favorites()) entries.putIfAbsent(key, savedLabel(key));
        choices.clear();
        entries.forEach((key, label) -> choices.add(new Choice(key, label)));
        choices.sort(Comparator.comparing(Choice::label));
        filter();
    }

    private String savedLabel(String key) {
        Identifier id = Identifier.tryParse(key);
        if (position == HudDragging.COOLDOWNS && id != null && BuiltInRegistries.ITEM.containsKey(id))
            return BuiltInRegistries.ITEM.getValue(id).getName().getString();
        return key;
    }

    private void filter() {
        String text = query.toLowerCase(Locale.ROOT);
        filtered.clear();
        for (Choice choice : choices)
            if (choice.label.toLowerCase(Locale.ROOT).contains(text) || choice.key.toLowerCase(Locale.ROOT).contains(text)) filtered.add(choice);
        scroll = Math.clamp(scroll, 0, Math.max(0, filtered.size() - VISIBLE_ROWS));
    }

    private void layout() {
        // Resizing the HUD must not move the slider under the held mouse button.
        if (controls.dragging()) return;
        popupHeight = LIST_TOP + Math.max(1, Math.min(VISIBLE_ROWS, filtered.size())) * ROW_HEIGHT + 8;
        float pixels = minecraft.getMainRenderTarget().width / (float) Math.max(1, width);
        scale = Math.max(.01f, Math.min(1.05f / pixels, Math.min((width - 8f) / WIDTH, (height - 8f) / popupHeight)));
        float gap = 7 / pixels;
        float right = width - 4 - position.x() - position.width() - gap;
        float left = position.x() - gap - 4;
        float below = height - 4 - position.y() - position.height() - gap;
        float above = position.y() - gap - 4;
        int side;
        if (right >= WIDTH * scale) side = 0;
        else if (left >= WIDTH * scale) side = 1;
        else if (below >= popupHeight * scale) side = 2;
        else if (above >= popupHeight * scale) side = 3;
        else {
            side = 0;
            float fit = right / WIDTH;
            if (left / WIDTH > fit) { side = 1; fit = left / WIDTH; }
            if (below / popupHeight > fit) { side = 2; fit = below / popupHeight; }
            if (above / popupHeight > fit) { side = 3; fit = above / popupHeight; }
            scale = Math.max(.01f, Math.min(scale, fit));
        }
        x = side == 0 ? position.x() + position.width() + gap
                : side == 1 ? position.x() - gap - WIDTH * scale
                : Math.clamp(position.x(), 4, Math.max(4, width - WIDTH * scale - 4));
        y = side == 2 ? position.y() + position.height() + gap
                : side == 3 ? position.y() - gap - popupHeight * scale
                : Math.clamp(position.y(), 4, Math.max(4, height - popupHeight * scale - 4));
        controls.position(10, 28, WIDTH - 20);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        parent.render(graphics, -1, -1, delta);
        layout();
        long now = System.nanoTime();
        alpha = Math.clamp(animation.update(now), 0, 1);
        float mx = (mouseX - x) / scale, my = (mouseY - y) / scale;
        float pixels = minecraft.getMainRenderTarget().width / (float) Math.max(1, width);
        backdrop.prepare(minecraft.getMainRenderTarget(), x * pixels, y * pixels,
                WIDTH * scale * pixels, popupHeight * scale * pixels, now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        for (int i = 6; i >= 1; i--) box(graphics, -i, 2 - i, WIDTH + i * 2, popupHeight + i * 2, 10 + i, 0x03000000);
        backdrop.draw(graphics, WIDTH, (int) Math.ceil(popupHeight), 10, alpha);
        box(graphics, 0, 0, WIDTH, popupHeight, 10, 0xC4000000);
        label(graphics, getTitle().getString(), 10, 9, 12, 0xFFFFFFFF);
        closeHover.to(!closing && inside(mx, my, WIDTH - 24, 5, 18, 18) ? 1 : 0);
        float closeGlow = closeHover.update(now);
        box(graphics, WIDTH - 24, 5, 18, 18, 5, mix(0x0CFFFFFF, 0x28FFFFFF, closeGlow));
        PngIconRenderer.draw(graphics, CLOSE_ICON, WIDTH - 20, 9, 10, color(mix(0xBFFFFFFF, 0xFFFFFFFF, closeGlow)));
        controls.render(graphics, 10, 28, WIDTH - 20, mx, my, alpha);
        label(graphics, "Избранное", 10, 100, 11, 0xFFFFFFFF);
        searchHover.to(!closing && (searchFocused || inside(mx, my, 10, SEARCH_TOP, WIDTH - 20, 18)) ? 1 : 0);
        box(graphics, 10, SEARCH_TOP, WIDTH - 20, 18, 5, mix(0x10FFFFFF, 0x30408CFF, searchHover.update(now)));
        graphics.enableScissor(15, SEARCH_TOP + 1, WIDTH - 15, SEARCH_TOP + 18);
        label(graphics, query.isEmpty() ? "Поиск..." : query, 15, SEARCH_TOP + 5, 10, query.isEmpty() ? 0x70FFFFFF : 0xFFFFFFFF);
        graphics.disableScissor();
        int count = Math.min(VISIBLE_ROWS, filtered.size() - scroll);
        for (int i = 0; i < count; i++) {
            Choice choice = filtered.get(scroll + i);
            float top = LIST_TOP + i * ROW_HEIGHT;
            boolean selected = position.favorite(choice.key);
            ClickGuiScreen.Motion hover = rowHover.computeIfAbsent(choice.key, key -> new ClickGuiScreen.Motion(0, 26));
            hover.to(!closing && inside(mx, my, 6, top, WIDTH - 12, ROW_HEIGHT - 1) ? 1 : 0);
            float glow = hover.update(now);
            ClickGuiScreen.Motion selection = rowSelection.computeIfAbsent(choice.key, key -> new ClickGuiScreen.Motion(selected ? 1 : 0, 26));
            selection.to(selected ? 1 : 0);
            float checked = selection.update(now);
            box(graphics, 6, top, WIDTH - 12, ROW_HEIGHT - 1, 4, mix(mix(0x00FFFFFF, 0x12FFFFFF, glow), 0x38408CFF, checked));
            graphics.enableScissor(10, (int) top, WIDTH - 30, (int) top + ROW_HEIGHT);
            label(graphics, choice.label, 10, top + 5, 10, 0xEFFFFFFF);
            graphics.disableScissor();
            box(graphics, WIDTH - 22, top + 4, 11, 11, 3, mix(mix(0x25FFFFFF, 0x50FFFFFF, glow), 0xFF408CFF, checked));
            if (checked > .001f) box(graphics, WIDTH - 18, top + 8, 3, 3, 1, Math.round(checked * 255) << 24 | 0xFFFFFF);
        }
        if (filtered.isEmpty()) label(graphics, "Нет записей", 10, LIST_TOP + 5, 10, 0x80FFFFFF);
        if (filtered.size() > VISIBLE_ROWS) {
            float track = VISIBLE_ROWS * ROW_HEIGHT;
            float thumb = track * VISIBLE_ROWS / filtered.size();
            scrollHover.to(!closing && inside(mx, my, WIDTH - 9, LIST_TOP, 9, track) ? 1 : 0);
            box(graphics, WIDTH - 6, LIST_TOP + (track - thumb) * scroll / (filtered.size() - VISIBLE_ROWS), 2, thumb, 1,
                    mix(0x70408CFF, 0xFF78ADFF, scrollHover.update(now)));
        }
        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) return true;
        layout();
        if (event.button() == 1) {
            var hit = HudDragging.hit(event.x(), event.y());
            if (hit != null && hit != position) minecraft.setScreen(new HudSettingsScreen(parent, hit));
            else onClose();
            return true;
        }
        float mx = (float) ((event.x() - x) / scale), my = (float) ((event.y() - y) / scale);
        if (!inside(mx, my, 0, 0, WIDTH, popupHeight)) {
            onClose();
            return true;
        }
        if (event.button() != 0) return true;
        if (inside(mx, my, WIDTH - 24, 5, 18, 18)) { onClose(); return true; }
        searchFocused = inside(mx, my, 10, SEARCH_TOP, WIDTH - 20, 18);
        if (searchFocused) { controls.finish(); return true; }
        if (controls.click(mx, my, event.button())) return true;
        if (mx >= 6 && mx < WIDTH - 6 && my >= LIST_TOP) {
            int index = scroll + (int) ((my - LIST_TOP) / ROW_HEIGHT);
            if (my < LIST_TOP + VISIBLE_ROWS * ROW_HEIGHT && index < filtered.size()) {
                position.toggleFavorite(filtered.get(index).key);
                ConfigManager.changed();
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!closing && event.button() == 0) controls.drag((float) ((event.x() - x) / scale), (float) ((event.y() - y) / scale));
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) { controls.release(); return true; }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        float mx = (float) ((mouseX - x) / scale), my = (float) ((mouseY - y) / scale);
        if (!closing && inside(mx, my, 0, LIST_TOP, WIDTH, popupHeight - LIST_TOP - 8))
            scroll = Math.clamp(scroll - (int) Math.signum(vertical), 0, Math.max(0, filtered.size() - VISIBLE_ROWS));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (closing) return true;
        if (event.isEscape()) { onClose(); return true; }
        if (controls.key(event)) return true;
        if (searchFocused) {
            if (event.isSelectAll()) replaceSearch = true;
            else if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                query = replaceSearch ? "" : query.isEmpty() ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1));
                replaceSearch = false; scroll = 0; filter();
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (closing || controls.character(event)) return true;
        if (searchFocused && query.length() < 64) {
            query = (replaceSearch ? "" : query) + event.codepointAsString();
            replaceSearch = false; scroll = 0; filter();
        }
        return true;
    }

    @Override
    public void onClose() { if (!closing) { controls.finish(); closing = true; animation.to(0); } }

    @Override
    public void tick() {
        if (closing && animation.update(System.nanoTime()) < .004f) minecraft.setScreen(parent);
    }

    @Override
    public void removed() { controls.finish(); backdrop.close(); }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }

    private void box(GuiGraphics g, float x, float y, float w, float h, float radius, int color) {
        CrosshairControls.box(g, x, y, w, h, radius, color(color));
    }

    private void label(GuiGraphics g, String text, float x, float y, float size, int color) {
        MtsdfFont.draw(g, text, x, y, size, color(color));
    }

    private int color(int color) { return Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF; }

    private static int mix(int from, int to, float progress) {
        int color = 0;
        for (int shift = 0; shift <= 24; shift += 8)
            color |= Math.round((from >>> shift & 255) * (1 - progress) + (to >>> shift & 255) * progress) << shift;
        return color;
    }
}
