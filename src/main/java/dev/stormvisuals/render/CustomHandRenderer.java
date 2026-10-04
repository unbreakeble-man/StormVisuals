package dev.stormvisuals.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.stormvisuals.module.CustomHand;
import java.util.OptionalInt;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/** The isolated hand's alpha is the mask; its RGB keeps the original item visible. */
public final class CustomHandRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                    .withLocation(id("pipeline/custom_hand"))
                    .withVertexShader(id("core/backdrop_blur"))
                    .withFragmentShader(id("core/custom_hand"))
                    .withSampler("HandMask").withUniform("HandSettings", UniformType.UNIFORM_BUFFER)
                    .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA).withCull(false).build());
    private static TextureTarget mask;
    private static MappableRingBuffer settings;
    private static GpuTextureView previousColor, previousDepth;
    private static long lastFrame;
    private static double time;
    private static boolean capturing;

    private CustomHandRenderer() {}
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("stormvisuals", path); }

    public static void initialize() {
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!CustomHand.enabled() || client.level == null || client.player == null) close();
        });
    }

    public static void beginFrame() {
        if (!CustomHand.enabled()) return;
        long now = System.nanoTime();
        if (lastFrame != 0) time += Math.min((now - lastFrame) / 1_000_000_000.0, 0.1) * CustomHand.speed();
        lastFrame = now;
    }

    public static boolean begin(InteractionHand hand) {
        if (!CustomHand.affects(hand)) return false;
        RenderSystem.assertOnRenderThread();
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return false;
        flush(client);
        var main = client.getMainRenderTarget();
        if (mask == null || mask.width != main.width || mask.height != main.height) {
            if (mask != null) mask.destroyBuffers();
            mask = new TextureTarget("StormVisuals hand mask", main.width, main.height, false);
        }
        if (settings == null) settings = new MappableRingBuffer(() -> "StormVisuals hand settings",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 16);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(mask.getColorTexture(), 0);
        previousColor = RenderSystem.outputColorTextureOverride;
        previousDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
        // Share vanilla hand depth so overlapping hands/items retain their normal occlusion.
        RenderSystem.outputDepthTextureOverride = previousDepth != null ? previousDepth : main.getDepthTextureView();
        capturing = true;
        return true;
    }

    /** A two-handed map submits both arms inside MAIN_HAND; leave the other arm plain when requested. */
    public static boolean beginPlainMapArm(HumanoidArm arm) {
        Minecraft client = Minecraft.getInstance();
        if (!capturing || CustomHand.bothHands() || client.player == null || arm == client.player.getMainArm()) return false;
        flush(client);
        RenderSystem.outputColorTextureOverride = previousColor;
        return true;
    }

    public static void finishPlainMapArm() {
        try {
            flush(Minecraft.getInstance());
        } finally {
            RenderSystem.outputColorTextureOverride = mask.getColorTextureView();
        }
    }

    public static void finish(boolean completed) {
        Minecraft client = Minecraft.getInstance();
        try {
            flush(client);
        } finally {
            RenderSystem.outputColorTextureOverride = previousColor;
            RenderSystem.outputDepthTextureOverride = previousDepth;
            capturing = false;
        }
        if (!completed) return;
        GpuBuffer buffer = settings.currentBuffer();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var view = encoder.mapBuffer(buffer, false, true)) {
            Std140Builder.intoBuffer(view.data()).putVec4((float) (time * 0.5 + 23),
                    mask.width, mask.height, CustomHand.opacity() / 100);
        }
        GpuTextureView destination = previousColor != null ? previousColor : client.getMainRenderTarget().getColorTextureView();
        try (var pass = encoder.createRenderPass(() -> "StormVisuals CustomHand", destination, OptionalInt.empty())) {
            pass.setPipeline(PIPELINE);
            pass.setUniform("HandSettings", buffer);
            pass.bindTexture("HandMask", mask.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
            pass.draw(0, 3);
        }
        settings.rotate();
        previousColor = previousDepth = null;
    }

    private static void flush(Minecraft client) {
        client.gameRenderer.getFeatureRenderDispatcher().renderAllFeatures();
        client.renderBuffers().bufferSource().endBatch();
    }

    private static void close() {
        RenderSystem.assertOnRenderThread();
        if (mask != null) { mask.destroyBuffers(); mask = null; }
        if (settings != null) { settings.close(); settings = null; }
        lastFrame = 0;
        time = 0;
        previousColor = previousDepth = null;
    }
}
