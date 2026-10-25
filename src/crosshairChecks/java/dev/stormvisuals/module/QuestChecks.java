package dev.stormvisuals.module;

import dev.stormvisuals.config.ConfigState;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;

/** Screenshot fixtures and real menu updates; no game client or network is launched. */
final class QuestChecks {
    static void run() {
        for (String host : List.of("play.funtime.su", "ANY.funtime.su:25565", "a.b.funtime.su", "play.funtime.su."))
            check(FuntimeQuests.acceptsAddress(host), "Funtime host accepted: " + host);
        for (String host : List.of("funtime.su", "notfuntime.su", "play.funtime.su.evil.net", "funtime.su.evil", "localhost",
                "127.0.0.1", "evil.net:play.funtime.su", "play.funtime.su:65536", ".funtime.su", "a..funtime.su"))
            check(!FuntimeQuests.acceptsAddress(host), "Other host rejected: " + host);
        check(!FuntimeQuests.acceptsAddress(null), "No server is rejected");

        var easy = FuntimeQuests.parse("§c[М] §6Сломать Камень 500 раз", List.of("♧ Прогресс: §e319§6/500", "Награды:",
                "• Опыт пропуска +225", "⌛ До обновления: 3ч 39м"));
        check(easy != null && easy.current() == 319 && easy.total() == 500 && easy.difficulty() == 0 && !easy.hidden(), "Easy screenshot");
        check(easy.name().equals("Сломать Камень 500 раз") && easy.refreshSeconds() == 13140, "Formatting and easy timer");
        var medium = FuntimeQuests.parse("[М] Сдать Бревно тропического дерева 256 раз", List.of("✓ Статус: [Забрано]",
                "♧ Прогресс: 256/256", "→ [ЛКМ] сдать один предмет", "• Опыт пропуска +425", "⌛ До обновления: 3ч 39м"));
        check(medium != null && medium.difficulty() == 1 && medium.hidden(), "Claimed medium screenshot");
        var hard = FuntimeQuests.parse("[М] Убить Крипера 32 раз", List.of("♧ Прогресс: 5/32", "Награды:",
                "• Опыт пропуска +950", "⌛ До обновления: 2д 3ч 39м"));
        check(hard != null && hard.difficulty() == 2 && hard.current() == 5 && hard.refreshSeconds() == 185940, "Hard screenshot");
        check(FuntimeQuests.parse("[М] Задания", List.of("Нажмите для просмотра")) == null, "Navigation button is not a quest");
        check(FuntimeQuests.parse("Камень", List.of("Прогресс: 1/2", "Цена: 225", "До обновления: 1ч")) == null, "Ordinary lore rejected");
        check(FuntimeQuests.parse("Задание", List.of("Прогресс: 1/0", "Опыт пропуска +225", "До обновления: 1ч")) == null, "Invalid target rejected");
        check(FuntimeQuests.parse("Задание", List.of("Прогресс: 1/2", "Опыт пропуска +226", "До обновления: 1ч")) == null, "Unknown reward is not assigned a difficulty");
        check(FuntimeQuests.parse("Задание", List.of("Прогресс: 999999999999999/2", "Опыт пропуска +225", "До обновления: 1ч")) == null, "Overflow rejected");
        check(FuntimeQuests.duration("2д 3ч 39м") == 185940 && FuntimeQuests.duration("1ч30м") == 5400, "Combined time units");
        check(FuntimeQuests.duration("нет") == -1 && FuntimeQuests.duration("1ч ???") == -1
                && FuntimeQuests.duration("99999999999999999999д") == -1, "Malformed timer rejected");
        var notDone = FuntimeQuests.parse("Задание", List.of("Статус: [Не выполнено]", "Прогресс: 1\u00a0000 / 2\u202f000",
                "Опыт пропуска +225", "До обновления: 1ч"));
        check(notDone != null && notDone.current() == 1000 && !notDone.hidden(), "Grouped numbers and negative status");

        statusChecks();

        FuntimeQuests.reset();
        long now = 1_000_000_000L;
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.observe(hard, now);
        FuntimeQuests.observe(easy, now + 1_000_000_000L);
        check(FuntimeQuests.quests().size() == 2 && FuntimeQuests.quests().getFirst().expiresAt() == now + 13140_000_000_000L,
                "Snapshots merge across pages without extending rounded deadlines");
        FuntimeQuests.observe(new FuntimeQuests.Quest(easy.name(), 500, 500, 225, 0, 13140, true), now);
        check(FuntimeQuests.quests().size() == 1 && FuntimeQuests.quests().getFirst().quest().equals(hard), "Completion removes only the confirmed quest");
        FuntimeQuests.show(2, false);
        check(!FuntimeQuests.shows(2) && FuntimeQuests.quests().size() == 1, "Filters preserve cached quests");
        var saved = new ConfigState.ModuleState();
        FuntimeQuests.capture(saved);
        FuntimeQuests.show(2, true);
        FuntimeQuests.restore(saved);
        check(!FuntimeQuests.shows(2), "Difficulty filter restored");
        FuntimeQuests.restore(new ConfigState.ModuleState());

        completionChecks(easy, hard, now);
        progressChecks(easy, hard, now);
        worldTransitionChecks(easy, hard, now);
        fishingChecks(now);
        learningChecks(now);
        screenshotActionChecks(now);

        FuntimeQuests.reset();
        Inventory inventory = new Inventory(null, new EntityEquipment());
        ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x3, 1, inventory, new SimpleContainer(27), 3);
        FuntimeQuests.readMenu(menu, now);
        menu.slots.get(0).set(stack("Задание", 1));
        menu.slots.get(27).set(stack("Квест в инвентаре", 1));
        FuntimeQuests.readMenu(menu, now);
        check(FuntimeQuests.quests().size() == 1, "Initial content with unchanged state ID is read; player slots ignored");
        menu.slots.get(0).set(stack("Задание", 2));
        FuntimeQuests.readMenu(menu, now);
        check(FuntimeQuests.quests().isEmpty(), "Completed slot update removes quest without reopening");
        FuntimeQuests.observe(hard, now);
        FuntimeQuests.setEnabled(false);
        check(FuntimeQuests.quests().isEmpty() && FuntimeQuests.lastRead() == 0, "Disabling clears transient state");
        System.out.println("Quests: parsing, lifecycle, mining/crafting/delivery screenshots, fishing, learned aliases, resync and completion checks passed");
    }

    private static void completionChecks(FuntimeQuests.Quest easy, FuntimeQuests.Quest hard, long now) {
        String notice = "╔\n╠ §6[Боевой Пропуск] §eЗадание выполнено!\n╠ Сломать Камень\n╠ §7Проверь задания: §e/bp\n╚";
        FuntimeQuests.reset();
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.observe(hard, now);
        FuntimeQuests.receiveGameMessage(notice, now);
        check(FuntimeQuests.count() == 1 && FuntimeQuests.quests().getFirst().quest().equals(hard),
                "Screenshot notice removes the named quest outside the menu, preserving other quests");
        FuntimeQuests.observe(easy, now + 1);
        check(FuntimeQuests.count() == 1, "Stale menu snapshot cannot resurrect a completed quest");
        FuntimeQuests.observe(easy, now + 13141_000_000_000L);
        check(FuntimeQuests.count() == 2, "Same quest can appear again after its refresh period");

        FuntimeQuests.reset();
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.receiveGameMessage("╠ [Боевой Пропуск] Задание выполнено!", now);
        FuntimeQuests.receiveGameMessage("╠ Сломать Камень", now + 1);
        check(FuntimeQuests.count() == 1, "Incomplete notice waits for its /bp footer");
        FuntimeQuests.receiveGameMessage("╠ Проверь задания: /bp", now + 2);
        check(FuntimeQuests.count() == 0, "Notice sent as separate server messages is recognized");

        FuntimeQuests.reset();
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.receiveGameMessage("Сломать Камень\nПроверь задания: /bp", now);
        FuntimeQuests.receiveGameMessage("Игрок: [Боевой Пропуск] Задание выполнено!\nСломать Камень\nПроверь задания: /bp", now);
        check(FuntimeQuests.count() == 1, "Ordinary chat and player-prefixed copies do not complete quests");
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень", now);
        FuntimeQuests.receiveGameMessage("Проверь задания: /bp", now + 4_000_000_000L);
        check(FuntimeQuests.count() == 1, "Expired partial notice is ignored");
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень\nПостороннее сообщение\nПроверь задания: /bp", now);
        check(FuntimeQuests.count() == 1, "Interleaved unrelated line cancels recognition");
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Каменную плиту\nПроверь задания: /bp", now);
        check(FuntimeQuests.count() == 1, "Similar but different task name is ignored");

        var duplicate = new FuntimeQuests.Quest("Сломать Камень 1000 раз", 100, 1000, 950, 2, 18000, false);
        FuntimeQuests.observe(duplicate, now);
        FuntimeQuests.receiveGameMessage(notice, now);
        check(FuntimeQuests.count() == 2, "Short name shared by multiple tasks does not remove either by guessing");
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень 500 раз\nПроверь задания: /bp", now);
        check(FuntimeQuests.count() == 1 && FuntimeQuests.quests().getFirst().quest().equals(duplicate),
                "Full task name resolves ambiguity without removing the other difficulty");

        FuntimeQuests.reset();
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень", now);
        FuntimeQuests.reset();
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.receiveGameMessage("Проверь задания: /bp", now);
        check(FuntimeQuests.count() == 1, "World/disconnect reset clears partial notices and completion history");
    }

    private static void statusChecks() {
        FuntimeQuests.reset();
        var active = FuntimeQuests.parse("[М] Убить Зомби 64 раз", List.of("Прогресс: 0/64",
                "Опыт пропуска +950", "До обновления: 2д 3ч 10м"));
        check(active != null && !active.hidden(), "Active task with zero progress is visible");
        for (String status : List.of("§c[Закрыто]", "§7[Забрано]")) {
            var hidden = FuntimeQuests.parse("[М] Убить Зомби 64 раз", List.of("⚒ Статус: " + status,
                    "Доступно с Премиум статуса", "Прогресс: 0/64", "Опыт пропуска +950", "До обновления: 2д 3ч 10м"));
            check(hidden != null && hidden.hidden(), "Hidden status recognized independently of progress: " + status);
            FuntimeQuests.observe(hidden, 1);
            check(FuntimeQuests.count() == 0, "Locked/claimed quest is never added to HUD");
            FuntimeQuests.observe(active, 1);
            FuntimeQuests.observe(hidden, 1);
            check(FuntimeQuests.count() == 0, "Status update removes a previously visible quest");
        }
        FuntimeQuests.observe(active, 1);
        check(FuntimeQuests.count() == 1, "Unlocking a quest allows it to appear again");
        FuntimeQuests.reset();
    }

    private static void progressChecks(FuntimeQuests.Quest easy, FuntimeQuests.Quest hard, long now) {
        FuntimeQuests.reset();
        FuntimeQuestProgress.rebuildNames(Map.of(
                Items.STONE.getDescriptionId(), "Камень",
                Items.ACACIA_LEAVES.getDescriptionId(), "Листва акации",
                Items.GOLDEN_PICKAXE.getDescriptionId(), "Золотая кирка",
                Items.BONE_MEAL.getDescriptionId(), "Костная мука",
                Items.FURNACE.getDescriptionId(), "Печь",
                EntityType.CREEPER.getDescriptionId(), "Крипер"));
        check(FuntimeQuestProgress.accusative("Листва акации", false).equals("листву акации"), "Declines only the head noun");
        check(FuntimeQuestProgress.accusative("Золотая кирка", false).equals("золотую кирку"), "Tool adjective and noun");
        check(FuntimeQuestProgress.accusative("Костная мука", false).equals("костную муку"), "Used item adjective and noun");
        check(FuntimeQuestProgress.accusative("Крипер", true).equals("крипера"), "Animate target name");
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.observe(hard, now);
        var hardStone = new FuntimeQuests.Quest("Сломать Камень 4000 раз", 128, 4000, 950, 2, 3600, false);
        var leaves = new FuntimeQuests.Quest("Сломать Листву акации 120 раз", 0, 120, 225, 0, 3600, false);
        var tool = new FuntimeQuests.Quest("Сломать Золотую кирку 3 раз", 0, 3, 225, 0, 3600, false);
        var lighter = new FuntimeQuests.Quest("Сломать Зажигалку 2 раз", 0, 2, 225, 0, 3600, false);
        var use = new FuntimeQuests.Quest("Использовать Костную муку 512 раз", 0, 512, 950, 2, 3600, false);
        var delivery = new FuntimeQuests.Quest("Сдать Печь 32 раз", 0, 32, 225, 0, 3600, false);
        for (var quest : List.of(hardStone, leaves, tool, lighter, use, delivery)) FuntimeQuests.observe(quest, now);
        long beforeRevision = FuntimeQuests.revision();
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.COBBLESTONE, now);
        check(FuntimeQuests.revision() == beforeRevision, "Wrong block does not change any quest");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        check(entry(easy).current() == 320 && entry(easy).estimated() && entry(easy).quest().current() == 319,
                "Mining advances immediately while preserving the confirmed server baseline");
        check(entry(hardStone).current() == 129 && entry(hard).current() == 5, "One action updates matching difficulties only");
        check(entry(easy).expiresAt() == now + easy.refreshSeconds() * 1_000_000_000L, "Local progress does not extend refresh timer");
        check(FuntimeQuests.revision() > beforeRevision, "Live progress invalidates the HUD before its timer refresh");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.ACACIA_LEAVES, now);
        check(entry(leaves).current() == 1, "Inflected block target resolved to registry item");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.USE, Items.GOLDEN_PICKAXE, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.GOLDEN_PICKAXE, now);
        check(entry(tool).current() == 0, "Using a tool does not count as breaking it");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.BREAK_ITEM, Items.GOLDEN_PICKAXE, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.BREAK_ITEM, Items.FLINT_AND_STEEL, now);
        check(entry(tool).current() == 1 && entry(lighter).current() == 1, "Equipment break and Funtime lighter alias counted");
        FuntimeQuestProgress.recordUse(Items.BONE_MEAL, InteractionResult.PASS, now);
        FuntimeQuestProgress.recordUse(Items.BONE_MEAL, InteractionResult.FAIL, now);
        FuntimeQuestProgress.recordUse(Items.BONE_MEAL, InteractionResult.SUCCESS.withoutItem(), now);
        check(entry(use).current() == 0, "Failed/pass/block-only interactions do not count as item use");
        FuntimeQuestProgress.recordUse(Items.BONE_MEAL, InteractionResult.SUCCESS, now);
        check(entry(use).current() == 1, "Successful bone meal use counted");
        UUID mobId = UUID.randomUUID();
        FuntimeQuestProgress.killed(mobId, EntityType.CREEPER, now);
        FuntimeQuestProgress.killed(mobId, EntityType.CREEPER, now);
        FuntimeQuestProgress.killed(UUID.randomUUID(), EntityType.SKELETON, now);
        check(entry(hard).current() == 6, "One death counts once and ignores other mob types");
        FuntimeQuestProgress.recordUse(Items.FURNACE, InteractionResult.SUCCESS, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.FURNACE, now);
        check(entry(delivery).current() == 0 && !entry(delivery).estimated(), "Delivery is updated only by server response, not item use/mining");
        var rebased = new FuntimeQuests.Quest(easy.name(), 321, 500, 225, 0, 13140, false);
        FuntimeQuests.observe(rebased, now);
        check(entry(easy).current() == 321 && !entry(easy).estimated(), "Server snapshot replaces local estimate without double counting");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        check(entry(easy).current() == 322, "Counting continues from the new authoritative baseline");
        FuntimeQuests.observe(easy, now);
        check(entry(easy).current() == 319 && !entry(easy).estimated(), "Server can correct rejected or uncounted actions downward");
        var almostDone = new FuntimeQuests.Quest(easy.name(), 499, 500, 225, 0, 13140, false);
        FuntimeQuests.observe(almostDone, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        check(entry(easy).current() == 500 && entry(easy).estimated(), "Estimate is capped and waits for server confirmation of completion");
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень 500 раз\nПроверь задания: /bp", now);
        check(FuntimeQuests.quests().stream().noneMatch(observed -> observed.quest().key().equals(easy.key())), "Server completion removes estimated finished task");
        var expired = new FuntimeQuests.Quest("Сломать Камень 20 раз", 0, 20, 225, 0, 1, false);
        FuntimeQuests.observe(expired, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now + 2_000_000_000L);
        check(entry(expired).current() == 0, "Expired quest cannot receive local increments");
        FuntimeQuests.reset();
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        check(FuntimeQuests.count() == 0, "Disconnect reset discards bindings and local progress");
    }

    private static void worldTransitionChecks(FuntimeQuests.Quest easy, FuntimeQuests.Quest hard, long now) {
        FuntimeQuests.reset();
        FuntimeQuestProgress.rebuildNames(Map.of(Items.STONE.getDescriptionId(), "Камень", EntityType.CREEPER.getDescriptionId(), "Крипер"));
        // Connection objects are identity tokens here; no channel is opened and no game client is started.
        Connection session = new Connection(PacketFlow.CLIENTBOUND);
        FuntimeQuests.updateConnection(session, "play.funtime.su");
        Object firstWorld = new Object(), secondWorld = new Object();
        FuntimeQuests.changeWorld(firstWorld);
        Inventory inventory = new Inventory(null, new EntityEquipment());
        ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x3, 1, inventory, new SimpleContainer(27), 3);
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, Component.literal(easy.name()));
        paper.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Прогресс: 319/500"),
                Component.literal("Опыт пропуска +225"), Component.literal("До обновления: 3ч 39м"))));
        menu.slots.get(0).set(paper);
        FuntimeQuests.readMenu(menu, now);
        FuntimeQuests.observe(hard, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        UUID deadMob = UUID.randomUUID();
        FuntimeQuestProgress.killed(deadMob, EntityType.CREEPER, now);
        var completed = new FuntimeQuests.Quest("Сломать Камень 10 раз", 9, 10, 425, 1, 3600, false);
        FuntimeQuests.observe(completed, now);
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень 10 раз\nПроверь задания: /bp", now);
        var before = FuntimeQuests.quests();
        long revision = FuntimeQuests.revision();
        FuntimeQuests.changeWorld(firstWorld);
        check(FuntimeQuests.revision() == revision, "Staying in the same world does not invalidate the HUD each tick");
        FuntimeQuests.receiveGameMessage("[Боевой Пропуск] Задание выполнено!\nСломать Камень 500 раз", now);
        FuntimeQuests.changeWorld(null);
        check(FuntimeQuests.quests().equals(before) && FuntimeQuests.lastRead() == now,
                "Loading gap preserves quest snapshots, local deltas, refresh deadlines and last read time");
        FuntimeQuests.updateConnection(session, "play.funtime.su");
        FuntimeQuests.changeWorld(secondWorld);
        check(FuntimeQuests.quests().equals(before) && FuntimeQuests.revision() > revision,
                "New world/respawn on the same connection restores HUD data without reopening quests");
        FuntimeQuests.receiveGameMessage("Проверь задания: /bp", now);
        check(FuntimeQuests.quests().equals(before), "Partial completion messages cannot cross world boundaries");
        FuntimeQuests.observe(completed, now);
        check(FuntimeQuests.quests().equals(before), "Completed quests stay hidden after world transition");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.MINE, Items.STONE, now);
        FuntimeQuestProgress.killed(deadMob, EntityType.CREEPER, now);
        check(entry(easy).current() == 321 && entry(hard).current() == 7,
                "Action bindings and progress continue in the new world while old death IDs are cleared");
        FuntimeQuests.readMenu(menu, now + 1);
        check(entry(easy).current() == 319 && !entry(easy).estimated() && FuntimeQuests.lastRead() == now + 1,
                "World transition invalidates menu references so the next real snapshot can resynchronize");
        FuntimeQuests.changeWorld(firstWorld);
        check(entry(easy).current() == 319 && entry(hard).current() == 7, "Returning to a previous world keeps progress");
        FuntimeQuests.updateConnection(new Connection(PacketFlow.CLIENTBOUND), "play.funtime.su");
        check(FuntimeQuests.count() == 0 && FuntimeQuests.lastRead() == 0,
                "A separate connection, even to the same address, cannot inherit another session's quests");
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.updateConnection(session, "other.funtime.su");
        check(FuntimeQuests.count() == 0, "Changing the Funtime address clears session-specific quests");
        FuntimeQuests.observe(easy, now);
        FuntimeQuests.updateConnection(session, "example.org");
        check(FuntimeQuests.count() == 0, "Leaving Funtime does not carry quests to another server");
        FuntimeQuests.reset();
    }

    private static FuntimeQuests.Observed entry(FuntimeQuests.Quest quest) {
        return FuntimeQuests.quests().stream().filter(observed -> observed.quest().key().equals(quest.key())).findFirst().orElseThrow();
    }

    private static void fishingChecks(long now) {
        FuntimeQuests.reset();
        FuntimeQuestProgress.rebuildNames(Map.of(Items.SALMON.getDescriptionId(), "Сырой лосось", Items.COD.getDescriptionId(), "Сырая треска"));
        var salmon = new FuntimeQuests.Quest("Поймать Лосося 3 раз", 0, 3, 225, 0, 3600, false);
        var cod = new FuntimeQuests.Quest("Выловить Треску 10 раз", 0, 10, 425, 1, 3600, false);
        FuntimeQuests.observe(salmon, now);
        FuntimeQuests.observe(cod, now);
        Vec3 origin = new Vec3(10, 63, 12);
        UUID item = UUID.randomUUID(), hook = UUID.randomUUID();
        FuntimeQuestProgress.fishingItemSpawned(item, origin, now);
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.SALMON, now);
        FuntimeQuestProgress.recordUse(Items.FISHING_ROD, InteractionResult.SUCCESS, now);
        check(entry(salmon).current() == 0, "Ordinary fish pickup and rod swings are not catches");
        FuntimeQuestProgress.beginFishing(hook, origin, now);
        FuntimeQuestProgress.fishingItemSpawned(item, origin.add(10, 0, 0), now);
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.SALMON, now);
        check(entry(salmon).current() == 0, "A remote item cannot be attributed to the local bobber");
        FuntimeQuestProgress.fishingItemSpawned(item, origin.add(.25, 0, .25), now);
        FuntimeQuestProgress.fishingItemPickedUp(UUID.randomUUID(), Items.SALMON, now);
        check(entry(salmon).current() == 0, "Pre-existing/unrelated item IDs are ignored");
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.SALMON, now);
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.SALMON, now);
        check(entry(salmon).current() == 1 && entry(cod).current() == 0, "Screenshot salmon target resolves and one catch counts once");
        FuntimeQuestProgress.beginFishing(UUID.randomUUID(), origin, now);
        FuntimeQuestProgress.fishingItemSpawned(item, origin, now);
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.COD, now);
        check(entry(salmon).current() == 1 && entry(cod).current() == 1, "Catch type and inflected raw-fish aliases are distinguished");
        FuntimeQuestProgress.beginFishing(hook, origin, now);
        FuntimeQuestProgress.fishingItemSpawned(item, origin, now);
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.SALMON, now + 6_000_000_000L);
        check(entry(salmon).current() == 1, "Expired reel window cannot claim a later pickup");
        FuntimeQuestProgress.beginFishing(hook, origin, now);
        FuntimeQuestProgress.fishingItemSpawned(item, origin, now);
        FuntimeQuests.changeWorld(new Object());
        FuntimeQuestProgress.fishingItemPickedUp(item, Items.SALMON, now);
        check(entry(salmon).current() == 1, "World changes discard fishing references while preserving existing progress");
        FuntimeQuests.reset();
    }

    private static void learningChecks(long now) {
        FuntimeQuests.reset();
        var unknown = new FuntimeQuests.Quest("Поймать Серебряную рыбу 20 раз", 0, 20, 225, 0, 3600, false);
        FuntimeQuests.observe(unknown, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        FuntimeQuests.observe(progress(unknown, 1), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 1, "A single correlated BP change is insufficient to learn an alias");
        FuntimeQuests.observe(progress(unknown, 2), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 3 && entry(unknown).estimated(), "Two independent verified intervals enable an unknown target alias");
        FuntimeQuests.observe(progress(unknown, 3), now);
        check(entry(unknown).current() == 3 && !entry(unknown).estimated(), "BP remains authoritative after learning; no double increment");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.COD, now);
        check(entry(unknown).current() == 3, "Learned rule only responds to its matched item type");
        FuntimeQuests.changeWorld(new Object());
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 4, "Learned rules survive world transitions in the same session");
        FuntimeQuests.observe(progress(unknown, 3), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 3, "A contradictory BP snapshot revokes the learned alias and corrects the HUD");

        FuntimeQuests.reset();
        FuntimeQuests.observe(unknown, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.COD, now);
        FuntimeQuests.observe(progress(unknown, 1), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.COD, now);
        FuntimeQuests.observe(progress(unknown, 2), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 2, "Ambiguous action histories never choose one candidate by guessing");

        FuntimeQuests.reset();
        FuntimeQuests.observe(unknown, now);
        for (int progress = 1; progress <= 2; progress++) {
            FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
            FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
            FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.COD, now);
            FuntimeQuests.observe(progress(unknown, progress), now);
        }
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.COD, now);
        check(entry(unknown).current() == 2, "Mixed targets remain ambiguous even when only one count equals BP delta");

        FuntimeQuests.reset();
        var otherDifficulty = new FuntimeQuests.Quest(unknown.name(), 0, 20, 950, 2, 3600, false);
        FuntimeQuests.observe(unknown, now);
        FuntimeQuests.observe(otherDifficulty, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        FuntimeQuests.observe(progress(unknown, 1), now);
        FuntimeQuests.observe(progress(otherDifficulty, 1), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 1 && entry(otherDifficulty).current() == 1,
                "Same action reported by two difficulties is not two independent confirmations");
        FuntimeQuests.observe(progress(unknown, 2), now);
        FuntimeQuests.observe(progress(otherDifficulty, 2), now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 3 && entry(otherDifficulty).current() == 3, "Confirmed subject rule applies across difficulties");
        FuntimeQuests.reset();
        FuntimeQuests.observe(unknown, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.FISH, Items.SALMON, now);
        check(entry(unknown).current() == 0, "Learned rules cannot leak into a new server session");
        FuntimeQuests.reset();
    }

    private static FuntimeQuests.Quest progress(FuntimeQuests.Quest quest, int current) {
        return new FuntimeQuests.Quest(quest.name(), current, quest.total(), quest.reward(), quest.difficulty(), quest.refreshSeconds(), false);
    }

    private static void screenshotActionChecks(long now) {
        FuntimeQuests.reset();
        // Use the shipped 1.21.11 name data, with the headless Minecraft default language left English.
        var names = FuntimeQuestProgress.baseNames();
        check(names.get(Items.COBBLESTONE.getDescriptionId()).equals("Булыжник")
                && names.get(Items.GRASS_BLOCK.getDescriptionId()).equals("Дёрн"), "Real vanilla Russian names available independently of client locale");
        FuntimeQuestProgress.rebuildNames(names);
        var cobble = new FuntimeQuests.Quest("Сломать Булыжник 250 раз", 0, 250, 225, 0, 3600, false);
        var grass = new FuntimeQuests.Quest("Сломать Блок травы 160 раз", 0, 160, 225, 0, 3600, false);
        var logs = new FuntimeQuests.Quest("Сдать Бревно тёмного дуба 256 раз", 0, 256, 425, 1, 3600, false);
        var ingot = new FuntimeQuests.Quest("Скрафтить Незеритовый слиток 1 раз", 0, 1, 425, 1, 3600, false);
        var otherLogs = new FuntimeQuests.Quest("Сдать Бревно тёмного дуба 512 раз", 0, 512, 950, 2, 3600, false);
        for (var quest : List.of(cobble, grass, logs, ingot, otherLogs)) FuntimeQuests.observe(quest, now);
        FuntimeQuestProgress.mined(Blocks.COBBLESTONE.defaultBlockState(), now);
        FuntimeQuestProgress.mined(Blocks.GRASS_BLOCK.defaultBlockState(), now);
        FuntimeQuestProgress.mined(Blocks.DIRT.defaultBlockState(), now);
        check(entry(cobble).current() == 1 && entry(grass).current() == 1,
                "Both screenshot mining tasks resolve; grass counts the broken block, not its dirt drop");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.USE, Items.NETHERITE_INGOT, now);
        check(entry(ingot).current() == 0, "Using or obtaining an ingot does not count as crafting");
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.CRAFT, Items.NETHERITE_INGOT, 1, now);
        check(entry(ingot).current() == 1, "Screenshot netherite crafting task updates immediately");
        var planks = new FuntimeQuests.Quest("Скрафтить Дубовые доски 128 раз", 0, 128, 225, 0, 3600, false);
        FuntimeQuests.observe(planks, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.CRAFT, Items.OAK_PLANKS, 4, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.CRAFT, Items.OAK_PLANKS, 64, now);
        FuntimeQuestProgress.record(FuntimeQuestProgress.Action.CRAFT, Items.OAK_PLANKS, 0, now);
        check(entry(planks).current() == 68, "Normal and Shift crafting count output quantity once; empty takes ignored");

        Inventory inventory = new Inventory(null, new EntityEquipment());
        ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x3, 1, inventory, new SimpleContainer(27), 3);
        FuntimeQuestProgress.confirmDelivery(0, now);
        check(entry(logs).current() == 0, "Inventory losses without a quest-button click never count as delivery");
        FuntimeQuestProgress.beginDelivery(logs, menu, Items.DARK_OAK_LOG, 128, now);
        FuntimeQuestProgress.confirmDelivery(128, now);
        check(entry(logs).current() == 0, "Unchanged inventory or moving stacks within it does not count as donation");
        FuntimeQuestProgress.confirmDelivery(127, now);
        FuntimeQuestProgress.confirmDelivery(127, now);
        check(entry(logs).current() == 1, "One delivered log counts once despite repeated acknowledgements");
        FuntimeQuestProgress.confirmDelivery(64, now);
        check(entry(logs).current() == 64 && entry(otherLogs).current() == 0,
                "Stack donation accumulates partial acknowledgements and updates only the clicked task");
        FuntimeQuests.observe(progress(logs, 64), now);
        FuntimeQuestProgress.confirmDelivery(64, now);
        check(entry(logs).current() == 64 && !entry(logs).estimated(), "Lore resynchronization does not duplicate delivery");
        FuntimeQuestProgress.beginDelivery(logs, menu, Items.DARK_OAK_LOG, 64, now);
        FuntimeQuests.observe(progress(logs, 128), now);
        FuntimeQuestProgress.confirmDelivery(0, now);
        check(entry(logs).current() == 128 && !entry(logs).estimated(), "Lore arriving before inventory acknowledgement is not counted twice");
        FuntimeQuestProgress.beginDelivery(logs, menu, Items.DARK_OAK_LOG, 64, now);
        FuntimeQuestProgress.confirmDelivery(0, now + 6_000_000_000L);
        check(entry(logs).current() == 128, "An expired delivery request cannot claim a later inventory change");
        FuntimeQuestProgress.beginDelivery(logs, menu, Items.DARK_OAK_LOG, 64, now);
        FuntimeQuests.changeWorld(new Object());
        FuntimeQuestProgress.confirmDelivery(0, now);
        check(entry(logs).current() == 128, "World changes discard pending donations but preserve saved quest progress");
        FuntimeQuests.reset();
    }

    private static ItemStack stack(String name, int progress) {
        ItemStack item = new ItemStack(Items.PAPER);
        item.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        item.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Прогресс: " + progress + "/2"),
                Component.literal("Опыт пропуска +225"), Component.literal("До обновления: 1ч"))));
        return item;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
