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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public final class AimAssist extends Module implements HudListener, MouseMoveListener {

    public enum TargetMode { Players, Mobs, All }
    public enum AimMode    { Head, Chest, Legs }

    private final ModeSetting<TargetMode> targets = new ModeSetting<>(EncryptedString.of("Targets"), TargetMode.Players, TargetMode.class)
            .setDescription(EncryptedString.of("Which entity types to aim at: Players only, Mobs only, or All living entities"));

    private final BooleanSetting onlyWeapon   = new BooleanSetting(EncryptedString.of("Only Weapon"), true)
            .setDescription(EncryptedString.of("Only aims when holding a sword or axe"));
    private final BooleanSetting onLeftClick  = new BooleanSetting(EncryptedString.of("On Left Click"), true)
            .setDescription(EncryptedString.of("Only aims while holding left mouse button"));
    private final ModeSetting<AimMode> aimAt  = new ModeSetting<>(EncryptedString.of("Aim At"), AimMode.Head, AimMode.class)
            .setDescription(EncryptedString.of("Which part of the hitbox to aim at"));
    private final BooleanSetting stopAtTarget = new BooleanSetting(EncryptedString.of("Stop at Target"), true)
            .setDescription(EncryptedString.of("Stops rotating once the crosshair is on the target"));
    private final BooleanSetting gcdCorrection = new BooleanSetting(EncryptedString.of("GCD Correction"), true)
            .setDescription(EncryptedString.of("Quantises rotation to match real mouse input — bypasses some anti-cheat detection"));
    private final BooleanSetting randomOffset  = new BooleanSetting(EncryptedString.of("Random Offset"), true)
            .setDescription(EncryptedString.of("Slightly randomises the aim point within the hitbox each frame"));

    private final BooleanSetting stickyAim   = new BooleanSetting(EncryptedString.of("Sticky Aim"), false)
            .setDescription(EncryptedString.of("Locks onto the first target — won't switch even if they leave FOV"));
    private final NumberSetting stickyRange  = new NumberSetting(EncryptedString.of("Sticky Range"), 1, 12, 6.0, 0.1)
            .setDescription(EncryptedString.of("Distance at which the sticky lock is released"));

    private final NumberSetting range         = new NumberSetting(EncryptedString.of("Range"), 1, 10, 4.5, 0.1)
            .setDescription(EncryptedString.of("Max distance to target an entity"));
    private final NumberSetting fov           = new NumberSetting(EncryptedString.of("FOV"), 5, 180, 60, 1)
            .setDescription(EncryptedString.of("Half-angle cone in which targets are considered"));
    private final MinMaxSetting speed         = new MinMaxSetting(EncryptedString.of("Speed"), 5, 180, 1, 40, 80)
            .setDescription(EncryptedString.of("Rotation speed in degrees per second (randomised per target)"));
    private final NumberSetting acceleration  = new NumberSetting(EncryptedString.of("Acceleration"), 0.1, 3.0, 1.5, 0.1)
            .setDescription(EncryptedString.of("How quickly the aim reaches full speed (higher = snappier)"));
    private final BooleanSetting jitterEnabled = new BooleanSetting(EncryptedString.of("Jitter"), false)
            .setDescription(EncryptedString.of("Adds random micro-shake to the aim path"));
    private final NumberSetting jitterAmount  = new NumberSetting(EncryptedString.of("Jitter Amount"), 0.05, 1.5, 0.3, 0.05);
    // NOTE: explicit packet sending removed — mc.player.setYaw/setPitch() is picked
    // up by the game's own ClientPlayerEntity.sendMovementPackets() every tick,
    // which sends exactly one PositionAndRotation at game-tick rate (20 hz).
    // Sending extra LookAndOnGround packets at render rate (60-144 hz) is what
    // was causing Grim/Vulcan flags — the AC saw dozens of rotation packets per
    // second instead of the expected ≤20.

    private final TimerUtils speedRerollTimer = new TimerUtils();
    private float currentSpeed;
    private float lerpFactor = 0;

    private float aimOffsetYaw   = 0f;
    private float aimOffsetPitch = 0f;

    private LivingEntity lockedTarget = null;

    public AimAssist() {
        super(EncryptedString.of("Aim Assist"),
                EncryptedString.of("Smoothly rotates toward the nearest entity within range"),
                -1, CategoryManager.PVP);
        addSettings(targets, onlyWeapon, onLeftClick, aimAt, stopAtTarget, stickyAim, stickyRange,
                gcdCorrection, randomOffset, range, fov, speed, acceleration,
                jitterEnabled, jitterAmount);
    }

    @Override
    public void onEnable() {
        currentSpeed   = speed.getRandomValueFloat();
        lerpFactor     = 0;
        aimOffsetYaw   = 0f;
        aimOffsetPitch = 0f;
        lockedTarget   = null;
        eventManager.add(HudListener.class, this);
        eventManager.add(MouseMoveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        lockedTarget = null;
        lerpFactor   = 0;
        eventManager.remove(HudListener.class, this);
        eventManager.remove(MouseMoveListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null || mc.screen != null) return;

        if (onlyWeapon.getValue() && !(WorldUtils.isSword(mc.player.getMainHandItem().getItem())
                || mc.player.getMainHandItem().getItem() instanceof AxeItem)) {
            lerpFactor = 0;
            return;
        }

        if (onLeftClick.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().handle(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            lerpFactor = Math.max(0, lerpFactor - 0.15f);
            if (!stickyAim.getValue()) lockedTarget = null;
            return;
        }

        LivingEntity target = resolveTarget();
        if (target == null || target.isDeadOrDying() || target.isRemoved()) {
            lerpFactor   = Math.max(0, lerpFactor - 0.1f);
            lockedTarget = null;
            return;
        }

        if (stickyAim.getValue() && lockedTarget == null) {
            lockedTarget = target;
        }

        // Re-roll speed every 500 ms for human-like variation
        if (speedRerollTimer.delay(500)) {
            currentSpeed = speed.getRandomValueFloat();
            speedRerollTimer.reset();
        }

        if (randomOffset.getValue()) {
            aimOffsetYaw   = aimOffsetYaw   * 0.85f + (float)(Math.random() - 0.5) * 0.06f;
            aimOffsetPitch = aimOffsetPitch * 0.85f + (float)(Math.random() - 0.5) * 0.06f;
        } else {
            aimOffsetYaw = 0; aimOffsetPitch = 0;
        }

        // Build aim position
        Vec3 targetPos = target.getEyePosition();
        double eyeH = target.getEyeHeight(target.getPose());
        if (aimAt.isMode(AimMode.Chest)) targetPos = targetPos.add(0, -eyeH * 0.4, 0);
        else if (aimAt.isMode(AimMode.Legs)) targetPos = targetPos.add(0, -eyeH * 0.8, 0);

        // Lightweight velocity prediction
        Vec3 vel = target.getDeltaMovement();
        targetPos = targetPos.add(vel.x * 0.4, vel.y * 0.2, vel.z * 0.4);

        Rotation rotation = RotationUtils.getDirection(mc.player.getEyePosition(), targetPos);
        if (rotation == null) return;

        double angleToTarget = RotationUtils.getAngleToRotation(rotation);

        if (!stickyAim.getValue() && angleToTarget > (double) fov.getValueInt() / 2.0) {
            lerpFactor = Math.max(0, lerpFactor - 0.05f);
            return;
        }

        // Stop rotating once crosshair is on the target.
        // Non-sticky: hard-return (no rotation at all).
        // Sticky: decay lerpFactor but continue — hard-return creates a start/stop
        // oscillation that manifests as visible screen shake.
        if (stopAtTarget.getValue()) {
            EntityHitResult hitResult = WorldUtils.getHitResult(mc.player, false,
                    mc.player.getYRot(), mc.player.getXRot(), range.getValue()) instanceof EntityHitResult r ? r : null;
            if (hitResult != null && hitResult.getEntity() == target) {
                lerpFactor = Math.max(0, lerpFactor - 0.10f);
                if (!stickyAim.getValue()) return;
                // Sticky: fall through with reduced lerpFactor (gentle hold, no oscillation)
            }
        }

        // ── Acceleration ────────────────────────────────────────────────────
        float delta = (float) RenderUtils.deltaTime();
        lerpFactor = Math.min(1.0f, lerpFactor + delta * acceleration.getValueFloat() * 8.0f);

        // ── Rotation step (degrees per second → per frame) ──────────────────
        float maxDegreesThisFrame = currentSpeed * delta * lerpFactor;

        // Sticky: when target is far outside FOV, slow rotation dramatically.
        // Without this the camera swings rapidly toward a target that may be
        // 90+ degrees away, which looks like severe shake.
        if (stickyAim.getValue() && lockedTarget != null && angleToTarget > fov.getValueInt()) {
            maxDegreesThisFrame *= 0.15f;
        }

        float targetYaw   = (float) rotation.yaw()   + aimOffsetYaw;
        float targetPitch = (float) rotation.pitch() + aimOffsetPitch;

        float rawYawDelta   = Mth.wrapDegrees(targetYaw   - mc.player.getYRot());
        float rawPitchDelta = Mth.wrapDegrees(targetPitch - mc.player.getXRot());

        float yawDelta   = Mth.clamp(rawYawDelta,   -maxDegreesThisFrame, maxDegreesThisFrame);
        float pitchDelta = Mth.clamp(rawPitchDelta, -maxDegreesThisFrame, maxDegreesThisFrame);

        // ── GCD correction ──────────────────────────────────────────────────
        if (gcdCorrection.getValue()) {
            float gcd = calcGcd();
            if (gcd > 0) {
                if (Math.abs(yawDelta) >= gcd) yawDelta   -= yawDelta   % gcd;
                if (Math.abs(pitchDelta) >= gcd) pitchDelta -= pitchDelta % gcd;
            }
        }

        if (jitterEnabled.getValue()) {
            float jitter = jitterAmount.getValueFloat();
            yawDelta   += (float)((Math.random() - 0.5) * jitter);
            pitchDelta += (float)((Math.random() - 0.5) * jitter);
        }

        float newYaw   = mc.player.getYRot()   + yawDelta;
        float newPitch = Mth.clamp(mc.player.getXRot() + pitchDelta, -90, 90);

        mc.player.setYRot(newYaw);
        mc.player.setXRot(newPitch);
        // No manual packet here — setting yaw/pitch is enough.
        // The game's sendMovementPackets() fires every tick and carries the
        // updated rotation in a PositionAndRotation packet at exactly 20 hz,
        // which is indistinguishable from real mouse input.
    }

    private LivingEntity resolveTarget() {
        if (stickyAim.getValue() && lockedTarget != null) {
            if (!lockedTarget.isAlive() || lockedTarget.isRemoved()
                    || mc.player.distanceTo(lockedTarget) > stickyRange.getValue()) {
                lockedTarget = null;
            } else {
                return lockedTarget;
            }
        }

        if (targets.isMode(TargetMode.Players)) {
            return WorldUtils.findNearestPlayer(mc.player, range.getValueFloat(), true, true);
        }

        // Mobs or All — iterate entities ourselves
        float maxDist = range.getValueFloat();
        float halfFov = (float) fov.getValueInt() / 2f;
        LivingEntity best = null;
        float bestDist = Float.MAX_VALUE;

        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == mc.player) continue;
            if (!le.isAlive() || le.isRemoved()) continue;

            // Filter by target mode
            if (targets.isMode(TargetMode.Mobs) && !(le instanceof Mob)) continue;
            // TargetMode.All passes through without extra filtering

            float dist = mc.player.distanceTo(le);
            if (dist > maxDist) continue;

            // FOV check
            Rotation rot = RotationUtils.getDirection(mc.player.getEyePosition(), le.getEyePosition());
            if (rot != null && RotationUtils.getAngleToRotation(rot) > halfFov) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = le;
            }
        }
        return best;
    }

    private float calcGcd() {
        double sens = mc.options.sensitivity().get();
        double f    = sens * 0.6 + 0.2;
        return (float)(f * f * f * 8.0);
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {}
}
