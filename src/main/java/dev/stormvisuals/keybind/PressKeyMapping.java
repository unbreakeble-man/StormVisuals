package dev.stormvisuals.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Uses Minecraft's input dispatch, retaining short presses but ignoring key repeat. */
public final class PressKeyMapping extends KeyMapping {
    private int presses;

    public PressKeyMapping(String name, InputConstants.Type type, int key, Category category) {
        super(name, type, key, category);
    }

    @Override
    public void setDown(boolean down) {
        Minecraft client = Minecraft.getInstance();
        if (down && !isDown() && client.player != null && client.level != null
                && client.screen == null && client.isWindowActive()) presses++;
        super.setDown(down);
    }

    @Override
    protected boolean shouldSetOnIngameFocus() {
        return false;
    }

    @Override
    protected void release() {
        super.release();
        presses = 0;
    }

    @Override
    public void setKey(InputConstants.Key key) {
        release();
        super.setKey(key);
    }

    public int takePresses() {
        while (consumeClick()) { }
        int result = presses;
        presses = 0;
        return result;
    }
}
