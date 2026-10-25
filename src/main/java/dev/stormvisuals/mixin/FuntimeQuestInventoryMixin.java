package dev.stormvisuals.mixin;

import dev.stormvisuals.module.FuntimeQuestProgress;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Fabric event exposes applied server inventory updates; TAIL observes them after thread dispatch. */
@Mixin(ClientPacketListener.class)
public abstract class FuntimeQuestInventoryMixin {
    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void stormvisuals$questSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        FuntimeQuestProgress.inventoryUpdated();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void stormvisuals$questContents(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        FuntimeQuestProgress.inventoryUpdated();
    }

    @Inject(method = "handleSetPlayerInventory", at = @At("TAIL"))
    private void stormvisuals$questInventory(ClientboundSetPlayerInventoryPacket packet, CallbackInfo ci) {
        FuntimeQuestProgress.inventoryUpdated();
    }
}
