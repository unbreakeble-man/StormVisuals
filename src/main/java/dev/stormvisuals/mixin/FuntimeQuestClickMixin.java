package dev.stormvisuals.mixin;

import dev.stormvisuals.module.FuntimeQuestProgress;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric screen events cannot identify the exact quest slot before vanilla predicts its click. */
@Mixin(MultiPlayerGameMode.class)
public abstract class FuntimeQuestClickMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"))
    private void stormvisuals$questClick(int container, int slot, int button, ClickType type, Player player, CallbackInfo ci) {
        FuntimeQuestProgress.clickedContainer(player, container, slot, button, type);
    }
}
