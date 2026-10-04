package dev.stormvisuals.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderPipeline.Builder.class)
public abstract class FullbrightPipelineMixin {
    @Shadow private Optional<Identifier> vertexShader;
    @Shadow private Optional<Identifier> fragmentShader;

    @Inject(method = "build", at = @At("HEAD"))
    private void stormvisuals$lightPipeline(CallbackInfoReturnable<RenderPipeline> result) {
        if (vertexShader.isEmpty() || fragmentShader.isEmpty()) return;
        Identifier shader = vertexShader.get();
        if (!shader.getNamespace().equals("minecraft") || !shader.equals(fragmentShader.get())) return;
        if (!shader.getPath().equals("core/terrain") && !shader.getPath().equals("core/entity") && !shader.getPath().equals("core/block") && !shader.getPath().equals("core/particle")) return;
        RenderPipeline.Builder builder = (RenderPipeline.Builder) (Object) this;
        Identifier replacement = Identifier.fromNamespaceAndPath("stormvisuals", shader.getPath());
        builder.withVertexShader(replacement).withFragmentShader(replacement).withUniform("StormLight", UniformType.UNIFORM_BUFFER);
    }
}
