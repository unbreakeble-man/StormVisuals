package dev.stormvisuals.mixin;

import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Vanilla exposes progress, but no remaining cooldown ticks. Read-only. */
@Mixin(ItemCooldowns.class)
public interface ItemCooldownsAccessor {
    @Accessor("cooldowns") Map<Identifier, ?> stormvisuals$cooldowns();
    @Accessor("tickCount") int stormvisuals$tickCount();
}
