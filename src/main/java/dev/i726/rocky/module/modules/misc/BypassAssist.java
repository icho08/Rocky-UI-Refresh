package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.combat.Criticals;
import dev.i726.rocky.module.modules.movement.NoFall;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Applies human-like imperfections and packet-level randomisation to
 * make flagging by movement-based anti-cheats harder.
 *
 * Strategies used:
 * 1. Motion Noise   — sends micro-offset position packets each tick so the
 *                     movement trace is not perfectly smooth (bots are).
 * 2. Flag Detect    — watches for server velocity corrections; on detection
 *                     temporarily suppresses NoFall & Criticals so the server
 *                     does not see suspicious packets piling up during a flag.
 */
public final class BypassAssist extends Module implements TickListener {

    public enum Strictness { Low, Medium, High }

    private final ModeSetting<Strictness> strictness = new ModeSetting<>(
            EncryptedString.of("Strictness"), Strictness.Medium, Strictness.class)
            .setDescription(EncryptedString.of("How strong the randomisation is"));

    private final BooleanSetting motionNoise = new BooleanSetting(EncryptedString.of("Motion Noise"), true)
            .setDescription(EncryptedString.of("Sends micro-offset position packets to break linear movement detection"));

    private final BooleanSetting flagDetect = new BooleanSetting(EncryptedString.of("Flag Detect"), true)
            .setDescription(EncryptedString.of("Detects server velocity corrections and pauses aggressive modules"));

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
    private Vec3d prevVelocity      = null;
    private double prevY            = Double.NaN;

    // Track which modules we suppressed so we can restore them after cooldown
    private boolean suppressedCriticals = false;
    private boolean suppressedNoFall    = false;

    private Criticals criticals;
    private NoFall    noFall;

    public BypassAssist() {
        super(EncryptedString.of("Bypass Assist"),
                EncryptedString.of("Human-like randomisation to reduce anticheat flags"),
                -1, CategoryManager.NETWORK);
        addSettings(strictness, motionNoise, flagDetect, flagCooldown, noiseInterval);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        flagTicks           = 0;
        noiseTick           = 0;
        prevVelocity        = null;
        prevY               = Double.NaN;
        suppressedCriticals = false;
        suppressedNoFall    = false;
        cacheModules();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        // Restore any suppressed modules when we turn off
        restoreSuppressedModules();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null) return;

        double currentY = mc.player.getY();

        // ── 1. Flag detection ─────────────────────────────────────────────────
        if (flagDetect.getValue()) {
            Vec3d vel = mc.player.getVelocity();
            if (prevVelocity != null) {
                double dh = Math.sqrt(
                        Math.pow(vel.x - prevVelocity.x, 2) +
                        Math.pow(vel.z - prevVelocity.z, 2));

                // Threshold raised to 0.4 to avoid triggering on normal step movement.
                // Also skip detection if the player is stepping upward (Y increased
                // by a step-like amount) — that is expected Step module behaviour,
                // not a server rubber-band.
                boolean steppingUp = !Double.isNaN(prevY) && (currentY - prevY) > 0.05 && (currentY - prevY) < 1.5;

                if (dh > 0.4 && !mc.player.isOnGround() && !steppingUp) {
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
                // Cooldown expired — restore any modules we suppressed
                restoreSuppressedModules();
            }
            return;
        }

        // ── 2. Motion noise (while moving on the ground) ──────────────────────
        if (motionNoise.getValue() && mc.player.isOnGround()) {
            noiseTick++;
            if (noiseTick >= noiseInterval.getValueInt()) {
                noiseTick = 0;
                sendNoisePacket();
            }
        }
    }

    // ── Flag response ─────────────────────────────────────────────────────────

    private void triggerFlagResponse() {
        flagTicks = flagCooldown.getValueInt();

        // Suppress (not permanently toggle off) — remember state so we can restore
        if (criticals != null && criticals.isEnabled()) {
            criticals.toggle();
            suppressedCriticals = true;
        }
        if (noFall != null && noFall.isEnabled()) {
            noFall.toggle();
            suppressedNoFall = true;
        }
    }

    private void restoreSuppressedModules() {
        if (suppressedCriticals && criticals != null && !criticals.isEnabled()) {
            criticals.toggle();
        }
        suppressedCriticals = false;

        if (suppressedNoFall && noFall != null && !noFall.isEnabled()) {
            noFall.toggle();
        }
        suppressedNoFall = false;
    }

    // ── Motion noise packet ───────────────────────────────────────────────────

    private void sendNoisePacket() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        // Only inject while walking/sprinting in a straight line
        if (mc.options.forwardKey.isPressed()) {
            double amp = noiseAmplitude();
            double nx  = (rng.nextDouble() - 0.5) * amp;
            double nz  = (rng.nextDouble() - 0.5) * amp;
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(
                            mc.player.getX() + nx,
                            mc.player.getY(),
                            mc.player.getZ() + nz,
                            mc.player.isOnGround(),
                            mc.player.horizontalCollision));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void cacheModules() {
        if (Rocky.INSTANCE == null || Rocky.INSTANCE.getModuleManager() == null) return;
        criticals = Rocky.INSTANCE.getModuleManager().getModule(Criticals.class);
        noFall    = Rocky.INSTANCE.getModuleManager().getModule(NoFall.class);
    }

    private double noiseAmplitude() {
        return switch (strictness.getMode()) {
            case Low    -> 0.0008;
            case Medium -> 0.0015;
            case High   -> 0.003;
        };
    }
}
