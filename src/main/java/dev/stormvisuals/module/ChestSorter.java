package dev.stormvisuals.module;

import dev.stormvisuals.gui.StormButton;
import dev.stormvisuals.config.ConfigState;
import dev.stormvisuals.mixin.ContainerScreenAccessor;
import net.minecraft.world.inventory.AbstractContainerMenu;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** Cursor-empty transactions through vanilla clicks, restricted to chest slots. */
public final class ChestSorter {
    public static final String[] MODES = {"По типу", "По количеству", "По ценности"};
    private static boolean enabled;
    private static int mode;
    private static ChestMenu sorting;
    private static List<ItemStack> expected;
    private static int waitTicks, settleTicks;
    private static boolean waiting;
    private static String status = "";
    private static final Comparator<ItemStack> ORDER = Comparator
            .comparing((ItemStack item) -> item.isEmpty() ? "\uffff" : BuiltInRegistries.ITEM.getKey(item.getItem()).toString())
            .thenComparing(item -> item.isEmpty() ? "" : item.getComponents().toString())
            .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
    private ChestSorter() {}
    public static void setEnabled(boolean on) { enabled = on; if (!on) stop(""); }

    public static int mode() { return mode; }
    public static void mode(int value) { mode = Math.clamp(value, 0, 2); stop(""); }
    public static void capture(ConfigState.ModuleState s) { s.chestSortMode = mode; }
    public static void restore(ConfigState.ModuleState s) { mode(s.chestSortMode); }
    public static boolean isSorting(AbstractContainerMenu menu) { return sorting == menu; }

