package dev.stormvisuals.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real Minecraft stacks/slot limits; no running client, world or network is needed. */
public final class UtilsChecks {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        QuestChecks.run();
        auctionChecks();
        Inventory inventory = new Inventory(null, new EntityEquipment());
        check(ItemScroller.crossing(8, 8, 8, 8, 0, 0) == 0, "Initial held click hits its slot");
        check(Double.isNaN(ItemScroller.crossing(17, 8, 17, 8, 0, 0)), "Slot gaps are not clicked");
        for (int column = 0; column < 9; column++) {
            check(!Double.isNaN(ItemScroller.crossing(-4, 8, 170, 8, column * 18, 0)), "Fast forward sweep visits every slot");
            check(!Double.isNaN(ItemScroller.crossing(170, 8, -4, 8, column * 18, 0)), "Fast reverse sweep visits every slot");
        }
        check(Double.isNaN(ItemScroller.crossing(-4, 20, 170, 20, 0, 0)), "Adjacent rows are untouched");
        check(Double.isNaN(ItemScroller.crossing(0, 0, 2, 2, 80, 80)), "Only the actual sweep segment is used");
        check(!Double.isNaN(ItemScroller.crossing(-10, -10, 90, 90, 36, 36)), "Diagonal sweep supported");
        check(ChestSorter.value(new ItemStack(Items.DIAMOND)) > ChestSorter.value(new ItemStack(Items.IRON_INGOT)), "Diamond ranks above iron");
        check(ChestSorter.value(new ItemStack(Items.NETHERITE_INGOT)) > ChestSorter.value(new ItemStack(Items.DIAMOND)), "Netherite ranks above diamond");

