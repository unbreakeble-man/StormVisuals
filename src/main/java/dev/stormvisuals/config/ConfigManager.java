package dev.stormvisuals.config;

import dev.stormvisuals.gui.ClickGuiScreen;
import dev.stormvisuals.module.CustomCrosshair;
import dev.stormvisuals.module.Removals;
import dev.stormvisuals.module.TargetESP;
import dev.stormvisuals.render.HudDragging;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.slf4j.LoggerFactory;

/** Capture/apply on the client thread; all filesystem work runs on one ordered worker. */
public final class ConfigManager {
    private static final ConfigStore STORE = new ConfigStore(FabricLoader.getInstance().getConfigDir().resolve("stormvisuals"));
    private static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "StormVisuals config IO");
        thread.setDaemon(false);
        return thread;
    });
    private static boolean ready, applying, dirty, stopping, failedLoad;
    private static int delay;
    private static volatile String autosaveError;
    private static volatile boolean saving;

    private ConfigManager() {}
    public static boolean ready() { return ready; }
    public static String autosaveError() { return autosaveError; }
    public static boolean saving() { return saving; }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> IO.execute(() -> {
            try {
                ConfigState state = STORE.readAutosave();
                ClickGuiScreen.validateConfig(state);
                client.execute(() -> {
                    if (!dirty) apply(state);
                    ready = true;
                    changed();
                });
            } catch (Exception error) {
                LoggerFactory.getLogger("StormVisuals").warn("Could not load autosave", error);
                client.execute(() -> { ready = true; failedLoad = true; autosaveError = "Автосейв не прочитан: " + message(error); });
            }
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (ready && dirty && !stopping && --delay <= 0) flush();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            HudDragging.finish();
            flush();
            stopping = true;
            IO.shutdown();
            // The game is shutting down. Wait only here so System.exit cannot discard the final save.
            try {
                if (!IO.awaitTermination(5, TimeUnit.SECONDS))
                    LoggerFactory.getLogger("StormVisuals").warn("Config IO did not finish before shutdown");
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
    }

    public static void changed() {
        if (applying || stopping) return;
        dirty = true;
        delay = 15;
    }

    public static void flush() {
        if (!ready || stopping || IO.isShutdown() || failedLoad && !dirty) return;
        String contents = ConfigStore.encode(capture());
        dirty = false;
        failedLoad = false;
        saving = true;
        IO.execute(() -> {
            try { STORE.saveAutosave(contents); autosaveError = null; }
            catch (Exception error) {
                autosaveError = "Не удалось сохранить: " + message(error);
                LoggerFactory.getLogger("StormVisuals").warn("Could not write autosave", error);
                Minecraft.getInstance().execute(() -> { if (!stopping) { dirty = true; delay = 200; } });
            } finally { saving = false; }
        });
    }

    public static ConfigState capture() {
        ConfigState state = new ConfigState();
        ClickGuiScreen.captureConfig(state);
        state.crosshair = new com.google.gson.Gson().fromJson(CustomCrosshair.snapshot(), dev.stormvisuals.module.CrosshairSettings.class);
        state.removals = Removals.mask();
        state.targets = TargetESP.targetMask();
        state.targetStyle = TargetESP.style();
        state.targetColor = TargetESP.color();
        state.watermark = HudDragging.WATERMARK.snapshot();
        state.potions = HudDragging.POTIONS.snapshot();
        state.cooldowns = HudDragging.COOLDOWNS.snapshot();
        state.hotkeys = HudDragging.HOTKEYS.snapshot();
        state.quests = HudDragging.QUESTS.snapshot();
        state.auctionTimer = HudDragging.AUCTION_TIMER.snapshot();
        return state;
    }

    private static void apply(ConfigState state) {
        state.validate();
        ClickGuiScreen.validateConfig(state);
        applying = true;
        try {
            HudDragging.finish();
            CustomCrosshair.applySettings(state.crosshair);
            HudDragging.WATERMARK.restore(state.watermark);
            HudDragging.POTIONS.restore(state.potions);
            HudDragging.COOLDOWNS.restore(state.cooldowns);
            HudDragging.HOTKEYS.restore(state.hotkeys);
            HudDragging.QUESTS.restore(state.quests);
            HudDragging.AUCTION_TIMER.restore(state.auctionTimer);
            ClickGuiScreen.applyConfig(state);
        } finally { applying = false; }
    }

    public record Result(List<ConfigStore.Entry> entries, ConfigStore.Entry selected, String error) {}
    @FunctionalInterface private interface Operation { ConfigStore.Entry run() throws Exception; }

    private static void submit(Operation operation, Consumer<Result> done) {
        if (!ready || stopping) { done.accept(new Result(List.of(), null, "Настройки ещё загружаются")); return; }
        IO.execute(() -> {
            try {
                ConfigStore.Entry selected = operation.run();
                List<ConfigStore.Entry> entries = STORE.list();
                Minecraft.getInstance().execute(() -> done.accept(new Result(entries, selected, null)));
            } catch (Exception error) {
                Minecraft.getInstance().execute(() -> done.accept(new Result(List.of(), null, message(error))));
            }
        });
    }

    public static void refresh(Consumer<Result> done) { submit(() -> null, done); }
    public static void create(String name, Consumer<Result> done) {
        ConfigState state = capture();
        submit(() -> STORE.create(name, state), done);
    }
    public static void overwrite(ConfigStore.Entry entry, Consumer<Result> done) {
        ConfigState state = capture();
        submit(() -> { STORE.overwrite(entry, state); return entry; }, done);
    }
    public static void rename(ConfigStore.Entry entry, String name, Consumer<Result> done) { submit(() -> STORE.rename(entry, name), done); }
    public static void delete(ConfigStore.Entry entry, Consumer<Result> done) { submit(() -> { STORE.delete(entry); return null; }, done); }

    public static void load(ConfigStore.Entry entry, Consumer<Result> done) {
        if (!ready || stopping) { done.accept(new Result(List.of(), null, "Настройки ещё загружаются")); return; }
        IO.execute(() -> {
            try {
                ConfigState state = STORE.load(entry);
                ClickGuiScreen.validateConfig(state);
                List<ConfigStore.Entry> entries = STORE.list();
                Minecraft.getInstance().execute(() -> {
                    try { apply(state); changed(); flush(); done.accept(new Result(entries, entry, null)); }
                    catch (Exception error) { done.accept(new Result(entries, null, message(error))); }
                });
            } catch (Exception error) {
                Minecraft.getInstance().execute(() -> done.accept(new Result(List.of(), null, message(error))));
            }
        });
    }

    public static void openFolder(Consumer<Result> done) {
        submit(() -> { Files.createDirectories(STORE.directory()); Util.getPlatform().openPath(STORE.directory()); return null; }, done);
    }

    public record AccountsResult(AccountsState state, String error) {}
    public static void readAccounts(Consumer<AccountsResult> done) {
        IO.execute(() -> {
            AccountsResult result;
            try { result = new AccountsResult(STORE.readAccounts(), null); }
            catch (Exception error) { result = new AccountsResult(null, message(error)); }
            AccountsResult completed = result;
            Minecraft.getInstance().execute(() -> done.accept(completed));
        });
    }

    public static void saveAccounts(AccountsState state, Consumer<String> done) {
        AccountsState snapshot = state.copy();
        IO.execute(() -> {
            String failure = null;
            try { STORE.saveAccounts(snapshot); }
            catch (Exception error) { failure = message(error); }
            String result = failure;
            Minecraft.getInstance().execute(() -> done.accept(result));
        });
    }

    private static String message(Exception error) {
        return error.getMessage() == null ? "Не удалось обработать конфиг" : error.getMessage();
    }
}