    static int compare(ItemStack a, ItemStack b) {
        if (a.isEmpty() || b.isEmpty()) return a.isEmpty() == b.isEmpty() ? 0 : a.isEmpty() ? 1 : -1;
        int primary = switch (mode) {
            case 1 -> Integer.compare(b.getCount(), a.getCount());
            case 2 -> Integer.compare(value(b), value(a));
            default -> 0;
        };
        return primary != 0 ? primary : ORDER.compare(a, b);
    }
    // Vanilla rarity/material priority, independent of server-specific economy prices.
    static int value(ItemStack stack) {
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        int material = id.contains("netherite") || id.equals("ancient_debris") ? 90
                : id.equals("totem_of_undying") || id.equals("elytra") || id.equals("nether_star") ? 85
                : id.contains("diamond") ? 80 : id.contains("emerald") ? 70
                : id.contains("gold") ? 60 : id.contains("iron") ? 50
                : id.contains("copper") ? 40 : id.contains("redstone") || id.contains("lapis") ? 30
                : id.contains("coal") ? 20 : 0;
        return stack.getRarity().ordinal() * 100 + material;
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(ChestSorter::tick);
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof ContainerScreen chest)) return;
            ChestMenu menu = chest.getMenu();
            var origin = (ContainerScreenAccessor) chest;
            int left = origin.stormvisuals$left(), top = origin.stormvisuals$top();
            // Attached to the right of the title strip, clear of even long custom container names.
            StormButton button = new StormButton(left + 180, top + 3, 48, 16, "Sort", () -> {
                if (sorting == menu) stop("Сортировка остановлена");
                else start(menu);
            });
            button.setTooltip(Tooltip.create(Component.literal("Сортировать сундук / остановить")));
            button.visible = enabled;
            Screens.getButtons(screen).add(button);
            ScreenEvents.afterTick(screen).register(s -> {
                button.visible = enabled && !Auction.isAuctionMenu(menu);
                button.active = client.player != null && !client.player.isSpectator() && (sorting == menu || menu.getCarried().isEmpty());
                button.setMessage(Component.literal(sorting == menu ? "Stop" : "Sort"));
            });
            ScreenEvents.afterRender(screen).register((s, g, mx, my, delta) -> {
                if (enabled && !status.isEmpty() && sorting == null && button.isHoveredOrFocused())
                    g.setTooltipForNextFrame(client.font, Component.literal(status), mx, my);
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> sorting != menu || button.isMouseOver(event.x(), event.y()));
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> sorting != menu || button.isMouseOver(event.x(), event.y()));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> sorting != menu);
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (sorting != menu) return true;
                if (event.isEscape() || client.options.keyInventory.matches(event)) { stop(""); return true; }
                return false;
            });
            ScreenEvents.remove(screen).register(s -> { if (sorting == menu) stop(""); });
        });
    }

    private static void start(ChestMenu menu) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || sorting != null || client.player == null || client.player.isSpectator()
                || client.player.containerMenu != menu || !menu.getCarried().isEmpty() || Auction.isAuctionMenu(menu)) return;
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            var slot = menu.slots.get(i);
            if (!slot.mayPickup(client.player) || !slot.mayPlace(slot.getItem())) { status = "Этот контейнер запрещает сортировку"; return; }
        }
        sorting = menu; expected = snapshot(menu); waiting = false; waitTicks = 0; status = "";
    }

    private static void tick(Minecraft client) {
        if (sorting == null) return;
        if (!enabled || client.player == null || client.gameMode == null || client.player.containerMenu != sorting
                || !(client.screen instanceof ContainerScreen)) { stop(""); return; }
        if (waiting) {
            waitTicks++;
            if (waitTicks > 100) { stop("Сервер не подтвердил сортировку"); return; }
            // Vanilla sends corrections, not an acknowledgement for correctly predicted clicks.
            if (waitTicks < settleTicks || !matches(sorting, expected)) return;
            if (!sorting.getCarried().isEmpty()) { stop("Сортировка прервана: предмет на курсоре"); return; }
            waiting = false;
        }
        if (!matches(sorting, expected) || !sorting.getCarried().isEmpty()) { stop("Содержимое изменилось — нажмите сортировку снова"); return; }
        Move move = nextMove(sorting, expected);
        if (move == null) stop("Сундук отсортирован");
        else transaction(client, move.source, move.target);
    }

    record Move(int source, int target) {}
    static Move nextMove(ChestMenu menu, List<ItemStack> items) {
        int count = items.size();
        // Consolidate compatible components, preserving enchantments, names and container contents.
        for (int target = 0; target < count; target++) {
            ItemStack a = items.get(target);
            if (a.isEmpty() || a.getCount() >= menu.slots.get(target).getMaxStackSize(a)) continue;
            for (int source = target + 1; source < count; source++) {
                ItemStack b = items.get(source);
                if (!b.isEmpty() && ItemStack.isSameItemSameComponents(a, b)) {
                    return new Move(source, target);
                }
            }
        }
        // Selection sort requires no spare slot, even when the chest is completely full.
        for (int target = 0; target < count; target++) {
            int best = target;
            for (int source = target + 1; source < count; source++)
                if (compare(items.get(source), items.get(best)) < 0) best = source;
            if (best != target) return new Move(best, target);
        }
        return null;
    }

    private static void transaction(Minecraft client, int source, int target) {
        var info = client.getConnection().getPlayerInfo(client.player.getUUID());
        int latency = info == null ? 150 : Math.max(0, info.getLatency());
        settleTicks = Math.clamp((latency + 49) / 50 + 3, 3, 80);
        client.gameMode.handleInventoryMouseClick(sorting.containerId, source, 0, ClickType.PICKUP, client.player);
        client.gameMode.handleInventoryMouseClick(sorting.containerId, target, 0, ClickType.PICKUP, client.player);
        if (!sorting.getCarried().isEmpty())
            client.gameMode.handleInventoryMouseClick(sorting.containerId, source, 0, ClickType.PICKUP, client.player);
        List<ItemStack> after = snapshot(sorting);
        if (matches(sorting, expected)) { stop("Этот контейнер не поддерживает сортировку"); return; }
        expected = after;
        if (!sorting.getCarried().isEmpty()) { stop("Сортировка прервана: предмет на курсоре"); return; }
        waiting = true; waitTicks = 0;
    }
    private static List<ItemStack> snapshot(ChestMenu menu) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < menu.getRowCount() * 9; i++) items.add(menu.slots.get(i).getItem().copy());
        return items;
    }
    private static boolean matches(ChestMenu menu, List<ItemStack> items) {
        for (int i = 0; i < items.size(); i++) if (!ItemStack.matches(items.get(i), menu.slots.get(i).getItem())) return false;
        return true;
    }
    private static void stop(String message) { sorting = null; expected = null; waiting = false; status = message; }
}
