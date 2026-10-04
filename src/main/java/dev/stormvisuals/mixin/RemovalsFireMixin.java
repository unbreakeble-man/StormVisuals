package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Removals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.ScreenEffectRenderer;

@Mixin(ScreenEffectRenderer.class)
public abstract class RemovalsFireMixin {
    @Inject(method = "renderFire", at = @At("HEAD"), cancellable = true)
    private static void stormvisuals$hide(CallbackInfo ci) {
        if (Removals.hides(1)) ci.cancel();
    }
}
