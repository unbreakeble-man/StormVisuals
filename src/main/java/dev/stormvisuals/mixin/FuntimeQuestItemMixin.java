package dev.stormvisuals.mixin;

import dev.stormvisuals.module.FuntimeQuestProgress;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fabric's use callbacks run before success is known; these observers do not alter the result. */
@Mixin(ItemStack.class)
public abstract class FuntimeQuestItemMixin {
    // Preserve the item identity even when successful use consumes the last item in this stack.
    @Shadow @Final private Item item;

    @Inject(method = "useOn", at = @At("RETURN"))
    private void stormvisuals$questUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (context.getLevel().isClientSide() && context.getPlayer() != null)
            FuntimeQuestProgress.usedItem(context.getPlayer(), item, cir.getReturnValue());
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void stormvisuals$questUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (level.isClientSide() && ((ItemStack) (Object) this).getUseDuration(player) <= 0)
            FuntimeQuestProgress.usedItem(player, item, cir.getReturnValue());
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void stormvisuals$questFinishUse(Level level, LivingEntity actor, CallbackInfoReturnable<ItemStack> cir) {
        if (level.isClientSide()) FuntimeQuestProgress.usedItem(actor, item, InteractionResult.SUCCESS);
    }

    // Vanilla calls this once per taken crafting result, including Shift-click quantities.
    @Inject(method = "onCraftedBy", at = @At("HEAD"))
    private void stormvisuals$questCrafted(Player player, int amount, CallbackInfo ci) {
        if (player.level().isClientSide()) FuntimeQuestProgress.crafted(player, item, amount);
    }
}
