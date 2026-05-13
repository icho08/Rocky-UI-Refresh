package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class Sprint extends Module implements TickListener {
    public enum Mode { Forward, Omni }

    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Direction"), Mode.Forward, Mode.class)
            .setDescription(EncryptedString.of("Forward = vanilla-like, Omni = sprint in any direction"));

    private final BooleanSetting keepInAir = new BooleanSetting(EncryptedString.of("Keep In Air"), true)
            .setDescription(EncryptedString.of("Maintain sprint state while jumping/falling"));

    private final BooleanSetting keepOnSneak = new BooleanSetting(EncryptedString.of("Keep On Sneak"), false)
            .setDescription(EncryptedString.of("Don't release sprint when sneaking (looks suspicious — off by default)"));

    private final BooleanSetting stopOnHurt = new BooleanSetting(EncryptedString.of("Release On Hurt"), true)
            .setDescription(EncryptedString.of("Briefly stop sprinting when taking damage — feels more human"));

    private final NumberSetting hurtRelease = new NumberSetting(EncryptedString.of("Hurt Release Ticks"), 1, 20, 5, 1)
            .setDescription(EncryptedString.of("Ticks to suppress sprint after a hurt event"));

    private int hurtClock = 0;
    private int lastHurtTime = 0;

    public Sprint() {
        super(EncryptedString.of("Auto Sprint"),
                EncryptedString.of("Automatically sprints"), -1, CategoryManager.MOVEMENT);
        addSettings(mode, keepInAir, keepOnSneak, stopOnHurt, hurtRelease);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        hurtClock = 0;
        lastHurtTime = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.player.input == null) return;

        // Track hurt events to release sprint briefly
        if (stopOnHurt.getValue()) {
            int ht = mc.player.hurtTime;
            if (ht > lastHurtTime) {
                hurtClock = hurtRelease.getValueInt();
            }
            lastHurtTime = ht;
            if (hurtClock > 0) {
                hurtClock--;
                mc.player.setSprinting(false);
                return;
            }
        }

        if (!keepOnSneak.getValue() && mc.player.isSneaking()) {
            mc.player.setSprinting(false);
            return;
        }

        if (!keepInAir.getValue() && !mc.player.isOnGround()) return;

        boolean shouldSprint;
        if (mode.isMode(Mode.Omni)) {
            var in = mc.player.input;
            shouldSprint = in.playerInput.forward() || in.playerInput.backward() || in.playerInput.left() || in.playerInput.right();
        } else {
            shouldSprint = mc.player.input.playerInput.forward();
        }
        mc.player.setSprinting(shouldSprint);
    }
}
