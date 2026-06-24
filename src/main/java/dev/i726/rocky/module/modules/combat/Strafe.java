package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public final class Strafe extends Module implements TickListener {

    public enum StrafeMode { Auto, Clockwise, CounterClockwise }

    private final ModeSetting<StrafeMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), StrafeMode.Auto, StrafeMode.class)
            .setDescription(EncryptedString.of("Auto = chooses best side, Clockwise/CounterClockwise = fixed"));

    private final NumberSetting speed = new NumberSetting(
            EncryptedString.of("Speed"), 0.1, 1.5, 0.5, 0.05)
            .setDescription(EncryptedString.of("Strafe velocity added per tick"));

    private final NumberSetting radius = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 8.0, 4.0, 0.5)
            .setDescription(EncryptedString.of("Only strafe when a target is within this range"));

    private final BooleanSetting onlyAttacking = new BooleanSetting(
            EncryptedString.of("Only Attacking"), true)
            .setDescription(EncryptedString.of("Only strafe when Kill Aura or Aim Assist has a target"));

    private boolean clockwise = true;

    public Strafe() {
        super(EncryptedString.of("Strafe"),
                EncryptedString.of("Automatically strafes around your target during combat"),
                -1, CategoryManager.BLATANT);
        addSettings(mode, speed, radius, onlyAttacking);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        clockwise = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        LivingEntity target = getNearestPlayer();
        if (target == null) return;
        if (target.distanceTo(mc.player) > radius.getValue()) return;

        if (onlyAttacking.getValue()) {
            KillAura ka = dev.i726.rocky.Rocky.INSTANCE.getModuleManager().getModule(KillAura.class);
            AimAssist aa = dev.i726.rocky.Rocky.INSTANCE.getModuleManager().getModule(AimAssist.class);
            boolean hasKATarget = ka != null && ka.isEnabled() && ka.getCurrentTarget() != null;
            boolean hasAATarget = aa != null && aa.isEnabled();
            if (!hasKATarget && !hasAATarget) return;
        }

        // Determine strafe direction
        boolean cw = switch (mode.getMode()) {
            case Clockwise        -> true;
            case CounterClockwise -> false;
            case Auto             -> {
                // Flip direction occasionally to make it less predictable
                if (Math.random() < 0.003) clockwise = !clockwise;
                yield clockwise;
            }
        };

        // Build perpendicular velocity vector relative to look direction
        double yawRad = Math.toRadians(mc.player.getYRot() + (cw ? 90.0 : -90.0));
        double vx = -Math.sin(yawRad) * speed.getValue();
        double vz =  Math.cos(yawRad) * speed.getValue();

        mc.player.push(vx, 0, vz);
    }

    private LivingEntity getNearestPlayer() {
        LivingEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player p)) continue;
            if (p == mc.player) continue;
            double dist = p.distanceTo(mc.player);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }
}
