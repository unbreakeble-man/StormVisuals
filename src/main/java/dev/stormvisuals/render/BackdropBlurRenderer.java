package dev.stormvisuals.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.OptionalInt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Quarter-resolution separable Gaussian blur, clipped to a rounded GUI panel. */
public final class BackdropBlurRenderer implements AutoCloseable {
    private static final RenderPipeline DOWNSAMPLE = passPipeline("downsample", "DOWNSAMPLE");
    private static final RenderPipeline HORIZONTAL = passPipeline("blur_horizontal", "HORIZONTAL");
    private static final RenderPipeline VERTICAL = passPipeline("blur_vertical", "VERTICAL");
    private static final RenderPipeline PANEL = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET, RenderPipelines.GLOBALS_SNIPPET)
                    .withLocation(id("pipeline/backdrop_panel"))
                    .withVertexShader(id("core/rounded_rect"))
                    .withFragmentShader(id("core/backdrop_panel"))
                    .build());
    private TextureTarget first;
    private TextureTarget second;
    private long lastUpdate;
    private int sourceWidth;
    private int sourceHeight;
    private int regionX;
    private int regionY;
    private int regionWidth;
    private int regionHeight;

    public static void initialize() {
        // Register pipelines before the first shader resource reload.
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("stormvisuals", path);
    }

    private static RenderPipeline passPipeline(String name, String define) {
        return RenderPipelines.register(RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(id("pipeline/" + name))
                .withVertexShader(id("core/backdrop_blur"))
                .withFragmentShader(id("core/backdrop_blur"))
                .withShaderDefine(define).withSampler("Sampler0").withCull(false).build());
    }

    /** Called during screen extraction, after the world has rendered and before GUI drawing. */
    public void prepare(RenderTarget source, float x, float y, float panelWidth, float panelHeight, long now) {
        RenderSystem.assertOnRenderThread();
        int width = Math.max(1, (source.width + 3) / 4);
        int height = Math.max(1, (source.height + 3) / 4);
        boolean resized = first == null || first.width != width || first.height != height;
        if (resized) {
            close();
            first = new TextureTarget("StormVisuals blur A", width, height, false);
            second = new TextureTarget("StormVisuals blur B", width, height, false);
        }
        // Padding covers the dense Gaussian's 32-texel radius and bilinear sampling.
        // Scissor coordinates use the framebuffer's bottom-left origin.
        int left = Math.max(0, (int) Math.floor(x / 4) - 36);
        int bottom = Math.max(0, (int) Math.floor((source.height - y - panelHeight) / 4) - 36);
        int right = Math.min(width, (int) Math.ceil((x + panelWidth) / 4) + 36);
        int top = Math.min(height, (int) Math.ceil((source.height - y) / 4) + 36);
        boolean moved = regionX != left || regionY != bottom || regionWidth != right - left || regionHeight != top - bottom;
        boolean framebufferChanged = sourceWidth != source.width || sourceHeight != source.height;
        if (!resized && !moved && !framebufferChanged && now - lastUpdate < 16_666_667L) return;
        sourceWidth = source.width;
        sourceHeight = source.height;
        regionX = left;
        regionY = bottom;
        regionWidth = right - left;
        regionHeight = top - bottom;
        lastUpdate = now;
        pass(DOWNSAMPLE, source.getColorTextureView(), first);
        pass(HORIZONTAL, first.getColorTextureView(), second);
        pass(VERTICAL, second.getColorTextureView(), first);
    }

    private void pass(RenderPipeline pipeline, GpuTextureView source, TextureTarget destination) {
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        try (var pass = encoder.createRenderPass(() -> "StormVisuals backdrop blur",
                destination.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(pipeline);
            pass.enableScissor(regionX, regionY, regionWidth, regionHeight);
            pass.bindTexture("Sampler0", source,
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(0, 3);
        }
    }

    public void draw(GuiGraphics graphics, int width, int height, float radius, float opacity) {
        draw(graphics, width, height, radius, opacity, false);
    }

    public void draw(GuiGraphics graphics, int width, int height, float radius, float opacity, boolean squareTop) {
        if (first == null) return;
        Matrix3x2fc pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.scissorStack.peek();
        ScreenRectangle bounds = new ScreenRectangle(0, 0, width, height).transformMaxBounds(pose);
        if (scissor != null) bounds = scissor.intersection(bounds);
        var texture = TextureSetup.singleTexture(first.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if (bounds == null) return;
        graphics.guiRenderState.submitGuiElement(new PanelState(pose, width, height, squareTop,
                Math.clamp(radius, 0.01F, Math.min(width, height) / 2F),
                Math.round(Math.clamp(opacity, 0, 1) * 255) << 24 | 0xFFFFFF, texture, scissor, bounds));
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (first != null) { first.destroyBuffers(); first = null; }
        if (second != null) { second.destroyBuffers(); second = null; }
        lastUpdate = 0;
    }

    private record PanelState(Matrix3x2fc pose, int width, int height, boolean squareTop, float radius, int color,
                              TextureSetup textureSetup, ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() { return PANEL; }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            float xm = width / 2F;
            float ym = height / 2F;
            corner(vertices, 0, 0, xm, ym, false, false);
            corner(vertices, xm, 0, width, ym, true, false);
            corner(vertices, xm, ym, width, height, true, true);
            corner(vertices, 0, ym, xm, height, false, true);
        }

        private void corner(VertexConsumer vertices, float x0, float y0, float x1, float y1,
                            boolean right, boolean bottom) {
            float u0 = right ? (x1 - x0) / radius : 0;
            float u1 = right ? 0 : (x1 - x0) / radius;
            float v0 = bottom ? (y1 - y0) / radius : 0;
            float v1 = bottom ? 0 : (y1 - y0) / radius;
            if (squareTop && !bottom) { u0 = u1 = v0 = v1 = 2; }
            vertices.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
            vertices.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
            vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
            vertices.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
        }
    }
}
