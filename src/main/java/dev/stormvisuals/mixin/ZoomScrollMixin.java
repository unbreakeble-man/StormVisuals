package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class ZoomScrollMixin {
    // Fabric screen scroll events do not cover camera input while no screen is open.
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void stormvisuals$zoomScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (window == client.getWindow().handle() && Zoom.scroll(vertical)) ci.cancel();
    }
}
