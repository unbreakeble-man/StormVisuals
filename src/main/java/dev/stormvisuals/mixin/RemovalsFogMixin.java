package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Removals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.fog.FogRenderer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class RemovalsFogMixin {
    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void stormvisuals$fog(FogRenderer.FogMode mode, CallbackInfoReturnable<GpuBufferSlice> ci) {
        if (mode == FogRenderer.FogMode.WORLD && Removals.hides(0))
            ci.setReturnValue(((FogRenderer) (Object) this).getBuffer(FogRenderer.FogMode.NONE));
    }
}
