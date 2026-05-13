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
    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 3.0, 5.0, 3.2, 0.1);
    private final NumberSetting fov = new NumberSetting(EncryptedString.of("FOV"), 5, 360, 90, 1);
    private final NumberSetting smoothing = new NumberSetting(EncryptedString.of("Smoothing"), 0, 10, 2, 0.1)
            .setDescription(EncryptedString.of("Higher values make rotations slower and more legit-looking on the server"));
    private final BooleanSetting players = new BooleanSetting(EncryptedString.of("Players"), true);
    private final BooleanSetting mobs = new BooleanSetting(EncryptedString.of("Mobs"), false);
    private final BooleanSetting weaponOnly = new BooleanSetting(EncryptedString.of("Weapon Only"), true);
    private final BooleanSetting predict = new BooleanSetting(EncryptedString.of("Prediction"), true)
            .setDescription(EncryptedString.of("Predicts target movement for better accuracy"));
    private final BooleanSetting overrideCrosshair = new BooleanSetting(EncryptedString.of("Override Crosshair"), true)
            .setDescription(EncryptedString.of("Makes other modules see the target through your crosshair"));

    private Entity target;
    private Rotation targetRotation;
    private float serverYaw, serverPitch;
    private boolean rotating;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"),
                EncryptedString.of("Aims without rotating your view"),
                -1,
                CategoryManager.PVP);
        addSettings(range, fov, smoothing, players, mobs, weaponOnly, predict, overrideCrosshair);
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        target = null;
        targetRotation = null;
        rotating = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (weaponOnly.getValue() && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem()) || mc.player.getMainHandStack().getItem() instanceof AxeItem || mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            target = null;
            targetRotation = null;
            rotating = false;
            return;
        }

        target = null;
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
                Vec3d velocity = entity.getVelocity();
                targetPos = targetPos.add(velocity.x * 2.0, velocity.y * 2.0, velocity.z * 2.0);
            }

            Rotation rot = RotationUtils.getDirection(mc.player.getEyePos(), targetPos);
            double angle = RotationUtils.getAngleToRotation(rot);

            if (angle < (fov.getValue() / 2.0)) {
                if (angle < closestAngle) {
                    closestAngle = angle;
                    target = entity;
                    targetRotation = rot;
                }
            }
        }

        if (target != null && targetRotation != null) {
            rotating = true;
            float smooth = smoothing.getValueFloat();
            if (smooth <= 0) {
                serverYaw = (float) targetRotation.yaw();
                serverPitch = (float) targetRotation.pitch();
            } else {
                serverYaw = MathHelper.lerpAngleDegrees(1.0f / (smooth * 2.0f), serverYaw, (float) targetRotation.yaw());
                serverPitch = MathHelper.lerpAngleDegrees(1.0f / (smooth * 2.0f), serverPitch, (float) targetRotation.pitch());
            }

            if (overrideCrosshair.getValue()) {
                HitResult silentResult = WorldUtils.getHitResult(mc.player, false, serverYaw, serverPitch, range.getValue());
                if (silentResult != null && silentResult.getType() != HitResult.Type.MISS) {
                    mc.crosshairTarget = silentResult;
                }
            }
        } else {
            // Gradually return to player's look direction or just stop
            rotating = false;
            serverYaw = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
        }
    }

    public Entity getTarget() {
        return isEnabled() ? target : null;
    }

    public Rotation getRotation() {
        if (!isEnabled() || !rotating) return null;
        return new Rotation(serverYaw, serverPitch);
    }
}
