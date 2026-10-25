package dev.stormvisuals.module;

import dev.stormvisuals.config.ConfigState;
import dev.stormvisuals.render.HudRenderSystem;
import dev.stormvisuals.render.MtsdfFont;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class TotemBar {
    private static boolean enabled, hideZero;
    private static int count;
    private static String text = "0";
    private static Object world;
    private static final ItemStack ICON = new ItemStack(Items.TOTEM_OF_UNDYING);
    private TotemBar() {}
    public static void setEnabled(boolean on) { enabled = on; }
    public static boolean hideZero() { return hideZero; }
    public static void hideZero(boolean on) { hideZero = on; }
    public static void capture(ConfigState.ModuleState s) { s.hideZero = hideZero; }
    public static void restore(ConfigState.ModuleState s) { hideZero = s.hideZero; }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            world = client.level;
            int total = 0;
            if (enabled && client.player != null) {
                var inventory = client.player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack.is(Items.TOTEM_OF_UNDYING)) total += stack.getCount();
                }
            }
            if (total != count) { count = total; text = Integer.toString(total); }
        });
        HudRenderSystem.addLayer((g, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (!enabled || client.player == null || world != client.level || client.player.isSpectator() || hideZero && count == 0) return;
            float width = 15 + MtsdfFont.width(text, 9);
            float x = (g.guiWidth() - width) / 2f;
            float y = Math.max(4, g.guiHeight() - 57);
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(.75f, .75f);
            g.renderItem(ICON, 0, 0);
            g.pose().popMatrix();
            MtsdfFont.draw(g, text, x + 15, y + 1.5f, 9, count == 0 ? 0xFFAAAAAA : 0xFFFFFFFF);
        });
    }
}
