package dev.stormvisuals.mixin;

import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.stormvisuals.render.FullbrightLightUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public abstract class FullbrightRenderSystemMixin {
    @Inject(method = "bindDefaultUniforms", at = @At("TAIL"))
    private static void stormvisuals$lightUniform(RenderPass pass, CallbackInfo ci) {
        FullbrightLightUniform.bind(pass);
    }
}
