package dev.stormvisuals.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla exposes the owner, but its server-synchronized bite flag is private. */
@Mixin(FishingHook.class)
public interface FuntimeFishingHookAccessor {
    @Accessor("biting")
    boolean stormvisuals$isBiting();
}
