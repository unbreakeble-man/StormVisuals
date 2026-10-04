package dev.stormvisuals.module;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import net.minecraft.client.player.LocalPlayer;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class Fullbright {
    private static volatile boolean enabled;
    private static volatile int mode;
    private static LocalPlayer effectPlayer;
    private static MobEffectInstance ownedEffect;
    private static double brightness = 1;
    private static float radius = 10;

    private Fullbright() {}

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(Fullbright::tick);
    }

    public static void configure(boolean active, int selectedMode) {
        enabled = active;
        mode = selectedMode;

        tick(Minecraft.getInstance());
    }

    public static boolean brightnessEnabled() {
        return enabled && mode == 0;
    }

    private static void tick(Minecraft client) {
        boolean effect = enabled && mode == 1 && client.player != null && client.level != null;
        if (effectPlayer != null && (!effect || effectPlayer != client.player)) removeOwnedEffect();
        if (effect) {
            effectPlayer = client.player;
            if (!effectPlayer.hasEffect(MobEffects.NIGHT_VISION)) {
                ownedEffect = new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, true);
                effectPlayer.addEffect(ownedEffect);
            }
        }
    }

    private static void removeOwnedEffect() {
        // A server effect may have replaced ours. Never remove or edit that effect.
        if (ownedEffect != null && effectPlayer.getEffect(MobEffects.NIGHT_VISION) == ownedEffect)
            effectPlayer.removeEffect(MobEffects.NIGHT_VISION);
        ownedEffect = null;
        effectPlayer = null;
    }

    public static double brightness() { return brightness; }
    public static void setBrightness(double value) { brightness = Math.clamp(value, 0, 1); }
    public static void setRadius(float value) { radius = Math.clamp(value, 0, 10); }
    public static float radius() { return enabled && mode == 2 ? radius : 0; }
}