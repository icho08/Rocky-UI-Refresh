package dev.i726.rocky.module.modules.misc;

import com.mojang.blaze3d.platform.InputConstants;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class InvMove extends Module implements TickListener {

    private final BooleanSetting jump = new BooleanSetting(
            EncryptedString.of("Jump"), true)
            .setDescription(EncryptedString.of("Allow jumping while in inventory"));

    private final BooleanSetting sprint = new BooleanSetting(
            EncryptedString.of("Sprint"), true)
            .setDescription(EncryptedString.of("Maintain sprint while holding forward in inventory"));

    public InvMove() {
        super(EncryptedString.of("Inv Move"),
                EncryptedString.of("Allows moving while inventory or containers are open"),
                -1, CategoryManager.AUTOMATION);
        addSettings(jump, sprint);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        // Release all keys when disabling to avoid stuck movement
        if (mc.player != null) {
            mc.options.keyUp.setDown(false);
            mc.options.keyDown.setDown(false);
            mc.options.keyLeft.setDown(false);
            mc.options.keyRight.setDown(false);
            mc.options.keyJump.setDown(false);
            mc.options.keyShift.setDown(false);
        }
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || !(mc.screen instanceof AbstractContainerScreen<?>)) return;

        var win = mc.getWindow();

        mc.options.keyUp.setDown(isDown(mc.options.keyUp, win));
        mc.options.keyDown.setDown(isDown(mc.options.keyDown, win));
        mc.options.keyLeft.setDown(isDown(mc.options.keyLeft, win));
        mc.options.keyRight.setDown(isDown(mc.options.keyRight, win));
        mc.options.keyShift.setDown(isDown(mc.options.keyShift, win));

        if (jump.getValue()) {
            mc.options.keyJump.setDown(isDown(mc.options.keyJump, win));
        }

        if (sprint.getValue() && isDown(mc.options.keyUp, win)) {
            mc.player.setSprinting(true);
        }
    }

    private static boolean isDown(KeyMapping kb, com.mojang.blaze3d.platform.Window win) {
        try {
            InputConstants.Key k = InputConstants.getKey(kb.saveString());
            return InputConstants.isKeyDown(win, k.getValue());
        } catch (Exception e) {
            return false;
        }
    }
}
