package dev.stormvisuals.module;

import dev.stormvisuals.config.ConfigState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

/** Server snapshots plus explicitly marked local progress estimates. */
public final class FuntimeQuests {
    public static final String[] DIFFICULTIES = {"Лёгкие", "Средние", "Тяжёлые"};
    private static final Pattern FORMATTING = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROGRESS = Pattern.compile("(?iu)прогресс\\s*:\\s*([0-9 ]+)\\s*/\\s*([0-9 ]+)");
    private static final Pattern REWARD = Pattern.compile("(?iu)опыт\\s+пропуска\\s*\\+\\s*([0-9 ]+)");
    private static final Pattern REFRESH = Pattern.compile("(?iu)до\\s+обновления\\s*:\\s*(.+)$");
    private static final Pattern TIME = Pattern.compile("(?iu)([0-9]+)\\s*([дчмс])(?:[а-яё.]*)");
    private static final Pattern HIDDEN_STATUS = Pattern.compile("(?iu)статус\\s*:\\s*\\[?(закрыто|забрано|выполнено|завершено|получено)\\]?\\s*$");
    private static final Pattern COMPLETION = Pattern.compile("(?iu)^[^\\p{L}\\p{N}]*\\[боевой\\s+пропуск\\]\\s+задание\\s+выполнено!\\s*$");
    private static final Pattern COMPLETION_END = Pattern.compile("(?iu)^проверь\\s+задания\\s*:\\s*/bp\\s*$");
    private static final Map<String, Observed> QUESTS = new LinkedHashMap<>();
    private static final Map<String, Long> COMPLETED = new LinkedHashMap<>();
    private static boolean enabled;
    private static boolean allowedServer;
    private static String serverAddress;
    private static Connection connection;
    private static int difficulties = 7;
    private static Object world;
    private static ChestMenu observedMenu;
    private static ItemStack[] observedItems = new ItemStack[0];
    private static long lastRead;
    private static long completionUntil;
    private static String completionName;
    private static long revision;

    private FuntimeQuests() {}

    public record Quest(String name, int current, int total, int reward, int difficulty, long refreshSeconds, boolean hidden) {
        public String key() { return difficulty + ":" + name.toLowerCase(Locale.ROOT); }
    }
    public record Observed(Quest quest, long expiresAt, int localDelta) {
        public int current() { return (int) Math.min(quest.total, (long) quest.current + localDelta); }
        public boolean estimated() { return localDelta > 0; }
    }

