package dev.stormvisuals.editor;

import dev.stormvisuals.editor.CanvasDocument.Kind;
import dev.stormvisuals.editor.CanvasDocument.Item;
import dev.stormvisuals.editor.CanvasDocument.TextItem;
import dev.stormvisuals.editor.CanvasDocument.ImageItem;
import dev.stormvisuals.editor.CanvasDocument.Shape;
import dev.stormvisuals.editor.CanvasDocument.State;
import dev.stormvisuals.render.HudRenderSystem;
import dev.stormvisuals.render.RoundedRectRenderer;
import dev.stormvisuals.render.MtsdfFont;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A mouse-driven HUD canvas attached to the normal Minecraft chat screen. */
public final class ChatCanvasEditor {
    private static final CanvasDocument DOCUMENT = new CanvasDocument();
    private static final ImportedImages IMAGES = new ImportedImages();
    private static final int PANEL_WIDTH = 198;
    private static final int PANEL_HEIGHT = 246;
    private static final int MENU_WIDTH = 125;
    private static final int ROW_HEIGHT = 19;
    private static final int ACCENT = 0xFF55B9FF;

    private int screenWidth;
    private int screenHeight;
    private int menuX;
    private int menuY;
    private boolean menuOpen;
    private boolean shapeMenu;
    private boolean editorFocus;
    private final ChatScreen screen;
    private final EditBox textBox;
    private GuiEventListener previousFocus;
    private State textBefore;
    private boolean textEditing;
    private int dragMode = -1; // -1 idle, 0 move, 1..8 resize handles, 9..16 property sliders
    private double dragX;
    private double dragY;
    private Item dragOrigin;
    private State dragBefore;

    private ChatCanvasEditor(ChatScreen screen, int width, int height) {
        this.screen = screen;
        screenWidth = width;
        screenHeight = height;
        textBox = new EditBox(Minecraft.getInstance().font, Math.max(8, width / 2 - 150), 30,
                Math.min(300, width - 16), 20, Component.literal("Текст"));
        textBox.setMaxLength(120);
        textBox.setVisible(false);
        textBox.setResponder(this::textChanged);
        Screens.getButtons(screen).add(textBox);
    }

