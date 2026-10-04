package dev.stormvisuals;

import dev.stormvisuals.gui.ClickGuiScreen;
import dev.stormvisuals.render.HudRenderSystem;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StormVisualsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("StormVisuals");

    @Override
    public void onInitializeClient() {
        dev.stormvisuals.render.HudDragging.initialize();
        dev.stormvisuals.module.Fullbright.initialize();
        dev.stormvisuals.module.CustomCrosshair.initialize();
        dev.stormvisuals.module.CustomHand.initialize();
        dev.stormvisuals.render.FullbrightLightUniform.initialize();
        dev.stormvisuals.module.TargetESP.initialize();
        ClickGuiScreen.initialize();
        dev.stormvisuals.render.WatermarkHud.initialize();
        dev.stormvisuals.render.PotionsHud.initialize();
        HudRenderSystem.initialize();
        dev.stormvisuals.config.ConfigManager.initialize();
        LOGGER.info("StormVisuals initialized");
        
        
    }
}
