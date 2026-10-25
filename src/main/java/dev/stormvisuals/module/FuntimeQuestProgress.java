package dev.stormvisuals.module;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import dev.stormvisuals.mixin.FuntimeFishingHookAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleResourceReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.block.state.BlockState;

/** Tracks actual client actions, not clicks or arbitrary inventory changes. */
public final class FuntimeQuestProgress {
    enum Action { MINE, BREAK_ITEM, USE, KILL, FISH, CRAFT, DELIVER }
    private record Target(Action action, Object type) {}
    private record Checkpoint(long sequence, Map<Target, Long> totals) {}
    private record Trial(Target target, long sequence, int confirmations) {}
    private record FishingAttempt(UUID hook, Vec3 origin, long expiresAt) {}
    private record Delivery(String key, Item item, ChestMenu menu, int inventory, int progress, int total, int applied, long expiresAt) {}
    private static final Map<String, Item> ITEM_NAMES = new HashMap<>();
    private static final Map<String, EntityType<?>> ENTITY_NAMES = new HashMap<>();
    private static final Map<String, Target> TARGETS = new HashMap<>();
    private static final Set<UUID> DEATHS = new HashSet<>();
    private static final Map<Target, Long> ACTION_TOTALS = new HashMap<>();
    private static final Map<String, Checkpoint> CHECKPOINTS = new HashMap<>();
    private static final Map<String, Trial> TRIALS = new HashMap<>();
    private static final Map<String, Target> LEARNED = new HashMap<>();
    private static final Set<UUID> FISHING_ITEMS = new HashSet<>();
    private static FishingAttempt fishing;
    private static Delivery delivery;
    private static long sequence;

    private FuntimeQuestProgress() {}

