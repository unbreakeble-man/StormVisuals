package dev.stormvisuals.render;

import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Top-centred HUD watermark. Server TPS is an estimate from time synchronization packets. */
public final class WatermarkHud {
    private static final Identifier LOGO = icon("watermark_logo");
    private static final Identifier FPS = icon("fps");
    private static final Identifier PING = icon("ping");
    private static final Identifier TPS = icon("tps");
    private static final BackdropBlurRenderer BLUR = new BackdropBlurRenderer();
    private static long packetTime;
    private static long gameTime;
    private static double estimatedTps = Double.NaN;
    private static long lastLabelUpdate;
    private static String fpsLabel = "--";
    private static String pingLabel = "--";
    private static String tpsLabel = "--";
    private static float fpsWidth;
    private static float pingWidth;
    private static float tpsWidth;

    private WatermarkHud() {}

    public static List<Identifier> icons() { return List.of(LOGO, FPS, PING, TPS); }

    public static void initialize() {
        HudRenderSystem.addLayer((graphics, delta) -> render(graphics));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset();
            BLUR.close();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BLUR.close());
    }

    private static void reset() {
        packetTime = 0;
        gameTime = 0;
        estimatedTps = Double.NaN;
        lastLabelUpdate = 0;
        fpsLabel = pingLabel = tpsLabel = "--";
    }

    /** Invoked on the client thread after vanilla has processed the time packet. */
    public static void serverTime(long ticks) {
        long now = System.nanoTime();
        if (packetTime != 0 && ticks > gameTime && now - packetTime < 5_000_000_000L) {
            double sample = Math.min(20, (ticks - gameTime) * 1_000_000_000.0 / (now - packetTime));
            estimatedTps = Double.isNaN(estimatedTps) ? sample : estimatedTps + (sample - estimatedTps) * 0.25;
        } else estimatedTps = Double.NaN;
        packetTime = now;
        gameTime = ticks;
    }

    public static void render(GuiGraphics graphics) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        long now = System.nanoTime();
        if (lastLabelUpdate == 0 || now - lastLabelUpdate >= 250_000_000L) {
            fpsLabel = Integer.toString(client.getFps());
            var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(client.player.getUUID());
            pingLabel = info == null ? "--" : Integer.toString(Math.max(0, info.getLatency()));
            tpsLabel = packetTime == 0 || now - packetTime > 5_000_000_000L || Double.isNaN(estimatedTps)
                    ? "--" : String.format(Locale.ROOT, "%.1f", estimatedTps);
            fpsWidth = MtsdfFont.width(fpsLabel, 13);
            pingWidth = MtsdfFont.width(pingLabel, 13);
            tpsWidth = MtsdfFont.width(tpsLabel, 13);
            lastLabelUpdate = now;
        }
        int width = (int) Math.ceil(47 + MtsdfFont.width("Storm", 15) + 13 + 3 * 54
                + MtsdfFont.width("fps", 13) + MtsdfFont.width("ms", 13) + MtsdfFont.width("tps", 13)
                + fpsWidth + pingWidth + tpsWidth);
        float pixels = client.getMainRenderTarget().width / (float) graphics.guiWidth();
        // Match the compact HTML preview instead of growing with Minecraft GUI scale.
        float scale = Math.min(HudDragging.WATERMARK.scale() / pixels, graphics.guiWidth() * 0.9F / width);
        float radius = HudDragging.WATERMARK.radius(13);
        float x = (graphics.guiWidth() - width * scale) / 2;
        HudDragging.WATERMARK.layout(x, 0, width * scale, 44 * scale, graphics.guiWidth(), graphics.guiHeight());
        x = HudDragging.WATERMARK.x();
        float y = HudDragging.WATERMARK.y();
        float topRadius = y <= 0 ? 0 : radius;
        BLUR.prepare(client.getMainRenderTarget(), x * pixels, y * pixels, width * scale * pixels, 44 * scale * pixels, now);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        for (int i = 8; i >= 1; i--) {
            float spread = i * 1.25F;
            rect(graphics, -spread, 3 - spread, width + spread * 2, 44 + spread * 2, topRadius, radius + spread, 0x03000000);
        }
        BLUR.draw(graphics, width, 44, radius, 1, y <= 0);
        rect(graphics, 0, 0, width, 44, topRadius, radius, 0xCC080A0C);
        PngIconRenderer.drawCircle(graphics, LOGO, 11, 8, 28, 0xFFFFFFFF);
        MtsdfFont.draw(graphics, "Storm", 47, 13, 15, 0xFFFFFFFF);
        float cursor = 47 + MtsdfFont.width("Storm", 15) + 13;
        for (int pass = 0; pass < 2; pass++) {
            boolean favorite = pass == 0;
            if (HudDragging.WATERMARK.favorite("fps") == favorite) cursor = metric(graphics, cursor, FPS, fpsLabel, fpsWidth, "fps");
            if (HudDragging.WATERMARK.favorite("ping") == favorite) cursor = metric(graphics, cursor, PING, pingLabel, pingWidth, "ms");
            if (HudDragging.WATERMARK.favorite("tps") == favorite) cursor = metric(graphics, cursor, TPS, tpsLabel, tpsWidth, "tps");
        }
        graphics.pose().popMatrix();
    }

    private static float metric(GuiGraphics graphics, float x, Identifier icon, String value, float valueWidth, String unit) {
        rect(graphics, x, 14, 1, 15, 0, 0, 0x12FFFFFF);
        x += 13;
        PngIconRenderer.draw(graphics, icon, Math.round(x), 14, 15, 0xFFFFFFFF);
        x += 22;
        MtsdfFont.draw(graphics, value, x, 14, 13, 0xFFFFFFFF);
        x += valueWidth + 6;
        MtsdfFont.draw(graphics, unit, x, 14, 13, 0xCCFFFFFF);
        return x + MtsdfFont.width(unit, 13) + 13;
    }

    private static void rect(GuiGraphics graphics, float x, float y, float w, float h, float top, float bottom, int color) {
        RoundedRectRenderer.draw(graphics, new RoundedRectRenderer.Rect(x, y, w, h, top, top, bottom, bottom, color));
    }

    private static Identifier icon(String name) {
        return Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/" + name + ".png");
    }
}
