package dev.stormvisuals.render;

import dev.stormvisuals.gui.ClickGuiScreen;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.resources.Identifier;

public final class HotkeysHud {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/keybinds.png");
    private static final HudListPanel PANEL = new HudListPanel("Hotkeys", "Нет активных биндов", ICON, HudDragging.HOTKEYS);
    private HotkeysHud() {}

    public static void initialize() {
        PANEL.initialize();
        HudRenderSystem.addLayer((graphics, delta) -> PANEL.render(graphics, .66f));
        ClientTickEvents.END_CLIENT_TICK.register(client -> PANEL.rows(client.player == null || client.level == null ? List.of() :
                ClickGuiScreen.activeHudBindings().stream().map(binding -> new HudListPanel.Row(
                        binding.getName(), binding.getTranslatedKeyMessage().getString(), false)).toList()));
    }
}
