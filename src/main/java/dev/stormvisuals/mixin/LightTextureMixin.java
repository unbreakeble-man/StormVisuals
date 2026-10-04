package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Fullbright;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    // Options validate gamma to 0..1. Override its render-time read without altering options.txt.
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object stormvisuals$gamma(OptionInstance<?> option) {
        Minecraft client = Minecraft.getInstance();
        Object value = option.get();
        if (Fullbright.brightnessEnabled() && client.player != null && option == client.options.gamma()) {
            double normal = (Double) value;
            return normal + (9.99 - normal) * Fullbright.brightness();
        }
        return value;
    }
}
