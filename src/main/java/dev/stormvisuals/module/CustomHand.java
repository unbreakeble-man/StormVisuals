package dev.stormvisuals.module;

import dev.stormvisuals.render.CustomHandRenderer;
import net.minecraft.world.InteractionHand;

public final class CustomHand {
    private static boolean enabled;
    private static boolean bothHands = true;
    private static float speed = 1;
    private static float opacity = 50;

    private CustomHand() {}

    public static void initialize() { CustomHandRenderer.initialize(); }
    public static boolean enabled() { return enabled; }
    public static void setEnabled(boolean value) { enabled = value; }
    public static boolean bothHands() { return bothHands; }
    public static void setBothHands(boolean value) { bothHands = value; }
    public static float speed() { return speed; }
    public static void setSpeed(float value) { speed = CrosshairSettings.limit(value, 0, 5, 1); }
    public static float opacity() { return opacity; }
    public static void setOpacity(float value) { opacity = CrosshairSettings.limit(value, 0, 100, 50); }
    public static boolean affects(InteractionHand hand) {
        return enabled && opacity > 0 && (bothHands || hand == InteractionHand.MAIN_HAND);
    }
}
