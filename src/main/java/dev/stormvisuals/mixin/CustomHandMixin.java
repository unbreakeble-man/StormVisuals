package dev.stormvisuals.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stormvisuals.render.CustomHandRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class CustomHandMixin {
    @Inject(method = "renderHandsWithItems", at = @At("HEAD"))
    private void stormvisuals$handFrame(CallbackInfo ci) { CustomHandRenderer.beginFrame(); }

    // Fabric world/HUD events cannot isolate the two vanilla first-person submissions.
    @WrapOperation(method = "renderHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void stormvisuals$maskedHand(ItemInHandRenderer renderer, AbstractClientPlayer player,
                                       float delta, float pitch, InteractionHand hand, float swing,
                                       ItemStack item, float equip, PoseStack poses, SubmitNodeCollector collector, int light,
                                       Operation<Void> original) {
        // Vanilla submits no geometry for an empty offhand, an invisible bare arm or a spyglass view.
        boolean captured = !player.isScoping()
                && !(item.isEmpty() && (hand == InteractionHand.OFF_HAND || player.isInvisible()))
                && CustomHandRenderer.begin(hand);
        boolean completed = false;
        try {
            original.call(renderer, player, delta, pitch, hand, swing, item, equip, poses, collector, light);
            completed = true;
        } finally {
            if (captured) CustomHandRenderer.finish(completed);
        }
    }

    @WrapOperation(method = "renderTwoHandedMap", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderMapHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;)V"))
    private void stormvisuals$mapArm(ItemInHandRenderer renderer, PoseStack poses, SubmitNodeCollector collector,
                                   int light, HumanoidArm arm, Operation<Void> original) {
        boolean plain = CustomHandRenderer.beginPlainMapArm(arm);
        try {
            original.call(renderer, poses, collector, light, arm);
        } finally {
            if (plain) CustomHandRenderer.finishPlainMapArm();
        }
    }
}
