package dev.stormvisuals.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.stormvisuals.module.TargetESP;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Camera-facing PNG sprites following the actual world-space path of three orbiting heads. */
public final class TargetEspRenderer {
    private static final int COMETS = 3;
    private static final int LENGTH = 32;
    private static final int CORE_LENGTH = 18;
    private static final double STEP = 1.0 / 120;
    private static final double SPEED = 1.15;
    private static final Identifier SHADER = id("core/target_comet");
    private static final RenderPipeline PIPELINE = pipeline("target_comet", false);
    private static final RenderPipeline GLOW_PIPELINE = pipeline("target_comet_glow", false);
    private static final RenderType TYPE = type("stormvisuals_target_comet", PIPELINE);
    private static final RenderType GLOW_TYPE = type("stormvisuals_target_comet_glow", GLOW_PIPELINE);
    private static final RenderType CORE_TYPE = type("stormvisuals_target_comet_core", PIPELINE);

    private static RenderPipeline pipeline(String name, boolean depthWrite) {
        return RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(id("pipeline/" + name))
                    .withVertexShader(SHADER).withFragmentShader(SHADER)
                    .withSampler("Sampler0")
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                    .withCull(false).withDepthWrite(depthWrite)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST).build());
    }

    private static RenderType type(String name, RenderPipeline pipeline) {
        return RenderType.create(name,
            RenderSetup.builder(pipeline).withTexture("Sampler0", id("textures/targetesp/glow.png"))
                    .sortOnUpload().bufferSize(COMETS * (LENGTH + CORE_LENGTH) * 4 * 24).createRenderSetup());
    }
    private static final double[] TRAIL = new double[COMETS * LENGTH * 3];
    private static final float[] ALPHA = new float[LENGTH];
    private static final float[] SIZE = new float[LENGTH];
    private static final float[] CORE_ALPHA = new float[CORE_LENGTH];
    private static final float[] HEIGHTS = {1.05F, 0.62F, 0.19F};
    private static final Vector3f RIGHT = new Vector3f();
    private static final Vector3f UP = new Vector3f();
    private static TargetESP.Frame previous;
    private static int head;
    private static int count;
    private static double nextSample;
    private static double angle;
    private static double wave;
    private static double lastHurt = Double.NEGATIVE_INFINITY;

    static {
        for (int i = 0; i < LENGTH; i++) {
            float progress = i / (float) LENGTH;
            ALPHA[i] = (float) Math.pow(1 - progress, 1.3);
            SIZE[i] = 0.21F * (1 - progress * 0.55F);
        }
    }

    private TargetEspRenderer() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("stormvisuals", path);
    }

    public static void initialize() {
        TargetEspStyles.initialize();
        WorldRenderEvents.AFTER_ENTITIES.register(TargetEspRenderer::draw);
    }

    private static void draw(WorldRenderContext context) {
        TargetESP.Frame frame = context.worldState().getData(TargetESP.FRAME);
        if (frame == null) {
            previous = null;
            count = 0;
            return;
        }
        for (int i = 0; i < CORE_LENGTH; i++) CORE_ALPHA[i] = (float) Math.pow(1 - i / (float) CORE_LENGTH, 0.7);
        if (frame.style() != 0) {
            previous = null;
            count = 0;
            TargetEspStyles.draw(context, frame);
            return;
        }
        // Clear history on target/world changes, clock resets, or teleports.
        boolean reset = previous == null || previous.generation() != frame.generation()
                || frame.time() < previous.time() || frame.time() - previous.time() > 0.25
                || distanceSquared(frame, previous) > 16;
        if (reset) {
            previous = frame;
            head = LENGTH - 1;
            count = 0;
            nextSample = frame.time();
            angle = frame.time() * SPEED * (1.3 / 0.35) % (Math.PI * 2);
            wave = frame.time() * SPEED / 0.35;
            lastHurt = Double.NEGATIVE_INFINITY;
        }
        if (frame.hurtTime() > 0) lastHurt = frame.time();
        double sinceHurt = frame.time() - lastHurt;
        float bonus = 0.2F * (float) Math.clamp(1 - sinceHurt / 0.3, 0, 1);
        float hurt = Math.max(Math.clamp(frame.hurtTime() / 10F, 0, 1),
                (float) Math.clamp(1 - sinceHurt / 0.2, 0, 1));
        double dt = frame.time() - previous.time();
        double nextWave = wave + dt * SPEED * (1 + bonus) / 0.35;
        double nextAngle = angle + dt * SPEED * (1 + bonus) * (1.3 + bonus * 0.5) / 0.35;
        // Fixed-rate ring buffers keep trail length independent of the display's FPS.
        while (nextSample <= frame.time()) {
            double fraction = dt > 0 ? Math.clamp((nextSample - previous.time()) / dt, 0, 1) : 1;
            double x = previous.x() + (frame.x() - previous.x()) * fraction;
            double y = previous.y() + (frame.y() - previous.y()) * fraction;
            double z = previous.z() + (frame.z() - previous.z()) * fraction;
            double height = previous.height() + (frame.height() - previous.height()) * fraction;
            head = (head + 1) % LENGTH;
            count = Math.min(count + 1, LENGTH);
            for (int comet = 0; comet < COMETS; comet++) {
                int index = (comet * LENGTH + head) * 3;
                double phase = comet * Math.PI * 2 / COMETS + angle + (nextAngle - angle) * fraction;
                double oscillation = wave + (nextWave - wave) * fraction;
                TRAIL[index] = x + Math.cos(phase) * 0.7;
                // Parallel height bands keep neighbouring souls from crossing each other's path.
                TRAIL[index + 1] = y + Math.max(height, 1.35) * HEIGHTS[comet]
                        + Math.sin(oscillation * 1.5) * (0.06 + hurt * 0.02);
                TRAIL[index + 2] = z + Math.sin(phase) * 0.7;
            }
            nextSample += STEP;
        }
        angle = nextAngle;
        wave = nextWave;
        previous = frame;
        var camera = context.worldState().cameraRenderState;
        RIGHT.set(1, 0, 0).rotate(camera.orientation);
        UP.set(0, 1, 0).rotate(camera.orientation);
        Matrix4f pose = context.matrices().last().pose();
        float red = (frame.color() >> 16 & 255) / 255F;
        float green = (frame.color() >> 8 & 255) / 255F;
        float blue = (frame.color() & 255) / 255F;
        red += (1 - red) * hurt;
        green += (0.32F - green) * hurt;
        blue += (0.32F - blue) * hurt;
        double sampleBlend = Math.clamp((frame.time() - (nextSample - STEP)) / STEP, 0, 1);
        // Test against the world's depth, but never let transparent trail sprites mask each other.
        // White bodies and cores render over the coloured glow.
        for (int layer = 0; layer < 3; layer++) {
            RenderType type = layer == 0 ? GLOW_TYPE : layer == 1 ? TYPE : CORE_TYPE;
            VertexConsumer vertices = context.consumers().getBuffer(type);
            for (int comet = 0; comet < COMETS; comet++) {
                for (int age = count - 1; age >= 0; age--) {
                    int slot = (head - age + LENGTH) % LENGTH;
                    int index = (comet * LENGTH + slot) * 3;
                    double x = TRAIL[index], y = TRAIL[index + 1], z = TRAIL[index + 2];
                    if (age > 0) {
                        // Move every trail point between samples, not just the head. Otherwise the
                        // first gap doubles in size and the whole tail jumps whenever the ring advances.
                        int newer = (comet * LENGTH + (slot + 1) % LENGTH) * 3;
                        x += (TRAIL[newer] - x) * sampleBlend;
                        y += (TRAIL[newer + 1] - y) * sampleBlend;
                        z += (TRAIL[newer + 2] - z) * sampleBlend;
                    }
                    if (age == 0) {
                        double phase = comet * Math.PI * 2 / COMETS + angle;
                        x = frame.x() + Math.cos(phase) * 0.7;
                        y = frame.y() + Math.max(frame.height(), 1.35F) * HEIGHTS[comet]
                                + Math.sin(wave * 1.5) * (0.06 + hurt * 0.02);
                        z = frame.z() + Math.sin(phase) * 0.7;
                    }
                    float cx = (float) (x - camera.pos.x);
                    float cy = (float) (y - camera.pos.y);
                    float cz = (float) (z - camera.pos.z);
                    float alpha = frame.opacity() * ALPHA[age];
                    if (layer == 0) {
                        sprite(vertices, pose, cx, cy, cz, SIZE[age] * (1.3F + hurt * 0.12F),
                            red, green, blue, alpha * (0.22F + hurt * 0.08F));
                    } else if (layer == 1) {
                        sprite(vertices, pose, cx, cy, cz, SIZE[age], 1, 1, 1, alpha);
                    } else if (age < CORE_LENGTH) {
                        // Keep the large head but shorten the light section along the tail.
                        sprite(vertices, pose, cx, cy, cz, SIZE[age] * 0.8F,
                                1, 1, 1, frame.opacity() * CORE_ALPHA[age]);
                    }
                }
            }
            if (context.consumers() instanceof MultiBufferSource.BufferSource buffers) buffers.endBatch(type);
        }
    }

    private static double distanceSquared(TargetESP.Frame a, TargetESP.Frame b) {
        double x = a.x() - b.x(), y = a.y() - b.y(), z = a.z() - b.z();
        return x * x + y * y + z * z;
    }

    private static void sprite(VertexConsumer vertices, Matrix4f pose, float x, float y, float z,
                               float size, float red, float green, float blue, float alpha) {
        for (int corner = 0; corner < 4; corner++) {
            float u = corner < 2 ? 0 : 1;
            float v = corner == 0 || corner == 3 ? 1 : 0;
            float dx = (u * 2 - 1) * size;
            float dy = (1 - v * 2) * size;
            vertices.addVertex(pose, x + RIGHT.x * dx + UP.x * dy,
                            y + RIGHT.y * dx + UP.y * dy, z + RIGHT.z * dx + UP.z * dy)
                    .setUv(u, v).setColor(red, green, blue, alpha);
        }
    }
}
