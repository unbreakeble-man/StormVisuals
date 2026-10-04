package dev.stormvisuals.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stormvisuals.keybind.Keybinds;
import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.RoundedRectRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Keybind management in the click GUI's existing coordinate space. */
final class KeybindsPanel {
    private static final int X = 180, WIDTH = 684, BOTTOM = 544, ROW = 58, GAP = 8;
    private static final int INPUT_WIDTH = 568, ACTION_X = 760;
    private final List<KeyMapping> functions;
    private final Consumer<KeyMapping> functionChanged;
    private final List<Entry> entries = new ArrayList<>();
    private int selectionAnchor;
    private final EditBox input = new EditBox(Minecraft.getInstance().font, 0, 0, INPUT_WIDTH - 24, 20,
            net.minecraft.network.chat.Component.literal("Команда")) {
        @Override
        public void setHighlightPos(int position) {
            super.setHighlightPos(position);
            selectionAnchor = Math.clamp(position, 0, getValue().length());
        }
    };
    private boolean commands, dirty = true;
    private String query = "", error = "";
    private KeyMapping binding;
    private Keybinds.Command bindingCommand, editing;
    private float scroll, targetScroll, alpha = 1, mouseX, mouseY, inputOffset;
    private long lastRender = System.nanoTime(), seenRevision = -1;

    private record Entry(String name, String key, KeyMapping binding, Keybinds.Command command) { }

    KeybindsPanel(List<KeyMapping> functions, Consumer<KeyMapping> functionChanged) {
        this.functions = List.copyOf(functions);
        this.functionChanged = functionChanged;
        input.setMaxLength(257); // Optional leading slash plus vanilla's 256-character command.
        input.setBordered(false);
    }

    boolean isBinding() { return binding != null; }
    boolean isBinding(KeyMapping key) { return binding == key; }

    void beginBinding(KeyMapping key) {
        binding = key;
        bindingCommand = null;
        input.setFocused(false);
    }

    private void beginBinding(Keybinds.Command command) {
        beginBinding(command.binding());
        bindingCommand = command;
    }

    void blur() {
        binding = null;
        bindingCommand = null;
        input.setFocused(false);
        dirty = true;
    }

    void filter(String text) {
        query = text.toLowerCase(Locale.ROOT);
        dirty = true;
        scroll = targetScroll = 0;
    }

    private int top() { return commands ? 192 : 148; }
    private int bindX() { return commands ? 516 : 610; }

    private void refresh() {
        if (!dirty && seenRevision == Keybinds.revision()) return;
        entries.clear();
        if (commands) {
            for (Keybinds.Command command : Keybinds.commands()) {
                if (command == bindingCommand || command.command().toLowerCase(Locale.ROOT).contains(query)
                        || command.keyLabel().toLowerCase(Locale.ROOT).contains(query))
                    entries.add(new Entry("/" + command.command(), command.keyLabel(), command.binding(), command));
            }
        } else {
            for (KeyMapping key : functions) {
                if (key.isUnbound()) continue;
                String name = net.minecraft.network.chat.Component.translatable(key.getName()).getString();
                String label = key.getTranslatedKeyMessage().getString();
                if (name.toLowerCase(Locale.ROOT).contains(query) || label.toLowerCase(Locale.ROOT).contains(query))
                    entries.add(new Entry(name, label, key, null));
            }
        }
        seenRevision = Keybinds.revision();
        dirty = false;
        targetScroll = Math.clamp(targetScroll, 0, maxScroll());
        scroll = Math.clamp(scroll, 0, maxScroll());
    }

    private float maxScroll() {
        return Math.max(0, entries.size() * (ROW + GAP) - GAP - (BOTTOM - top()));
    }