    public static void initialize() {
        FuntimeQuestProgress.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(FuntimeQuests::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (handler.getConnection() != connection) return;
            // A proxy can leave PLAY for reconfiguration without disconnecting from Funtime.
            if (connection.isConnected()) changeWorld(null);
            else reset();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && active(Minecraft.getInstance())) receiveGameMessage(message.getString(), System.nanoTime());
        });
    }

    public static void setEnabled(boolean on) { enabled = on; if (!on) reset(); }
    public static boolean shows(int difficulty) { return (difficulties & 1 << difficulty) != 0; }
    public static void show(int difficulty, boolean on) {
        difficulties = on ? difficulties | 1 << difficulty : difficulties & ~(1 << difficulty);
        revision++;
    }
    public static void capture(ConfigState.ModuleState state) { state.questDifficulties = difficulties; }
    public static void restore(ConfigState.ModuleState state) { difficulties = state.questDifficulties & 7; }
    public static long lastRead() { return lastRead; }
    public static List<Observed> quests() { return new ArrayList<>(QUESTS.values()); }
    public static int count() { return QUESTS.size(); }
    public static long revision() { return revision; }
    static int current(String key) {
        Observed entry = QUESTS.get(key);
        return entry == null ? -1 : entry.current();
    }

    public static boolean acceptsAddress(String address) {
        if (address == null) return false;
        String host = address.strip().toLowerCase(Locale.ROOT);
        int colon = host.indexOf(':');
        if (colon >= 0) {
            if (!host.substring(colon + 1).matches("[0-9]{1,5}")) return false;
            try { if (Integer.parseInt(host.substring(colon + 1)) > 65535) return false; }
            catch (NumberFormatException ignored) { return false; }
            host = host.substring(0, colon);
        }
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return host.matches("(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+funtime\\.su");
    }

    public static boolean active(Minecraft client) {
        return enabled && allowedServer && client.player != null && client.level != null && world == client.level
                && connection != null && connection.isConnected()
                && client.getConnection() != null && client.getCurrentServer() != null
                && client.getConnection().getConnection() == connection
                && Objects.equals(serverAddress, client.getCurrentServer().ip);
    }

    private static void tick(Minecraft client) {
        if (!enabled) return;
        if (connection != null && !connection.isConnected()) reset();
        var handler = client.getConnection();
        if (handler != null && !handler.getConnection().isConnected()) { reset(); return; }
        if (handler != null) updateConnection(handler.getConnection(), client.getCurrentServer() == null ? null : client.getCurrentServer().ip);
        if (!allowedServer || client.player == null || client.level == null || client.getConnection() == null) {
            changeWorld(null);
            return;
        }
        changeWorld(client.level);
        long now = System.nanoTime();
        if (QUESTS.entrySet().removeIf(entry -> {
            if (entry.getValue().expiresAt > now) return false;
            FuntimeQuestProgress.untrack(entry.getKey());
            return true;
        })) revision++;
        COMPLETED.values().removeIf(expiresAt -> expiresAt <= now);
        if (completionUntil != 0 && now >= completionUntil) clearCompletion();
        if (!(client.screen instanceof ContainerScreen screen)) { observedMenu = null; return; }
        ChestMenu menu = screen.getMenu();
        if (client.player.containerMenu != menu || !menu.getCarried().isEmpty()) return;
        readMenu(menu, now);
    }

    static void updateConnection(Connection next, String address) {
        if (connection == next && Objects.equals(serverAddress, address)) return;
        reset();
        connection = next;
        serverAddress = address;
        allowedServer = acceptsAddress(address);
    }

    static void changeWorld(Object next) {
        if (world == next) return;
        // Quests belong to the server session. Menu references and entity IDs belong to one world.
        world = next;
        observedMenu = null;
        observedItems = new ItemStack[0];
        clearCompletion();
        FuntimeQuestProgress.worldChanged();
        revision++;
    }

    static void readMenu(ChestMenu menu, long now) {
        if (menu != observedMenu) {
            observedMenu = menu;
            observedItems = new ItemStack[menu.getRowCount() * 9];
        }
        // Never inspect player inventory slots or require hovering over individual tasks.
        // Plugin menus can replace their contents without advancing the vanilla state ID.
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            if (observedItems[i] != null && ItemStack.matches(item, observedItems[i])) continue;
            observedItems[i] = item.copy();
            Quest quest = parse(item);
            if (quest == null) continue;
            observe(quest, now);
            lastRead = now;
        }
    }

    static void observe(Quest quest, long now) {
        if (quest.hidden || quest.refreshSeconds == 0) {
            if (QUESTS.remove(quest.key()) != null) revision++;
            FuntimeQuestProgress.untrack(quest.key());
            return;
        }
        Long completedUntil = COMPLETED.get(quest.key());
        if (completedUntil != null && now < completedUntil) return;
        long deadline = now + quest.refreshSeconds * 1_000_000_000L;
        Observed previous = QUESTS.get(quest.key());
        // Minute-rounded lore must not keep extending the deadline on unrelated slot updates.
        if (previous != null && Math.abs(previous.expiresAt - deadline) < 61_000_000_000L)
            deadline = Math.min(previous.expiresAt, deadline);
        QUESTS.put(quest.key(), new Observed(quest, deadline, 0));
        FuntimeQuestProgress.synchronize(quest, previous, now);
        revision++;
    }

    static void advance(String key, long now) {
        advance(key, 1, now);
    }

    static void advance(String key, int amount, long now) {
        Observed entry = QUESTS.get(key);
        if (amount <= 0 || entry == null || entry.expiresAt <= now || entry.current() >= entry.quest.total) return;
        int delta = (int) Math.min((long) entry.quest.total - entry.quest.current, (long) entry.localDelta + amount);
        QUESTS.put(key, new Observed(entry.quest, entry.expiresAt, delta));
        revision++;
        // Reaching an estimated target does not confirm completion. Wait for the server notice/menu.
    }

    static void reset() {
        QUESTS.clear();
        FuntimeQuestProgress.reset();
        revision++;
        COMPLETED.clear();
        clearCompletion();
        observedMenu = null;
        observedItems = new ItemStack[0];
        world = null;
        connection = null;
        serverAddress = null;
        allowedServer = false;
        lastRead = 0;
    }

    /** Supports both one multiline component and separate server messages forming the same notice. */
    static void receiveGameMessage(String message, long now) {
        for (String raw : message.split("\\R")) {
            String line = clean(raw);
            if (COMPLETION.matcher(line).matches()) {
                completionUntil = now + 3_000_000_000L;
                completionName = null;
                continue;
            }
            if (completionUntil == 0 || now >= completionUntil) { clearCompletion(); continue; }
            line = line.replaceFirst("^[^\\p{L}\\p{N}]+", "").replaceFirst("[\\s\\u2500-\\u259F|]+$", "").strip();
            if (line.isEmpty()) continue; // Decorative border/blank lines around the notice.
            if (COMPLETION_END.matcher(line).matches()) {
                if (completionName != null) completeNamedQuest(completionName, now);
                clearCompletion();
            } else if (completionName == null) {
                completionName = line;
            } else {
                clearCompletion(); // Unrelated/interleaved messages must not complete a quest.
            }
        }
    }

    private static void completeNamedQuest(String name, long now) {
        String fullName = normalizedName(name, false);
        List<Observed> matches = QUESTS.values().stream()
                .filter(entry -> entry.expiresAt > now && normalizedName(entry.quest.name, false).equals(fullName)).toList();
        if (matches.isEmpty()) {
            String shortName = normalizedName(name, true);
            matches = QUESTS.values().stream()
                    .filter(entry -> entry.expiresAt > now && normalizedName(entry.quest.name, true).equals(shortName)).toList();
        }
        // Chat omits the target count ("Сломать Камень"). Do not guess between matching difficulties.
        if (matches.size() != 1) return;
        Observed completed = matches.getFirst();
        QUESTS.remove(completed.quest.key());
        FuntimeQuestProgress.untrack(completed.quest.key());
        revision++;
        COMPLETED.put(completed.quest.key(), completed.expiresAt);
    }

    private static String normalizedName(String name, boolean shortName) {
        String normalized = clean(name).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        return shortName ? normalized.replaceFirst("\\s+[0-9][0-9 ]*\\s+раз(?:а)?\\.?$", "") : normalized;
    }

    private static void clearCompletion() { completionUntil = 0; completionName = null; }

    static Quest parse(ItemStack item) {
        if (item.isEmpty()) return null;
        var lore = item.get(DataComponents.LORE);
        if (lore == null) return null;
        return parse(item.getHoverName().getString(), lore.lines().stream().map(line -> line.getString()).toList());
    }

    static Quest parse(String name, List<String> lore) {
        int current = -1, total = -1, reward = -1;
        long refresh = -1;
        boolean hidden = false;
        try {
            for (String raw : lore) {
                String line = clean(raw);
                var progress = PROGRESS.matcher(line);
                if (progress.find()) {
                    current = Integer.parseInt(progress.group(1).replace(" ", ""));
                    total = Integer.parseInt(progress.group(2).replace(" ", ""));
                }
                var experience = REWARD.matcher(line);
                if (experience.find()) reward = Integer.parseInt(experience.group(1).replace(" ", ""));
                var timer = REFRESH.matcher(line);
                if (timer.find()) refresh = duration(timer.group(1));
                hidden |= HIDDEN_STATUS.matcher(line).find();
            }
        } catch (NumberFormatException ignored) { return null; }
        int difficulty = switch (reward) { case 225 -> 0; case 425 -> 1; case 950 -> 2; default -> -1; };
        String title = clean(name).replaceFirst("^\\[[^]]+\\]\\s*", "").strip();
        if (difficulty < 0 || current < 0 || total <= 0 || refresh < 0 || title.isEmpty()) return null;
        return new Quest(title, Math.min(current, total), total, reward, difficulty, refresh, hidden || current >= total);
    }

    static long duration(String text) {
        var matcher = TIME.matcher(text);
        long seconds = 0;
        int end = 0;
        boolean found = false;
        try {
            while (matcher.find()) {
                if (!text.substring(end, matcher.start()).isBlank()) return -1;
                long value = Long.parseLong(matcher.group(1));
                long unit = switch (matcher.group(2).toLowerCase(Locale.ROOT)) {
                    case "д" -> 86400; case "ч" -> 3600; case "м" -> 60; default -> 1;
                };
                seconds = Math.addExact(seconds, Math.multiplyExact(value, unit));
                end = matcher.end();
                found = true;
            }
        } catch (ArithmeticException | NumberFormatException ignored) { return -1; }
        return found && text.substring(end).isBlank() && seconds <= 366L * 86400 ? seconds : -1;
    }

    private static String clean(String text) {
        return FORMATTING.matcher(text).replaceAll("").replace('\u00a0', ' ').replace('\u202f', ' ')
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "").strip();
    }
}
