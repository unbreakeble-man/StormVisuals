package dev.stormvisuals.module;

import dev.stormvisuals.config.ConfigState;
import dev.stormvisuals.config.ConfigManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Hold binding; modifies the calculated world FOV without rewriting vanilla options. */
public final class Zoom {
    private static boolean enabled;
    private static KeyMapping binding;
    private static float strength = 4, speed = 12, factor = 1;
    private static long time = System.nanoTime();
    private static Object world;
    private Zoom() {}

    public static void configure(boolean on, KeyMapping key) { enabled = on; binding = key; }
    public static float strength() { return strength; }
    public static void strength(float value) { strength = Math.clamp(value, 1.5f, 10); }
    public static float speed() { return speed; }
    public static void speed(float value) { speed = value; }
    public static void capture(ConfigState.ModuleState s) { s.zoomStrength = strength; s.zoomSpeed = speed; }
    public static void restore(ConfigState.ModuleState s) { strength = s.zoomStrength; speed = s.zoomSpeed; }

    private static boolean held(Minecraft client) {
        return enabled && binding != null && binding.isDown() && client.screen == null && client.getOverlay() == null
                && client.player != null && client.level != null && client.isWindowActive();
    }
    public static boolean scroll(double amount) {
        Minecraft client = Minecraft.getInstance();
        if (!held(client)) return false;
        double steps = client.options.discreteMouseScroll().get() ? Math.signum(amount) : amount;
        float value = Math.clamp(strength + (float) (steps * client.options.mouseWheelSensitivity().get() * .5), 1.5f, 10);
        if (strength != value) { strength = value; ConfigManager.changed(); }
        return true;
    }

    public static float fov(float original) {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        double dt = Math.clamp((now - time) / 1_000_000_000.0, 0, .1);
        time = now;
        if (world != client.level) { world = client.level; factor = 1; }
        if (client.level == null || client.player == null) { factor = 1; return original; }
        boolean held = held(client);
        float target = held ? 1 / strength : 1;
        factor += (target - factor) * (float) (1 - Math.exp(-speed * dt));
        if (Math.abs(factor - target) < .0001f) factor = target;
        return original * factor;
    }
}
