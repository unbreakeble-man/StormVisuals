package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Zoom;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class ZoomMixin {
    // Fabric has no calculated-FOV event. RETURN preserves water/death/dynamic FOV modifiers.
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void stormvisuals$zoom(Camera camera, float partial, boolean world, CallbackInfoReturnable<Float> cir) {
        if (world) cir.setReturnValue(Zoom.fov(cir.getReturnValue()));
    }
}
