package dev.stormvisuals.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** Client HUD rendering entry point for StormVisuals features. */
public final class HudRenderSystem {
    private static final List<HudElement> LAYERS = new ArrayList<>();
    private static boolean initialized;

    private HudRenderSystem() {
    }

    /** Register at startup, or during a development reload on the client thread. */
    public static void addLayer(HudElement layer) {
        if (initialized && !dev.stormvisuals.dev.LiveReload.isApplying()) {
            throw new IllegalStateException("HUD layers must be registered before initialization");
        }
        LAYERS.add(Objects.requireNonNull(layer, "layer"));
    }

    public static void initialize() {
        if (initialized) {
            throw new IllegalStateException("HUD renderer is already initialized");
        }
        initialized = true;

        // This position follows Minecraft's hide-GUI condition and stays below chat.
        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("stormvisuals", "hud"),
                (graphics, tickCounter) -> {
                    if (Minecraft.getInstance().player == null) {
                        return;
                    }
                    for (HudElement layer : LAYERS) {
                        layer.render(graphics, tickCounter);
                    }
                    HudDragging.renderGuides(graphics);
                }
        );
    }
}
