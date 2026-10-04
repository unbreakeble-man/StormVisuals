package dev.stormvisuals.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/** Draws independently rounded, solid-color HUD rectangles with a single GLSL pipeline. */
public final class RoundedRectRenderer {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("stormvisuals", "core/rounded_rect");
    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("stormvisuals", "pipeline/rounded_rect"))
                    .withVertexShader(SHADER)
                    .withFragmentShader(SHADER)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .build()
    );

    private RoundedRectRenderer() {
    }

    public interface Rectangle {
        float x();
        float y();
        float width();
        float height();
        float topLeft();
        float topRight();
        float bottomRight();
        float bottomLeft();
        int argb();
    }

    public record Rect(float x, float y, float width, float height,
                       float topLeft, float topRight, float bottomRight, float bottomLeft,
                       int argb) implements Rectangle {
        public Rect {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(width) || !Float.isFinite(height)
                    || !Float.isFinite(topLeft) || !Float.isFinite(topRight)
                    || !Float.isFinite(bottomRight) || !Float.isFinite(bottomLeft)
                    || !Float.isFinite(x + width) || !Float.isFinite(y + height)
                    || width <= 0 || height <= 0) {
                throw new IllegalArgumentException("Rectangle coordinates, size and radii must be finite; size must be positive");
            }
            float maximumRadius = Math.min(width, height) * 0.5f;
            topLeft = Math.clamp(topLeft, 0.0f, maximumRadius);
            topRight = Math.clamp(topRight, 0.0f, maximumRadius);
            bottomRight = Math.clamp(bottomRight, 0.0f, maximumRadius);
            bottomLeft = Math.clamp(bottomLeft, 0.0f, maximumRadius);
        }
    }

    /** May be called every HUD frame with new rectangle parameters. */
    public static void draw(GuiGraphics graphics, Rectangle rect) {
        if ((rect.argb() >>> 24) == 0) {
            return;
        }

        Matrix3x2fc pose = new Matrix3x2f(graphics.pose());
        var window = Minecraft.getInstance().getWindow();
        float pixelsPerUnit = (float) Math.hypot(pose.m00(), pose.m01())
                * window.getWidth() / Math.max(1, window.getGuiScaledWidth());
        float padding = 1 / Math.max(0.001F, pixelsPerUnit);
        int left = (int) Math.floor(rect.x() - padding);
        int top = (int) Math.floor(rect.y() - padding);
        int right = (int) Math.ceil(rect.x() + rect.width() + padding);
        int bottom = (int) Math.ceil(rect.y() + rect.height() + padding);
        ScreenRectangle bounds = new ScreenRectangle(left, top, right - left, bottom - top)
                .transformMaxBounds(pose);
        ScreenRectangle scissor = graphics.scissorStack.peek();
        if (scissor != null) {
            bounds = scissor.intersection(bounds);
        }
        if (bounds == null) return;

        graphics.guiRenderState.submitGuiElement(new RoundedRectState(pose, rect, padding, scissor, bounds));
    }

    private record RoundedRectState(Matrix3x2fc pose, Rectangle rect, float padding,
                                    ScreenRectangle scissorArea, ScreenRectangle bounds)
            implements GuiElementRenderState {
        @Override
        public RenderPipeline pipeline() {
            return PIPELINE;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            float x0 = rect.x();
            float y0 = rect.y();
            float x1 = x0 + rect.width();
            float y1 = y0 + rect.height();
            float xm = (x0 + x1) * 0.5f;
            float ym = (y0 + y1) * 0.5f;

            if (rect.topLeft() == 0 && rect.topRight() == 0 && rect.bottomRight() == 0 && rect.bottomLeft() == 0) {
                corner(vertices, x0, y0, x1, y1, 0, false, false);
                return;
            }

            corner(vertices, x0, y0, xm, ym, rect.topLeft(), false, false);
            corner(vertices, xm, y0, x1, ym, rect.topRight(), true, false);
            corner(vertices, xm, ym, x1, y1, rect.bottomRight(), true, true);
            corner(vertices, x0, ym, xm, y1, rect.bottomLeft(), false, true);
        }

        private void corner(VertexConsumer vertices, float x0, float y0, float x1, float y1,
                            float radius, boolean right, boolean bottom) {
            // UVs are distances inward from this corner, divided by its radius.
            // A zero-radius corner uses UV 2, safely inside the shape everywhere.
            float u0 = radius == 0 ? 2 : (right ? (x1 - x0) / radius : 0);
            float u1 = radius == 0 ? 2 : (right ? 0 : (x1 - x0) / radius);
            float v0 = radius == 0 ? 2 : (bottom ? (y1 - y0) / radius : 0);
            float v1 = radius == 0 ? 2 : (bottom ? 0 : (y1 - y0) / radius);

            // Leave room outside the silhouette for symmetric one-pixel edge coverage.
            // Internal quarter seams remain exactly aligned and never overlap.
            if (radius > 0) {
                if (right) { x1 += padding; u1 -= padding / radius; }
                else { x0 -= padding; u0 -= padding / radius; }
                if (bottom) { y1 += padding; v1 -= padding / radius; }
                else { y0 -= padding; v0 -= padding / radius; }
            }

            vertices.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(rect.argb());
            vertices.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(rect.argb());
            vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(rect.argb());
            vertices.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(rect.argb());
        }
    }
}
