package dev.stormvisuals.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stormvisuals.module.TargetESP;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Ring, faceted crystals and a PNG marker; all use native world pipelines and the selected colour. */
final class TargetEspStyles {
    private static final int SEGMENTS = 64;
    private static final float NORMAL_SCALE = 1 / (float) (2 * Math.sin(Math.PI / SEGMENTS));
    private static final int CRYSTALS = 14;
    private static final float[] SIN = new float[SEGMENTS + 1], COS = new float[SEGMENTS + 1];
    private static final float[] PHASE = new float[CRYSTALS], HEIGHT = new float[CRYSTALS], RADIUS = new float[CRYSTALS];
    private static final float[] CRYSTAL_VERTICES = {0, 0, 1, 0, 0, -1, -.5F, 0, 0, 0, .5F, 0, .5F, 0, 0, 0, -.5F, 0};
    private static final int[] FACES = {0, 2, 3, 0, 3, 4, 0, 4, 5, 0, 5, 2, 1, 3, 2, 1, 4, 3, 1, 5, 4, 1, 2, 5};
    private static final Vector3f RIGHT = new Vector3f(), UP = new Vector3f(), NORMAL = new Vector3f();
    private static final RenderType RING = geometry("target_ring", VertexFormat.Mode.QUADS);
    private static final RenderType RING_LINE = RenderType.create("stormvisuals_target_ring_line", RenderSetup.builder(
            RenderPipelines.register(RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                    .withLocation(id("pipeline/target_ring_line"))
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).build()))
            .bufferSize(4096).createRenderSetup());
    private static final RenderType CRYSTAL = geometry("target_crystals", VertexFormat.Mode.TRIANGLES);
    private static final RenderPipeline TEXTURED = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(id("pipeline/target_marker"))
                    .withVertexShader(id("core/target_comet")).withFragmentShader(id("core/target_comet"))
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderPipeline GLOW = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(id("pipeline/target_crystal_glow"))
                    .withVertexShader(id("core/target_comet")).withFragmentShader(id("core/target_comet"))
                    .withSampler("Sampler0").withBlend(BlendFunction.LIGHTNING)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
    private static final RenderType MARKER = textured("target_marker", TEXTURED, "marker.png");
    private static final RenderType CRYSTAL_GLOW = textured("target_crystal_glow", GLOW, "glow.png");

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            SIN[i] = (float) Math.sin(i * Math.PI * 2 / SEGMENTS);
            COS[i] = (float) Math.cos(i * Math.PI * 2 / SEGMENTS);
        }
        for (int i = 0; i < CRYSTALS; i++) {
            float seed1 = (float) Math.sin(i * 1.7 + .3) * .5F + .5F;
            HEIGHT[i] = (float) Math.cos(i * 2.3 + .7) * .5F + .5F;
            RADIUS[i] = .25F + ((float) Math.sin(i * 3.1 + 1.1) * .5F + .5F) * .15F;
            PHASE[i] = (float) (i * Math.PI * 2 / CRYSTALS + Math.toRadians(seed1 * 12));
        }
    }

    private TargetEspStyles() {}
    static void initialize() {} // Register every pipeline before the first resource reload.
    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("stormvisuals", path); }

    private static RenderType geometry(String name, VertexFormat.Mode mode) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(id("pipeline/" + name)).withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).withDepthWrite(false).withCull(false).build());
        RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(pipeline).bufferSize(8192);
        if (mode == VertexFormat.Mode.QUADS) setup.sortOnUpload();
        return RenderType.create("stormvisuals_" + name, setup.createRenderSetup());
    }

    private static RenderType textured(String name, RenderPipeline pipeline, String texture) {
        return RenderType.create("stormvisuals_" + name, RenderSetup.builder(pipeline)
                .withTexture("Sampler0", id("textures/targetesp/" + texture)).sortOnUpload().bufferSize(2048).createRenderSetup());
    }

    static void draw(WorldRenderContext context, TargetESP.Frame frame) {
        var camera = context.worldState().cameraRenderState;
        RIGHT.set(1, 0, 0).rotate(camera.orientation);
        UP.set(0, 1, 0).rotate(camera.orientation);
        float x = (float) (frame.x() - camera.pos.x), y = (float) (frame.y() - camera.pos.y), z = (float) (frame.z() - camera.pos.z);
        float r = (frame.color() >> 16 & 255) / 255F, g = (frame.color() >> 8 & 255) / 255F, b = (frame.color() & 255) / 255F;
        Matrix4f pose = context.matrices().last().pose();
        switch (frame.style()) {
            case 1 -> ring(context, frame, pose, x, y, z, r, g, b);
            case 2 -> crystals(context, frame, pose, x, y, z, r, g, b);
            case 3 -> marker(context, frame, pose, x, y, z, r, g, b);
            default -> {}
        }
    }

    private static void ring(WorldRenderContext context, TargetESP.Frame f, Matrix4f pose,
                             float x, float y, float z, float r, float g, float b) {
        double cycle = f.time() * 2.8;
        float height = y + f.height() * (.06F + .88F * (float) (.5 - .5 * Math.cos(cycle)));
        // One short cylinder band trails opposite the vertical velocity and collapses smoothly at a reversal.
        float end = height - (float) Math.sin(cycle) * Math.min(.22F, f.height() * .12F);
        float radius = f.width() * .5F + .18F;
        VertexConsumer v = context.consumers().getBuffer(RING);
        for (int i = 0; i < SEGMENTS; i++) {
            vertex(v, pose, x + COS[i] * radius, end, z + SIN[i] * radius, r, g, b, 0);
            vertex(v, pose, x + COS[i] * radius, height, z + SIN[i] * radius, r, g, b, f.opacity() * .3F);
            vertex(v, pose, x + COS[i + 1] * radius, height, z + SIN[i + 1] * radius, r, g, b, f.opacity() * .3F);
            vertex(v, pose, x + COS[i + 1] * radius, end, z + SIN[i + 1] * radius, r, g, b, 0);
        }
        finish(context, RING);
        // Native lines keep a one-pixel contour at any distance, above the darker cylinder trail.
        float lr = .65F + r * .35F, lg = .65F + g * .35F, lb = .65F + b * .35F;
        VertexConsumer line = context.consumers().getBuffer(RING_LINE);
        for (int i = 0; i < SEGMENTS; i++) {
            context.matrices().last().transformNormal((COS[i + 1] - COS[i]) * NORMAL_SCALE, 0,
                    (SIN[i + 1] - SIN[i]) * NORMAL_SCALE, NORMAL);
            line.addVertex(pose, x + COS[i] * radius, height, z + SIN[i] * radius).setColor(lr, lg, lb, f.opacity())
                    .setNormal(NORMAL.x, NORMAL.y, NORMAL.z).setLineWidth(1);
            line.addVertex(pose, x + COS[i + 1] * radius, height, z + SIN[i + 1] * radius).setColor(lr, lg, lb, f.opacity())
                    .setNormal(NORMAL.x, NORMAL.y, NORMAL.z).setLineWidth(1);
        }
        finish(context, RING_LINE);
    }

    private static void crystals(WorldRenderContext context, TargetESP.Frame f, Matrix4f pose,
                                 float x, float y, float z, float r, float g, float b) {
        float eased = 1 - (1 - f.opacity()) * (1 - f.opacity()) * (1 - f.opacity());
        float scale = .18F * eased;
        double time = f.time() * Math.toRadians(120);
        VertexConsumer glow = context.consumers().getBuffer(CRYSTAL_GLOW);
        for (int i = 0; i < CRYSTALS; i++) {
            float radius = f.width() * .5F + RADIUS[i];
            double angle = time + PHASE[i];
            billboard(glow, pose, x + (float) Math.cos(angle) * radius, y + HEIGHT[i] * f.height(),
                    z + (float) Math.sin(angle) * radius, scale * 1.8F, 0, r, g, b, f.opacity() * .3F);
        }
        finish(context, CRYSTAL_GLOW);
        VertexConsumer v = context.consumers().getBuffer(CRYSTAL);
        for (int i = 0; i < CRYSTALS; i++) {
            float radius = f.width() * .5F + RADIUS[i];
            double angle = time + PHASE[i];
            float cx = x + (float) Math.cos(angle) * radius, cy = y + HEIGHT[i] * f.height(), cz = z + (float) Math.sin(angle) * radius;
            float cos = (float) Math.cos(-angle + Math.PI / 2), sin = (float) Math.sin(-angle + Math.PI / 2);
            for (int face = 0; face < 8; face++) {
                float light = face < 2 ? 1.3F : face < 4 ? 1 : .6F;
                for (int corner = 0; corner < 3; corner++) {
                    int offset = FACES[face * 3 + corner] * 3;
                    float vx = CRYSTAL_VERTICES[offset] * scale, vy = CRYSTAL_VERTICES[offset + 1] * scale, vz = CRYSTAL_VERTICES[offset + 2] * scale;
                    vertex(v, pose, cx + cos * vx + sin * vz, cy + vy, cz - sin * vx + cos * vz,
                            Math.min(1, r * light), Math.min(1, g * light), Math.min(1, b * light), f.opacity() * .494F);
                }
            }
        }
        finish(context, CRYSTAL);
    }

    private static void marker(WorldRenderContext context, TargetESP.Frame f, Matrix4f pose,
                               float x, float y, float z, float r, float g, float b) {
        double cycle = f.time() * 2.2;
        float rotation = (float) (Math.sin(cycle) * Math.PI + Math.sin(cycle * 2) * .12);
        float size = Math.max(.32F, Math.min(f.height() * .35F, f.width() * .65F));
        size *= 1 + (float) Math.cos(cycle * 2) * .035F;
        VertexConsumer v = context.consumers().getBuffer(MARKER);
        float cy = y + f.height() * .5F;
        float distance = (float) Math.sqrt(x * x + cy * cy + z * z);
        if (distance > .001F) {
            // Stay centred on the hitbox in projection, but move the flat marker to its camera-facing
            // surface. A billboard buried in the hitbox would be completely hidden by the target.
            float nx = -x / distance, ny = -cy / distance, nz = -z / distance;
            float halfWidth = f.width() * .5F, halfHeight = f.height() * .5F;
            float front = Math.min(halfWidth / Math.abs(nx), Math.min(halfHeight / Math.abs(ny), halfWidth / Math.abs(nz))) + .035F;
            x += nx * front;
            cy += ny * front;
            z += nz * front;
        }
        billboard(v, pose, x, cy, z, size, rotation, r, g, b, f.opacity());
        finish(context, MARKER);
    }

    private static void billboard(VertexConsumer v, Matrix4f pose, float x, float y, float z,
                                   float size, float rotation, float r, float g, float b, float a) {
        float cos = (float) Math.cos(rotation), sin = (float) Math.sin(rotation);
        for (int i = 0; i < 4; i++) {
            float u = i < 2 ? 0 : 1, t = i == 0 || i == 3 ? 1 : 0;
            float dx = (u * 2 - 1) * size, dy = (1 - t * 2) * size;
            float rx = dx * cos - dy * sin, ry = dx * sin + dy * cos;
            v.addVertex(pose, x + RIGHT.x * rx + UP.x * ry, y + RIGHT.y * rx + UP.y * ry, z + RIGHT.z * rx + UP.z * ry)
                    .setUv(u, t).setColor(r, g, b, a);
        }
    }

    private static void vertex(VertexConsumer v, Matrix4f pose, float x, float y, float z, float r, float g, float b, float a) {
        v.addVertex(pose, x, y, z).setColor(r, g, b, a);
    }

    private static void finish(WorldRenderContext context, RenderType type) {
        if (context.consumers() instanceof MultiBufferSource.BufferSource buffers) buffers.endBatch(type);
    }
}
