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
 * 2. Packet Jitter  — introduces a 1-3 tick random window before NoFall /
 *                     Criticals packets are sent (ground-spoof timing masks).
 * 3. Flag Detect    — watches for server velocity corrections; on detection
 *                     temporarily disables NoFall & Criticals so the server
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
            .setDescription(EncryptedString.of("Ticks to pause modules after a flag is detected"));

    // Noise send interval: send a jitter packet every N ticks
    private final NumberSetting noiseInterval = new NumberSetting(
            EncryptedString.of("Noise Interval"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("Ticks between motion-noise packets (lower = more frequent)"));

    // ── Runtime state ─────────────────────────────────────────────────────────
    private final Random rng     = new Random();
    private int flagTicks        = 0;
    private int noiseTick        = 0;
    private Vec3d prevVelocity   = null;

    // Modules paused on flag detection
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
        flagTicks     = 0;
        noiseTick     = 0;
        prevVelocity  = null;
        cacheModules();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (mc.getNetworkHandler() == null) return;

        // ── 1. Flag detection ─────────────────────────────────────────────────
        if (flagDetect.getValue()) {
            Vec3d vel = mc.player.getVelocity();
            if (prevVelocity != null) {
                double dh = Math.sqrt(
                        Math.pow(vel.x - prevVelocity.x, 2) +
                        Math.pow(vel.z - prevVelocity.z, 2));
                // Large unexpected horizontal correction while airborne = flag
                if (dh > 0.25 && !mc.player.isOnGround()) {
                    triggerFlagResponse();
                }
            }
            prevVelocity = vel;
        }

        if (flagTicks > 0) {
            flagTicks--;
            return; // pause all bypass activity during cooldown
        }

        // ── 2. Motion noise ───────────────────────────────────────────────────
        if (motionNoise.getValue() && !mc.player.isOnGround() == false /* ground only */) {
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
        if (criticals != null && criticals.isEnabled()) criticals.toggle();
        if (noFall    != null && noFall.isEnabled())    noFall.toggle();
    }

    // ── Motion noise packet ───────────────────────────────────────────────────

    /**
     * Sends a tiny position packet with imperceptible randomisation.
     * This mimics the sub-millimetre jitter that real humans produce and
     * breaks the perfectly-straight movement signatures that bot detectors look for.
     */
    private void sendNoisePacket() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        // Only inject while walking — avoids noise during combat/elytra
        if (mc.player.isSprinting() && mc.options.forwardKey.isPressed()) {
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
