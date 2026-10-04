package dev.stormvisuals.mixin;

import dev.stormvisuals.render.WatermarkHud;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No general Fabric event exposes vanilla time packets; TAIL runs after thread dispatch. */
@Mixin(ClientPacketListener.class)
public abstract class WatermarkTimeMixin {
    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void stormvisuals$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
        WatermarkHud.serverTime(packet.gameTime());
    }
}
