package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

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
            mc.options.forwardKey.setPressed(false);
            mc.options.backKey.setPressed(false);
            mc.options.leftKey.setPressed(false);
            mc.options.rightKey.setPressed(false);
            mc.options.jumpKey.setPressed(false);
            mc.options.sneakKey.setPressed(false);
        }
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || !(mc.currentScreen instanceof HandledScreen<?>)) return;

        var win = mc.getWindow();

        mc.options.forwardKey.setPressed(isDown(mc.options.forwardKey, win));
        mc.options.backKey.setPressed(isDown(mc.options.backKey, win));
        mc.options.leftKey.setPressed(isDown(mc.options.leftKey, win));
        mc.options.rightKey.setPressed(isDown(mc.options.rightKey, win));
        mc.options.sneakKey.setPressed(isDown(mc.options.sneakKey, win));

        if (jump.getValue()) {
            mc.options.jumpKey.setPressed(isDown(mc.options.jumpKey, win));
        }

        if (sprint.getValue() && isDown(mc.options.forwardKey, win)) {
            mc.player.setSprinting(true);
        }
    }

    private static boolean isDown(KeyBinding kb, net.minecraft.client.util.Window win) {
        try {
            InputUtil.Key k = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
            return InputUtil.isKeyPressed(win, k.getCode());
        } catch (Exception e) {
            return false;
        }
    }
}
