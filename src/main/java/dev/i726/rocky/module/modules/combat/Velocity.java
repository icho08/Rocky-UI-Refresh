package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.world.phys.Vec3;

public final class Velocity extends Module implements TickListener {

    public enum VelocityMode {
        /**
         * Reduce — shrinks knockback by percentage instantly, but leaves a small remainder
         * so position packets still show movement. Best for NCP / AAC.
         */
        Reduce,
        /**
         * Gradual — spreads the cancellation over several ticks, matching what a laggy
         * client would look like. Harder to flag on Grim / Vulcan.
         */
        Gradual,
        /**
         * Cancel — zeroes knockback entirely. Detectable on Grim but fine for rage.
         */
        Cancel
    }

    private final ModeSetting<VelocityMode> velocityMode = new ModeSetting<>(
            EncryptedString.of("Mode"), VelocityMode.Gradual, VelocityMode.class)
            .setDescription(EncryptedString.of("Reduce = % reduction | Gradual = multi-tick bypass | Cancel = full zero"));

    private final NumberSetting horizontal = new NumberSetting(
            EncryptedString.of("Horizontal"), 0, 100, 20, 1)
            .setDescription(EncryptedString.of("Remaining knockback % (0 = none, 100 = full vanilla)"));

    private final NumberSetting vertical = new NumberSetting(
            EncryptedString.of("Vertical"), 0, 100, 40, 1)
            .setDescription(EncryptedString.of("Remaining vertical knockback %"));

    private final NumberSetting gradualTicks = new NumberSetting(
            EncryptedString.of("Gradual Ticks"), 1, 8, 4, 1)
            .setDescription(EncryptedString.of("Over how many ticks to bleed off the knockback (Gradual mode)"));

    private final BooleanSetting randomize = new BooleanSetting(
            EncryptedString.of("Randomize"), true)
            .setDescription(EncryptedString.of("Add slight randomness to each reduction so the pattern is not constant"));

    // State for Gradual mode
    private int hitTicks           = 0;
    private int gradualTicksLeft   = 0;
    private Vec3 savedVelocity    = Vec3.ZERO;

    public Velocity() {
        super(EncryptedString.of("Anti Knockback"),
                EncryptedString.of("Reduces or cancels knockback with anticheat bypass"),
                -1, CategoryManager.PVP);
        addSettings(velocityMode, horizontal, vertical, gradualTicks, randomize);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        hitTicks         = 0;
        gradualTicksLeft = 0;
        savedVelocity    = Vec3.ZERO;
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

        switch (velocityMode.getMode()) {
            case Reduce -> handleReduce();
            case Gradual -> handleGradual();
            case Cancel -> handleCancel();
        }

        if (mc.player.hurtTime == 0) {
            hitTicks = 0;
        }
    }

    private void handleReduce() {
        if (mc.player.hurtTime > 0 && hitTicks == 0) {
            hitTicks = mc.player.hurtTime;

            double h = (horizontal.getValue() / 100.0) + (randomize.getValue() ? (Math.random() - 0.5) * 0.04 : 0);
            double v = (vertical.getValue()   / 100.0) + (randomize.getValue() ? (Math.random() - 0.5) * 0.04 : 0);

            h = Math.max(0, Math.min(1, h));
            v = Math.max(0, Math.min(1, v));

            Vec3 vel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(vel.x * h, vel.y * v, vel.z * h);
        }
    }

    private void handleGradual() {
        if (mc.player.hurtTime > 0 && hitTicks == 0) {
            hitTicks         = mc.player.hurtTime;
            gradualTicksLeft = gradualTicks.getValueInt();
            savedVelocity    = mc.player.getDeltaMovement();
        }

        if (gradualTicksLeft > 0) {
            gradualTicksLeft--;
            // Each tick we bleed off a fraction of the saved excess
            double h    = (horizontal.getValue() / 100.0);
            double v    = (vertical.getValue()   / 100.0);
            double prog = 1.0 - (double) gradualTicksLeft / gradualTicks.getValueInt();
            Vec3  vel  = mc.player.getDeltaMovement();

            double rx = randomize.getValue() ? 1 + (Math.random() - 0.5) * 0.05 : 1;
            double rz = randomize.getValue() ? 1 + (Math.random() - 0.5) * 0.05 : 1;

            // Lerp velocity toward (h * savedVelocity) over gradualTicks
            double targetX = savedVelocity.x * h;
            double targetZ = savedVelocity.z * h;
            double targetY = savedVelocity.y * v;

            mc.player.setDeltaMovement(
                lerp(prog, vel.x, targetX) * rx,
                lerp(prog, vel.y, targetY),
                lerp(prog, vel.z, targetZ) * rz
            );
        }
    }

    private void handleCancel() {
        if (mc.player.hurtTime > 0 && hitTicks == 0) {
            hitTicks = mc.player.hurtTime;
            Vec3 vel = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(0, vel.y * 0.05, 0);
        }
    }

    private double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }
}
