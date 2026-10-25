package dev.stormvisuals.module;

import dev.stormvisuals.config.ConfigState;
import dev.stormvisuals.render.HudRenderSystem;
import dev.stormvisuals.render.MtsdfFont;
import dev.stormvisuals.render.RoundedRectRenderer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class TntTimer {
    private static boolean enabled;
    private static float distance = 32;
    private static Object world;
    private static final ItemStack ICON = new ItemStack(Items.TNT);
    private static final List<Label> LABELS = new ArrayList<>();
    private static final float[] X = new float[32], Y = new float[32], WIDTH = new float[32];
    private record Label(PrimedTnt entity, String text, float width) {}
    private TntTimer() {}
    public static void setEnabled(boolean on) { enabled = on; if (!on) LABELS.clear(); }
    public static float distance() { return distance; }
    public static void distance(float value) { distance = value; }
    public static void capture(ConfigState.ModuleState s) { s.tntDistance = distance; }
    public static void restore(ConfigState.ModuleState s) { distance = s.tntDistance; }
    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            LABELS.clear();
            world = client.level;
            if (!enabled || client.player == null || client.level == null) return;
            client.level.getEntitiesOfClass(PrimedTnt.class, client.player.getBoundingBox().inflate(distance),
                    tnt -> !tnt.isRemoved() && tnt.distanceToSqr(client.player) <= distance * distance)
                    .stream().sorted(Comparator.comparingDouble(tnt -> tnt.distanceToSqr(client.player))).limit(32)
                    .forEach(tnt -> {
                        String text = String.format(Locale.ROOT, "~ %.1f s", Math.max(0, tnt.getFuse()) / 20f);
                        LABELS.add(new Label(tnt, text, 28 + MtsdfFont.width(text, 10)));
                    });
        });
        HudRenderSystem.addLayer((graphics, delta) -> render(graphics, delta.getGameTimeDeltaPartialTick(false)));
    }

    private static void render(GuiGraphics g, float partial) {
        Minecraft client = Minecraft.getInstance();
        if (!enabled || world != client.level || client.player == null) return;
        var camera = client.gameRenderer.getMainCamera();
        Vec3 eye = camera.position();
        int drawn = 0;
        for (Label label : LABELS) {
            if (label.entity.isRemoved()) continue;
            Vec3 point = label.entity.getPosition(partial).add(0, 1.15, 0);
            Vec3 offset = point.subtract(eye);
            if (offset.x * camera.forwardVector().x() + offset.y * camera.forwardVector().y()
                    + offset.z * camera.forwardVector().z() <= 0) continue;
            Vec3 screen = client.gameRenderer.projectPointToScreen(point);
            if (!Double.isFinite(screen.x) || screen.z < -1 || screen.z > 1) continue;
            float x = (float) ((screen.x + 1) * .5 * g.guiWidth()) - label.width / 2;
            float y = (float) ((1 - screen.y) * .5 * g.guiHeight()) - 11;
            if (x < 2 || x + label.width > g.guiWidth() - 2 || y < 2 || y + 22 > g.guiHeight() - 2) continue;
            boolean overlap = false;
            for (int i = 0; i < drawn; i++) if (x < X[i] + WIDTH[i] + 2 && x + label.width + 2 > X[i]
                    && Math.abs(y - Y[i]) < 24) { overlap = true; break; }
            if (overlap) continue;
            X[drawn] = x; WIDTH[drawn] = label.width; Y[drawn++] = y;
            RoundedRectRenderer.draw(g, new RoundedRectRenderer.Rect(x, y, label.width, 22, 6, 6, 6, 6, 0xDD101824));
            g.renderItem(ICON, (int) x + 3, (int) y + 3);
            MtsdfFont.draw(g, label.text, x + 23, y + 7, 10, label.entity.getFuse() <= 20 ? 0xFFFF7979 : 0xFFFFFFFF);
        }
    }
}
