package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Removals;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

@Mixin(GameRenderer.class)
public abstract class RemovalsGameRendererMixin {
    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
    private void stormvisuals$bob(CallbackInfo ci) {
        if (Removals.hides(4)) ci.cancel();
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"))
    private float stormvisuals$nausea(net.minecraft.client.player.LocalPlayer player, Holder<MobEffect> effect, float delta) {
        return Removals.hides(3) && effect == MobEffects.NAUSEA ? 0 : player.getEffectBlendFactor(effect, delta);
    }
}
