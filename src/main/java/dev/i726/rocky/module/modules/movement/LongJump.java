package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class LongJump extends Module implements TickListener {

    private final NumberSetting boost = new NumberSetting(
            EncryptedString.of("Boost"), 1.0, 5.0, 2.0, 0.1)
            .setDescription(EncryptedString.of("Horizontal velocity multiplier applied on jump"));

    private final NumberSetting heightBoost = new NumberSetting(
            EncryptedString.of("Height Boost"), 0.0, 1.0, 0.1, 0.05)
            .setDescription(EncryptedString.of("Extra upward velocity added on jump"));

    private final BooleanSetting onlyForward = new BooleanSetting(
            EncryptedString.of("Only Forward"), true)
            .setDescription(EncryptedString.of("Only boost when holding forward"));

    private final BooleanSetting requireSprint = new BooleanSetting(
            EncryptedString.of("Require Sprint"), true)
            .setDescription(EncryptedString.of("Only activate while sprinting"));

    private boolean wasOnGround = true;

    public LongJump() {
        super(EncryptedString.of("Long Jump"),
                EncryptedString.of("Boosts horizontal distance when jumping"),
                -1, CategoryManager.BLATANT);
        addSettings(boost, heightBoost, onlyForward, requireSprint);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        wasOnGround = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean onGround = mc.player.isOnGround();

        // Detect the jump frame: was on ground last tick, now airborne, going up
        if (wasOnGround && !onGround && mc.player.getVelocity().y > 0) {
            if (requireSprint.getValue() && !mc.player.isSprinting()) {
                wasOnGround = onGround;
                return;
            }
            if (onlyForward.getValue() && mc.player.input != null
                    && !mc.player.input.playerInput.forward()) {
                wasOnGround = onGround;
                return;
            }

            double yaw = Math.toRadians(mc.player.getYaw());
            double vx = mc.player.getVelocity().x;
            double vz = mc.player.getVelocity().z;

            // Scale horizontal component by boost multiplier
            double scale = boost.getValue();
            mc.player.addVelocity(
                    vx * (scale - 1.0),
                    heightBoost.getValue(),
                    vz * (scale - 1.0));
        }

        wasOnGround = onGround;
    }
}
