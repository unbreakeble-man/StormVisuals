package dev.stormvisuals.render;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.Gui;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/** Active effects known to the client, refreshed on ticks. */
public final class PotionsHud {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/leaf.png");
    private static final HudListPanel PANEL = new HudListPanel("Potions", "Нет эффектов", ICON, HudDragging.POTIONS);

    private PotionsHud() {}
    public static List<Identifier> icons() { return List.of(ICON); }

    public static void initialize() {
        PANEL.initialize();
        HudRenderSystem.addLayer((graphics, delta) -> PANEL.render(graphics, .30f));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            List<HudListPanel.Row> rows = new ArrayList<>();
            if (client.player != null && client.level != null) {
                for (var effect : client.player.getActiveEffects()) {
                    if (!effect.showIcon()) continue;
                    String name = Component.translatable(effect.getDescriptionId()).getString();
                    int amplifier = effect.getAmplifier();
                    if (amplifier > 0) name += " " + (amplifier >= 10 ? amplifier + 1 :
                            Component.translatable("enchantment.level." + (amplifier + 1)).getString());
                    int seconds = Math.max(0, (effect.getDuration() + 19) / 20);
                    String time = effect.isInfiniteDuration() ? "--:--" : seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
                    rows.add(new HudListPanel.Row(BuiltInRegistries.MOB_EFFECT.getKey(effect.getEffect().value()).toString(),
                            name, time, !effect.isInfiniteDuration() && seconds <= 20,
                            Gui.getMobEffectSprite(effect.getEffect()), ItemStack.EMPTY,
                            effect.isInfiniteDuration() ? Integer.MAX_VALUE : effect.getDuration()));
                }
            }
            PANEL.rows(rows);
        });
    }
}
