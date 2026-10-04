package dev.stormvisuals.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Compact active-effect list. Labels and measurements are refreshed on ticks, not frames. */
public final class PotionsHud {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath("stormvisuals", "textures/gui/icons/potions.png");
    private static final BackdropBlurRenderer BLUR = new BackdropBlurRenderer();
    private static final List<Row> ROWS = new ArrayList<>();
    private static int width;
    private static int timerWidth;
    private static int refreshTicks;

    private PotionsHud() {}

    public static List<Identifier> icons() { return List.of(ICON); }

    public static void initialize() {
        HudRenderSystem.addLayer((graphics, delta) -> render(graphics));
        ClientTickEvents.END_CLIENT_TICK.register(PotionsHud::update);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ROWS.clear();
            refreshTicks = 0;
            BLUR.close();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> BLUR.close());
    }

    private static void update(Minecraft client) {
        if (client.player == null || client.level == null) {
            ROWS.clear();
            refreshTicks = 0;
            return;
        }
        if (refreshTicks++ % 5 != 0) return;
        ROWS.clear();
        float longest = 0;
        timerWidth = 34;
        for (var effect : client.player.getActiveEffects()) {
            if (!effect.showIcon()) continue;
            String name = Component.translatable(effect.getDescriptionId()).getString();
            String level = effect.getAmplifier() == 0 ? "" : " " + Component.translatable(
                    "enchantment.level." + (effect.getAmplifier() + 1)).getString();
            // Vanilla only translates levels through X; use a number for larger amplifiers.
            if (effect.getAmplifier() >= 10) level = " " + (effect.getAmplifier() + 1);
            int seconds = Math.max(0, (effect.getDuration() + 19) / 20);
            String time = effect.isInfiniteDuration() ? "--:--" : seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
            float nameWidth = MtsdfFont.width(name, 11);
            float timeWidth = MtsdfFont.width(time, 10);
            longest = Math.max(longest, nameWidth + MtsdfFont.width(level, 12));
            timerWidth = Math.max(timerWidth, (int) Math.ceil(timeWidth + 8));
            ROWS.add(new Row(Gui.getMobEffectSprite(effect.getEffect()), name, level, nameWidth,
                    time, timeWidth, !effect.isInfiniteDuration() && seconds <= 20));
        }
        ROWS.sort(Comparator.comparing(Row::name));
        width = (int) Math.ceil(Math.max(96, 22 + 16 + 7 + longest + 14 + timerWidth));
    }

    private static void render(GuiGraphics graphics) {
        if (ROWS.isEmpty()) {
            HudDragging.POTIONS.hide();
            return;
        }
        Minecraft client = Minecraft.getInstance();
        float pixels = client.getMainRenderTarget().width / (float) graphics.guiWidth();
        int height = 38 + ROWS.size() * 28;
        float scale = Math.min(1.1F / pixels, Math.max(1, graphics.guiWidth() - 36 / pixels) / width);
        float x = 24 / pixels;
        float y = Math.min(graphics.guiHeight() * .38F, Math.max(0, graphics.guiHeight() - (height + 12) * scale));
        HudDragging.POTIONS.layout(x, y, width * scale, height * scale, graphics.guiWidth(), graphics.guiHeight());
        x = HudDragging.POTIONS.x();
        y = HudDragging.POTIONS.y();
        BLUR.prepare(client.getMainRenderTarget(), x * pixels, y * pixels, width * scale * pixels,
                height * scale * pixels, System.nanoTime());
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        for (int i = 8; i >= 1; i--) {
            float spread = i * 1.25F;
            rect(graphics, -spread, 3 - spread, width + spread * 2, height + spread * 2, 10 + spread, 0x03000000);
        }
        BLUR.draw(graphics, width, height, 10, 1);
        rect(graphics, 0, 0, width, height, 10, 0xC4000000);
        PngIconRenderer.draw(graphics, ICON, 11, 8, 14, 0xFFFFFFFF);
        MtsdfFont.draw(graphics, "Potions", 32, 8, 12, 0xFFFFFFFF);
        rect(graphics, 1, 29, width - 2, 1, 0, 0x0FFFFFFF);
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            int top = 35 + i * 28;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite, 11, top + 5, 16, 16);
            MtsdfFont.draw(graphics, row.name, 34, top + 6, 11, 0xFFFFFFFF);
            MtsdfFont.draw(graphics, row.level, 34 + row.nameWidth, top + 5, 12, 0xFFFFFFFF);
            int tx = width - 11 - timerWidth;
            rect(graphics, tx, top + 4, timerWidth, 18, 5, row.expiring ? 0x24EDBA7C : 0x14FFFFFF);
            rect(graphics, tx + 1, top + 5, timerWidth - 2, 16, 4, 0xFF171717);
            MtsdfFont.draw(graphics, row.time, tx + (timerWidth - row.timeWidth) / 2, top + 7, 10,
                    row.expiring ? 0xFFEDBA7C : 0xE0FFFFFF);
        }
        graphics.pose().popMatrix();
    }

    private static void rect(GuiGraphics graphics, float x, float y, float w, float h, float radius, int color) {
        RoundedRectRenderer.draw(graphics, new RoundedRectRenderer.Rect(x, y, w, h, radius, radius, radius, radius, color));
    }

    private record Row(Identifier sprite, String name, String level, float nameWidth,
                       String time, float timeWidth, boolean expiring) {}
}