        var types = List.of(Items.STONE, Items.APPLE, Items.DIAMOND, Items.ENDER_PEARL, Items.IRON_SWORD);
        MenuType<?>[] menus = {MenuType.GENERIC_9x1, MenuType.GENERIC_9x2, MenuType.GENERIC_9x3,
                MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6};
        for (int mode = 0; mode < 3; mode++) {
        ChestSorter.mode(mode);
        for (int rows = 1; rows <= 6; rows++) {
            ChestMenu menu = new ChestMenu(menus[rows - 1], 1, inventory, new SimpleContainer(rows * 9), rows);
            List<ItemStack> empty = new ArrayList<>(Collections.nCopies(rows * 9, ItemStack.EMPTY));
            check(ChestSorter.nextMove(menu, empty) == null, "Empty chest needs no clicks");
            for (boolean full : new boolean[]{false, true}) {
                List<ItemStack> items = new ArrayList<>();
                for (int i = 0; i < rows * 9; i++) {
                    Item type = types.get(i % types.size());
                    ItemStack stack = new ItemStack(type);
                    stack.setCount(full ? stack.getMaxStackSize() : Math.min(stack.getMaxStackSize(), 1 + i * 7 % 48));
                    if (i % 7 == 0) stack.set(DataComponents.CUSTOM_NAME, Component.literal("Keep " + i % 3));
                    if (type == Items.IRON_SWORD) stack.set(DataComponents.DAMAGE, i % 11);
                    items.add(stack);
                }
                Collections.shuffle(items, new Random(100 + rows));
                Map<String, Integer> before = totals(items);
                int steps = 0;
                ChestSorter.Move move;
                while ((move = ChestSorter.nextMove(menu, items)) != null) {
                    check(++steps < 2000, "Sorter terminates");
                    check(move.source() >= 0 && move.source() < rows * 9 && move.target() >= 0 && move.target() < rows * 9,
                            "Only chest slots are touched");
                    pickupTransaction(items, move.source(), move.target(), menu);
                    check(totals(items).equals(before), "Every transaction preserves items and all components");
                }
                ItemStack previous = null;
                Map<String, Integer> partialStacks = new HashMap<>();
                for (ItemStack stack : items) {
                    check(previous == null || ChestSorter.compare(previous, stack) <= 0, "Selected sorting order respected, empties last");
                    previous = stack;
                    if (!stack.isEmpty()) {
                        check(stack.getCount() <= stack.getMaxStackSize(), "Stack limits respected");
                        if (stack.getCount() < stack.getMaxStackSize())
                            check(partialStacks.merge(key(stack), 1, Integer::sum) == 1, "Compatible stacks completely consolidated");
                    }
                }
                check(ChestSorter.nextMove(menu, items) == null, "Sorting is idempotent");
            }
        }
        }
        System.out.println("Utils: immediate sweep intersection; three sorting modes, chest sizes 9–54, full/partial stacks, component preservation and termination passed");
    }

    // Ordinary left-click pickup rules: pickup source, deposit/swap target, return any remainder.
    private static void pickupTransaction(List<ItemStack> items, int source, int target, ChestMenu menu) {
        ItemStack carried = items.get(source);
        ItemStack destination = items.get(target);
        items.set(source, ItemStack.EMPTY);
        if (destination.isEmpty()) { items.set(target, carried); return; }
        if (ItemStack.isSameItemSameComponents(carried, destination)) {
            int moved = Math.min(carried.getCount(), menu.slots.get(target).getMaxStackSize(destination) - destination.getCount());
            destination.grow(moved); carried.shrink(moved);
            if (!carried.isEmpty()) items.set(source, carried);
        } else { items.set(target, carried); items.set(source, destination); }
    }
    private static String key(ItemStack stack) { return BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getComponents(); }
    private static Map<String, Integer> totals(List<ItemStack> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : items) if (!stack.isEmpty()) counts.merge(key(stack), stack.getCount(), Integer::sum);
        return counts;
    }
    private static void auctionChecks() {
        Inventory inventory = new Inventory(null, new EntityEquipment());
        ItemStack relistButton = new ItemStack(Items.PAPER);
        relistButton.set(DataComponents.CUSTOM_NAME, Component.literal("§c[↻] §6Перевыставить предметы"));
        ChestMenu small = new ChestMenu(MenuType.GENERIC_9x3, 2, inventory, new SimpleContainer(27), 3);
        small.slots.get(25).set(relistButton.copy());
        check(Auction.relistSlot(small) == 25, "Relisting button found in a three-row submenu");
        small.slots.get(25).set(ItemStack.EMPTY);
        small.slots.get(52).set(relistButton.copy());
        check(Auction.relistSlot(small) == -1, "Player inventory slot 52 must never be clicked as a menu control");
        ChestMenu large = new ChestMenu(MenuType.GENERIC_9x6, 3, inventory, new SimpleContainer(54), 6);
        large.slots.get(52).set(relistButton.copy());
        check(Auction.relistSlot(large) == 52, "Original six-row relisting button still supported");
        large.slots.get(52).set(new ItemStack(Items.PAPER));
        check(Auction.relistSlot(large) == -1, "Unrelated paper at slot 52 is not a relisting button");
        auctionRetryChecks(inventory, relistButton);
        check(Auction.INTERVAL == 61_000_000_000L, "Relisting uses 61 real seconds");
        check(Auction.countdownSeconds(0, 123) == 61, "The timer starts at the configured 61-second interval");
        check(Auction.countdownSeconds(61_000_000_000L, 1_000_000_000L) == 60,
                "The HUD countdown follows the actual relisting deadline");
        check(Auction.countdownSeconds(1_000_000_001L, 1_000_000_000L) == 1
                && Auction.countdownSeconds(1_000_000_000L, 2_000_000_000L) == 0,
                "The countdown rounds up and never displays negative seconds");
        check(Auction.isAuctionCommand("ah") && Auction.isAuctionCommand("/ah search зелье"), "Auction and search commands recognized");
        check(!Auction.isAuctionCommand("ah sell 500") && !Auction.isAuctionCommand("ahistory"), "Other commands do not identify auction listings");
        check(Auction.parsePrice("$ Цена: $50,000").longValueExact() == 50_000, "Screenshot price parsed");
        check(Auction.parsePrice("§a$ §fЦена: §a$1,250,000").longValueExact() == 1_250_000, "Legacy colors and grouping parsed");
        check(Auction.parsePrice("Цена: $1\u00a0250\u202f000").longValueExact() == 1_250_000, "Nonbreaking price grouping parsed");
        check(Auction.parsePrice("Цена: $0").signum() == 0, "Free listings sort first");
        check(Auction.parsePrice("Цена: $1,250.50").compareTo(new java.math.BigDecimal("1250.50")) == 0, "Fractional prices retain their value");
        check(Auction.parsePrice("$ Цена:\u00a0$70,000").longValueExact() == 70_000, "Screenshot price supports nonbreaking spacing");
        check(Auction.parsePrice("$ Цeнa: $70,000 ◆").longValueExact() == 70_000, "Visually identical Latin letters and trailing icons do not hide the price");
        check(Auction.parsePrice("Це\u200bна: $100,000").longValueExact() == 100_000, "Invisible server formatting does not hide the price");
        check(Auction.parsePrice("Продавец: V_77") == null && Auction.parsePrice("Истекает: 24 ч.") == null,
                "Seller and expiry numbers are not prices");
        check(Auction.parsePrice("Цена: неизвестно") == null && Auction.parsePrice("Цена: $-1") == null, "Invalid prices ignored");
        List<ItemStack> listings = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY));
        listings.set(0, listing("50,000", Items.POTION));
        listings.set(1, listing("5", Items.DIAMOND));
        listings.set(2, new ItemStack(Items.GRAY_STAINED_GLASS_PANE));
        listings.set(3, listing("5", Items.APPLE));
        listings.set(4, listing("0", Items.STONE));
        listings.set(46, listing("1", Items.PAPER));
        listings.set(52, listing("2", Items.PAPER));
        var order = Auction.priceOrder(listings);
        check(order.subList(0, 4).equals(List.of(4, 1, 3, 0)), "Numeric ascending sort preserves equal-price order");
        check(!order.contains(46) && !order.contains(52), "Control row stays fixed");
        check(order.get(4) == 2 && order.get(5) == 5 && listings.get(0).is(Items.POTION), "Priced lots come first, then unknown prices, then empties; server contents stay unchanged");
        check(Auction.hasAuctionListings(listings), "Listings identify item-named auction search pages without a command or auction title");
        check(!Auction.hasAuctionListings(Collections.nCopies(54, ItemStack.EMPTY)), "Unpopulated server menu is not yet an auction");
        List<ItemStack> ordinaryChest = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY));
        ItemStack priceTag = new ItemStack(Items.PAPER);
        priceTag.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(Component.literal("Цена: $100"))));
        ordinaryChest.set(0, priceTag);
        check(!Auction.hasAuctionListings(ordinaryChest), "An ordinary chest with a price tag does not enable auction sorting");
        List<ItemStack> page = new ArrayList<>(Collections.nCopies(54, ItemStack.EMPTY));
        for (int i = 0; i < 45; i++) page.set(i, listing(Integer.toString((45 - i) * 10_000), Items.ENDER_PEARL));
        page.set(45, new ItemStack(Items.ARROW));
        List<ItemStack> original = page.stream().map(ItemStack::copy).toList();
        var sortedPage = Auction.priceOrder(page);
        check(sortedPage.size() == 45 && sortedPage.getFirst() == 44 && sortedPage.getLast() == 0,
                "All 45 page listings sort from cheapest to most expensive; navigation is excluded");
        for (int i = 0; i < 54; i++) check(ItemStack.matches(page.get(i), original.get(i)), "Sorting preserves server slot contents and purchase indices");
        page.set(45, listing("1", Items.ENDER_PEARL));
        check(Auction.priceOrder(page).getFirst() == 45, "Slot 45 is included when it actually contains a priced lot");
    }
    private static ItemStack listing(String price, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(List.of(
                Component.literal("↪ Нажмите, чтобы купить"), Component.literal("Продавец: V_77"),
                Component.literal("$ Цена: $" + price), Component.literal("Истекает: 24 ч."))));
        return stack;
    }

    private static void auctionRetryChecks(Inventory inventory, ItemStack button) {
        long retry = 700_000_000L;
        ChestMenu main = new ChestMenu(MenuType.GENERIC_9x6, 4, inventory, new SimpleContainer(54), 6);
        ItemStack mine = new ItemStack(Items.CHEST);
        mine.set(DataComponents.CUSTOM_NAME, Component.literal("Мои товары"));
        main.slots.get(45).set(mine);
        ChestMenu sales = new ChestMenu(MenuType.GENERIC_9x6, 5, inventory, new SimpleContainer(54), 6);
        sales.slots.get(52).set(button.copy());
        sales.slots.get(0).set(new ItemStack(Items.DIAMOND));
        var flow = new Auction.RelistFlow();
        check(flow.next(main, true, 0, retry) == Auction.RelistAction.NAVIGATE && flow.slot == 45,
                "Navigation uses the named control even when it is at slot 45");
        check(flow.stage == 1 && flow.next(main, true, 100_000_000L, retry) == Auction.RelistAction.WAIT,
                "Sending a navigation click does not advance the step or immediately spam a second click");
        check(flow.next(main, true, retry, retry) == Auction.RelistAction.NAVIGATE && flow.stage == 1,
                "Ignored navigation click is retried until the submenu actually opens");
        check(flow.next(sales, true, 1_400_000_000L, retry) == Auction.RelistAction.RELIST && flow.slot == 52,
                "The relist step starts only when its actual button appears");
        check(flow.next(sales, true, 2_100_000_000L, retry) == Auction.RelistAction.RELIST && flow.stage == 2,
                "Ignored relisting click is repeated instead of prematurely closing the menu");
        ChestMenu reopened = new ChestMenu(MenuType.GENERIC_9x6, 6, inventory, new SimpleContainer(54), 6);
        reopened.slots.get(52).set(button.copy());
        reopened.slots.get(0).set(new ItemStack(Items.DIAMOND));
        check(flow.next(reopened, true, 2_800_000_000L, retry) == Auction.RelistAction.RELIST && flow.stage == 2,
                "A rejected click that reopens the same populated submenu does not falsely complete the cycle");
        sales = reopened;
        check(flow.next(sales, true, 12_000_000_000L, retry) == Auction.RelistAction.RELIST,
                "A slow server does not abandon the sequence after the former ten-second timeout");
        sales.slots.get(0).set(ItemStack.EMPTY);
        check(flow.next(sales, true, 12_700_000_000L, retry) == Auction.RelistAction.CLOSE,
                "Confirmed removal of expired lots advances to closing");
        check(flow.next(sales, true, 13_400_000_000L, retry) == Auction.RelistAction.CLOSE,
                "Closing is repeated if the menu remains open");
        check(flow.next(null, false, 13_500_000_000L, retry) == Auction.RelistAction.DONE,
                "The cycle ends after the menu actually closes");
        flow = new Auction.RelistFlow();
        check(flow.next(null, false, 0, retry) == Auction.RelistAction.OPEN, "A missing auction is opened");
        check(flow.next(null, false, 1_000_000_000L, retry) == Auction.RelistAction.WAIT,
                "Command retries are spaced apart");
        check(flow.next(null, false, 2_000_000_000L, retry) == Auction.RelistAction.OPEN,
                "A lost /ah response is retried");
        check(flow.next(main, true, 2_650_000_000L, retry) == Auction.RelistAction.NAVIGATE,
                "A newly loaded main menu is processed immediately without waiting for the command retry timer");
        flow = new Auction.RelistFlow();
        check(flow.next(sales, true, 0, retry) == Auction.RelistAction.RELIST, "Already open relisting page can resume directly");
        flow.acknowledged = true;
        check(flow.next(sales, true, 650_000_000L, retry) == Auction.RelistAction.CLOSE,
                "Server success/no-items feedback finishes even when menu contents stay unchanged");
        check(Auction.relistFinished("Вы успешно перевыставили предметы")
                && Auction.relistFinished("У вас нет предметов для перевыставления")
                && !Auction.relistFinished("Перевыставление доступно через 2 секунды"),
                "A cooldown response must not be mistaken for successful relisting");
        sales.slots.get(52).set(ItemStack.EMPTY);
        sales.setCarried(button.copy());
        Auction.restoreButtonPrediction(sales, 52, button);
        check(sales.getCarried().isEmpty() && ItemStack.matches(sales.slots.get(52).getItem(), button),
                "Our predicted GUI button pickup cannot block the next attempt");
        sales.slots.get(52).set(ItemStack.EMPTY);
        sales.setCarried(new ItemStack(Items.DIAMOND));
        Auction.restoreButtonPrediction(sales, 52, button);
        check(sales.getCarried().is(Items.DIAMOND) && sales.slots.get(52).getItem().isEmpty(),
                "A genuinely different carried item is never overwritten by button recovery");
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
