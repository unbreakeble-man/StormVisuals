package dev.stormvisuals.render;

import dev.stormvisuals.module.Auction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** A plain countdown label; its only interactive behavior is chat dragging. */
public final class AuctionTimerHud {
    private static long seconds = -1;
    private static String text = "";
    private static int width;

    private AuctionTimerHud() {}

    public static void initialize() {
        HudRenderSystem.addLayer((graphics, delta) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        var position = HudDragging.AUCTION_TIMER;
        if (!Auction.timerVisible()) { position.hide(); return; }
        var font = Minecraft.getInstance().font;
        long remaining = Auction.secondsUntilRelist();
        if (remaining != seconds) {
            seconds = remaining;
            text = "До обновления аукциона: " + remaining + " сек.";
            width = font.width(text);
        }
        position.layout(8, graphics.guiHeight() * .65f, width, font.lineHeight, graphics.guiWidth(), graphics.guiHeight());
        graphics.drawString(font, text, (int) position.x(), (int) position.y(), 0xFFFFFFFF, false);
    }
}
