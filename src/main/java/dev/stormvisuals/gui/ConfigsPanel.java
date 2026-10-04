package dev.stormvisuals.gui;

import dev.stormvisuals.config.ConfigManager;
import dev.stormvisuals.config.ConfigStore;
import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.PngIconRenderer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** The Configs tab shares the click GUI's coordinates, typography and transition. */
final class ConfigsPanel {
    private static final int WHITE = 0xFFFFFFFF, MUTED = 0x90FFFFFF;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy / HH:mm").withZone(ZoneId.systemDefault());
    private static final Identifier CONFIG_ICON = Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/configs.png");
    private final Runnable applied;
    private int selectionAnchor;
    private final EditBox input = new EditBox(Minecraft.getInstance().font, 0, 0, 460, 20,
            Component.literal("Название конфига")) {
        @Override
        public void setHighlightPos(int position) {
            super.setHighlightPos(position);
            selectionAnchor = Math.clamp(position, 0, getValue().length());
        }
    };
    private List<ConfigStore.Entry> entries = List.of();
    private final List<ConfigStore.Entry> filtered = new ArrayList<>();
    private String filter = "", status = "", activeFile;
    private String modal;
    private ConfigStore.Entry target;
    private boolean busy, composing, error;
    private float alpha = 1, mx, my, compose, scroll, listTop = 246, inputOffset;
    private long previousFrame = System.nanoTime();

    ConfigsPanel(Runnable applied) {
        this.applied = applied;
        input.setMaxLength(48);
        input.setBordered(false);
    }

    void refresh() {
        if (!busy) run(ConfigManager::refresh, null);
    }

