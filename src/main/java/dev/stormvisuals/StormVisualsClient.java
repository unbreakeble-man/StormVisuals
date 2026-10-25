package dev.stormvisuals;

import dev.stormvisuals.gui.ClickGuiScreen;
import dev.stormvisuals.render.HudRenderSystem;
import dev.stormvisuals.dev.LiveReload;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class StormVisualsClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("StormVisuals");

    @Override
    public void onInitializeClient() {
        initializeModules();
        LiveReload.initialize();
        LOGGER.info("StormVisuals initialized");
    }

    /** Keep new initializers here; HotSwap reruns this method, skipping existing registrations. */
    public static void initializeModules() {
        LiveReload.initializeOnce(dev.stormvisuals.render.HudDragging.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.Fullbright.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.CustomCrosshair.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.CustomHand.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.FullbrightLightUniform.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.TargetESP.class);
        LiveReload.initializeOnce(ClickGuiScreen.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.WatermarkHud.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.PotionsHud.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.CooldownsHud.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.HotkeysHud.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.TntTimer.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.TotemBar.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.ChestSorter.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.Auction.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.AuctionTimerHud.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.ItemScroller.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.CommandSafe.class);
        LiveReload.initializeOnce(dev.stormvisuals.module.FuntimeQuests.class);
        LiveReload.initializeOnce(dev.stormvisuals.render.QuestsHud.class);
        LiveReload.initializeOnce(dev.stormvisuals.gui.AltManagerScreen.class);
        LiveReload.initializeOnce(HudRenderSystem.class);
        LiveReload.initializeOnce(dev.stormvisuals.config.ConfigManager.class);
        LiveReload.initializeOnce(dev.stormvisuals.config.OfflineAccounts.class);
    }
}
