package dev.stormvisuals.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Comfortaa glyph metrics and MTSDF GUI pipeline. */
public final class MtsdfFont {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("stormvisuals", "core/mtsdf_text");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("stormvisuals", "pipeline/mtsdf_text"))
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .build());
    private static final FontFace FONT = loadFace("comfortaa_regular_mtsdf");

    private static FontFace loadFace(String name) {
        try (InputStream input = MtsdfFont.class.getResourceAsStream("/stormvisuals/font/" + name + ".json")) {
            if (input == null) throw new IOException("Missing font atlas metadata: " + name);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject atlas = root.getAsJsonObject("atlas");
            if (!"mtsdf".equals(atlas.get("type").getAsString())) throw new IOException("Atlas is not MTSDF");
            int atlasWidth = atlas.get("width").getAsInt();
            int atlasHeight = atlas.get("height").getAsInt();
            JsonObject metrics = root.getAsJsonObject("metrics");
            float lineHeight = metrics.get("lineHeight").getAsFloat();
            float baseline = -metrics.get("ascender").getAsFloat();
            Map<Integer, Glyph> glyphMap = new HashMap<>();
            JsonArray glyphs = root.getAsJsonArray("glyphs");
            for (JsonElement entry : glyphs) {
                JsonObject item = entry.getAsJsonObject();
                JsonObject plane = item.getAsJsonObject("planeBounds");
                JsonObject image = item.getAsJsonObject("atlasBounds");
                glyphMap.put(item.get("unicode").getAsInt(), new Glyph(item.get("advance").getAsFloat(),
                        plane == null ? 0 : plane.get("left").getAsFloat(),
                        plane == null ? 0 : plane.get("top").getAsFloat(),
                        plane == null ? 0 : plane.get("right").getAsFloat(),
                        plane == null ? 0 : plane.get("bottom").getAsFloat(),
                        image == null ? 0 : image.get("left").getAsFloat(),
                        image == null ? 0 : image.get("top").getAsFloat(),
                        image == null ? 0 : image.get("right").getAsFloat(),
                        image == null ? 0 : image.get("bottom").getAsFloat()));
            }
            return new FontFace(Identifier.fromNamespaceAndPath("stormvisuals", "textures/font/" + name + ".png"),
                    Map.copyOf(glyphMap), lineHeight, baseline, atlasWidth, atlasHeight);
        } catch (IOException | RuntimeException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private MtsdfFont() {
    }

    public static float width(String text, float size) {
        float em = 0;
        for (int offset = 0; offset < text.length();) {
            int codepoint = text.codePointAt(offset);
            Glyph glyph = FONT.glyphs().getOrDefault(codepoint, FONT.glyphs().get((int) '?'));
            if (glyph != null) em += glyph.advance();
            offset += Character.charCount(codepoint);
        }
        return Math.max(8, em * size);
    }

    public static float height(float size) {
        return FONT.lineHeight() * size;
    }

    public static void draw(GuiGraphics graphics, String text, float x, float y, float size, int argb) {
        if (text.isEmpty() || size <= 0 || (argb >>> 24) == 0) return;
        Matrix3x2fc pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(width(text, size)) + 2, (int) Math.ceil(height(size)) + 2)
                .transformMaxBounds(pose);
        ScreenRectangle scissor = graphics.scissorStack.peek();
        if (scissor != null) bounds = scissor.intersection(bounds);
        AbstractTexture atlas = Minecraft.getInstance().getTextureManager().getTexture(FONT.texture());
        TextureSetup texture = TextureSetup.singleTexture(atlas.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        graphics.guiRenderState.submitGuiElement(new TextState(pose, texture, scissor, bounds,
                text, x, y, size, argb));
    }

    private record FontFace(Identifier texture, Map<Integer, Glyph> glyphs, float lineHeight,
                            float baseline, int atlasWidth, int atlasHeight) {
    }

    private record Glyph(float advance, float left, float top, float right, float bottom,
                         float u0, float v0, float u1, float v1) {
        boolean visible() {
            return u1 > u0 && v1 > v0;
        }
    }

    private record TextState(Matrix3x2fc pose, TextureSetup textureSetup, ScreenRectangle scissorArea,
                             ScreenRectangle bounds, String text, float x, float y, float size, int color)
            implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return PIPELINE;
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            float pen = x;
            float baseline = y + FONT.baseline() * size;
            for (int offset = 0; offset < text.length();) {
                int codepoint = text.codePointAt(offset);
                Glyph glyph = FONT.glyphs().getOrDefault(codepoint, FONT.glyphs().get((int) '?'));
                if (glyph != null) {
                    if (glyph.visible()) {
                        float x0 = pen + glyph.left() * size;
                        float y0 = baseline + glyph.top() * size;
                        float x1 = pen + glyph.right() * size;
                        float y1 = baseline + glyph.bottom() * size;
                        float u0 = glyph.u0() / FONT.atlasWidth();
                        float v0 = glyph.v0() / FONT.atlasHeight();
                        float u1 = glyph.u1() / FONT.atlasWidth();
                        float v1 = glyph.v1() / FONT.atlasHeight();
                        vertices.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(color);
                        vertices.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(color);
                        vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(color);
                        vertices.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(color);
                    }
                    pen += glyph.advance() * size;
                }
                offset += Character.charCount(codepoint);
            }
        }
    }
}
