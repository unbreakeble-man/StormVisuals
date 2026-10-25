package dev.stormvisuals.mixin;

import dev.stormvisuals.module.FuntimeQuestProgress;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No client Fabric event exposes server death/equipment-break notifications. */
@Mixin(LivingEntity.class)
public abstract class FuntimeQuestEntityMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"))
    private void stormvisuals$questEntityEvent(byte event, CallbackInfo ci) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity.level().isClientSide()) FuntimeQuestProgress.entityEvent(entity, event);
    }
}
