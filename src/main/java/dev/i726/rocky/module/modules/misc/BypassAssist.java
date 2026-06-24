package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.combat.AimAssist;
import dev.i726.rocky.module.modules.combat.Criticals;
import dev.i726.rocky.module.modules.combat.Reach;
import dev.i726.rocky.module.modules.combat.Velocity;
import dev.i726.rocky.module.modules.movement.NoFall;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import java.util.Random;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

/**
 * Applies human-like imperfections and packet-level randomisation to
 * make flagging by movement-based anti-cheats harder.
 *
 * Strategies used:
 * 1. Motion Noise      — sends micro-offset position packets each tick so the
 *                        movement trace is not perfectly smooth (bots are).
 * 2. Rotation Noise    — jitters the sent yaw/pitch by a tiny sub-GCD amount
 *                        to mimic natural hand tremor.
 * 3. Flag Detect       — watches for server velocity corrections; on detection
 *                        temporarily suppresses NoFall, Criticals, Velocity,
 *                        Reach, and AimAssist so the server does not see
 *                        suspicious packets piling up during a flag.
 * 4. Packet Jitter     — randomly skips one noise packet per interval so the
 *                        send pattern is not perfectly periodic.
 */
public final class BypassAssist extends Module implements TickListener {

    public enum Strictness { Low, Medium, High }

    private final ModeSetting<Strictness> strictness = new ModeSetting<>(
            EncryptedString.of("Strictness"), Strictness.Medium, Strictness.class)
            .setDescription(EncryptedString.of("How strong the randomisation is"));

    private final BooleanSetting motionNoise = new BooleanSetting(EncryptedString.of("Motion Noise"), true)
            .setDescription(EncryptedString.of("Sends micro-offset position packets to break linear movement detection"));

    private final BooleanSetting rotationNoise = new BooleanSetting(EncryptedString.of("Rotation Noise"), true)
            .setDescription(EncryptedString.of("Adds tiny sub-GCD jitter to sent rotations to mimic hand tremor"));

    private final BooleanSetting flagDetect = new BooleanSetting(EncryptedString.of("Flag Detect"), true)
            .setDescription(EncryptedString.of("Detects server velocity corrections and pauses aggressive modules"));

    private final BooleanSetting packetJitter = new BooleanSetting(EncryptedString.of("Packet Jitter"), true)
            .setDescription(EncryptedString.of("Randomly skips noise packets so the send pattern is not perfectly periodic"));

    private final NumberSetting flagCooldown = new NumberSetting(
            EncryptedString.of("Flag Cooldown"), 20, 200, 60, 5)
            .setDescription(EncryptedString.of("Ticks to suppress modules after a flag is detected"));

