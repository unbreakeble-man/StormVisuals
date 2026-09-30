package dev.stormvisuals.editor;

import dev.stormvisuals.render.RoundedRectRenderer;
import dev.stormvisuals.render.MtsdfFont;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CanvasDocument {
    private static final Logger LOGGER = LoggerFactory.getLogger("StormVisuals Editor");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_SHAPES = 256;
    private static final int MAX_HISTORY = 50;

    private final Path file = FabricLoader.getInstance().getConfigDir().resolve("stormvisuals/layout.json");
    private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "StormVisuals layout IO");
        thread.setDaemon(false);
        return thread;
    });
    private final List<Item> items = new ArrayList<>();
    private final List<Item> view = Collections.unmodifiableList(items);
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    private int selected = -1;
    private boolean editedBeforeLoad;

    enum Kind {
        RECTANGLE("Прямоугольник", false, false, 120, 70, 0),
        SQUARE("Квадрат", true, false, 90, 90, 0),
        ROUNDED("Скруглённый", false, false, 120, 70, 16),
        CIRCLE("Круг", true, true, 90, 90, 45);

        final String label;
        final boolean square;
        final boolean circle;
        final float defaultWidth;
        final float defaultHeight;
        final float defaultRadius;

        Kind(String label, boolean square, boolean circle,
             float defaultWidth, float defaultHeight, float defaultRadius) {
            this.label = label;
            this.square = square;
            this.circle = circle;
            this.defaultWidth = defaultWidth;
            this.defaultHeight = defaultHeight;
            this.defaultRadius = defaultRadius;
        }
    }

    sealed interface Item permits Shape, TextItem, ImageItem {
        float x();
        float y();
        float width();
        float height();
        Item move(float x, float y);
        Item resize(float x, float y, float width, float height);
    }

    record Shape(Kind kind, float x, float y, float width, float height,
                 float topLeft, float topRight, float bottomRight, float bottomLeft,
                 int argb) implements RoundedRectRenderer.Rectangle, Item {
        Shape {
            if (kind == null || !Float.isFinite(x) || !Float.isFinite(y)
                    || !Float.isFinite(width) || !Float.isFinite(height)
                    || !Float.isFinite(topLeft) || !Float.isFinite(topRight)
                    || !Float.isFinite(bottomRight) || !Float.isFinite(bottomLeft)
                    || width < 8 || height < 8 || width > 16384 || height > 16384) {
                throw new IllegalArgumentException("Invalid shape");
            }
            if (kind.square) {
                height = width;
            }
            float maxRadius = Math.min(width, height) * 0.5f;
            topLeft = Math.clamp(topLeft, 0, maxRadius);
            topRight = Math.clamp(topRight, 0, maxRadius);
            bottomRight = Math.clamp(bottomRight, 0, maxRadius);
            bottomLeft = Math.clamp(bottomLeft, 0, maxRadius);
            if (kind.circle) {
                topLeft = topRight = bottomRight = bottomLeft = maxRadius;
            }
        }

        @Override
        public Shape move(float newX, float newY) {
            return new Shape(kind, newX, newY, width, height,
                    topLeft, topRight, bottomRight, bottomLeft, argb);
        }

        @Override
        public Shape resize(float newX, float newY, float newWidth, float newHeight) {
            return new Shape(kind, newX, newY, newWidth, newHeight,
                    topLeft, topRight, bottomRight, bottomLeft, argb);
        }

        Shape corner(int index, float value) {
            return new Shape(kind, x, y, width, height,
                    index == 0 ? value : topLeft,
                    index == 1 ? value : topRight,
                    index == 2 ? value : bottomRight,
                    index == 3 ? value : bottomLeft, argb);
        }

        Shape channel(int index, int value) {
            int shift = 24 - index * 8;
            int color = (argb & ~(0xFF << shift)) | ((value & 0xFF) << shift);
            return new Shape(kind, x, y, width, height,
                    topLeft, topRight, bottomRight, bottomLeft, color);
        }

        float corner(int index) {
            return switch (index) {
                case 0 -> topLeft;
                case 1 -> topRight;
                case 2 -> bottomRight;
                default -> bottomLeft;
            };
        }

        int channel(int index) {
            return (argb >>> (24 - index * 8)) & 0xFF;
        }
    }

    record TextItem(float x, float y, float size, String text, int argb) implements Item {
        TextItem {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(size)
                    || size < 6 || size > 256 || text == null || text.isEmpty() || text.length() > 120) {
                throw new IllegalArgumentException("Invalid text");
            }
            text = text.replace('\n', ' ').replace('\r', ' ');
        }

        @Override
        public float width() {
            return MtsdfFont.width(text, size);
        }

        @Override
        public float height() {
            return MtsdfFont.height(size);
        }

        @Override
        public TextItem move(float newX, float newY) {
            return new TextItem(newX, newY, size, text, argb);
        }

        @Override
        public TextItem resize(float newX, float newY, float newWidth, float newHeight) {
            float widthScale = newWidth / width();
            float heightScale = newHeight / height();
            float scale = Math.abs(heightScale - 1) > Math.abs(widthScale - 1) ? heightScale : widthScale;
            return new TextItem(newX, newY, Math.clamp(size * scale, 6, 256), text, argb);
        }

        TextItem withText(String value) {
            return new TextItem(x, y, size, value, argb);
        }

        TextItem withColor(int value) {
            return new TextItem(x, y, size, text, value);
        }
    }

    record ImageItem(float x, float y, float width, float height, String file) implements Item {
        ImageItem {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                    || width < 8 || height < 8 || width > 16384 || height > 16384
                    || file == null || !file.matches("[A-Za-z0-9_-]+\\.png")) {
                throw new IllegalArgumentException("Invalid image");
            }
        }

        @Override
        public ImageItem move(float newX, float newY) {
            return new ImageItem(newX, newY, width, height, file);
        }

        @Override
        public ImageItem resize(float newX, float newY, float newWidth, float newHeight) {
            return new ImageItem(newX, newY, newWidth, newHeight, file);
        }
    }

    record State(List<Item> items, int selected) {
    }

    void loadAsync(Minecraft client) {
        io.execute(() -> {
            List<Item> loaded = read();
            client.execute(() -> {
                if (!editedBeforeLoad) {
                    items.clear();
                    items.addAll(loaded);
                }
            });
        });
    }

    List<Item> items() {
        return view;
    }

    int selectedIndex() {
        return selected;
    }

    Item selected() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    void select(int index) {
        selected = index >= 0 && index < items.size() ? index : -1;
    }

    State capture() {
        return new State(List.copyOf(items), selected);
    }

    void commit(State before) {
        if (before.items().equals(items)) {
            return;
        }
        undo.addLast(before);
        while (undo.size() > MAX_HISTORY) {
            undo.removeFirst();
        }
        redo.clear();
        editedBeforeLoad = true;
        saveAsync();
    }

    void add(Item item) {
        if (items.size() >= MAX_SHAPES) {
            return;
        }
        items.add(item);
        selected = items.size() - 1;
    }

    void replaceSelected(Item item) {
        if (selected() != null) {
            items.set(selected, item);
        }
    }

    void deleteSelected() {
        if (selected() != null) {
            items.remove(selected);
            selected = -1;
        }
    }

    void duplicateSelected() {
        Item item = selected();
        if (item != null) {
            add(item.move(item.x() + 12, item.y() + 12));
        }
    }

    void bringToFront() {
        Item item = selected();
        if (item != null && selected < items.size() - 1) {
            items.remove(selected);
            items.add(item);
            selected = items.size() - 1;
        }
    }

    void sendToBack() {
        Item item = selected();
        if (item != null && selected > 0) {
            items.remove(selected);
            items.add(0, item);
            selected = 0;
        }
    }

    void undo() {
        if (!undo.isEmpty()) {
            redo.addLast(capture());
            restore(undo.removeLast());
            saveAsync();
        }
    }

    void redo() {
        if (!redo.isEmpty()) {
            undo.addLast(capture());
            restore(redo.removeLast());
            saveAsync();
        }
    }

    void cancel(State before) {
        restore(before);
    }

    void close() {
        io.shutdown();
    }

    private void restore(State state) {
        items.clear();
        items.addAll(state.items());
        selected = state.selected();
        editedBeforeLoad = true;
    }

    private List<Item> read() {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray array = root.has("items") ? root.getAsJsonArray("items") : root.getAsJsonArray("shapes");
            List<Item> loaded = new ArrayList<>();
            for (JsonElement element : array) {
                if (loaded.size() >= MAX_SHAPES) {
                    break;
                }
                try {
                    JsonObject item = element.getAsJsonObject();
                    String type = item.has("type") ? item.get("type").getAsString() : "shape";
                    float x = item.get("x").getAsFloat();
                    float y = item.get("y").getAsFloat();
                    switch (type) {
                        case "shape" -> loaded.add(new Shape(
                                Kind.valueOf(item.get("kind").getAsString()), x, y,
                                item.get("width").getAsFloat(), item.get("height").getAsFloat(),
                                item.get("topLeft").getAsFloat(), item.get("topRight").getAsFloat(),
                                item.get("bottomRight").getAsFloat(), item.get("bottomLeft").getAsFloat(),
                                item.get("argb").getAsInt()));
                        case "text" -> loaded.add(new TextItem(x, y, item.get("size").getAsFloat(),
                                item.get("text").getAsString(), item.get("argb").getAsInt()));
                        case "image" -> loaded.add(new ImageItem(x, y,
                                item.get("width").getAsFloat(), item.get("height").getAsFloat(),
                                item.get("file").getAsString()));
                        default -> LOGGER.warn("Skipped unknown canvas item type {}", type);
                    }
                } catch (RuntimeException invalidItem) {
                    LOGGER.warn("Skipped invalid saved canvas item", invalidItem);
                }
            }
            return loaded;
        } catch (IOException | RuntimeException error) {
            LOGGER.warn("Could not load {}", file, error);
            return List.of();
        }
    }

    private void saveAsync() {
        JsonArray array = new JsonArray();
        for (Item element : items) {
            JsonObject item = new JsonObject();
            item.addProperty("x", element.x());
            item.addProperty("y", element.y());
            if (element instanceof Shape shape) {
                item.addProperty("type", "shape");
                item.addProperty("kind", shape.kind().name());
                item.addProperty("width", shape.width());
                item.addProperty("height", shape.height());
                item.addProperty("topLeft", shape.topLeft());
                item.addProperty("topRight", shape.topRight());
                item.addProperty("bottomRight", shape.bottomRight());
                item.addProperty("bottomLeft", shape.bottomLeft());
                item.addProperty("argb", shape.argb());
            } else if (element instanceof TextItem textItem) {
                item.addProperty("type", "text");
                item.addProperty("size", textItem.size());
                item.addProperty("text", textItem.text());
                item.addProperty("argb", textItem.argb());
            } else if (element instanceof ImageItem imageItem) {
                item.addProperty("type", "image");
                item.addProperty("width", imageItem.width());
                item.addProperty("height", imageItem.height());
                item.addProperty("file", imageItem.file());
            }
            array.add(item);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 2);
        root.add("items", array);
        String contents = GSON.toJson(root);
        io.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                Files.writeString(temporary, contents, StandardCharsets.UTF_8);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unavailable) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException error) {
                LOGGER.error("Could not save {}", file, error);
            }
        });
    }
}