    private void filterEntries() {
        filtered.clear();
        for (ConfigStore.Entry entry : entries)
            if (entry.name().toLowerCase(Locale.ROOT).contains(filter)) filtered.add(entry);
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    void render(GuiGraphics g, float mouseX, float mouseY, float opacity, String query) {
        alpha = opacity; mx = mouseX; my = mouseY;
        long now = System.nanoTime();
        float dt = Math.min(.1f, (now - previousFrame) / 1_000_000_000f);
        previousFrame = now;
        compose += ((composing ? 1 : 0) - compose) * (1 - (float)Math.exp(-dt * 18));
        if (Math.abs(compose - (composing ? 1 : 0)) < .002f) compose = composing ? 1 : 0;
        listTop = 246 + 38 * compose;
        String nextFilter = query.toLowerCase(Locale.ROOT);
        if (!filter.equals(nextFilter)) { filter = nextFilter; scroll = 0; filterEntries(); }
        scroll = Math.clamp(scroll, 0, maxScroll());

        label(g, "Configs", 180, 78, 22, WHITE);
        label(g, "Твои настройки. Один файл. Любой сервер.", 181, 108, 11, MUTED);
        button(g, "Обновить", 604, 78, 92, 30, false);
        button(g, "Папка конфигов", 704, 78, 160, 30, false);

        rect(g, 180, 132, 684, 62 + 38 * compose, 12, 0x0DFFFFFF);
        rect(g, 196, 145, 32, 32, 9, 0x24408CFF);
        label(g, "+", 206, 151, 22, 0xFF8DBAFF);
        label(g, "Нажмите, чтобы создать новый конфиг", 241, 156, 12, WHITE);
        button(g, composing ? "Свернуть" : "Новый конфиг", 708, 144, 140, 32, !composing);
        if (compose > .01f) {
            float old = alpha;
            alpha *= compose;
            g.enableScissor(180, 132, 864, (int)(194 + 38 * compose));
            field(g, 196, 186, 484, input.isFocused() && modal == null);
            button(g, "Создать", 692, 186, 156, 30, true);
            g.disableScissor();
            alpha = old;
        }

        label(g, "Сохранённые", 181, listTop - 28, 13, WHITE);
        String count = Integer.toString(filtered.size());
        label(g, count, 842 - MtsdfFont.width(count, 11), listTop - 26, 11, MUTED);
        g.enableScissor(180, (int)listTop, 864, 520);
        if (filtered.isEmpty()) {
            label(g, busy ? "Загрузка конфигов..." : filter.isEmpty() ? "Пока нет конфигов" : "Ничего не найдено", 200, listTop + 32, 15, MUTED);
            if (!busy && filter.isEmpty()) label(g, "Создай снимок или закинь файл .storm в папку конфигов", 200, listTop + 56, 11, MUTED);
        }
        for (int i = 0; i < filtered.size(); i++) {
            ConfigStore.Entry entry = filtered.get(i);
            float y = listTop + i * 72 - scroll;
            if (y + 64 < listTop || y >= 520) continue;
            boolean active = entry.file().equals(activeFile);
            rect(g, 180, y, 684, 64, 10, active ? 0x22408CFF : 0x0AFFFFFF);
            clippedLabel(g, entry.name(), 196, y + 13, 14, 264, WHITE);
            String details = entry.error() == null ? DATE.format(Instant.ofEpochMilli(entry.created())) + " / " + size(entry.bytes()) : entry.error();
            clippedLabel(g, details, 196, y + 38, 10, 270, entry.error() == null ? MUTED : 0xFFFF8888);
            button(g, active ? "Загружен" : "Загрузить", 478, y + 17, 104, 30, true);
            button(g, "Обновить", 590, y + 17, 96, 30, false);
            button(g, "Имя", 694, y + 17, 70, 30, false);
            button(g, "Удалить", 772, y + 17, 76, 30, false);
        }
        g.disableScissor();
        if (!status.isEmpty()) clippedLabel(g, busy ? "Обработка..." : status, 181, 533, 10, 674, error ? 0xFFFF8888 : 0xFF92C3FF);
        else if (ConfigManager.autosaveError() != null) clippedLabel(g, ConfigManager.autosaveError(), 181, 533, 10, 674, 0xFFFF8888);

        if (modal != null) {
            rect(g, 164, 54, 716, 506, 0, 0xB8000000);
            if (modal.equals("overwrite")) {
                drawOverwriteModal(g);
                return;
            }
            rect(g, 264, 196, 516, 188, 14, 0xFF171A20);
            String title = switch (modal) { case "rename" -> "Новое имя конфига"; case "delete" -> "Удалить конфиг?"; default -> "Обновить снимок?"; };
            label(g, title, 284, 218, 18, WHITE);
            clippedLabel(g, target.name(), 284, 249, 12, 470, MUTED);
            if (modal.equals("rename")) field(g, 284, 272, 476, input.isFocused());
            else label(g, modal.equals("delete") ? "Файл будет удалён. Текущие настройки сохранятся."
                    : "Текущие настройки заменят этот снимок.", 284, 280, 11, MUTED);
            if (error) clippedLabel(g, status, 284, 311, 10, 470, 0xFFFF8888);
            button(g, "Отмена", 516, 332, 112, 32, false);
            button(g, modal.equals("delete") ? "Удалить" : "Сохранить", 640, 332, 120, 32, !modal.equals("delete"));
        }
    }

    boolean modalOpen() { return modal != null; }

    private void drawOverwriteModal(GuiGraphics g) {
        rect(g, 262, 190, 520, 224, 16, 0x60000000);
        rect(g, 264, 186, 516, 224, 16, 0xFF2A303C);
        rect(g, 265, 187, 514, 222, 15, 0xFF14171D);
        rect(g, 284, 206, 42, 42, 11, 0x28408CFF);
        PngIconRenderer.draw(g, CONFIG_ICON, 295, 217, 20, withAlpha(0xFF8DBAFF));
        label(g, "Обновить конфиг", 340, 209, 18, WHITE);
        label(g, "Текущие настройки заменят сохранённые", 340, 235, 11, MUTED);
        rect(g, 284, 266, 476, 58, 10, 0x0CFFFFFF);
        clippedLabel(g, target.name(), 300, 280, 14, 440, WHITE);
        label(g, DATE.format(Instant.ofEpochMilli(target.created())), 300, 302, 10, MUTED);
        if (error) clippedLabel(g, status, 284, 337, 10, 470, 0xFFFF8888);
        button(g, "Отмена", 516, 358, 112, 32, false);
        button(g, busy ? "Обновление..." : "Обновить", 640, 358, 120, 32, true);
    }

    boolean click(float x, float y, int button) {
        if (button != 0 || busy) return true;
        if (modal != null) {
            float footer = modal.equals("overwrite") ? 358 : 332;
            if (hit(x, y, 516, footer, 112, 32)) cancelInput();
            else if (hit(x, y, 640, footer, 120, 32)) submitModal();
            else {
                blur();
                if (modal.equals("rename") && hit(x, y, 284, 272, 476, 30)) focusInput(x, 284);
            }
            return true;
        }
        blur();
        if (hit(x, y, 604, 78, 92, 30)) { refresh(); return true; }
        if (hit(x, y, 704, 78, 160, 30)) { run(ConfigManager::openFolder, "Папка открыта"); return true; }
        if (hit(x, y, 708, 144, 140, 32)) {
            composing = !composing;
            if (composing) { input.setValue(""); input.setFocused(true); }
            return true;
        }
        if (composing && compose > .9f) {
            if (hit(x, y, 196, 186, 484, 30)) { focusInput(x, 196); return true; }
            if (hit(x, y, 692, 186, 156, 30)) { create(); return true; }
        }
        if (y < listTop || y >= 520) return true;
        for (int i = 0; i < filtered.size(); i++) {
            float row = listTop + i * 72 - scroll;
            ConfigStore.Entry entry = filtered.get(i);
            if (hit(x, y, 478, row + 17, 104, 30)) {
                if (entry.error() != null) { report(entry.error(), true); return true; }
                run(done -> ConfigManager.load(entry, result -> {
                    if (result.error() == null) { activeFile = entry.file(); applied.run(); }
                    done.accept(result);
                }), "Загружен: " + entry.name());
                return true;
            }
            String action = hit(x, y, 590, row + 17, 96, 30) ? "overwrite"
                    : hit(x, y, 694, row + 17, 70, 30) ? "rename"
                    : hit(x, y, 772, row + 17, 76, 30) ? "delete" : null;
            if (action != null) {
                if (entry.error() != null && !action.equals("delete")) { report(entry.error(), true); return true; }
                modal = action; target = entry; input.setValue(entry.name());
                error = false;
                input.setFocused(action.equals("rename"));
                if (input.isFocused()) { input.moveCursorTo(0, false); input.moveCursorTo(input.getValue().length(), true); }
                return true;
            }
        }
        return true;
    }

    private void create() {
        String clean;
        try { clean = ConfigStore.validName(input.getValue()); }
        catch (IllegalArgumentException e) { report(e.getMessage(), true); input.setFocused(true); return; }
        run(done -> ConfigManager.create(clean, result -> {
            if (result.error() == null) { composing = false; input.setValue(""); }
            done.accept(result);
        }), "Создан: " + clean + ".storm");
    }

    private void submitModal() {
        ConfigStore.Entry entry = target;
        String action = modal;
        if (action.equals("rename")) {
            String clean;
            try { clean = ConfigStore.validName(input.getValue()); }
            catch (IllegalArgumentException e) { report(e.getMessage(), true); return; }
            run(done -> ConfigManager.rename(entry, clean, result -> {
                if (result.error() == null && entry.file().equals(activeFile)) activeFile = result.selected().file();
                done.accept(result);
            }), "Конфиг переименован");
        } else if (action.equals("delete")) {
            run(done -> ConfigManager.delete(entry, result -> {
                if (result.error() == null && entry.file().equals(activeFile)) activeFile = null;
                done.accept(result);
            }), "Конфиг удалён");
        } else run(done -> ConfigManager.overwrite(entry, done), "Снимок обновлён");
    }

    private void run(Consumer<Consumer<ConfigManager.Result>> operation, String success) {
        busy = true;
        operation.accept(result -> {
            busy = false;
            if (result.error() != null) { report(result.error(), true); return; }
            entries = result.entries(); filterEntries();
            if (success != null) { report(success, false); cancelInput(); }
        });
    }

    private void report(String message, boolean failed) { status = message; error = failed; }
    void blur() { input.setFocused(false); }
    void cancelInput() { modal = null; target = null; blur(); }

    boolean scroll(double amount) {
        if (modal != null || busy) return true;
        scroll = Math.clamp(scroll - (float)amount * 36, 0, maxScroll());
        return true;
    }
    private float maxScroll() { return Math.max(0, filtered.size() * 72 - 8 - (520 - listTop)); }

    boolean key(KeyEvent event) {
        if (busy && (modal != null || input.isFocused())) return true;
        if (event.isEscape() && (modal != null || input.isFocused() || composing)) {
            cancelInput(); composing = false; return true;
        }
        if (modal != null && !input.isFocused()) {
            if (event.key() == GLFW.GLFW_KEY_ENTER) submitModal();
            return true;
        }
        if (!input.isFocused()) return false;
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (modal != null) submitModal(); else create();
        } else input.keyPressed(event);
        return true;
    }

