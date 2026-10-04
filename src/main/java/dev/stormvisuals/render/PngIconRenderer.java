package dev.stormvisuals.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.TextureFormat;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.ResourceReloaderKeys;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleResourceReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.BlitRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.joml.Matrix3x2f;

/** Mipmap-filtered alpha masks from the supplied monochrome PNG icons. */
public final class PngIconRenderer {
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("stormvisuals", "pipeline/png_icon"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("stormvisuals", "core/png_icon"))
                    .build());

    private PngIconRenderer() {}

    private static final RenderPipeline LOGO_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("stormvisuals", "pipeline/png_circle"))
                    .withFragmentShader(Identifier.fromNamespaceAndPath("stormvisuals", "core/png_circle"))
                    .build());

    public static void initialize(List<Identifier> images) {
        Identifier listener = Identifier.fromNamespaceAndPath("stormvisuals", "gui_icons");
        ResourceLoader loader = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        loader.registerReloader(listener, new SimpleResourceReloader<List<PreparedIcon>>() {
            @Override
            protected List<PreparedIcon> prepare(SharedState state) {
                List<PreparedIcon> prepared = new ArrayList<>(images.size());
                try {
                    for (Identifier image : images) {
                        NativeImage source;
                        try (var input = state.resourceManager().getResourceOrThrow(image).open()) {
                            source = NativeImage.read(input);
                        }
                        int levels = 31 - Integer.numberOfLeadingZeros(Math.min(source.getWidth(), source.getHeight()));
                        try {
                            prepared.add(new PreparedIcon(image, MipmapGenerator.generateMipLevels(image,
                                    new NativeImage[]{source}, levels, MipmapStrategy.MEAN, 0)));
                        } catch (RuntimeException exception) {
                            source.close();
                            throw exception;
                        }
                    }
                    return prepared;
                } catch (IOException | RuntimeException exception) {
                    prepared.forEach(PreparedIcon::close);
                    if (exception instanceof IOException io) throw new UncheckedIOException(io);
                    throw (RuntimeException) exception;
                }
            }

            @Override
            protected void apply(List<PreparedIcon> prepared, SharedState state) {
                try {
                    var textures = Minecraft.getInstance().getTextureManager();
                    for (PreparedIcon image : prepared) {
                        textures.register(image.id(), new IconTexture(image));
                    }
                } finally {
                    prepared.forEach(PreparedIcon::close);
                }
            }
        });
        loader.addReloaderOrdering(ResourceReloaderKeys.Client.TEXTURES, listener);
    }

    public static void draw(GuiGraphics graphics, Identifier image, int x, int y, int size, int color) {
        draw(graphics, image, x, y, size, color, PIPELINE);
    }

    public static void drawCircle(GuiGraphics graphics, Identifier image, int x, int y, int size, int color) {
        draw(graphics, image, x, y, size, color, LOGO_PIPELINE);
    }

    private static void draw(GuiGraphics graphics, Identifier image, int x, int y, int size, int color, RenderPipeline pipeline) {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(image);
        var setup = TextureSetup.singleTexture(texture.getTextureView(), texture.getSampler());
        graphics.guiRenderState.submitGuiElement(new BlitRenderState(pipeline, setup,
                new Matrix3x2f(graphics.pose()), x, y, x + size, y + size,
                0, 1, 0, 1, color, graphics.scissorStack.peek()));
    }

    private record PreparedIcon(Identifier id, NativeImage[] levels) implements AutoCloseable {
        @Override
        public void close() {
            for (NativeImage image : levels) image.close();
        }
    }

    private static final class IconTexture extends AbstractTexture {
        IconTexture(PreparedIcon image) {
            RenderSystem.assertOnRenderThread();
            NativeImage source = image.levels()[0];
            var device = RenderSystem.getDevice();
            texture = device.createTexture(image.id()::toString,
                    GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8,
                    source.getWidth(), source.getHeight(), 1, image.levels().length);
            try {
                textureView = device.createTextureView(texture);
                sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR, true);
                var encoder = device.createCommandEncoder();
                for (int level = 0; level < image.levels().length; level++) {
                    NativeImage mip = image.levels()[level];
                    encoder.writeToTexture(texture, mip, level, 0, 0, 0,
                            mip.getWidth(), mip.getHeight(), 0, 0);
                }
            } catch (RuntimeException exception) {
                close();
                throw exception;
            }
        }
    }
}
