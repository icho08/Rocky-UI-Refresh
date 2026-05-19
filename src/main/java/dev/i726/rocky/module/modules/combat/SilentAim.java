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
            .setDescription(EncryptedString.of("Higher = slower, more legit rotations on the server side"));
    private final BooleanSetting players = new BooleanSetting(EncryptedString.of("Players"), true);
    private final BooleanSetting mobs    = new BooleanSetting(EncryptedString.of("Mobs"), false);
    private final BooleanSetting weaponOnly = new BooleanSetting(EncryptedString.of("Weapon Only"), true);
    private final BooleanSetting predict    = new BooleanSetting(EncryptedString.of("Prediction"), true)
            .setDescription(EncryptedString.of("Leads the target by one tick of velocity"));
    private final BooleanSetting overrideCrosshair = new BooleanSetting(EncryptedString.of("Override Crosshair"), true)
            .setDescription(EncryptedString.of("Lets other modules (AutoCrystal, etc.) see through the silent crosshair"));
    private final BooleanSetting gcdCorrection = new BooleanSetting(EncryptedString.of("GCD Correction"), true)
            .setDescription(EncryptedString.of("Snaps rotation deltas to DPI-quantised steps so Grim can't distinguish them from real mouse"));
    private final BooleanSetting randomBodyPart = new BooleanSetting(EncryptedString.of("Random Body Part"), true)
            .setDescription(EncryptedString.of("Slightly randomises the aim point so it never locks dead-centre each tick"));

    private Entity target;
    private Rotation targetRotation;
    private float serverYaw, serverPitch;
    private boolean rotating;

    // Smoothly drifting aim offset — avoids constant dead-centre aim
    private float aimOffsetYaw   = 0f;
    private float aimOffsetPitch = 0f;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"),
                EncryptedString.of("Silently rotates on the server without moving your view"),
                -1, CategoryManager.PVP);
        addSettings(range, fov, smoothing, players, mobs, weaponOnly, predict,
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

        if (weaponOnly.getValue() && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem())
                || mc.player.getMainHandStack().getItem() instanceof AxeItem
                || mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            target         = null;
            targetRotation = null;
            rotating       = false;
            return;
        }

        target        = null;
        double closestAngle = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (entity == mc.player) continue;
            if (!entity.isAlive()) continue;
            if (entity instanceof PlayerEntity && !players.getValue()) continue;
            if (!(entity instanceof PlayerEntity) && !mobs.getValue()) continue;

            double dist = mc.player.distanceTo(entity);
            if (dist > range.getValue()) continue;

            Vec3d targetPos = entity.getEyePos();

            if (predict.getValue()) {
                // Lead target by exactly one tick — multiplier >1 causes overshoot
                Vec3d velocity = entity.getVelocity();
                targetPos = targetPos.add(velocity.x, velocity.y * 0.5, velocity.z);
            }

            Rotation rot   = RotationUtils.getDirection(mc.player.getEyePos(), targetPos);
            double   angle = RotationUtils.getAngleToRotation(rot);

            if (angle < (fov.getValue() / 2.0) && angle < closestAngle) {
                closestAngle   = angle;
                target         = entity;
                targetRotation = rot;
            }
        }

        if (target != null && targetRotation != null) {
            rotating = true;

            // Slowly drift aim point so we never lock 100 % dead-centre
            if (randomBodyPart.getValue()) {
                aimOffsetYaw   = aimOffsetYaw   * 0.8f + (float)(Math.random() - 0.5) * 0.06f;
                aimOffsetPitch = aimOffsetPitch * 0.8f + (float)(Math.random() - 0.5) * 0.06f;
            } else {
                aimOffsetYaw   = 0;
                aimOffsetPitch = 0;
            }

            float targetYaw   = (float) targetRotation.yaw()   + aimOffsetYaw;
            float targetPitch = (float) targetRotation.pitch() + aimOffsetPitch;

            float smooth = smoothing.getValueFloat();
            if (smooth <= 0) {
                serverYaw   = targetYaw;
                serverPitch = targetPitch;
            } else {
                // Lerp speed inversely proportional to smoothing
                float t = 1.0f / (smooth * 1.5f + 1.0f);
                serverYaw   = MathHelper.lerpAngleDegrees(t, serverYaw,   targetYaw);
                serverPitch = MathHelper.lerpAngleDegrees(t, serverPitch, targetPitch);
            }

            // GCD correction — makes the rotation delta pattern match real mouse DPI steps
            if (gcdCorrection.getValue()) {
                float gcd = calcGcd();
                if (gcd > 0) {
                    float yawDelta   = MathHelper.wrapDegrees(serverYaw   - mc.player.getYaw());
                    float pitchDelta = serverPitch - mc.player.getPitch();
                    yawDelta   -= yawDelta   % gcd;
                    pitchDelta -= pitchDelta % gcd;
                    serverYaw   = mc.player.getYaw()   + yawDelta;
                    serverPitch = mc.player.getPitch() + pitchDelta;
                }
            }

            serverPitch = MathHelper.clamp(serverPitch, -90f, 90f);

            if (overrideCrosshair.getValue()) {
                HitResult silentResult = WorldUtils.getHitResult(
                        mc.player, false, serverYaw, serverPitch, range.getValue());
                if (silentResult != null && silentResult.getType() != HitResult.Type.MISS) {
                    mc.crosshairTarget = silentResult;
                }
            }

        } else {
            rotating    = false;
            serverYaw   = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
    }

    private float calcGcd() {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float)(f * f * f * 1.2);
    }

    public Entity getTarget() {
        return isEnabled() ? target : null;
    }

    public Rotation getRotation() {
        if (!isEnabled() || !rotating) return null;
        return new Rotation(serverYaw, serverPitch);
    }
}
