package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Removals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.Gui;

@Mixin(Gui.class)
public abstract class RemovalsGuiMixin {
    @Inject(method = "renderConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void stormvisuals$hide(CallbackInfo ci) {
        if (Removals.hides(3)) ci.cancel();
    }
}
