package dev.stormvisuals.mixin;

import dev.stormvisuals.module.FuntimeQuestProgress;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe the server's collector and item before vanilla removes/shrinks it, after thread dispatch. */
@Mixin(ClientPacketListener.class)
public abstract class FuntimeQuestPickupMixin {
    @Inject(method = "handleTakeItemEntity", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientLevel;getEntity(I)Lnet/minecraft/world/entity/Entity;", ordinal = 0))
    private void stormvisuals$questPickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        FuntimeQuestProgress.pickedUpFishingItem(packet.getItemId(), packet.getPlayerId());
    }
}