    public static void initialize() {
        DOCUMENT.loadAsync(Minecraft.getInstance());
        HudRenderSystem.addLayer((graphics, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            for (Item item : DOCUMENT.items()) {
                if (item instanceof Shape shape) RoundedRectRenderer.draw(graphics, shape);
                else if (item instanceof TextItem text) MtsdfFont.draw(graphics,
                        text.text(), text.x(), text.y(), text.size(), text.argb());
                else if (item instanceof ImageItem image) IMAGES.draw(client, graphics, image);
            }
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ChatScreen chat)) {
                return;
            }
            ChatCanvasEditor editor = new ChatCanvasEditor(chat, width, height);
            ScreenEvents.afterRender(screen).register((ignored, graphics, mouseX, mouseY, delta) ->
                    editor.render(graphics));
            ScreenMouseEvents.allowMouseClick(screen).register((ignored, event) -> editor.click(event));
            ScreenMouseEvents.allowMouseDrag(screen).register((ignored, event, dx, dy) -> editor.drag(event));
            ScreenMouseEvents.allowMouseRelease(screen).register((ignored, event) -> editor.release(event));
            ScreenKeyboardEvents.allowKeyPress(screen).register((ignored, event) -> editor.key(event));
            ScreenEvents.remove(screen).register(ignored -> {
                editor.finishDrag();
                editor.finishText(false);
            });
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            DOCUMENT.close();
            IMAGES.close(client);
        });
    }

    private void render(GuiGraphics graphics) {
        graphics.nextStratum();
        Item selected = DOCUMENT.selected();
        if (selected != null) {
            renderSelection(graphics, selected);
            renderPanel(graphics, selected);
        }
        if (textEditing) {
            label(graphics, "Текст · Enter сохранить · Esc отменить",
                    Math.max(8, screenWidth / 2 - 150), 17, 0xFFFFFFFF);
        }
        if (menuOpen) {
            renderMenu(graphics);
        }
    }

    private void renderSelection(GuiGraphics graphics, Item shape) {
        int x0 = Math.round(shape.x());
        int y0 = Math.round(shape.y());
        int x1 = Math.round(shape.x() + shape.width());
        int y1 = Math.round(shape.y() + shape.height());
        graphics.renderOutline(x0 - 1, y0 - 1, x1 - x0 + 2, y1 - y0 + 2, ACCENT);
        for (int i = 0; i < 8; i++) {
            int x = Math.round(handleX(shape, i));
            int y = Math.round(handleY(shape, i));
            graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF17212C);
            graphics.renderOutline(x - 3, y - 3, 7, 7, ACCENT);
        }
    }

    private void renderPanel(GuiGraphics graphics, Item shape) {
        int x = panelX();
        int y = panelY();
        graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xEE101923);
        graphics.renderOutline(x, y, PANEL_WIDTH, PANEL_HEIGHT, 0xFF507A96);
        String name = shape instanceof Shape rect ? rect.kind().label
                : shape instanceof TextItem ? "Текст · Comfortaa Regular" : "PNG изображение";
        label(graphics, name, x + 9, y + 8, 0xFFFFFFFF);
        label(graphics, "X " + Math.round(shape.x()) + "   Y " + Math.round(shape.y()), x + 9, y + 24, 0xFFB6CBD9);
        label(graphics, "W " + Math.round(shape.width()) + "   H " + Math.round(shape.height()), x + 9, y + 38, 0xFFB6CBD9);
        graphics.fill(x + 8, y + 53, x + PANEL_WIDTH - 8, y + 54, 0xFF406075);

        if (shape instanceof Shape rect) {
            String[] names = {"TL", "TR", "BR", "BL", "A", "R", "G", "B"};
            for (int i = 0; i < 8; i++) {
                int value = i < 4 ? Math.round(rect.corner(i)) : rect.channel(i - 4);
                int maximum = i < 4 ? Math.max(1, Math.round(Math.min(rect.width(), rect.height()) / 2)) : 255;
                drawSlider(graphics, x, y + 61 + i * 18, names[i], value, maximum,
                        rect.kind().circle && i < 4);
            }
        } else if (shape instanceof TextItem text) {
            drawSlider(graphics, x, y + 61, "px", Math.round(text.size()), 128, false);
            String[] names = {"A", "R", "G", "B"};
            for (int i = 0; i < 4; i++) {
                drawSlider(graphics, x, y + 79 + i * 18, names[i],
                        (text.argb() >>> (24 - 8 * i)) & 255, 255, false);
            }
            graphics.fill(x + 8, y + 165, x + PANEL_WIDTH - 8, y + 183, 0xFF263C4E);
            label(graphics, "Изменить текст", x + 15, y + 170, 0xFFFFFFFF);
        } else if (shape instanceof ImageItem image) {
            label(graphics, image.file(), x + 9, y + 66, 0xFFB6CBD9);
            graphics.fill(x + 8, y + 165, x + PANEL_WIDTH - 8, y + 183, 0xFF263C4E);
            label(graphics, "Заменить PNG", x + 15, y + 170, 0xFFFFFFFF);
        }
        String[] actions = {"Копия", "Удалить", "Вперёд", "Назад"};
        for (int i = 0; i < 4; i++) {
            int bx = x + 8 + (i % 2) * 92;
            int by = y + 209 + (i / 2) * 17;
            graphics.fill(bx, by, bx + 86, by + 15, 0xFF263C4E);
            label(graphics, actions[i], bx + 5, by + 3, 0xFFFFFFFF);
        }
    }

    private static void drawSlider(GuiGraphics graphics, int x, int y, String name,
                                   int value, int maximum, boolean disabled) {
        label(graphics, name, x + 9, y, disabled ? 0xFF667684 : 0xFFE4EFF6);
        graphics.fill(x + 33, y + 3, x + 150, y + 8, 0xFF32414E);
        int end = x + 33 + Math.round(value * 117f / maximum);
        graphics.fill(x + 33, y + 3, end, y + 8, disabled ? 0xFF657688 : ACCENT);
        label(graphics, Integer.toString(value), x + 157, y, 0xFFE4EFF6);
    }

    private void renderMenu(GuiGraphics graphics) {
        String[] entries = menuEntries();
        graphics.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + entries.length * ROW_HEIGHT + 4, 0xF2121B25);
        graphics.renderOutline(menuX, menuY, MENU_WIDTH, entries.length * ROW_HEIGHT + 4, 0xFF507A96);
        for (int i = 0; i < entries.length; i++) {
            label(graphics, entries[i], menuX + 8, menuY + 7 + i * ROW_HEIGHT, 0xFFFFFFFF);
        }
    }

    private boolean click(MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        if (textEditing) {
            if (x >= textBox.getX() && x < textBox.getX() + textBox.getWidth()
                    && y >= textBox.getY() && y < textBox.getY() + textBox.getHeight()) return true;
            finishText(false);
        }
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (y >= screenHeight - 24 || panelContains(x, y)) {
                return true;
            }
            int shape = shapeAt(x, y);
            if (shape >= 0) {
                DOCUMENT.select(shape);
            }
            shapeMenu = shape >= 0;
            menuOpen = true;
            menuX = Math.clamp((int) x, 0, Math.max(0, screenWidth - MENU_WIDTH));
            menuY = Math.clamp((int) y, 0, Math.max(0, screenHeight - 24 - menuEntries().length * ROW_HEIGHT - 4));
            editorFocus = true;
            return false;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (menuOpen) {
            if (x >= menuX && x < menuX + MENU_WIDTH && y >= menuY
                    && y < menuY + menuEntries().length * ROW_HEIGHT + 4) {
                int item = ((int) y - menuY - 3) / ROW_HEIGHT;
                if (item >= 0 && item < menuEntries().length) {
                    menuAction(item, x, y);
                }
                menuOpen = false;
                return false;
            }
            menuOpen = false;
        }
        if (panelContains(x, y) && DOCUMENT.selected() != null) {
            editorFocus = true;
            panelClick(x, y);
            return false;
        }
        Item selected = DOCUMENT.selected();
        if (selected != null) {
            int handle = handleAt(selected, x, y);
            if (handle >= 0) {
                beginDrag(handle + 1, x, y);
                return false;
            }
        }
        if (y < screenHeight - 24) {
            int index = shapeAt(x, y);
            if (index >= 0) {
                DOCUMENT.select(index);
                editorFocus = true;
                beginDrag(0, x, y);
                return false;
            }
        }
        DOCUMENT.select(-1);
        editorFocus = false;
        return true;
    }

    private boolean drag(MouseButtonEvent event) {
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || dragMode < 0) {
            return true;
        }
        Item original = dragOrigin;
        if (original == null || DOCUMENT.selected() == null) {
            return false;
        }
        double dx = event.x() - dragX;
        double dy = event.y() - dragY;
        if (dragMode == 0) {
            DOCUMENT.replaceSelected(original.move(original.x() + (float) dx, original.y() + (float) dy));
        } else if (dragMode <= 8) {
            DOCUMENT.replaceSelected(resize(original, dragMode - 1, (float) dx, (float) dy));
        } else {
            updateSlider(dragMode - 9, event.x());
        }
        return false;
    }

    private boolean release(MouseButtonEvent event) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && dragMode >= 0) {
            finishDrag();
            return false;
        }
        return true;
    }

    private boolean key(KeyEvent event) {
        if (textEditing) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                finishText(false);
                return false;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                finishText(true);
                return false;
            }
            return true;
        }
        if (menuOpen && event.key() == GLFW.GLFW_KEY_ESCAPE) {
            menuOpen = false;
            return false;
        }
        if (!editorFocus) {
            return true;
        }
        boolean control = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
        if (control && event.key() == GLFW.GLFW_KEY_Z) {
            DOCUMENT.undo();
            return false;
        }
        if (control && event.key() == GLFW.GLFW_KEY_Y) {
            DOCUMENT.redo();
            return false;
        }
        if (control && event.key() == GLFW.GLFW_KEY_D) {
            edit(DOCUMENT::duplicateSelected);
            return false;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE && DOCUMENT.selected() != null) {
            edit(DOCUMENT::deleteSelected);
            return false;
        }
        return true;
    }

    private void panelClick(double x, double y) {
        int relativeX = (int) x - panelX();
        int relativeY = (int) y - panelY();
        Item selected = DOCUMENT.selected();
        if (relativeY >= 165 && relativeY < 183 && relativeX >= 8 && relativeX < 190) {
            if (selected instanceof TextItem) startText(false, 0, 0);
            else if (selected instanceof ImageItem) chooseImage(true, 0, 0);
            return;
        }
        if (relativeY >= 59 && relativeY < 204) {
            int slider = (relativeY - 59) / 18;
            int count = selected instanceof Shape ? 8 : selected instanceof TextItem ? 5 : 0;
            if (slider < count && relativeX >= 30 && relativeX <= 155
                    && !(selected instanceof Shape rect && rect.kind().circle && slider < 4)) {
                beginDrag(9 + slider, x, y);
                updateSlider(slider, x);
            }
            return;
        }
        if (relativeY >= 209 && relativeY < 241 && relativeX >= 8 && relativeX < 186) {
            int column = (relativeX - 8) / 92;
            int row = (relativeY - 209) / 17;
            if ((relativeX - 8) % 92 < 86 && (relativeY - 209) % 17 < 15) {
                action(row * 2 + column);
            }
        }
    }

    private void updateSlider(int slider, double mouseX) {
        Item shape = DOCUMENT.selected();
        if (shape == null) return;
        float fraction = Math.clamp(((float) mouseX - panelX() - 33) / 117, 0, 1);
        if (shape instanceof Shape rect) {
            if (slider < 4) {
                float maximum = Math.min(rect.width(), rect.height()) / 2;
                DOCUMENT.replaceSelected(rect.corner(slider, Math.round(fraction * maximum)));
            } else {
                DOCUMENT.replaceSelected(rect.channel(slider - 4, Math.round(fraction * 255)));
            }
        } else if (shape instanceof TextItem text) {
            if (slider == 0) {
                DOCUMENT.replaceSelected(new TextItem(text.x(), text.y(),
                        Math.clamp(Math.round(fraction * 128), 6, 128), text.text(), text.argb()));
            } else {
                int shift = 32 - slider * 8;
                int value = Math.round(fraction * 255);
                DOCUMENT.replaceSelected(text.withColor((text.argb() & ~(255 << shift)) | (value << shift)));
            }
        }
    }

    private void menuAction(int item, double x, double y) {
        if (shapeMenu) {
            action(item);
            return;
        }
        if (item == 4) {
            startText(true, menuX, menuY);
            return;
        }
        if (item == 5) {
            chooseImage(false, menuX, menuY);
            return;
        }
        Kind kind = Kind.values()[item];
        Shape shape = new Shape(kind, menuX, menuY, kind.defaultWidth, kind.defaultHeight,
                kind.defaultRadius, kind.defaultRadius, kind.defaultRadius, kind.defaultRadius, 0xCC218BFF);
        edit(() -> DOCUMENT.add(shape));
    }

    private void action(int item) {
        switch (item) {
            case 0 -> edit(DOCUMENT::duplicateSelected);
            case 1 -> edit(DOCUMENT::deleteSelected);
            case 2 -> edit(DOCUMENT::bringToFront);
            case 3 -> edit(DOCUMENT::sendToBack);
            default -> { }
        }
    }

    private void beginDrag(int mode, double x, double y) {
        dragMode = mode;
        dragX = x;
        dragY = y;
        dragOrigin = DOCUMENT.selected();
        dragBefore = DOCUMENT.capture();
    }

    private void finishDrag() {
        if (dragMode >= 0 && dragBefore != null) {
            DOCUMENT.commit(dragBefore);
        }
        dragMode = -1;
        dragOrigin = null;
        dragBefore = null;
    }

    private void edit(Runnable change) {
        State before = DOCUMENT.capture();
        change.run();
        DOCUMENT.commit(before);
    }

    private void startText(boolean create, float x, float y) {
        if (textEditing) finishText(false);
        textBefore = DOCUMENT.capture();
        if (create) DOCUMENT.add(new TextItem(x, y, 22, "Текст", 0xFFFFFFFF));
        if (!(DOCUMENT.selected() instanceof TextItem selected)) {
            textBefore = null;
            return;
        }
        previousFocus = screen.getFocused();
        textEditing = true;
        textBox.setVisible(true);
        textBox.setValue(selected.text());
        textBox.setCursorPosition(0);
        textBox.setHighlightPos(selected.text().length());
        screen.setFocused(textBox);
        textBox.setFocused(true);
        editorFocus = true;
    }

    private void textChanged(String value) {
        if (textEditing && !value.isBlank() && DOCUMENT.selected() instanceof TextItem selected) {
            DOCUMENT.replaceSelected(selected.withText(value));
        }
    }

    private void finishText(boolean cancel) {
        if (!textEditing) return;
        if (cancel || textBox.getValue().isBlank()) DOCUMENT.cancel(textBefore);
        else DOCUMENT.commit(textBefore);
        textEditing = false;
        textBefore = null;
        textBox.setVisible(false);
        textBox.setFocused(false);
        if (previousFocus != null && Minecraft.getInstance().screen == screen) {
            screen.setFocused(previousFocus);
        }
        previousFocus = null;
    }

    private void chooseImage(boolean replace, float x, float y) {
        int target = DOCUMENT.selectedIndex();
        IMAGES.choose(Minecraft.getInstance(), imported -> {
            float scale = Math.min(1f, 220f / Math.max(imported.width(), imported.height()));
            float width = Math.max(8, imported.width() * scale);
            float height = Math.max(8, imported.height() * scale);
            if (replace && target >= 0 && target < DOCUMENT.items().size()
                    && DOCUMENT.items().get(target) instanceof ImageItem existing) {
                DOCUMENT.select(target);
                edit(() -> DOCUMENT.replaceSelected(new ImageItem(existing.x(), existing.y(),
                        existing.width(), existing.height(), imported.file())));
            } else {
                edit(() -> DOCUMENT.add(new ImageItem(x, y, width, height, imported.file())));
            }
        });
    }

    private static Item resize(Item original, int handle, float dx, float dy) {
        int sx = handleXDirection(handle);
        int sy = handleYDirection(handle);
        float left = original.x();
        float top = original.y();
        float right = left + original.width();
        float bottom = top + original.height();
        if (sx < 0) left = Math.min(right - 8, left + dx);
        if (sx > 0) right = Math.max(left + 8, right + dx);
        if (sy < 0) top = Math.min(bottom - 8, top + dy);
        if (sy > 0) bottom = Math.max(top + 8, bottom + dy);
        if (original instanceof Shape rect && rect.kind().square) {
            float size = sx == 0 ? bottom - top : sy == 0 ? right - left : Math.max(right - left, bottom - top);
            size = Math.clamp(size, 8, 16384);
            float centerX = original.x() + original.width() / 2;
            float centerY = original.y() + original.height() / 2;
            left = sx < 0 ? original.x() + original.width() - size : sx == 0 ? centerX - size / 2 : original.x();
            top = sy < 0 ? original.y() + original.height() - size : sy == 0 ? centerY - size / 2 : original.y();
            return original.resize(left, top, size, size);
        }
        return original.resize(left, top, Math.min(16384, right - left), Math.min(16384, bottom - top));
    }

    private int shapeAt(double x, double y) {
        for (int i = DOCUMENT.items().size() - 1; i >= 0; i--) {
            Item shape = DOCUMENT.items().get(i);
            if (x >= shape.x() && y >= shape.y() && x < shape.x() + shape.width()
                    && y < shape.y() + shape.height()) {
                return i;
            }
        }
        return -1;
    }

    private static int handleAt(Item shape, double x, double y) {
        for (int i = 0; i < 8; i++) {
            if (Math.abs(x - handleX(shape, i)) <= 5 && Math.abs(y - handleY(shape, i)) <= 5) {
                return i;
            }
        }
        return -1;
    }

    private static float handleX(Item shape, int index) {
        return shape.x() + (handleXDirection(index) + 1) * shape.width() / 2;
    }

    private static float handleY(Item shape, int index) {
        return shape.y() + (handleYDirection(index) + 1) * shape.height() / 2;
    }

    private static int handleXDirection(int index) {
        return switch (index) {
            case 0, 6, 7 -> -1;
            case 2, 3, 4 -> 1;
            default -> 0;
        };
    }

    private static int handleYDirection(int index) {
        return switch (index) {
            case 0, 1, 2 -> -1;
            case 4, 5, 6 -> 1;
            default -> 0;
        };
    }

    private int panelX() {
        return Math.max(0, screenWidth - PANEL_WIDTH - 8);
    }

    private int panelY() {
        return 8;
    }

    private boolean panelContains(double x, double y) {
        return DOCUMENT.selected() != null && x >= panelX() && x < panelX() + PANEL_WIDTH
                && y >= panelY() && y < panelY() + PANEL_HEIGHT;
    }

    private String[] menuEntries() {
        return shapeMenu
                ? new String[]{"Копия", "Удалить", "Вперёд", "Назад"}
                : new String[]{"Прямоугольник", "Квадрат", "Скруглённый", "Круг", "Текст", "PNG файл..."};
    }

    private static void label(GuiGraphics graphics, String value, int x, int y, int color) {
        Font font = Minecraft.getInstance().font;
        graphics.drawString(font, value, x, y, color, false);
    }
}
