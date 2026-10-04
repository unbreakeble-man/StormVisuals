package dev.stormvisuals.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;

/** Disk work only. Each .storm is one gzip-compressed compact JSON document. */
public final class ConfigStore {
    public static final String EXTENSION = ".storm";
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final int MAX_JSON = 2 * 1024 * 1024;
    private final Path root, directory;

    public ConfigStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
        directory = this.root.resolve("configs");
    }

    public Path directory() { return directory; }
    public record Entry(String file, String name, long created, long bytes, String error) {}
    private record Preset(String name, long created, ConfigState state) {}

    public static String encode(ConfigState state) {
        state.validate();
        JsonObject json = GSON.toJsonTree(state).getAsJsonObject();
        JsonObject defaults = GSON.toJsonTree(new ConfigState()).getAsJsonObject();
        prune(json, defaults);
        JsonObject modules = json.has("modules") ? json.getAsJsonObject("modules") : null;
        if (modules != null) for (JsonElement module : modules.asMap().values())
            prune(module.getAsJsonObject(), GSON.toJsonTree(new ConfigState.ModuleState()).getAsJsonObject());
        json.addProperty("version", 1);
        return GSON.toJson(json);
    }

    private static void prune(JsonObject object, JsonObject defaults) {
        for (String key : new ArrayList<>(object.keySet())) {
            JsonElement value = object.get(key), fallback = defaults.get(key);
            if (fallback != null && value.equals(fallback)) object.remove(key);
            else if (value.isJsonObject() && fallback != null && fallback.isJsonObject())
                prune(value.getAsJsonObject(), fallback.getAsJsonObject());
        }
    }

    public static ConfigState decode(String text) {
        JsonObject json = JsonParser.parseString(text).getAsJsonObject();
        if (!json.has("version")) throw new IllegalArgumentException("Отсутствует версия конфига");
        ConfigState state = GSON.fromJson(json, ConfigState.class);
        state.validate();
        return state;
    }

    public ConfigState readAutosave() throws IOException {
        Path file = root.resolve("autosave.json");
        if (Files.exists(file)) return decode(readText(file));
        // One-time migration: old files remain untouched until a successful unified save.
        ConfigState state = new ConfigState();
        Path crosshair = root.resolve("crosshair.json");
        if (Files.exists(crosshair)) {
            state.crosshair = GSON.fromJson(readText(crosshair), dev.stormvisuals.module.CrosshairSettings.class);
            if (state.crosshair == null || state.crosshair.version != 1)
                throw new IllegalArgumentException("Некорректный старый прицел");
        }
        state.validate();
        return state;
    }

    public void saveAutosave(String contents) throws IOException {
        atomicWrite(root.resolve("autosave.json"), contents.getBytes(StandardCharsets.UTF_8));
    }

    private static String readText(Path path) throws IOException {
        if (Files.size(path) > MAX_JSON) throw new IOException("Слишком большой конфиг");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public List<Entry> list() throws IOException {
        Files.createDirectories(directory);
        List<Entry> result = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + EXTENSION)) {
            for (Path path : files) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                long size = Files.size(path);
                String file = path.getFileName().toString();
                try {
                    Preset preset = readPreset(path);
                    result.add(new Entry(file, preset.name, preset.created, size, null));
                } catch (IOException | RuntimeException e) {
                    result.add(new Entry(file, file.substring(0, file.length() - EXTENSION.length()),
                            Files.getLastModifiedTime(path).toMillis(), size, "Повреждённый конфиг"));
                }
            }
        }
        result.sort(Comparator.comparingLong(Entry::created).reversed().thenComparing(Entry::name));
        return List.copyOf(result);
    }

    public Entry create(String name, ConfigState state) throws IOException {
        String clean = validName(name);
        Path target = namedPath(clean);
        if (Files.exists(target)) throw new IOException("Конфиг с таким именем уже есть");
        long created = Instant.now().toEpochMilli();
        writePreset(target, new Preset(clean, created, state));
        return new Entry(target.getFileName().toString(), clean, created, Files.size(target), null);
    }

    public void overwrite(Entry entry, ConfigState state) throws IOException {
        Path path = entryPath(entry.file);
        Preset previous = readPreset(path);
        writePreset(path, new Preset(previous.name, previous.created, state));
    }

    public ConfigState load(Entry entry) throws IOException {
        Path path = entryPath(entry.file);
        return readPreset(path).state;
    }

    public Entry rename(Entry entry, String name) throws IOException {
        String clean = validName(name);
        Path source = entryPath(entry.file), target = namedPath(clean);
        boolean sameFile = source.equals(target) || Files.exists(target) && Files.isSameFile(source, target);
        if (!sameFile && Files.exists(target)) throw new IOException("Конфиг с таким именем уже есть");
        Preset old = readPreset(source);
        long created = old.created;
        Path temporary = Files.createTempFile(directory, ".rename-", ".tmp");
        try {
            writeCompressed(temporary, presetJson(new Preset(clean, created, old.state)));
            if (sameFile) replace(temporary, target);
            else {
                Files.move(temporary, target);
                try { Files.delete(source); }
                catch (IOException error) { Files.deleteIfExists(target); throw error; }
            }
        } finally { Files.deleteIfExists(temporary); }
        return new Entry(target.getFileName().toString(), clean, created, Files.size(target), null);
    }

    public void delete(Entry entry) throws IOException { Files.delete(entryPath(entry.file)); }

    private void writePreset(Path target, Preset preset) throws IOException {
        byte[] json = presetJson(preset);
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".save-", ".tmp");
        try {
            writeCompressed(temporary, json);
            replace(temporary, target);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static byte[] presetJson(Preset preset) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("name", preset.name);
        json.addProperty("created", preset.created);
        json.add("state", JsonParser.parseString(encode(preset.state)));
        byte[] bytes = GSON.toJson(json).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_JSON) throw new IOException("Слишком большой конфиг");
        return bytes;
    }

    private static Preset readPreset(Path file) throws IOException {
        if (Files.size(file) > MAX_JSON) throw new IOException("Слишком большой конфиг");
        byte[] data;
        try (var input = new GZIPInputStream(Files.newInputStream(file))) {
            data = input.readNBytes(MAX_JSON + 1);
            if (data.length > MAX_JSON) throw new IOException("Слишком большой конфиг");
        }
        JsonObject json = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        String name = validName(json.get("name").getAsString());
        long created = json.get("created").getAsLong();
        if (created <= 0 || created > 253402300799000L) throw new IOException("Некорректная дата создания");
        return new Preset(name, created, decode(json.get("state").toString()));
    }

    private static void writeCompressed(Path file, byte[] contents) throws IOException {
        try (var output = new GZIPOutputStream(Files.newOutputStream(file)) {
            { def.setLevel(Deflater.BEST_COMPRESSION); }
        }) { output.write(contents); }
    }

    public static String validName(String input) {
        if (input == null) throw new IllegalArgumentException("Введите имя конфига");
        String name = input.strip();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 48)
            throw new IllegalArgumentException("Имя должно содержать от 1 до 48 символов");
        if (name.endsWith(".") || name.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0)
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))
            throw new IllegalArgumentException("В имени есть недопустимые символы");
        return name;
    }

    private Path namedPath(String name) { return directory.resolve(name + EXTENSION); }
    private Path entryPath(String file) throws IOException {
        Path path = directory.resolve(file).normalize();
        if (!path.getParent().equals(directory) || !file.endsWith(EXTENSION)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Конфиг не найден");
        return path;
    }

    private static void atomicWrite(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), ".save-", ".tmp");
        try { Files.write(temporary, bytes); replace(temporary, target); }
        finally { Files.deleteIfExists(temporary); }
    }

    private static void replace(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
}
