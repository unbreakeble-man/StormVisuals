package dev.stormvisuals.module;

import dev.stormvisuals.config.ConfigState;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

public final class Auction {
    static final long INTERVAL = 61_000_000_000L;
    private static final long TIMEOUT = 10_000_000_000L;
    private static final Pattern PRICE = Pattern.compile("(?iu)ц[еe][нh][аa]\\s*:\\s*\\$?\\s*([0-9][0-9 ,.'\\u00a0\\u202f]*)");
    private static boolean enabled, autoRelist, sortPrice, showTimer, sendingAutomatic, auctionTransition;
    private static Object world;
    private static long nextRelist, commandUntil;
    private static long afkRetryAt, releaseMovementAt;
    private static RelistFlow flow;
    private static Layout layout;

    private Auction() {}
    public static void setEnabled(boolean on) {
        enabled = on;
        if (!on) reset();
    }
    public static boolean autoRelist() { return autoRelist; }
    public static void autoRelist(boolean on) {
        autoRelist = on;
        nextRelist = 0;
        cancelCycle();
    }
    public static boolean sortPrice() { return sortPrice; }
    public static void sortPrice(boolean on) {
        sortPrice = on;
        if (!on && layout != null) layout.restore();
    }
    public static boolean showTimer() { return showTimer; }
    public static void showTimer(boolean on) { showTimer = on; }
    public static boolean timerVisible() { return enabled && autoRelist && showTimer; }
    public static long secondsUntilRelist() { return flow == null ? countdownSeconds(nextRelist, System.nanoTime()) : 0; }
    static long countdownSeconds(long deadline, long now) {
        long remaining = deadline == 0 ? INTERVAL : Math.max(0, deadline - now);
        return (remaining + 999_999_999L) / 1_000_000_000L;
    }
    public static void capture(ConfigState.ModuleState s) {
        s.auctionAutoRelist = autoRelist;
        s.auctionSortPrice = sortPrice;
        s.auctionShowTimer = showTimer;
    }
    public static void restore(ConfigState.ModuleState s) {
        autoRelist(s.auctionAutoRelist);
        sortPrice(s.auctionSortPrice);
        showTimer(s.auctionShowTimer);
    }

