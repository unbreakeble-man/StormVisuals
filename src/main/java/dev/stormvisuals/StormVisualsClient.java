package dev.stormvisuals;

import dev.stormvisuals.editor.ChatCanvasEditor;
import dev.stormvisuals.render.HudRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StormVisualsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("StormVisuals");

    @Override
    public void onInitializeClient() {
        ChatCanvasEditor.initialize();
        HudRenderSystem.initialize();
        LOGGER.info("StormVisuals initialized");
    }
}
