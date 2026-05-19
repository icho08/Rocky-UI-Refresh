package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RotationUtils;
import dev.i726.rocky.utils.WorldUtils;
import dev.i726.rocky.utils.rotation.Rotation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.MaceItem;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class SilentAim extends Module implements TickListener {

    private final NumberSetting range    = new NumberSetting(EncryptedString.of("Range"), 3.0, 6.0, 3.5, 0.1);
    private final NumberSetting fov      = new NumberSetting(EncryptedString.of("FOV"), 5, 360, 90, 1);
    private final NumberSetting smoothing = new NumberSetting(EncryptedString.of("Smoothing"), 0, 10, 3, 0.1)
            .setDescription(EncryptedString.of("Higher = slower server-side rotations"));

    /** Hard cap on degrees rotated per tick — prevents Grim rotation-speed flag. */
    private final NumberSetting maxRotSpeed = new NumberSetting(
            EncryptedString.of("Max Speed"), 5, 60, 28, 1)
            .setDescription(EncryptedString.of("Max degrees per tick sent to server (Grim flags >30 deg/tick)"));

    private final BooleanSetting players    = new BooleanSetting(EncryptedString.of("Players"), true);
    private final BooleanSetting mobs       = new BooleanSetting(EncryptedString.of("Mobs"), false);
    private final BooleanSetting weaponOnly = new BooleanSetting(EncryptedString.of("Weapon Only"), true);
    private final BooleanSetting predict    = new BooleanSetting(EncryptedString.of("Prediction"), true)
            .setDescription(EncryptedString.of("Leads the target by one tick of velocity"));
    private final BooleanSetting overrideCrosshair = new BooleanSetting(
            EncryptedString.of("Override Crosshair"), true);
    private final BooleanSetting gcdCorrection = new BooleanSetting(
            EncryptedString.of("GCD Correction"), true)
            .setDescription(EncryptedString.of("Snaps deltas to DPI-quantised steps (Grim bypass)"));
    private final BooleanSetting randomBodyPart = new BooleanSetting(
            EncryptedString.of("Random Body Part"), true)
            .setDescription(EncryptedString.of("Drifts aim point within hitbox — avoids dead-centre lock"));

    private Entity  target;
    private Rotation targetRotation;
    private float   serverYaw, serverPitch;
    private boolean rotating;

    private float aimOffsetYaw   = 0f;
    private float aimOffsetPitch = 0f;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"),
                EncryptedString.of("Silently rotates on the server without moving your view"),
                -1, CategoryManager.PVP);
        addSettings(range, fov, smoothing, maxRotSpeed, players, mobs, weaponOnly, predict,
                overrideCrosshair, gcdCorrection, randomBodyPart);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            serverYaw   = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
        aimOffsetYaw   = 0f;
        aimOffsetPitch = 0f;
        rotating       = false;
        target         = null;
        targetRotation = null;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        target         = null;
        targetRotation = null;
        rotating       = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (weaponOnly.getValue()
                && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem())
                || mc.player.getMainHandStack().getItem() instanceof AxeItem
                || mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            target = null; targetRotation = null; rotating = false;
            return;
        }

        target        = null;
        double closestAngle = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player || !entity.isAlive()) continue;
            if (entity instanceof PlayerEntity && !players.getValue()) continue;
            if (!(entity instanceof PlayerEntity) && !mobs.getValue()) continue;
            if (mc.player.distanceTo(entity) > range.getValue()) continue;

            Vec3d targetPos = entity.getEyePos();
            if (predict.getValue()) {
                Vec3d v = entity.getVelocity();
                targetPos = targetPos.add(v.x, v.y * 0.5, v.z);
            }

            Rotation rot   = RotationUtils.getDirection(mc.player.getEyePos(), targetPos);
            double   angle = RotationUtils.getAngleToRotation(rot);

            if (angle < (fov.getValue() / 2.0) && angle < closestAngle) {
                closestAngle   = angle;
                target         = entity;
                targetRotation = rot;
            }
        }

        if (target == null || targetRotation == null) {
            rotating    = false;
            serverYaw   = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
            return;
        }

        rotating = true;

        // Drift aim offset slowly — never lock dead-centre
        if (randomBodyPart.getValue()) {
            aimOffsetYaw   = aimOffsetYaw   * 0.8f + (float)(Math.random() - 0.5) * 0.06f;
            aimOffsetPitch = aimOffsetPitch * 0.8f + (float)(Math.random() - 0.5) * 0.06f;
        } else {
            aimOffsetYaw = 0; aimOffsetPitch = 0;
        }

        float targetYaw   = (float) targetRotation.yaw()   + aimOffsetYaw;
        float targetPitch = (float) targetRotation.pitch() + aimOffsetPitch;

        float smooth = smoothing.getValueFloat();
        float newYaw, newPitch;
        if (smooth <= 0) {
            newYaw   = targetYaw;
            newPitch = targetPitch;
        } else {
            float t = 1.0f / (smooth * 1.5f + 1.0f);
            newYaw   = MathHelper.lerpAngleDegrees(t, serverYaw, targetYaw);
            newPitch = serverPitch + (targetPitch - serverPitch) * t;
        }

        // ── Step 1: clamp per-tick rotation speed (Grim checks this) ──────────
        float maxSpeed = maxRotSpeed.getValueFloat();

        float yawDelta   = MathHelper.wrapDegrees(newYaw   - serverYaw);
        float pitchDelta = newPitch - serverPitch;

        if (Math.abs(yawDelta)   > maxSpeed) yawDelta   = Math.signum(yawDelta)   * maxSpeed;
        if (Math.abs(pitchDelta) > maxSpeed) pitchDelta = Math.signum(pitchDelta) * maxSpeed;

        newYaw   = serverYaw   + yawDelta;
        newPitch = serverPitch + pitchDelta;

        // ── Step 2: GCD correction — correct formula: (sens*0.6+0.2)^3 * 8 ───
        if (gcdCorrection.getValue()) {
            float gcd = calcGcd();
            if (gcd > 0) {
                // Re-compute deltas after speed clamp
                yawDelta   = MathHelper.wrapDegrees(newYaw   - mc.player.getYaw());
                pitchDelta = newPitch - mc.player.getPitch();

                // Round DOWN to nearest GCD multiple (floor toward zero)
                yawDelta   = (float)(Math.floor(yawDelta   / gcd) * gcd);
                pitchDelta = (float)(Math.floor(pitchDelta / gcd) * gcd);

                // If delta rounds to zero, don't rotate this tick — send nothing different
                // (avoids micro-rotations that can't be produced by a real mouse)
                if (Math.abs(yawDelta) < gcd * 0.5f && Math.abs(pitchDelta) < gcd * 0.5f) {
                    // Keep server rotation as-is this tick
                    newYaw   = serverYaw;
                    newPitch = serverPitch;
                } else {
                    newYaw   = mc.player.getYaw()   + yawDelta;
                    newPitch = mc.player.getPitch() + pitchDelta;
                }
            }
        }

        serverYaw   = newYaw;
        serverPitch = MathHelper.clamp(newPitch, -90f, 90f);

        if (overrideCrosshair.getValue()) {
            HitResult silentResult = WorldUtils.getHitResult(
                    mc.player, false, serverYaw, serverPitch, range.getValue());
            if (silentResult != null && silentResult.getType() != HitResult.Type.MISS) {
                mc.crosshairTarget = silentResult;
            }
        }
    }

    /**
     * Correct GCD formula matching Minecraft's Mouse.java:
     *   f = (sensitivity * 0.6 + 0.2)^3 * 8.0
     * This is the minimum yaw/pitch change produceable by a single mouse pixel.
     */
    private float calcGcd() {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float)(f * f * f * 8.0);
    }

    public Entity  getTarget()   { return isEnabled() ? target : null; }

    public Rotation getRotation() {
        if (!isEnabled() || !rotating) return null;
        return new Rotation(serverYaw, serverPitch);
    }
}