    public static void initialize() {
        ClientSendMessageEvents.COMMAND.register(command -> {
            if (!enabled || sendingAutomatic) return;
            commandUntil = isAuctionCommand(command) ? System.nanoTime() + TIMEOUT : 0;
            cancelCycle();
        });
        ClientSendMessageEvents.COMMAND_CANCELED.register(command -> {
            if (sendingAutomatic) cancelCycle();
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            String text = plainText(message.getString()).toLowerCase(Locale.ROOT);
            if (flow != null && text.contains("команда недоступна") && text.contains("afk")) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null && client.screen == null && afkRetryAt == 0) {
                    client.options.keyUp.setDown(true);
                    releaseMovementAt = System.nanoTime() + 150_000_000L;
                    afkRetryAt = System.nanoTime() + 1_000_000_000L;
                }
            }
            if (flow != null && text.contains("подождите")) {
                var wait = Pattern.compile("подождите\\s+(\\d+)\\s*сек").matcher(text);
                if (wait.find()) {
                    nextRelist = System.nanoTime() + (Long.parseLong(wait.group(1)) + 1) * 1_000_000_000L;
                    cancelCycle();
                }
            }
            if (flow != null && flow.attemptedMenu != null && relistFinished(message.getString())) flow.acknowledged = true;
        });
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            boolean inheritedAuction = auctionTransition || layout != null && layout.screen == screen && layout.auction;
            auctionTransition = false;
            if (!(screen instanceof ContainerScreen chest)) return;
            if (enabled && chest.getMenu().getRowCount() == 6 && (layout == null || layout.screen != screen))
                layout = new Layout(chest, inheritedAuction || System.nanoTime() < commandUntil || auctionTitle(screen.getTitle().getString()));
            // Explicit closing still interrupts automation; unrelated keys no longer lose the current step.
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (event.isEscape() || client.options.keyInventory.matches(event)) cancelCycle();
                return true;
            });
            ScreenEvents.beforeRender(screen).register((s, graphics, mouseX, mouseY, delta) -> {
                if (enabled && layout != null && layout.screen == screen) layout.update(sortPrice && flow == null);
            });
            ScreenEvents.remove(screen).register(s -> {
                if (layout != null && layout.screen == screen) {
                    auctionTransition = layout.auction;
                    layout.restore();
                    layout = null;
                }
            });
        });
        ClientTickEvents.END_CLIENT_TICK.register(Auction::tick);
    }

    static boolean isAuctionCommand(String command) {
        String text = command.strip().toLowerCase(Locale.ROOT);
        if (text.startsWith("/")) text = text.substring(1);
        return text.equals("ah") || text.equals("ah search") || text.startsWith("ah search ");
    }

    private static boolean auctionTitle(String title) {
        String text = title.toLowerCase(Locale.ROOT);
        return text.contains("аукцион") || text.contains("auction") || text.contains("/ah")
                || text.contains("поиск") || text.contains("search");
    }

    public static boolean isAuctionMenu(AbstractContainerMenu menu) {
        return enabled && layout != null && layout.menu == menu && layout.auction;
    }

    /** Display-only slots keep the server container untouched and carry its original click index. */
    public static Slot displaySlot(AbstractContainerMenu menu, Slot slot) {
        if (!enabled || !sortPrice || flow != null || layout == null || layout.menu != menu || !layout.sorted) return slot;
        return layout.display(slot);
    }

    private static void tick(Minecraft client) {
        long time = System.nanoTime();
        if (releaseMovementAt != 0 && time >= releaseMovementAt) {
            client.options.keyUp.setDown(false);
            releaseMovementAt = 0;
        }
        if (!enabled) return;
        if (client.player == null || client.level == null || client.getConnection() == null) { reset(); return; }
        if (world != client.level) { reset(); world = client.level; }
        long now = System.nanoTime();
        if (afkRetryAt != 0) {
            if (now < afkRetryAt) return;
            afkRetryAt = 0;
            flow = new RelistFlow();
            openAuction(client, now);
            if (flow == null) return;
            flow.readyAt = now + 600_000_000L;
            flow.retryAt = now + 2_000_000_000L;
        }
        auctionTransition = false;
        ContainerScreen screen = client.screen instanceof ContainerScreen chest ? chest : null;
        if (layout != null && layout.screen != screen) { layout.restore(); layout = null; }
        if (screen != null && screen.getMenu().getRowCount() == 6) {
            if (layout == null) layout = new Layout(screen, now < commandUntil || auctionTitle(screen.getTitle().getString()));
            layout.update(sortPrice && flow == null);
        }
        if (!autoRelist || client.player.isSpectator() || client.gameMode == null) { cancelCycle(); return; }
        if (flow == null) {
            if (nextRelist == 0) nextRelist = now + INTERVAL;
            if (now < nextRelist || !client.player.containerMenu.getCarried().isEmpty()) return;
            nextRelist += INTERVAL;
            if (nextRelist <= now) nextRelist = now + INTERVAL;
            flow = new RelistFlow();
            openAuction(client, now);
            if (flow == null) return;
            flow.retryAt = now + 2_000_000_000L;
            flow.readyAt = now + 600_000_000L;
        }
        ChestMenu menu = screen == null ? null : screen.getMenu();
        if (menu != null && client.player.containerMenu != menu) return;
        if (!client.player.containerMenu.getCarried().isEmpty()) return;
        var info = client.getConnection().getPlayerInfo(client.player.getUUID());
        long retryDelay = Math.max(700, (info == null ? 150 : info.getLatency()) + 250L) * 1_000_000L;
        switch (flow.next(menu, menu != null && (isAuctionMenu(menu) || now < commandUntil), now, retryDelay)) {
            case OPEN -> openAuction(client, now);
            case NAVIGATE, RELIST -> click(client, menu, flow.slot);
            case CLOSE -> client.player.closeContainer();
            case DONE -> { nextRelist = now + INTERVAL; cancelCycle(); }
            case WAIT -> { }
        }
    }

    private static void openAuction(Minecraft client, long now) {
        commandUntil = now + TIMEOUT;
        sendingAutomatic = true;
        try { client.getConnection().sendCommand("ah"); }
        finally { sendingAutomatic = false; }
    }

    enum RelistAction { WAIT, OPEN, NAVIGATE, RELIST, CLOSE, DONE }

    /** Steps advance on observed server results, never merely because a click was sent. */
    static final class RelistFlow {
        int stage = 1, slot, beforeLots;
        long retryAt, readyAt;
        ChestMenu attemptedMenu, navigationMenu;
        boolean acknowledged;

        RelistAction next(ChestMenu menu, boolean auction, long now, long retryDelay) {
            if (now < readyAt) return RelistAction.WAIT;
            if (stage == 3) {
                if (menu == null) return RelistAction.DONE;
                if (now < retryAt) return RelistAction.WAIT;
                retryAt = now + retryDelay;
                return RelistAction.CLOSE;
            }
            if (attemptedMenu != null && (acknowledged || menu == null
                    || menu != attemptedMenu && relistSlot(menu) < 0 || lotCount(menu) < beforeLots)) {
                stage = 3;
                retryAt = now;
                return next(menu, auction, now, retryDelay);
            }
            int relist = menu == null ? -1 : relistSlot(menu);
            if (relist >= 0 && stage == 1) { stage = 2; retryAt = now; }
            if (stage == 1 && auction && menu != null && menu != navigationMenu && navigationSlot(menu) >= 0) {
                navigationMenu = menu;
                retryAt = now;
            }
            if (now < retryAt) return RelistAction.WAIT;
            retryAt = now + retryDelay;
            readyAt = now + 600_000_000L;
            if (stage == 2 && relist >= 0) {
                attemptedMenu = menu;
                beforeLots = lotCount(menu);
                slot = relist;
                return RelistAction.RELIST;
            }
            if (stage == 1 && auction && menu != null && (slot = navigationSlot(menu)) >= 0) return RelistAction.NAVIGATE;
            // A missing/unfinished menu is recovered by reopening /ah, without abandoning the cycle.
            stage = 1;
            attemptedMenu = null;
            acknowledged = false;
            retryAt = now + 2_000_000_000L;
            return RelistAction.OPEN;
        }
    }

    static int navigationSlot(ChestMenu menu) {
        int count = menu.getRowCount() * 9;
        for (int i = Math.max(0, count - 9); i < count; i++) {
            String name = plainText(menu.slots.get(i).getItem().getHoverName().getString()).toLowerCase(Locale.ROOT);
            if (name.contains("мои товары") || name.contains("мои предметы") || name.contains("истекшие товары")
                    || name.contains("просроченные товары")) return i;
        }
        return count == 54 && menu.slots.get(46).hasItem() ? 46 : -1;
    }

    private static int lotCount(ChestMenu menu) {
        int result = 0;
        for (int i = 0; i < (menu.getRowCount() - 1) * 9; i++) if (menu.slots.get(i).hasItem()) result++;
        return result;
    }

    static boolean relistFinished(String message) {
        String text = plainText(message).toLowerCase(Locale.ROOT);
        return text.contains("выстав") && (text.contains("успеш") || text.contains("нечего"))
                || (text.contains("нет предметов") || text.contains("нет товаров"))
                && (text.contains("перевыстав") || text.contains("истек") || text.contains("просроч"));
    }

    static int relistSlot(ChestMenu menu) {
        // The relisting page can have fewer rows than the main /ah page.
        // Search only server menu slots, never the player's inventory.
        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack item = menu.slots.get(i).getItem();
            String name = plainText(item.getHoverName().getString())
                    .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (!item.isEmpty() && name.contains("перевыставить предметы")) return menu.slots.get(i).index;
        }
        return -1;
    }

    private static void click(Minecraft client, ChestMenu menu, int slot) {
        // Native inventory handling retains the server's container/state IDs.
        ItemStack button = menu.slots.get(slot).getItem().copy();
        client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, client.player);
        // These are server GUI buttons, not inventory transfers. Undo only our own
        // pickup prediction so an ignored click cannot leave the next retry stuck.
        if (client.player.containerMenu == menu) restoreButtonPrediction(menu, slot, button);
    }

    static void restoreButtonPrediction(ChestMenu menu, int slot, ItemStack button) {
        if (menu.slots.get(slot).getItem().isEmpty() && ItemStack.matches(menu.getCarried(), button)) {
            menu.slots.get(slot).set(button);
            menu.setCarried(ItemStack.EMPTY);
        }
    }

    private static void cancelCycle() {
        if (releaseMovementAt != 0) Minecraft.getInstance().options.keyUp.setDown(false);
        releaseMovementAt = afkRetryAt = 0;
        flow = null;
    }
    private static void reset() {
        cancelCycle();
        nextRelist = commandUntil = 0;
        auctionTransition = false;
        world = null;
        if (layout != null) { layout.restore(); layout = null; }
    }

    static BigDecimal price(ItemStack item) {
        var lore = item.get(DataComponents.LORE);
        if (lore == null) return null;
        for (Component line : lore.lines()) {
            BigDecimal result = parsePrice(line.getString());
            if (result != null) return result;
        }
        return null;
    }

    static boolean hasAuctionListings(List<ItemStack> items) {
        for (int i = 0; i < Math.min(46, items.size()); i++) {
            ItemStack item = items.get(i);
            if (price(item) == null) continue;
            var lore = item.get(DataComponents.LORE);
            boolean seller = false, buy = false;
            for (Component line : lore.lines()) {
                String text = plainText(line.getString()).toLowerCase(Locale.ROOT);
                seller |= text.contains("продавец");
                buy |= text.contains("купить");
            }
            if (seller || buy) return true;
        }
        return false;
    }

    static BigDecimal parsePrice(String line) {
        var match = PRICE.matcher(plainText(line));
        if (!match.find()) return null;
        // Server money uses grouping separators, e.g. $50,000 in the supplied tooltip.
        String number = match.group(1).replaceAll("[ ,'\\u00a0\\u202f]", "");
        try { return new BigDecimal(number); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static String plainText(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("§[0-9a-fk-orxA-FK-ORX]", "")
                .replaceAll("\\p{Cf}", "").replace('\u00a0', ' ').replace('\u202f', ' ');
    }

    // Indices, rather than stacks, are sorted so purchases keep their original server slot.
    static List<Integer> priceOrder(List<ItemStack> items) {
        List<Integer> order = new ArrayList<>();
        BigDecimal[] prices = new BigDecimal[46];
        for (int i = 0; i < 46; i++) {
            prices[i] = price(items.get(i));
            // Slot 45 normally belongs to navigation. Include it only if it is a priced lot.
            if (i < 45 || prices[i] != null) order.add(i);
        }
        order.sort(Comparator.comparing((Integer i) -> prices[i], Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(i -> items.get(i).isEmpty()));
        return order;
    }

    private static List<ItemStack> snapshot(ChestMenu menu) {
        List<ItemStack> items = new ArrayList<>(54);
        for (int i = 0; i < 54; i++) items.add(menu.slots.get(i).getItem().copy());
        return items;
    }
    private static boolean matches(ChestMenu menu, List<ItemStack> items) {
        if (items == null) return false;
        for (int i = 0; i < 54; i++) if (!ItemStack.matches(items.get(i), menu.slots.get(i).getItem())) return false;
        return true;
    }

    private static final class Layout {
        final ContainerScreen screen;
        final ChestMenu menu;
        final Slot[] displayed = new Slot[46];
        boolean auction;
        List<ItemStack> contents;
        boolean sorted;

        Layout(ContainerScreen screen, boolean auction) {
            this.screen = screen;
            menu = screen.getMenu();
            this.auction = auction;
        }
        void update(boolean sort) {
            if (!sort) { restore(); return; }
            if (contents != null && matches(menu, contents)) return;
            restore();
            contents = snapshot(menu);
            // Search results can be titled solely with the item name, e.g. "Эндер-жемчуг".
            // Contents also arrive after screen init, so recognition must happen here.
            if (!auction) auction = hasAuctionListings(contents);
            if (!auction) return;
            List<Integer> order = priceOrder(contents);
            List<Integer> positions = new ArrayList<>(order);
            positions.sort(Comparator.naturalOrder());
            for (int i = 0; i < order.size(); i++) {
                Slot source = menu.slots.get(order.get(i));
                Slot position = menu.slots.get(positions.get(i));
                Slot display = new Slot(source.container, source.getContainerSlot(), position.x, position.y);
                display.index = source.index;
                displayed[positions.get(i)] = display;
            }
            sorted = true;
        }
        Slot display(Slot position) {
            int index = position.index;
            return index >= 0 && index < displayed.length && displayed[index] != null ? displayed[index] : position;
        }
        void restore() {
            if (!sorted) return;
            java.util.Arrays.fill(displayed, null);
            sorted = false;
            contents = null;
        }
    }
}