    void render(GuiGraphics g, float mouseX, float mouseY, float alpha) {
        this.alpha = alpha;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        refresh();
        long now = System.nanoTime();
        scroll += (targetScroll - scroll) * (float) (1 - Math.exp(-24 * (now - lastRender) / 1_000_000_000.0));
        lastRender = now;
        button(g, "Бинды функций", X, 70, 182, !commands, true);
        button(g, "Бинды команд", X + 194, 70, 182, commands, true);
        if (commands) {
            drawInput(g, now);
            button(g, editing == null ? "+ Создать" : "Сохранить", ACTION_X, 114, 104, true, true);
        }
        String hint = !error.isEmpty() ? error : Keybinds.saveError() != null ? Keybinds.saveError()
                : isBinding() ? "Нажмите клавишу или кнопку мыши. Esc: отмена. Delete / Backspace: снять бинд"
                : commands ? "Команда выполняется сразу в игре. Нажмите на текст команды, чтобы изменить её."
                : "Нажмите на клавишу для перебинда. Новые бинды: средняя кнопка мыши на карточке функции.";
        label(g, hint, X, commands ? 160 : 116, 11,
                !error.isEmpty() || Keybinds.saveError() != null ? 0xFFFF8585 : 0x80FFFFFF);
        g.enableScissor(X, top(), X + WIDTH, BOTTOM);
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float y = top() + i * (ROW + GAP) - scroll;
            if (y + ROW <= top() || y >= BOTTOM) continue;
            rect(g, X, y, WIDTH, ROW, 10, 0x0DFFFFFF);
            g.enableScissor(X + 12, (int) y, bindX() - 12, (int) y + ROW);
            label(g, entry.name, X + 14, y + (commands ? 12 : 21), 14, 0xFFFFFFFF);
            if (commands) label(g, "Изменить команду", X + 14, y + 35, 10, 0x80FFFFFF);
            g.disableScissor();
            String key = binding == entry.binding ? "[...]" : entry.key;
            button(g, key, bindX(), y + 13, 128, binding == entry.binding, true);
            button(g, "Снять", bindX() + 136, y + 13, commands ? 86 : 106, false, !entry.binding.isUnbound());
            if (commands) button(g, "Удалить", 746, y + 13, 106, false, true);
        }
        if (entries.isEmpty()) label(g, commands ? "Нет команд. Создайте новую выше" : "Нет назначенных биндов",
                X + 14, top() + 24, 13, 0x80FFFFFF);
        g.disableScissor();
        if (maxScroll() > 0) {
            float height = BOTTOM - top();
            float thumb = Math.max(24, height * height / (height + maxScroll()));
            rect(g, X + WIDTH - 4, top() + scroll / maxScroll() * (height - thumb), 3, thumb, 1.5F, 0x60408CFF);
        }
    }

    private void drawInput(GuiGraphics g, long now) {
        rect(g, X, 114, INPUT_WIDTH, 32, 8, input.isFocused() ? 0x22408CFF : 0x0DFFFFFF);
        String value = input.getValue();
        int cursor = input.getCursorPosition();
        float cursorWidth = MtsdfFont.advanceWidth(value.substring(0, cursor), 12);
        inputOffset = Math.max(0, cursorWidth - (INPUT_WIDTH - 36));
        g.enableScissor(X + 12, 116, X + INPUT_WIDTH - 12, 144);
        float textX = X + 12 - inputOffset;
        if (selectionAnchor != cursor && input.isFocused()) {
            int start = Math.min(cursor, selectionAnchor);
            int end = Math.max(cursor, selectionAnchor);
            float left = MtsdfFont.advanceWidth(value.substring(0, start), 12);
            float right = MtsdfFont.advanceWidth(value.substring(0, end), 12);
            rect(g, textX + left, 120, right - left, 18, 0, 0x60408CFF);
        }
        label(g, value.isEmpty() ? "Команда, например /spawn" : value, textX, 124, 12,
                value.isEmpty() ? 0x80FFFFFF : 0xFFFFFFFF);
        if (input.isFocused() && (now / 500_000_000L) % 2 == 0)
            rect(g, textX + cursorWidth, 122, 1, 14, 0, 0xFFFFFFFF);
        g.disableScissor();
    }

    boolean click(float x, float y, int button) {
        if (binding != null) {
            finishBinding(InputConstants.Type.MOUSE.getOrCreate(button));
            return true;
        }
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        refresh();
        input.setFocused(false);
        if (inside(x, y, X, 70, 376, 32)) {
            commands = x >= X + 194;
            dirty = true;
            error = "";
            scroll = targetScroll = 0;
            return true;
        }
        if (commands) {
            if (inside(x, y, X, 114, INPUT_WIDTH, 32)) {
                input.setFocused(true);
                String value = input.getValue();
                float at = x - X - 12 + inputOffset;
                int cursor = 0;
                float previousWidth = 0;
                while (cursor < value.length()) {
                    int next = value.offsetByCodePoints(cursor, 1);
                    float nextWidth = MtsdfFont.advanceWidth(value.substring(0, next), 12);
                    if ((previousWidth + nextWidth) * 0.5F > at) break;
                    cursor = next;
                    previousWidth = nextWidth;
                }
                input.moveCursorTo(cursor, false);
                return true;
            }
            if (inside(x, y, ACTION_X, 114, 104, 32)) { saveCommand(); return true; }
        }
        if (y < top() || y >= BOTTOM) return true;
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            float rowY = top() + i * (ROW + GAP) - scroll;
            if (inside(x, y, bindX(), rowY + 13, 128, 32)) {
                if (entry.command == null) beginBinding(entry.binding);
                else beginBinding(entry.command);
                return true;
            }
            if (inside(x, y, bindX() + 136, rowY + 13, commands ? 86 : 106, 32)) {
                if (entry.command == null) setFunction(entry.binding, InputConstants.UNKNOWN);
                else Keybinds.bind(entry.command, InputConstants.UNKNOWN);
                dirty = true;
                return true;
            }
            if (commands && inside(x, y, 746, rowY + 13, 106, 32)) {
                if (editing == entry.command) cancelEdit();
                Keybinds.remove(entry.command);
                dirty = true;
                return true;
            }
            if (commands && inside(x, y, X, rowY, 324, ROW)) {
                editing = entry.command;
                input.setValue("/" + editing.command());
                input.setFocused(true);
                error = "";
                return true;
            }
        }
        return true;
    }

    boolean scroll(double vertical) {
        refresh();
        targetScroll = Math.clamp(targetScroll - (float) vertical * 40, 0, maxScroll());
        return true;
    }

    boolean key(KeyEvent event) {
        if (binding != null) {
            if (event.isEscape()) blur();
            else if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE)
                finishBinding(InputConstants.UNKNOWN);
            else finishBinding(InputConstants.getKey(event));
            return true;
        }
        if (!commands || !input.isFocused()) return false;
        if (event.isEscape()) { cancelEdit(); return true; }
        if (event.isConfirmation()) { saveCommand(); return true; }
        input.keyPressed(event);
        error = "";
        return true;
    }

    boolean character(CharacterEvent event) {
        if (binding != null) return true;
        if (!commands || !input.isFocused()) return false;
        input.charTyped(event);
        error = "";
        return true;
    }

    private void saveCommand() {
        try {
            if (editing == null) {
                Keybinds.Command created = Keybinds.add(input.getValue());
                beginBinding(created);
                dirty = true;
                refresh();
                scroll = targetScroll = maxScroll();
            } else Keybinds.edit(editing, input.getValue());
            cancelEdit();
            dirty = true;
        } catch (IllegalArgumentException e) {
            error = e.getMessage();
            input.setFocused(true);
        }
    }

    private void cancelEdit() {
        editing = null;
        input.setValue("");
        input.setFocused(false);
        error = "";
    }

    private void finishBinding(InputConstants.Key key) {
        if (bindingCommand == null) setFunction(binding, key);
        else Keybinds.bind(bindingCommand, key);
        blur();
        dirty = true;
    }

    private void setFunction(KeyMapping key, InputConstants.Key value) {
        key.setKey(value);
        KeyMapping.resetMapping();
        Keybinds.changed();
        functionChanged.accept(key);
    }

    private void button(GuiGraphics g, String text, float x, float y, float width, boolean selected, boolean enabled) {
        boolean hover = enabled && inside(mouseX, mouseY, x, y, width, 32);
        rect(g, x, y, width, 32, 8, selected ? 0x50408CFF : hover ? 0x24FFFFFF : 0x12FFFFFF);
        g.enableScissor((int) x + 4, (int) y, (int) (x + width - 4), (int) y + 32);
        label(g, text, x + Math.max(7, (width - MtsdfFont.width(text, 12)) / 2), y + 11, 12,
                enabled ? 0xFFFFFFFF : 0x50FFFFFF);
        g.disableScissor();
    }

    private static boolean inside(float x, float y, float left, float top, float width, float height) {
        return x >= left && y >= top && x < left + width && y < top + height;
    }

    private int color(int argb) { return Math.round((argb >>> 24) * alpha) << 24 | argb & 0xFFFFFF; }
    private void rect(GuiGraphics g, float x, float y, float width, float height, float radius, int argb) {
        RoundedRectRenderer.draw(g, new RoundedRectRenderer.Rect(x, y, width, height,
                radius, radius, radius, radius, color(argb)));
    }
    private void label(GuiGraphics g, String text, float x, float y, float size, int argb) {
        MtsdfFont.draw(g, text, x, y, size, color(argb));
    }
}