    boolean character(CharacterEvent event) {
        if (!input.isFocused() || busy) return modal != null;
        input.charTyped(event);
        return true;
    }

    private void focusInput(float mouseX, float x) {
        input.setFocused(true);
        String value = input.getValue();
        float at = mouseX - x - 10 + inputOffset;
        int cursor = 0;
        float previousWidth = 0;
        while (cursor < value.length()) {
            int next = value.offsetByCodePoints(cursor, 1);
            float nextWidth = MtsdfFont.advanceWidth(value.substring(0, next), 12);
            if ((previousWidth + nextWidth) * .5f > at) break;
            cursor = next;
            previousWidth = nextWidth;
        }
        input.moveCursorTo(cursor, false);
    }

    private void field(GuiGraphics g, float x, float y, float width, boolean selected) {
        rect(g, x, y, width, 30, 6, selected ? 0x66408CFF : 0xFF202124);
        rect(g, x + 1, y + 1, width - 2, 28, 5, 0xFF171A20);
        g.enableScissor((int)x + 8, (int)y, (int)(x + width - 8), (int)y + 30);
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        float cursorWidth = MtsdfFont.advanceWidth(value.substring(0, cursor), 12);
        inputOffset = Math.max(0, cursorWidth - width + 28);
        float textX = x + 10 - inputOffset;
        if (selected && selectionAnchor != cursor) {
            int start = Math.min(cursor, selectionAnchor);
            int end = Math.max(cursor, selectionAnchor);
            float left = MtsdfFont.advanceWidth(value.substring(0, start), 12);
            float right = MtsdfFont.advanceWidth(value.substring(0, end), 12);
            rect(g, textX + left, y + 6, right - left, 18, 2, 0x70408CFF);
        }
        label(g, value.isEmpty() ? "Название конфига" : value, textX, y + 10, 12, value.isEmpty() ? MUTED : WHITE);
        if (selected && (System.nanoTime() / 500_000_000L & 1) == 0) rect(g, textX + cursorWidth, y + 7, 1, 16, 0, WHITE);
        g.disableScissor();
    }

