package dev.stormvisuals.mixin;

import dev.stormvisuals.module.Removals;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class RemovalsGrassMixin {
    @Shadow
    protected abstract BlockState asState();

    // Let both vanilla and Indigo observe the same visual shape, without replacing their hooks.
    @Inject(method = "getRenderShape", at = @At("HEAD"), cancellable = true)
    private void stormvisuals$grass(CallbackInfoReturnable<RenderShape> result) {
        if (Removals.hidesGrass(asState())) result.setReturnValue(RenderShape.INVISIBLE);
    }
}
