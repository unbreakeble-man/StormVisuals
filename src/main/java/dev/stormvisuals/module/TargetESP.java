package dev.stormvisuals.module;

import dev.stormvisuals.render.TargetEspRenderer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Only uses the entity actually under the vanilla crosshair. */
public final class TargetESP {
    public static final String[] OPTIONS = {"Животные", "Игроки", "Голые", "Мобы"};
    public static final String[] STYLES = {"Души", "Круг", "Кристаллы", "Маркер"};
    public static final RenderStateDataKey<Frame> FRAME = RenderStateDataKey.create(() -> "StormVisuals TargetESP");
    private static boolean enabled;
    private static int targetMask = 1 << 1;
    private static int style;
    private static String selectionLabel = "Выбрано: 1";
    private static int color = 0xFF1F6E;
    private static LivingEntity target;
    private static long lastFrame;
    private static float opacity;
    private static long generation;

    private TargetESP() {}

    public static int color() { return color; }

    public static int targetMask() { return targetMask; }

    public static void restore(boolean active, int mask, int selectedStyle, int rgb) {
        clear();
        enabled = active;
        targetMask = mask & 15;
        selectionLabel = "Выбрано: " + Integer.bitCount(targetMask);
        setStyle(selectedStyle);
        setColor(rgb);
    }

    public static void setColor(int rgb) { color = rgb & 0xFFFFFF; dev.stormvisuals.config.ConfigManager.changed(); }

    public static boolean selected(int type) { return (targetMask & 1 << type) != 0; }

    public static String selectionLabel() { return selectionLabel; }

    public static void toggleTarget(int type) {
        if (type < 0 || type >= OPTIONS.length) return;
        targetMask ^= 1 << type;
        selectionLabel = "Выбрано: " + Integer.bitCount(targetMask);
        clear();
        dev.stormvisuals.config.ConfigManager.changed();
    }

    public static int style() { return style; }

    public static void setStyle(int selectedStyle) {
        int next = Math.clamp(selectedStyle, 0, STYLES.length - 1);
        if (next != style) { style = next; clear(); dev.stormvisuals.config.ConfigManager.changed(); }
    }

    public static void initialize() {
        WorldRenderEvents.END_EXTRACTION.register(TargetESP::extract);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null || client.player == null || target != null && target.level() != client.level) clear();
        });
        TargetEspRenderer.initialize();
    }

    public static void configure(boolean active) {
        if (enabled != active) { clear(); dev.stormvisuals.config.ConfigManager.changed(); }
        enabled = active;
    }

    private static void clear() {
        generation++;
        target = null;
        opacity = 0;
        lastFrame = 0;
    }

    private static boolean accepts(LivingEntity entity) {
        if (entity instanceof Player player) {
            return selected(1) || selected(2)
                    && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
                    && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()
                    && player.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
                    && player.getItemBySlot(EquipmentSlot.FEET).isEmpty();
        }
        boolean animal = entity instanceof Animal || entity instanceof WaterAnimal || entity instanceof AmbientCreature;
        return animal ? selected(0) : selected(3) && entity instanceof Mob;
    }

    private static boolean valid(LivingEntity entity, Minecraft client) {
        return entity != client.player && entity.level() == client.level && entity.isAlive()
                && !entity.isRemoved() && !entity.isSpectator() && !entity.isInvisibleTo(client.player) && accepts(entity);
    }

    private static void extract(WorldExtractionContext context) {
        context.worldState().setData(FRAME, null);
        Minecraft client = Minecraft.getInstance();
        if (!enabled || client.player == null || client.level != context.world()) {
            clear();
            return;
        }
        if (target != null && !valid(target, client)) clear();

        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1_000_000_000F);
        lastFrame = now;
        LivingEntity hovered = client.screen == null && client.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity living && valid(living, client) ? living : null;
        if (hovered != null && hovered != target) {
            generation++;
            target = hovered;
            opacity = 0;
        }
        opacity = Math.clamp(opacity + dt * (hovered != null ? 7 : -5), 0, 1);
        if (opacity == 0 || target == null) {
            target = null;
            return;
        }

        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        Vec3 position = target.getPosition(partialTick);
        if (!context.frustum().isVisible(target.getBoundingBox().inflate(1))) return;
        // Drawing only receives a snapshot; it never reads an entity or the world.
        context.worldState().setData(FRAME, new Frame(position.x, position.y, position.z,
                target.getBbWidth(), target.getBbHeight(),
                (context.worldState().gameTime + (double) partialTick) / 20.0, opacity, generation, target.hurtTime, color, style));
    }

    public record Frame(double x, double y, double z, float width, float height, double time, float opacity,
                        long generation, int hurtTime, int color, int style) {}
}
