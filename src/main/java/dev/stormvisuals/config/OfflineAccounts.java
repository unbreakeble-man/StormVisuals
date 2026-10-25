package dev.stormvisuals.config;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

public final class OfflineAccounts {
    private static AccountsState state = new AccountsState();
    private static List<String> view = List.of();
    private static boolean ready;
    private static String error = "";
    private static long revision;
    private OfflineAccounts() {}
    public static boolean ready() { return ready; }
    public static String error() { return error; }
    public static long revision() { return revision; }
    public static List<String> accounts() { return view; }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ConfigManager.readAccounts(result -> {
            if (result.error() != null) { error = "Аккаунты не прочитаны: " + result.error(); return; }
            state = result.state(); view = List.copyOf(state.accounts); ready = true; revision++;
            if (!state.selected.isEmpty() && client.level == null && client.getConnection() == null) {
                switchSession(state.selected);
            }
        }));
    }
    public static void add(String text) {
        requireReady();
        String name = AccountsState.nickname(text);
        if (state.accounts.size() >= 512) throw new IllegalArgumentException("Можно сохранить до 512 аккаунтов");
        if (state.accounts.stream().anyMatch(account -> account.equalsIgnoreCase(name)))
            throw new IllegalArgumentException("Этот ник уже сохранён");
        state.accounts.add(name);
        save();
    }
    public static void remove(String name) {
        requireReady();
        state.accounts.remove(name);
        if (state.selected.equals(name)) state.selected = "";
        save();
    }
    public static void select(String name) {
        requireReady();
        if (!state.accounts.contains(name)) throw new IllegalArgumentException("Аккаунт не найден");
        switchSession(name);
        state.selected = name;
        save();
    }
    private static void switchSession(String name) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null || client.player != null || client.getConnection() != null)
            throw new IllegalArgumentException("Для смены аккаунта вернитесь в главное меню");
        User user = new User(name, AccountsState.uuid(name), "0", Optional.empty(), Optional.empty());
        if (client.getUser().getProfileId().equals(user.getProfileId()) && client.getUser().getAccessToken().equals("0")) return;
        ((OfflineSessionAccess) client).stormvisuals$offlineSession(user);
    }
    public static String randomName() {
        String[] first = {"Storm", "Frost", "Cloud", "Echo", "Nova", "Lunar", "Raven", "Pixel"};
        String[] last = {"Fox", "Wave", "Sky", "Wolf", "Leaf", "Star", "Craft", "Spark"};
        var random = ThreadLocalRandom.current();
        String name;
        do { name = first[random.nextInt(first.length)] + last[random.nextInt(last.length)] + random.nextInt(1000, 10000); }
        while (state.accounts.contains(name));
        return name;
    }
    private static void requireReady() { if (!ready) throw new IllegalArgumentException(error.isEmpty() ? "Аккаунты ещё загружаются" : error); }
    private static void save() {
        error = ""; view = List.copyOf(state.accounts); revision++;
        ConfigManager.saveAccounts(state, failure -> error = failure == null ? "" : "Не удалось сохранить аккаунты: " + failure);
    }
}
