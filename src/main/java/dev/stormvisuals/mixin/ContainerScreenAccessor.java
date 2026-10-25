package dev.stormvisuals.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Actual container origin, including recipe-book offsets and resized screens. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int stormvisuals$left();
    @Accessor("topPos") int stormvisuals$top();
}
