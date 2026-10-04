package dev.stormvisuals.module;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class Removals {
    public static final String[] OPTIONS = {"Туман", "Оверлей огня", "Трава", "Тошнота", "Покачивание камеры"};
    private static volatile boolean enabled;
    private static volatile int selected;

    private Removals() {}

    public static boolean selected(int option) { return (selected & (1 << option)) != 0; }
    public static boolean hides(int option) { return enabled && selected(option); }
    public static int selectedCount() { return Integer.bitCount(selected); }

    public static int mask() { return selected; }

    public static void restore(boolean active, int mask) {
        boolean grass = hides(2);
        selected = mask & 31;
        enabled = active;
        if (grass != hides(2)) rebuildGrass();
    }

    public static void setEnabled(boolean active) {
        boolean grass = hides(2);
        enabled = active;
        if (grass != hides(2)) rebuildGrass();
    }

    public static void toggle(int option) {
        selected ^= 1 << option;
        if (enabled && option == 2) rebuildGrass();
    }

    private static void rebuildGrass() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null) client.levelRenderer.allChanged();
    }

    public static boolean hidesGrass(BlockState state) {
        return hides(2) && (state.is(Blocks.SHORT_GRASS) || state.is(Blocks.TALL_GRASS));
    }
}