    private final NumberSetting noiseInterval = new NumberSetting(
            EncryptedString.of("Noise Interval"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("Ticks between motion-noise packets (lower = more frequent)"));

    // ── Runtime state ─────────────────────────────────────────────────────────
    private final Random rng        = new Random();
    private int  flagTicks          = 0;
    private int  noiseTick          = 0;
    private Vec3 prevVelocity      = null;
    private double prevY            = Double.NaN;

    // Smoothed rotation offset for rotation noise
    private float rotNoiseYaw   = 0f;
    private float rotNoisePitch = 0f;

    // Track which modules we suppressed so we can restore them after cooldown
    private boolean suppressedCriticals = false;
    private boolean suppressedNoFall    = false;
    private boolean suppressedVelocity  = false;
    private boolean suppressedReach     = false;
    private boolean suppressedAimAssist = false;

    private Criticals criticals;
    private NoFall    noFall;
    private Velocity  velocity;
    private Reach     reach;
    private AimAssist aimAssist;

    public BypassAssist() {
        super(EncryptedString.of("Bypass Assist"),
                EncryptedString.of("Human-like randomisation to reduce anticheat flags"),
                -1, CategoryManager.NETWORK);
        addSettings(strictness, motionNoise, rotationNoise, flagDetect, packetJitter, flagCooldown, noiseInterval);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        flagTicks           = 0;
        noiseTick           = 0;
        prevVelocity        = null;
        prevY               = Double.NaN;
        rotNoiseYaw         = 0f;
        rotNoisePitch       = 0f;
        suppressedCriticals = false;
        suppressedNoFall    = false;
        suppressedVelocity  = false;
        suppressedReach     = false;
        suppressedAimAssist = false;
        cacheModules();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        restoreSuppressedModules();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (mc.getConnection() == null) return;

        double currentY = mc.player.getY();

        // ── 1. Flag detection ─────────────────────────────────────────────────
        if (flagDetect.getValue()) {
            Vec3 vel = mc.player.getDeltaMovement();
            if (prevVelocity != null) {
                double dh = Math.sqrt(
                        Math.pow(vel.x - prevVelocity.x, 2) +
                        Math.pow(vel.z - prevVelocity.z, 2));

                boolean steppingUp = !Double.isNaN(prevY) && (currentY - prevY) > 0.05 && (currentY - prevY) < 1.5;

                if (dh > 0.4 && !mc.player.onGround() && !steppingUp) {
                    triggerFlagResponse();
                }
            }
            prevVelocity = vel;
        }
        prevY = currentY;

        // ── Cooldown tick ─────────────────────────────────────────────────────
        if (flagTicks > 0) {
            flagTicks--;
            if (flagTicks == 0) {
                restoreSuppressedModules();
            }
            return;
        }

        // ── 2. Rotation noise — applied every tick while moving ───────────────
        if (rotationNoise.getValue() && mc.player.onGround() && mc.options.keyUp.isDown()) {
            double amp = noiseAmplitude() * 0.5;
            rotNoiseYaw   = rotNoiseYaw   * 0.6f + (float)((rng.nextDouble() - 0.5) * amp);
            rotNoisePitch = rotNoisePitch * 0.6f + (float)((rng.nextDouble() - 0.5) * amp * 0.5);

            float newYaw   = mc.player.getYRot()   + rotNoiseYaw;
            float newPitch = net.minecraft.util.Mth.clamp(
                    mc.player.getXRot() + rotNoisePitch, -90f, 90f);

            mc.getConnection().send(
                    new net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot(
                            mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                            newYaw, newPitch,
                            mc.player.onGround(), mc.player.horizontalCollision));
        }

        // ── 3. Motion noise (while moving on the ground) ──────────────────────
        if (motionNoise.getValue() && mc.player.onGround()) {
            noiseTick++;
            if (noiseTick >= noiseInterval.getValueInt()) {
                noiseTick = 0;
                // Packet jitter: randomly skip ~20% of packets for irregular timing
                if (!packetJitter.getValue() || rng.nextDouble() > 0.2) {
                    sendNoisePacket();
                }
            }
        }
    }

    // ── Flag response ─────────────────────────────────────────────────────────

    private void triggerFlagResponse() {
        flagTicks = flagCooldown.getValueInt();

        if (criticals != null && criticals.isEnabled()) {
            criticals.toggle();
            suppressedCriticals = true;
        }
        if (noFall != null && noFall.isEnabled()) {
            noFall.toggle();
            suppressedNoFall = true;
        }
        if (velocity != null && velocity.isEnabled()) {
            velocity.toggle();
            suppressedVelocity = true;
        }
        if (reach != null && reach.isEnabled()) {
            reach.toggle();
            suppressedReach = true;
        }
        if (aimAssist != null && aimAssist.isEnabled()) {
            aimAssist.toggle();
            suppressedAimAssist = true;
        }
    }

    private void restoreSuppressedModules() {
        if (suppressedCriticals && criticals != null && !criticals.isEnabled()) criticals.toggle();
        suppressedCriticals = false;

        if (suppressedNoFall && noFall != null && !noFall.isEnabled()) noFall.toggle();
        suppressedNoFall = false;

        if (suppressedVelocity && velocity != null && !velocity.isEnabled()) velocity.toggle();
        suppressedVelocity = false;

        if (suppressedReach && reach != null && !reach.isEnabled()) reach.toggle();
        suppressedReach = false;

        if (suppressedAimAssist && aimAssist != null && !aimAssist.isEnabled()) aimAssist.toggle();
        suppressedAimAssist = false;
    }

    // ── Motion noise packet ───────────────────────────────────────────────────

    private void sendNoisePacket() {
        if (mc.player == null || mc.getConnection() == null) return;
        if (mc.options.keyUp.isDown()) {
            double amp = noiseAmplitude();
            double nx  = (rng.nextDouble() - 0.5) * amp;
            double nz  = (rng.nextDouble() - 0.5) * amp;
            mc.getConnection().send(
                    new ServerboundMovePlayerPacket.Pos(
                            mc.player.getX() + nx,
                            mc.player.getY(),
                            mc.player.getZ() + nz,
                            mc.player.onGround(),
                            mc.player.horizontalCollision));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cacheModules() {
        if (Rocky.INSTANCE == null || Rocky.INSTANCE.getModuleManager() == null) return;
        criticals  = Rocky.INSTANCE.getModuleManager().getModule(Criticals.class);
        noFall     = Rocky.INSTANCE.getModuleManager().getModule(NoFall.class);
        velocity   = Rocky.INSTANCE.getModuleManager().getModule(Velocity.class);
        reach      = Rocky.INSTANCE.getModuleManager().getModule(Reach.class);
        aimAssist  = Rocky.INSTANCE.getModuleManager().getModule(AimAssist.class);
    }

    private double noiseAmplitude() {
        return switch (strictness.getMode()) {
            case Low    -> 0.0008;
            case Medium -> 0.0015;
            case High   -> 0.003;
        };
    }
}
