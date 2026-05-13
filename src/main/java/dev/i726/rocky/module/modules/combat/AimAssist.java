package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.event.events.MouseMoveListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.*;
import dev.i726.rocky.utils.rotation.Rotation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public final class AimAssist extends Module implements HudListener, MouseMoveListener {
    private final BooleanSetting onlyWeapon = new BooleanSetting(EncryptedString.of("Only Weapon"), true);
    private final BooleanSetting onLeftClick = new BooleanSetting(EncryptedString.of("On Left Click"), true);
    private final ModeSetting<AimMode> aimAt = new ModeSetting<>(EncryptedString.of("Aim At"), AimMode.Head, AimMode.class);
    private final BooleanSetting stopAtTarget = new BooleanSetting(EncryptedString.of("Stop at Target"), true);

    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 1, 10, 4.5, 0.1);
    private final NumberSetting fov = new NumberSetting(EncryptedString.of("FOV"), 5, 180, 60, 1);

    private final MinMaxSetting speed = new MinMaxSetting(EncryptedString.of("Speed"), 0.1, 10, 0.1, 1.5, 3.5);
    private final NumberSetting acceleration = new NumberSetting(EncryptedString.of("Acceleration"), 0.1, 2.0, 1.0, 0.1);
    
    private final BooleanSetting jitterEnabled = new BooleanSetting(EncryptedString.of("Jitter"), false);
    private final NumberSetting jitterAmount = new NumberSetting(EncryptedString.of("Jitter Amount"), 0.1, 2.0, 0.5, 0.1);

    private final TimerUtils speedRerollTimer = new TimerUtils();
    private float currentSpeed;
    private float lerpFactor = 0;

    public enum AimMode {
        Head, Chest, Legs
    }

    public AimAssist() {
        super(EncryptedString.of("Aim Assist"),
                EncryptedString.of("Smoothly aims at nearby players"),
                -1,
                CategoryManager.PVP);

        addSettings(onlyWeapon, onLeftClick, aimAt, stopAtTarget, range, fov, speed, acceleration, jitterEnabled, jitterAmount);
    }

    @Override
    public void onEnable() {
        currentSpeed = speed.getRandomValueFloat();
        lerpFactor = 0;
        eventManager.add(HudListener.class, this);
        eventManager.add(MouseMoveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        eventManager.remove(MouseMoveListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null || mc.currentScreen != null)
            return;

        if (onlyWeapon.getValue() && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem()) || mc.player.getMainHandStack().getItem() instanceof AxeItem))
            return;

        if (onLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            lerpFactor = 0; // Reset acceleration
            return;
        }

        PlayerEntity target = WorldUtils.findNearestPlayer(mc.player, range.getValueFloat(), true, true);
        if (target == null || target.isDead() || target.isRemoved()) {
            lerpFactor = 0;
            return;
        }

        if (speedRerollTimer.delay(500)) {
            currentSpeed = speed.getRandomValueFloat();
            speedRerollTimer.reset();
        }

        Vec3d targetPos = target.getEyePos();
        double height = target.getEyeHeight(target.getPose());
        if (aimAt.isMode(AimMode.Chest)) targetPos = targetPos.add(0, -height * 0.4, 0);
        else if (aimAt.isMode(AimMode.Legs)) targetPos = targetPos.add(0, -height * 0.8, 0);

        Rotation rotation = RotationUtils.getDirection(mc.player.getEyePos(), targetPos);
        if (rotation == null) return;

        double angleToRotation = RotationUtils.getAngleToRotation(rotation);
        if (angleToRotation > (double) fov.getValueInt() / 2) {
            lerpFactor = 0;
            return;
        }

        // Check if already aiming at target
        if (stopAtTarget.getValue()) {
            EntityHitResult hitResult = WorldUtils.getHitResult(mc.player, false, mc.player.getYaw(), mc.player.getPitch(), range.getValue()) instanceof EntityHitResult result ? result : null;
            if (hitResult != null && hitResult.getEntity() == target) {
                lerpFactor = Math.max(0, lerpFactor - 0.05f); // Slowly decelerate
                return;
            }
        }

        // Acceleration logic for human-like movement
        float delta = (float) RenderUtils.deltaTime();
        lerpFactor = Math.min(1.0f, lerpFactor + delta * acceleration.getValueFloat() * 2.0f);
        
        float strength = (currentSpeed / 100.0f) * lerpFactor;
        
        float newYaw = lerp(strength, mc.player.getYaw(), (float) rotation.yaw());
        float newPitch = lerp(strength, mc.player.getPitch(), (float) rotation.pitch());

        if (jitterEnabled.getValue()) {
            float jitter = jitterAmount.getValueFloat();
            newYaw += (float) ((Math.random() - 0.5) * jitter);
            newPitch += (float) ((Math.random() - 0.5) * jitter);
        }

        mc.player.setYaw(newYaw);
        mc.player.setPitch(MathHelper.clamp(newPitch, -90, 90));
    }

    public float lerp(float delta, float start, float end) {
        return start + (MathHelper.wrapDegrees(end - start) * MathHelper.clamp(delta, 0, 1));
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
    }
}
