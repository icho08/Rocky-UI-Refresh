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

    private final BooleanSetting onlyWeapon   = new BooleanSetting(EncryptedString.of("Only Weapon"), true);
    private final BooleanSetting onLeftClick   = new BooleanSetting(EncryptedString.of("On Left Click"), true);
    private final ModeSetting<AimMode> aimAt   = new ModeSetting<>(EncryptedString.of("Aim At"), AimMode.Head, AimMode.class);
    private final BooleanSetting stopAtTarget  = new BooleanSetting(EncryptedString.of("Stop at Target"), true);
    private final BooleanSetting gcdCorrection = new BooleanSetting(EncryptedString.of("GCD Correction"), true)
            .setDescription(EncryptedString.of("Makes rotation increments match real mouse input to bypass anticheat"));
    private final BooleanSetting randomOffset  = new BooleanSetting(EncryptedString.of("Random Offset"), true)
            .setDescription(EncryptedString.of("Slightly randomizes the aim point within the hitbox"));

    private final NumberSetting range          = new NumberSetting(EncryptedString.of("Range"), 1, 10, 4.5, 0.1);
    private final NumberSetting fov            = new NumberSetting(EncryptedString.of("FOV"), 5, 180, 60, 1);
    private final MinMaxSetting speed          = new MinMaxSetting(EncryptedString.of("Speed"), 0.1, 15, 0.1, 1.5, 4.5);
    private final NumberSetting acceleration   = new NumberSetting(EncryptedString.of("Acceleration"), 0.1, 3.0, 1.2, 0.1);
    private final BooleanSetting jitterEnabled = new BooleanSetting(EncryptedString.of("Jitter"), false);
    private final NumberSetting jitterAmount   = new NumberSetting(EncryptedString.of("Jitter Amount"), 0.05, 1.5, 0.3, 0.05);

    private final TimerUtils speedRerollTimer = new TimerUtils();
    private float currentSpeed;
    private float lerpFactor = 0;

    // Smoothed random aim-point offset — evolves slowly to avoid identical-frame patterns
    private float aimOffsetYaw   = 0f;
    private float aimOffsetPitch = 0f;

    public enum AimMode { Head, Chest, Legs }

    public AimAssist() {
        super(EncryptedString.of("Aim Assist"),
                EncryptedString.of("Smoothly aims at nearby players"),
                -1, CategoryManager.PVP);
        addSettings(onlyWeapon, onLeftClick, aimAt, stopAtTarget, gcdCorrection, randomOffset,
                range, fov, speed, acceleration, jitterEnabled, jitterAmount);
    }

    @Override
    public void onEnable() {
        currentSpeed = speed.getRandomValueFloat();
        lerpFactor = 0;
        aimOffsetYaw = 0f;
        aimOffsetPitch = 0f;
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
        if (mc.player == null || mc.currentScreen != null) return;

        if (onlyWeapon.getValue() && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem())
                || mc.player.getMainHandStack().getItem() instanceof AxeItem)) return;

        if (onLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            lerpFactor = 0;
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

        // Slowly drift aim offset so the aim point isn't pixel-perfect identical each frame
        if (randomOffset.getValue()) {
            aimOffsetYaw   = aimOffsetYaw   * 0.85f + (float)(Math.random() - 0.5) * 0.08f;
            aimOffsetPitch = aimOffsetPitch * 0.85f + (float)(Math.random() - 0.5) * 0.08f;
        } else {
            aimOffsetYaw = 0; aimOffsetPitch = 0;
        }

        Vec3d targetPos = target.getEyePos();
        double height   = target.getEyeHeight(target.getPose());
        if (aimAt.isMode(AimMode.Chest)) targetPos = targetPos.add(0, -height * 0.4, 0);
        else if (aimAt.isMode(AimMode.Legs)) targetPos = targetPos.add(0, -height * 0.8, 0);

        // Predict target movement slightly
        Vec3d vel = target.getVelocity();
        targetPos = targetPos.add(vel.x * 0.5, vel.y * 0.3, vel.z * 0.5);

        Rotation rotation = RotationUtils.getDirection(mc.player.getEyePos(), targetPos);
        if (rotation == null) return;

        double angleToRotation = RotationUtils.getAngleToRotation(rotation);
        if (angleToRotation > (double) fov.getValueInt() / 2) {
            lerpFactor = 0;
            return;
        }

        if (stopAtTarget.getValue()) {
            EntityHitResult hitResult = WorldUtils.getHitResult(mc.player, false,
                    mc.player.getYaw(), mc.player.getPitch(), range.getValue()) instanceof EntityHitResult r ? r : null;
            if (hitResult != null && hitResult.getEntity() == target) {
                lerpFactor = Math.max(0, lerpFactor - 0.05f);
                return;
            }
        }

        float delta = (float) RenderUtils.deltaTime();
        lerpFactor = Math.min(1.0f, lerpFactor + delta * acceleration.getValueFloat() * 2.0f);

        // Speed is now in degrees/tick equivalent — scale makes it feel responsive yet smooth
        float strength = (currentSpeed / 20.0f) * lerpFactor;

        float newYaw   = lerp(strength, mc.player.getYaw(),   (float) rotation.yaw()   + aimOffsetYaw);
        float newPitch = lerp(strength, mc.player.getPitch(), (float) rotation.pitch() + aimOffsetPitch);

        if (jitterEnabled.getValue()) {
            float jitter = jitterAmount.getValueFloat();
            newYaw   += (float)((Math.random() - 0.5) * jitter);
            newPitch += (float)((Math.random() - 0.5) * jitter);
        }

        // GCD correction — snap rotation deltas to mouse-DPI-quantised steps
        if (gcdCorrection.getValue()) {
            float gcd = calcGcd();
            if (gcd > 0) {
                float yawDelta   = MathHelper.wrapDegrees(newYaw - mc.player.getYaw());
                float pitchDelta = newPitch - mc.player.getPitch();
                yawDelta   -= yawDelta   % gcd;
                pitchDelta -= pitchDelta % gcd;
                newYaw   = mc.player.getYaw()   + yawDelta;
                newPitch = mc.player.getPitch() + pitchDelta;
            }
        }

        mc.player.setYaw(newYaw);
        mc.player.setPitch(MathHelper.clamp(newPitch, -90, 90));
    }

    /** GCD of rotation steps mirrors what real mouse input produces at this sensitivity. */
    private float calcGcd() {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float)(f * f * f * 1.2);
    }

    private float lerp(float delta, float start, float end) {
        return start + (MathHelper.wrapDegrees(end - start) * MathHelper.clamp(delta, 0, 1));
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
    }
}
