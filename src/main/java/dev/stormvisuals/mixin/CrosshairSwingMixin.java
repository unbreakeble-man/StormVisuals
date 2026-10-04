package dev.stormvisuals.mixin;

import dev.stormvisuals.module.CustomCrosshair;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No Fabric callback covers accepted swings from attacks, mining and item use. */
@Mixin(LivingEntity.class)
public abstract class CrosshairSwingMixin {
    @Inject(method="swing(Lnet/minecraft/world/InteractionHand;Z)V",at=@At(value="FIELD",
            target="Lnet/minecraft/world/entity/LivingEntity;swingingArm:Lnet/minecraft/world/InteractionHand;",
            opcode=Opcodes.PUTFIELD,shift=At.Shift.AFTER))
    private void stormvisuals$crosshairSwing(InteractionHand hand,boolean broadcast,CallbackInfo ci){
        CustomCrosshair.onSwing((LivingEntity)(Object)this,hand);
    }
}