    private void button(GuiGraphics g, String text, float x, float y, float width, float height, boolean primary) {
        boolean hover = !busy && hit(mx, my, x, y, width, height);
        int background = primary ? hover ? 0xB0408CFF : 0x70408CFF : hover ? 0x24FFFFFF : 0x12FFFFFF;
        rect(g, x, y, width, height, 6, background);
        label(g, text, x + (width - MtsdfFont.width(text, 11)) / 2, y + (height - 11) / 2, 11, busy ? MUTED : WHITE);
    }
    private void clippedLabel(GuiGraphics g, String text, float x, float y, float size, float width, int color) {
        g.enableScissor((int)x, (int)y - 2, (int)(x + width), (int)(y + size + 6));
        label(g, text, x, y, size, color);
        g.disableScissor();
    }
    private void label(GuiGraphics g, String text, float x, float y, float size, int color) { CrosshairControls.text(g, text, x, y, size, withAlpha(color)); }
    private void rect(GuiGraphics g, float x, float y, float width, float height, float radius, int color) { CrosshairControls.box(g, x, y, width, height, radius, withAlpha(color)); }
    private int withAlpha(int color) { return Math.round((color >>> 24) * alpha) << 24 | color & 0xFFFFFF; }
    private static boolean hit(float x, float y, float left, float top, float width, float height) { return x >= left && x < left + width && y >= top && y < top + height; }
    private static String size(long bytes) { return bytes < 1024 ? bytes + " Б" : String.format(Locale.ROOT, "%.1f КБ", bytes / 1024.0); }
}