    static void initialize() {
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            if (tracking(player)) mined(state, System.nanoTime());
        });
        ClientEntityEvents.ENTITY_UNLOAD.register((entity, level) -> DEATHS.remove(entity.getUUID()));
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide() && tracking(player) && player.getItemInHand(hand).is(Items.FISHING_ROD)) {
                var hook = player.fishing;
                if (hook != null && hook.getPlayerOwner() == player && hook.getHookedIn() == null
                        && ((FuntimeFishingHookAccessor) hook).stormvisuals$isBiting())
                    beginFishing(hook.getUUID(), hook.position(), System.nanoTime());
            }
            return InteractionResult.PASS;
        });
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof ItemEntity && FuntimeQuests.active(Minecraft.getInstance()))
                fishingItemSpawned(entity.getUUID(), entity.position(), System.nanoTime());
        });
        // Load Russian names even when the player's interface uses a different language.
        Identifier key = Identifier.fromNamespaceAndPath("stormvisuals", "quest_targets");
        var loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        loader.registerReloader(key, new SimpleResourceReloader<Map<String, String>>() {
            @Override
            protected Map<String, String> prepare(SharedState state) {
                Map<String, String> names = baseNames();
                for (var resource : state.resourceManager().getResourceStack(Identifier.withDefaultNamespace("lang/ru_ru.json"))) {
                    try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                        JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                                .forEach(entry -> names.putIfAbsent(entry.getKey(), entry.getValue().getAsString()));
                    } catch (IOException | RuntimeException error) {
                        org.slf4j.LoggerFactory.getLogger("StormVisuals").warn("Could not read Russian quest target names", error);
                    }
                }
                return names;
            }
            @Override
            protected void apply(Map<String, String> names, SharedState state) { rebuildNames(names); }
        });
        loader.addReloaderOrdering(ResourceReloaderKeys.Client.LANGUAGES, key);
    }

    /** Canonical Minecraft 1.21.11 names; recognition must not depend on the user's selected language. */
    static Map<String, String> baseNames() {
        Map<String, String> names = new HashMap<>();
        try (var input = FuntimeQuestProgress.class.getResourceAsStream("/stormvisuals/quest_names_ru.json")) {
            if (input == null) throw new IOException("Missing quest target names");
            try (var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                        .forEach(entry -> names.put(entry.getKey(), entry.getValue().getAsString()));
            }
        } catch (IOException error) { throw new IllegalStateException("Could not load quest target names", error); }
        return names;
    }

    static void rebuildNames(Map<String, String> translations) {
        ITEM_NAMES.clear();
        ENTITY_NAMES.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            String name = translations.getOrDefault(item.getDescriptionId(), item.getName().getString());
            ITEM_NAMES.putIfAbsent(normalize(name), item);
            ITEM_NAMES.putIfAbsent(accusative(name, false), item);
            ITEM_NAMES.putIfAbsent(normalize(item.getName().getString()), item);
            if (item == Items.SALMON || item == Items.COD || item == Items.TROPICAL_FISH || item == Items.PUFFERFISH) {
                String fishName = name.replaceFirst("(?iu)^сыр(?:ой|ая|ое|ые)\\s+", "");
                ITEM_NAMES.putIfAbsent(normalize(fishName), item);
                ITEM_NAMES.putIfAbsent(accusative(fishName, true), item);
            }
        }
        // Funtime uses this synonym in its tool-breaking tasks.
        ITEM_NAMES.put("зажигалка", Items.FLINT_AND_STEEL);
        ITEM_NAMES.put("зажигалку", Items.FLINT_AND_STEEL);
        ITEM_NAMES.put("блок травы", Items.GRASS_BLOCK);
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String name = translations.getOrDefault(type.getDescriptionId(), type.getDescription().getString());
            ENTITY_NAMES.putIfAbsent(normalize(name), type);
            ENTITY_NAMES.putIfAbsent(accusative(name, true), type);
        }
        TARGETS.clear();
        for (var entry : FuntimeQuests.quests()) track(entry.quest());
    }

    static void track(FuntimeQuests.Quest quest) {
        String name = taskName(quest.name());
        Target target = resolve(name);
        if (target == null) target = LEARNED.get(name);
        if (target == null) TARGETS.remove(quest.key());
        else TARGETS.put(quest.key(), target);
    }

    private static Target resolve(String name) {
        int separator = name.indexOf(' ');
        Target target = null;
        if (separator > 0) {
            String verb = name.substring(0, separator), subject = name.substring(separator + 1);
            if (verb.equals("убить")) {
                EntityType<?> type = ENTITY_NAMES.get(subject);
                if (type != null) target = new Target(Action.KILL, type);
            } else {
                Item item = ITEM_NAMES.get(subject);
                if (item != null && item != Items.AIR) {
                    if (verb.equals("использовать")) target = new Target(Action.USE, item);
                    else if (verb.equals("скрафтить")) target = new Target(Action.CRAFT, item);
                    else if (verb.equals("сдать")) target = new Target(Action.DELIVER, item);
                    else if (verb.equals("поймать") || verb.equals("выловить")) target = new Target(Action.FISH, item);
                    else if (verb.equals("сломать")) {
                        if (item instanceof BlockItem) target = new Target(Action.MINE, item);
                        else if (item.getDefaultInstance().isDamageableItem()) target = new Target(Action.BREAK_ITEM, item);
                    }
                }
            }
        }
        return target;
    }

    static void synchronize(FuntimeQuests.Quest quest, FuntimeQuests.Observed previous, long now) {
        String name = taskName(quest.name());
        String verb = name.split(" ", 2)[0];
        if (resolve(name) == null && supportsVerb(verb)) {
            Map<Target, Long> totals = new HashMap<>();
            ACTION_TOTALS.forEach((target, total) -> { if (matchesVerb(verb, target.action)) totals.put(target, total); });
            Checkpoint old = CHECKPOINTS.put(quest.key(), new Checkpoint(sequence, totals));
            if (previous != null && previous.expiresAt() > now && old != null) {
                int delta = quest.current() - previous.quest().current();
                Target learned = LEARNED.get(name);
                if (delta >= 0 && learned != null && difference(totals, old, learned) != delta) {
                    LEARNED.remove(name);
                    TRIALS.remove(name);
                }
                if (delta > 0 && !LEARNED.containsKey(name)) {
                    var candidates = totals.keySet().stream().filter(target -> difference(totals, old, target) > 0).toList();
                    if (candidates.size() == 1 && difference(totals, old, candidates.getFirst()) == delta) {
                        Target candidate = candidates.getFirst();
                        Trial trial = TRIALS.get(name);
                        if (trial == null || !trial.target.equals(candidate)) trial = new Trial(candidate, sequence, 1);
                        else if (old.sequence >= trial.sequence) trial = new Trial(candidate, sequence, trial.confirmations + 1);
                        TRIALS.put(name, trial);
                        if (trial.confirmations >= 2) LEARNED.put(name, candidate);
                    } else TRIALS.remove(name);
                } else if (delta < 0) { TRIALS.remove(name); LEARNED.remove(name); }
            }
        }
        track(quest);
    }

    private static long difference(Map<Target, Long> totals, Checkpoint old, Target target) {
        return totals.getOrDefault(target, 0L) - old.totals.getOrDefault(target, 0L);
    }

    private static boolean supportsVerb(String verb) {
        return switch (verb) { case "сломать", "использовать", "убить", "поймать", "выловить", "скрафтить", "сдать" -> true; default -> false; };
    }

    private static boolean matchesVerb(String verb, Action action) {
        return switch (verb) {
            case "сломать" -> action == Action.MINE || action == Action.BREAK_ITEM;
            case "использовать" -> action == Action.USE;
            case "убить" -> action == Action.KILL;
            case "поймать", "выловить" -> action == Action.FISH;
            case "скрафтить" -> action == Action.CRAFT;
            case "сдать" -> action == Action.DELIVER;
            default -> false;
        };
    }

    static void untrack(String key) { TARGETS.remove(key); CHECKPOINTS.remove(key); }
    static void worldChanged() { DEATHS.clear(); clearFishing(); delivery = null; }
    static void reset() {
        TARGETS.clear(); DEATHS.clear(); ACTION_TOTALS.clear(); CHECKPOINTS.clear(); TRIALS.clear(); LEARNED.clear();
        sequence = 0;
        clearFishing();
        delivery = null;
    }

    static void record(Action action, Object type, long now) {
        record(action, type, 1, now);
    }

    static void record(Action action, Object type, int amount, long now) {
        if (amount <= 0) return;
        addAction(action, type, amount);
        TARGETS.forEach((key, target) -> {
            if (target.action == action && target.type == type) FuntimeQuests.advance(key, amount, now);
        });
    }

    private static void addAction(Action action, Object type, int amount) {
        ACTION_TOTALS.merge(new Target(action, type), (long) amount, Long::sum);
        sequence++;
    }

    public static void crafted(Player player, Item item, int amount) {
        if (tracking(player)) record(Action.CRAFT, item, amount, System.nanoTime());
    }

    static void mined(BlockState state, long now) { record(Action.MINE, state.getBlock().asItem(), now); }

    public static void clickedContainer(Player player, int containerId, int slot, int button, ClickType clickType) {
        delivery = null; // A different manual action must not be mistaken for an earlier donation response.
        if (!tracking(player) || button != 0 || clickType != ClickType.PICKUP && clickType != ClickType.QUICK_MOVE
                || !(player.containerMenu instanceof ChestMenu menu) || menu.containerId != containerId
                || slot < 0 || slot >= menu.getRowCount() * 9 || !menu.getCarried().isEmpty()) return;
        var quest = FuntimeQuests.parse(menu.slots.get(slot).getItem());
        if (quest == null || quest.hidden()) return;
        Target target = resolve(taskName(quest.name()));
        if (target == null || target.action != Action.DELIVER || !(target.type instanceof Item item)) return;
        long now = System.nanoTime();
        if (FuntimeQuests.current(quest.key()) < 0) FuntimeQuests.observe(quest, now);
        beginDelivery(quest, menu, item, inventoryCount(player, item), now);
    }

    private static int inventoryCount(Player player, Item item) {
        int total = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            var stack = inventory.getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    static void beginDelivery(FuntimeQuests.Quest quest, ChestMenu menu, Item item, int inventory, long now) {
        delivery = null;
        int progress = FuntimeQuests.current(quest.key());
        if (inventory <= 0 || progress < 0) return;
        delivery = new Delivery(quest.key(), item, menu, inventory, progress, quest.total(), 0, now + 5_000_000_000L);
    }

    public static void inventoryUpdated() {
        if (delivery == null) return;
        Minecraft client = Minecraft.getInstance();
        if (!FuntimeQuests.active(client) || client.player.containerMenu != delivery.menu) { delivery = null; return; }
        confirmDelivery(inventoryCount(client.player, delivery.item), System.nanoTime());
    }

    /** Inventory is observed only after a server acknowledgement, following an exact quest-button click. */
    static void confirmDelivery(int remaining, long now) {
        if (delivery == null) return;
        Delivery pending = delivery;
        if (now >= pending.expiresAt) { delivery = null; return; }
        int consumed = Math.max(0, pending.inventory - remaining);
        if (consumed <= pending.applied) return;
        addAction(Action.DELIVER, pending.item, consumed - pending.applied);
        int desired = (int) Math.min(pending.total, (long) pending.progress + consumed);
        int current = FuntimeQuests.current(pending.key);
        // Only the clicked task receives the delivery; a lore update may already include these items.
        if (current >= 0 && desired > current) FuntimeQuests.advance(pending.key, desired - current, now);
        delivery = new Delivery(pending.key, pending.item, pending.menu, pending.inventory, pending.progress,
                pending.total, consumed, pending.expiresAt);
    }

    public static boolean tracking(LivingEntity actor) {
        Minecraft client = Minecraft.getInstance();
        return FuntimeQuests.active(client) && actor == client.player && !actor.isSpectator()
                && !client.player.hasInfiniteMaterials();
    }

    public static void usedItem(LivingEntity actor, Item item, InteractionResult result) {
        if (tracking(actor)) recordUse(item, result, System.nanoTime());
    }

    static void recordUse(Item item, InteractionResult result, long now) {
        if (result instanceof InteractionResult.Success success && success.wasItemInteraction()) record(Action.USE, item, now);
    }

    public static void entityEvent(LivingEntity entity, byte event) {
        Minecraft client = Minecraft.getInstance();
        if (!FuntimeQuests.active(client) || entity.level() != client.level) return;
        if (event == EntityEvent.DEATH && entity != client.player) {
            var damage = entity.getLastDamageSource();
            if (damage != null && damage.getEntity() == client.player && tracking(client.player))
                killed(entity.getUUID(), entity.getType(), System.nanoTime());
            return;
        }
        if (!tracking(entity)) return;
        EquipmentSlot slot = switch (event) {
            case EntityEvent.MAINHAND_BREAK -> EquipmentSlot.MAINHAND;
            case EntityEvent.OFFHAND_BREAK -> EquipmentSlot.OFFHAND;
            case EntityEvent.HEAD_BREAK -> EquipmentSlot.HEAD;
            case EntityEvent.CHEST_BREAK -> EquipmentSlot.CHEST;
            case EntityEvent.LEGS_BREAK -> EquipmentSlot.LEGS;
            case EntityEvent.FEET_BREAK -> EquipmentSlot.FEET;
            default -> null;
        };
        if (slot != null) record(Action.BREAK_ITEM, entity.getItemBySlot(slot).getItem(), System.nanoTime());
    }

    static void killed(UUID id, EntityType<?> type, long now) {
        if (DEATHS.add(id)) record(Action.KILL, type, now);
    }

    static void beginFishing(UUID hook, Vec3 origin, long now) {
        if (fishing != null && fishing.hook.equals(hook) && fishing.expiresAt > now) return;
        clearFishing();
        fishing = new FishingAttempt(hook, origin, now + 5_000_000_000L);
    }

    static void fishingItemSpawned(UUID id, Vec3 position, long now) {
        if (fishing == null) return;
        if (now >= fishing.expiresAt) { clearFishing(); return; }
        if (position.distanceToSqr(fishing.origin) <= 4) FISHING_ITEMS.add(id);
    }

    public static void pickedUpFishingItem(int entityId, int collectorId) {
        Minecraft client = Minecraft.getInstance();
        if (!FuntimeQuests.active(client) || !tracking(client.player) || collectorId != client.player.getId()) return;
        if (client.level.getEntity(entityId) instanceof ItemEntity item)
            fishingItemPickedUp(item.getUUID(), item.getItem().getItem(), System.nanoTime());
    }

    static void fishingItemPickedUp(UUID id, Item item, long now) {
        if (fishing == null) return;
        if (now >= fishing.expiresAt) { clearFishing(); return; }
        if (!FISHING_ITEMS.contains(id) || item == Items.AIR) return;
        clearFishing();
        record(Action.FISH, item, now);
    }

    private static void clearFishing() { fishing = null; FISHING_ITEMS.clear(); }

    private static String taskName(String text) {
        return normalize(text).replaceFirst("\\s+[0-9][0-9 ]*\\s+раз(?:а)?\\.?$", "");
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT).replace('ё', 'е').replaceAll("\\s+", " ");
    }

    /** Menu titles inflect the target: золотая кирка -> золотую кирку, крипер -> крипера. */
    static String accusative(String name, boolean animate) {
        String[] words = normalize(name).split(" ");
        for (int i = 0; i < words.length; i++) {
            String word = words[i];
            if (word.endsWith("ая")) words[i] = word.substring(0, word.length() - 2) + "ую";
            else if (word.endsWith("яя")) words[i] = word.substring(0, word.length() - 2) + "юю";
            else if (animate && (word.endsWith("ый") || word.endsWith("ой"))) words[i] = word.substring(0, word.length() - 2) + "ого";
            else if (animate && word.endsWith("ий")) words[i] = word.substring(0, word.length() - 2) + "его";
            else {
                if (word.endsWith("а")) words[i] = word.substring(0, word.length() - 1) + "у";
                else if (word.endsWith("я")) words[i] = word.substring(0, word.length() - 1) + "ю";
                else if (animate && (word.endsWith("ь") || word.endsWith("й"))) words[i] = word.substring(0, word.length() - 1) + "я";
                else if (animate && word.matches(".*[бвгджзклмнпрстфхцчшщ]$")) words[i] = word + "а";
                break; // Preserve genitive qualifiers such as "акации" and "тропического дерева".
            }
        }
        return String.join(" ", words);
    }
}
