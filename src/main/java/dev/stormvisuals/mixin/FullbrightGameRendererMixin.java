package dev.stormvisuals.mixin;

import dev.stormvisuals.render.FullbrightLightUniform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class FullbrightGameRendererMixin {
    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;extractCamera(F)V", shift = At.Shift.AFTER))
    private void stormvisuals$beginLight(DeltaTracker delta, CallbackInfo ci) {
        FullbrightLightUniform.beginWorld(delta);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void stormvisuals$endLight(DeltaTracker delta, CallbackInfo ci) {
        FullbrightLightUniform.endWorld();
    }
}
