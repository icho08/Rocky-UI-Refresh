package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.PacketSendListener;
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
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;

public final class SilentAim extends Module implements TickListener, PacketSendListener {

    private final NumberSetting range    = new NumberSetting(EncryptedString.of("Range"), 3.0, 6.0, 3.5, 0.1);
    private final NumberSetting fov      = new NumberSetting(EncryptedString.of("FOV"), 5, 360, 90, 1);
    private final NumberSetting smoothing = new NumberSetting(EncryptedString.of("Smoothing"), 0, 10, 3, 0.1)
            .setDescription(EncryptedString.of("Higher = slower server-side rotations"));

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

    /** Guards against re-intercepting packets we are currently sending ourselves. */
    private volatile boolean bypassing = false;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"),
                EncryptedString.of("Silently rotates on the server without moving your view"),
                -1, CategoryManager.BLATANT);
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
        bypassing      = false;
        eventManager.add(TickListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        target         = null;
        targetRotation = null;
        rotating       = false;
        super.onDisable();
    }

    // ── Packet-level rotation injection ───────────────────────────────────────
    // Intercepts outgoing movement packets and swaps in the silent aim rotation.
    // This approach never touches mc.player.getYaw()/setPitch(), so Minecraft's
    // internal lastSentYaw/lastSentPitch tracking stays perfectly in sync with
    // the real camera — no more stuck/frozen camera bug.

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (bypassing) return;
        // Don't clobber Clutch's block-look rotation during a placement window
        if (dev.i726.rocky.module.modules.movement.Clutch.placing) return;
        if (!rotating) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket pkt)) return;

        try {
            boolean changesLook = readBool(pkt, "changesLook");
            if (!changesLook) return;

            PlayerMoveC2SPacket replacement = buildRotatedPacket(pkt);
            if (replacement == null) return;

            event.cancel();
            bypassing = true;
            try {
                event.connection.send(replacement);
            } finally {
                bypassing = false;
            }
        } catch (Exception ignored) {
            // Reflection failed — let the packet through unchanged
        }
    }

    private PlayerMoveC2SPacket buildRotatedPacket(PlayerMoveC2SPacket pkt) {
        try {
            boolean changesPosition = readBool(pkt, "changesPosition");
            boolean hCol            = readBool(pkt, "horizontalCollision");
            boolean onGround        = readBool(pkt, "onGround");

            if (changesPosition) {
                return new PlayerMoveC2SPacket.Full(
                        readDouble(pkt, "x"), readDouble(pkt, "y"), readDouble(pkt, "z"),
                        serverYaw, serverPitch,
                        onGround, hCol);
            } else {
                return new PlayerMoveC2SPacket.LookAndOnGround(
                        serverYaw, serverPitch,
                        onGround, hCol);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // ── Per-tick rotation tracking ────────────────────────────────────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;

        if (weaponOnly.getValue()
                && !(WorldUtils.isSword(mc.player.getMainHandStack().getItem())
                || mc.player.getMainHandStack().getItem() instanceof AxeItem
                || mc.player.getMainHandStack().getItem() instanceof MaceItem)) {
            target = null; targetRotation = null; rotating = false;
            // Sync server values to real rotation so next enable is clean
            serverYaw   = mc.player.getYaw();
            serverPitch = mc.player.getPitch();
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

        // Clamp per-tick rotation speed
        float maxSpeed = maxRotSpeed.getValueFloat();
        float yawDelta   = MathHelper.wrapDegrees(newYaw   - serverYaw);
        float pitchDelta = newPitch - serverPitch;
        if (Math.abs(yawDelta)   > maxSpeed) yawDelta   = Math.signum(yawDelta)   * maxSpeed;
        if (Math.abs(pitchDelta) > maxSpeed) pitchDelta = Math.signum(pitchDelta) * maxSpeed;
        newYaw   = serverYaw   + yawDelta;
        newPitch = serverPitch + pitchDelta;

        // GCD correction
        if (gcdCorrection.getValue()) {
            float gcd = calcGcd();
            if (gcd > 0) {
                yawDelta   = MathHelper.wrapDegrees(newYaw   - mc.player.getYaw());
                pitchDelta = newPitch - mc.player.getPitch();
                yawDelta   = (float)(Math.floor(yawDelta   / gcd) * gcd);
                pitchDelta = (float)(Math.floor(pitchDelta / gcd) * gcd);
                if (Math.abs(yawDelta) < gcd * 0.5f && Math.abs(pitchDelta) < gcd * 0.5f) {
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

    private float calcGcd() {
        double sens = mc.options.getMouseSensitivity().getValue();
        double f    = sens * 0.6 + 0.2;
        return (float)(f * f * f * 8.0);
    }

    public Entity  getTarget()   { return isEnabled() ? target : null; }

    /** @deprecated — rotation is now injected at the packet level, not via this method */
    public Rotation getRotation() { return null; }

    // ── Reflection helpers (same pattern as NoFall) ───────────────────────────

    private static double readDouble(Object obj, String name) throws Exception {
        return (double) field(obj, name).get(obj);
    }

    private static float readFloat(Object obj, String name) throws Exception {
        return (float) field(obj, name).get(obj);
    }

    private static boolean readBool(Object obj, String name) throws Exception {
        return (boolean) field(obj, name).get(obj);
    }

    private static Field field(Object obj, String name) throws Exception {
        Class<?> cls = obj.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found in " + obj.getClass());
    }
}
