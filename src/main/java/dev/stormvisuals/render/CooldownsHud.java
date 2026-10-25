package dev.stormvisuals.render;

import dev.stormvisuals.mixin.CooldownInstanceAccessor;
import dev.stormvisuals.mixin.ItemCooldownsAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Only displays cooldowns that Minecraft has actually received or started locally. */
public final class CooldownsHud {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/tps.png");
    private static final HudListPanel PANEL = new HudListPanel("Cooldowns", "Нет перезарядок", ICON, HudDragging.COOLDOWNS);
    private CooldownsHud() {}

    public static void initialize() {
        PANEL.initialize();
        HudRenderSystem.addLayer((graphics, delta) -> PANEL.render(graphics, .48f));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            List<HudListPanel.Row> rows = new ArrayList<>();
            if (client.player != null && client.level != null) {
                var cooldowns = client.player.getCooldowns();
                var access = (ItemCooldownsAccessor) cooldowns;
                for (var entry : access.stormvisuals$cooldowns().entrySet()) {
                    int ticks = ((CooldownInstanceAccessor) entry.getValue()).stormvisuals$endTime() - access.stormvisuals$tickCount();
                    if (ticks <= 0) continue;
                    String name = entry.getKey().toString();
                    ItemStack item = ItemStack.EMPTY;
                    var inventory = client.player.getInventory();
                    boolean found = false;
                    for (int i = 0; i < inventory.getContainerSize(); i++) {
                        var stack = inventory.getItem(i);
                        if (!stack.isEmpty() && cooldowns.getCooldownGroup(stack).equals(entry.getKey())) {
                            name = stack.getHoverName().getString();
                            item = stack.copyWithCount(1);
                            found = true;
                            break;
                        }
                    }
                    if (!found && BuiltInRegistries.ITEM.containsKey(entry.getKey())) {
                        item = new ItemStack(BuiltInRegistries.ITEM.getValue(entry.getKey()));
                        name = item.getHoverName().getString();
                    }
                    rows.add(new HudListPanel.Row(entry.getKey().toString(), name,
                            String.format(Locale.ROOT, "%.1fs", ticks / 20f), false, null, item));
                }
            }
            rows.sort(Comparator.comparing(HudListPanel.Row::name));
            PANEL.rows(rows);
        });
    }
}
