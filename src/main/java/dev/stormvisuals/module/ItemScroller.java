package dev.stormvisuals.module;

import com.mojang.blaze3d.platform.InputConstants;
import dev.stormvisuals.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import org.lwjgl.glfw.GLFW;

/** Shift + held left mouse quick-moves crossed slots through vanilla inventory handling. */
public final class ItemScroller {
    private static boolean enabled;
    private ItemScroller() {}
    public static void setEnabled(boolean on) { enabled = on; }

    public static void initialize() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof CreativeModeInventoryScreen) return;
            Gesture gesture = new Gesture(client, container);
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                gesture.held = false;
                if (event.button() != 0 || !event.hasShiftDown() || !gesture.available()) return true;
                var origin = (ContainerScreenAccessor) container;
                boolean overSlot = container.getMenu().slots.stream().anyMatch(slot -> slot.isActive()
                        && event.x() >= origin.stormvisuals$left() + slot.x && event.x() < origin.stormvisuals$left() + slot.x + 16
                        && event.y() >= origin.stormvisuals$top() + slot.y && event.y() < origin.stormvisuals$top() + slot.y + 16);
                if (!overSlot) return true;
                gesture.held = true;
                gesture.visited.clear();
                gesture.x = event.x(); gesture.y = event.y();
                gesture.move(event.x(), event.y());
                return false;
            });
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> {
                if (!gesture.held || event.button() != 0) return true;
                if (gesture.available() && (InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                        || InputConstants.isKeyDown(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT)))
                    gesture.move(event.x(), event.y());
                else { gesture.x = event.x(); gesture.y = event.y(); }
                return false;
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> {
                if (event.button() != 0 || !gesture.held) return true;
                gesture.held = false;
                return false;
            });
        });
    }

    private record Hit(int index, double time) {}
    private static final class Gesture {
        final Minecraft client;
        final AbstractContainerScreen<?> screen;
        final BitSet visited = new BitSet();
        boolean held;
        double x, y;
        Gesture(Minecraft client, AbstractContainerScreen<?> screen) { this.client = client; this.screen = screen; }
        boolean available() {
            return enabled && client.screen == screen && client.player != null && !client.player.isSpectator()
                    && client.gameMode != null && client.player.containerMenu == screen.getMenu()
                    && screen.getMenu().getCarried().isEmpty() && !ChestSorter.isSorting(screen.getMenu())
                    && !Auction.isAuctionMenu(screen.getMenu());
        }
        void move(double nextX, double nextY) {
            var origin = (ContainerScreenAccessor) screen;
            var hits = new ArrayList<Hit>();
            for (var slot : screen.getMenu().slots) {
                if (!slot.isActive() || visited.get(slot.index)) continue;
                double t = crossing(x, y, nextX, nextY, origin.stormvisuals$left() + slot.x, origin.stormvisuals$top() + slot.y);
                if (!Double.isNaN(t)) hits.add(new Hit(slot.index, t));
            }
            hits.sort(Comparator.comparingDouble(Hit::time));
            for (Hit hit : hits) {
                visited.set(hit.index);
                var slot = screen.getMenu().slots.get(hit.index);
                if (slot.hasItem() && slot.mayPickup(client.player))
                    client.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, hit.index, 0, ClickType.QUICK_MOVE, client.player);
            }
            x = nextX; y = nextY;
        }
    }

    // Segment/slot intersection catches slots skipped by fast movement between frames.
    static double crossing(double x, double y, double nextX, double nextY, double left, double top) {
        double dx = nextX - x, dy = nextY - y, enter = 0, exit = 1;
        if (dx == 0) { if (x < left || x >= left + 16) return Double.NaN; }
        else {
            double a = (left - x) / dx, b = (left + 16 - x) / dx;
            enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        if (dy == 0) { if (y < top || y >= top + 16) return Double.NaN; }
        else {
            double a = (top - y) / dy, b = (top + 16 - y) / dy;
            enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
        }
        return enter <= exit ? enter : Double.NaN;
    }
}
