package dev.stormvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.stormvisuals.module.Fullbright;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class FullbrightLightUniform {
    private static MappableRingBuffer worldBuffer;
    private static GpuBuffer disabledBuffer;
    private static GpuBuffer current;

    private FullbrightLightUniform() {}

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> ensureBuffers());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (worldBuffer != null) worldBuffer.close();
            if (disabledBuffer != null) disabledBuffer.close();
            worldBuffer = null;
            disabledBuffer = null;
            current = null;
        });
    }

    private static void ensureBuffers() {
        if (worldBuffer != null) return;
        worldBuffer = new MappableRingBuffer(() -> "StormVisuals dynamic light", 130, 16);
        disabledBuffer = RenderSystem.getDevice().createBuffer(() -> "StormVisuals light disabled", 130, 16);
        write(disabledBuffer, new Vector3f(), 0);
    }

    private static void write(GpuBuffer buffer, Vector3f origin, float radius) {
        try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(view.data()).putVec4(origin.x, origin.y, origin.z, radius);
        }
    }

    public static void beginWorld(DeltaTracker delta) {
        ensureBuffers();
        Minecraft client = Minecraft.getInstance();
        var camera = client.gameRenderer.getMainCamera();
        Vec3 source = client.player.getPosition(delta.getGameTimeDeltaPartialTick(true)).add(0, 1, 0);
        Vector3f relative = source.subtract(camera.position()).toVector3f();
        relative.rotate(camera.rotation().conjugate(new Quaternionf()));
        current = worldBuffer.currentBuffer();
        write(current, relative, Fullbright.radius());
    }

    public static void endWorld() {
        current = null;
        if (worldBuffer != null) worldBuffer.rotate();
    }

    public static void bind(RenderPass pass) {
        if (disabledBuffer == null) return;
        pass.setUniform("StormLight", current == null ? disabledBuffer : current);
    }
}
