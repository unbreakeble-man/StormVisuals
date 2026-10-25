package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Auction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Render and hover use the same display mapping; vanilla clicks retain the source slot ID. */
@Mixin(AbstractContainerScreen.class)
public abstract class AuctionScreenMixin {
    @Shadow @Final protected AbstractContainerMenu menu;

    @ModifyVariable(method = "renderSlot", at = @At("HEAD"), argsOnly = true)
    private Slot stormvisuals$auctionItem(Slot slot) {
        return Auction.displaySlot(menu, slot);
    }

    @Inject(method = "getHoveredSlot", at = @At("RETURN"), cancellable = true)
    private void stormvisuals$auctionHover(double x, double y, CallbackInfoReturnable<Slot> cir) {
        Slot slot = cir.getReturnValue();
        if (slot != null) cir.setReturnValue(Auction.displaySlot(menu, slot));
    }
}
