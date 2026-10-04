package dev.stormvisuals.config;

import com.google.gson.Gson;
import dev.stormvisuals.module.CrosshairSettings;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Settings and actual file operations, independent of a running Minecraft client. */
public final class ConfigChecks {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("storm-config-checks-");
        ConfigStore store = new ConfigStore(root);
        ConfigState state = new ConfigState();
        var module = new ConfigState.ModuleState();
        module.enabled = true; module.expanded = true; module.brightness = .37f;
        module.effectIndex = 2; module.lightRadius = 7.5f; module.size = 147;
        module.showTimer = false; module.hideWithoutEffect = false; module.key = "key.keyboard.r";
        state.modules.put("FullBright", module);
        var hand = new ConfigState.ModuleState();
        hand.enabled = true; hand.bothHands = false; hand.handSpeed = 2.75f; hand.handOpacity = 64;
        state.modules.put("CustomHand", hand);
        state.crosshair.enabled = true; state.crosshair.style = CrosshairSettings.Style.CUSTOM;
        state.crosshair.hover = true; state.crosshair.dynamic = true;
        state.crosshair.elements.getFirst().x = -23.5f;
        state.crosshair.elements.getFirst().color = 0x123456;
        state.crosshair.elements.getFirst().ownSettings = true;
        state.watermark.moved = true; state.watermark.x = .25f; state.watermark.y = .7f;
        state.potions.moved = true; state.potions.x = .81f; state.potions.y = .11f;
        state.removals = 21; state.targets = 11; state.targetStyle = 3; state.targetColor = 0xAA33CC;
        state.guiKey = "key.keyboard.right.shift";
        var command = new ConfigState.CommandBind(); command.command = "home"; command.key = "key.keyboard.h";
        state.commandBinds.add(command);
        String json = ConfigStore.encode(state);
        same(state, ConfigStore.decode(json), "Every setting round trips through compact JSON");
        check(json.length() < new Gson().toJson(state).length(), "Default settings are omitted");
        check(ConfigStore.encode(new ConfigState()).equals("{\"version\":1}"), "Defaults occupy only the version field");
        store.saveAutosave(json);
        same(state, store.readAutosave(), "Autosave restores state");
        store.saveAutosave(ConfigStore.encode(new ConfigState()));
        check(store.list().isEmpty(), "Autosave is not listed as a preset");
        var entry = store.create("Мой PvP", state);
        check(entry.file().equals("Мой PvP.storm"), "Unicode names and .storm extension");
        check(entry.bytes() < json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length, "Preset compression reduces size");
        try (var files = Files.list(store.directory())) { check(files.count() == 1, "One file per preset, no sidecar"); }
        same(state, store.load(entry), "Compressed preset restores every setting");
        reject(() -> store.create("Мой PvP", new ConfigState()), "Duplicate creation is rejected");
        same(state, store.load(entry), "Duplicate creation preserves existing data");
        var renamed = store.rename(entry, "Другой PvP");
        check(renamed.created() == entry.created(), "Rename preserves creation date");
        check(!Files.exists(store.directory().resolve(entry.file())), "Rename removes the old filename");
        same(state, store.load(renamed), "Rename preserves settings");
        var second = store.create("Второй", new ConfigState());
        reject(() -> store.rename(renamed, second.name()), "Rename collision is rejected");
        same(state, store.load(renamed), "Rename collision leaves source intact");
        store.overwrite(renamed, new ConfigState());
        same(new ConfigState(), store.load(renamed), "Overwrite replaces the snapshot");
        check(store.list().stream().filter(e -> e.file().equals(renamed.file())).findFirst().orElseThrow().created() == entry.created(), "Overwrite preserves creation date");
        Path importedRoot = root.resolve("another-client");
        ConfigStore importedStore = new ConfigStore(importedRoot);
        Files.createDirectories(importedStore.directory());
        Files.copy(store.directory().resolve(second.file()), importedStore.directory().resolve(second.file()));
        same(new ConfigState(), importedStore.load(importedStore.list().getFirst()), "A single copied .storm works in another folder");
        for (String name : Arrays.asList("", "../escape", "a/b", "a\\b", "NUL", "COM1", "bad:", "bad.", "x".repeat(49)))
            reject(() -> store.create(name, state), "Invalid filename: " + name);
        reject(() -> ConfigStore.decode("{\"version\":999}"), "Future versions rejected");
        reject(() -> ConfigStore.decode("{\"version\":1,\"crosshair\":null}"), "Null settings rejected");
        Files.writeString(store.directory().resolve("broken.storm"), "not a preset");
        var broken = store.list().stream().filter(e -> e.file().equals("broken.storm")).findFirst().orElseThrow();
        check(broken.error() != null, "Broken file is visible and marked");
        reject(() -> store.load(broken), "Broken preset is not loaded");
        store.delete(broken); store.delete(renamed); store.delete(second);
        check(store.list().isEmpty(), "Deletion removes only presets");
        check(Files.exists(root.resolve("autosave.json")), "Deletion preserves autosave");
        Path legacyRoot = root.resolve("legacy"); Files.createDirectories(legacyRoot);
        Files.writeString(legacyRoot.resolve("crosshair.json"), new Gson().toJson(state.crosshair));
        check(new ConfigStore(legacyRoot).readAutosave().crosshair.elements.getFirst().x == -23.5f, "Legacy custom crosshair is migrated");
        check(Files.exists(legacyRoot.resolve("crosshair.json")), "Legacy source is preserved");
        System.out.println("Config round-trip, compression, CRUD, import, migration and invalid-file checks passed (preset " + entry.bytes() + " bytes)");
    }
    private static void same(ConfigState expected, ConfigState actual, String message) {
        check(new Gson().toJson(expected).equals(new Gson().toJson(actual)), message);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void reject(Action action, String message) throws Exception {
        boolean rejected = false;
        try { action.run(); } catch (java.io.IOException | IllegalArgumentException e) { rejected = true; }
        check(rejected, message);
    }
}
