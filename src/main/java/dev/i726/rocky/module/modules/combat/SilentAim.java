package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * SilentAim — silently attacks the nearest player without moving the crosshair,
 * timed for critical hits to deal 150% bonus damage.
 *
 * How it works:
 *  1. Finds the nearest enemy player in range + FOV every game tick.
 *  2. When the CPS timer fires (and optionally the cooldown is full):
 *       a. Checks for a crit frame (falling, not on ground, not in fluid).
 *       b. Sends ONE silent LookAndOnGround packet toward the target with
 *          Gaussian yaw/pitch jitter — the server validates the hit against this.
 *       c. Calls attackEntity — hit registers because the server saw the rotation.
 *       d. The game's own next PositionAndRotation packet restores the real
 *          yaw/pitch naturally — no "snap-back" visible to the AC.
 *
 * Anti-cheat profile:
 *  - ONE rotation packet per attack, not a continuous stream → avoids the
 *    "always-tracking" signature that Grim/Vulcan flag.
 *  - Gaussian jitter (±0.5° yaw, ±0.4° pitch) — no dead-centre machine lock.
 *  - Randomised CPS → attack interval is never constant.
 *  - "Require Crit" naturally limits attack rate to ~jump cycle (~9 ticks),
 *    which is completely indistinguishable from a skilled human player.
 *  - No PacketSendListener, no packet interception, no continuous spoofing.
 */
public final class SilentAim extends Module implements TickListener {

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 6.0, 3.8, 0.1)
            .setDescription(EncryptedString.of("Max attack distance (blocks)"));

    private final NumberSetting fov = new NumberSetting(
            EncryptedString.of("FOV"), 5, 360, 180, 1)
            .setDescription(EncryptedString.of("Total angle cone (degrees) in which targets are considered"));

    private final MinMaxSetting cps = new MinMaxSetting(
            EncryptedString.of("CPS"), 1, 20, 1, 8, 12)
            .setDescription(EncryptedString.of("Clicks per second — randomised every attack"));

    private final BooleanSetting fullCooldown = new BooleanSetting(
            EncryptedString.of("Full Cooldown"), true)
            .setDescription(EncryptedString.of("Only attack when the sword cooldown meter is full (much less detectable)"));

    private final BooleanSetting requireCrit = new BooleanSetting(
            EncryptedString.of("Require Crit"), true)
            .setDescription(EncryptedString.of("Wait for a falling frame — every hit deals 150%% bonus crit damage"));

    private final BooleanSetting forceCrit = new BooleanSetting(
            EncryptedString.of("Force Crit"), false)
            .setDescription(EncryptedString.of("Auto-jumps to guarantee crits when not naturally falling (slightly more detectable)"));

    private final BooleanSetting friendCheck = new BooleanSetting(
            EncryptedString.of("Friend Check"), true)
            .setDescription(EncryptedString.of("Skip players on your friends list"));

    private final TimerUtils attackTimer = new TimerUtils();
    private final Random rng = new Random();

    private int currentDelay;

    // Force-crit state machine: 0=idle, 1=jumped-waiting-to-fall, 2=falling-ready
    private int forceCritState = 0;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"),
                EncryptedString.of("Silently attacks the nearest player and deals crit bonus damage"),
                -1, CategoryManager.PVP);
        addSettings(range, fov, cps, fullCooldown, requireCrit, forceCrit, friendCheck);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        rollDelay();
        forceCritState = 0;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        forceCritState = 0;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // ── Target selection ──────────────────────────────────────────────────
        LivingEntity target = findTarget();
        if (target == null) {
            forceCritState = 0;
            return;
        }

        // ── Gates ─────────────────────────────────────────────────────────────
        if (fullCooldown.getValue() && mc.player.getAttackCooldownProgress(0f) < 1f) return;
        if (!attackTimer.delay(currentDelay)) return;

        // ── Critical hit logic ────────────────────────────────────────────────
        if (requireCrit.getValue() || forceCrit.getValue()) {
            if (forceCrit.getValue()) {
                switch (forceCritState) {
                    case 0 -> {
                        // Idle — jump only when on ground
                        if (mc.player.isOnGround()) {
                            mc.player.jump();
                            forceCritState = 1;
                        }
                        return;
                    }
                    case 1 -> {
                        // Waiting to start falling
                        if (!mc.player.isOnGround() && mc.player.getVelocity().y <= 0) {
                            forceCritState = 2; // now descending — crit frame
                        } else {
                            return;
                        }
                    }
                    // case 2: falling — fall through to attack below
                }
            }

            // Require Crit gate (applies whether forceCrit is on or off)
            if (requireCrit.getValue() && !isCritFrame()) return;
        }

        forceCritState = 0;

        // ── Silent rotation — ONE packet per attack ───────────────────────────
        // Aim at the target's eye position with slight Gaussian jitter so the
        // angle is never machine-perfect. The server sees this and validates the
        // hit. The game's own next PositionAndRotation packet restores the real
        // yaw/pitch — the AC sees a brief natural look toward the target, then
        // smooth return. No snap-back, no continuous stream.
        float[] rot = calcRotation(target);
        rot[0] += (float) (rng.nextGaussian() * 0.5);
        rot[1] += (float) (rng.nextGaussian() * 0.4);
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.LookAndOnGround(
                        rot[0], rot[1],
                        mc.player.isOnGround(),
                        mc.player.horizontalCollision));

        // ── Attack ────────────────────────────────────────────────────────────
        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        rollDelay();
        attackTimer.reset();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Finds the closest alive enemy player within range and FOV cone.
     * The FOV cone is measured from the CLIENT yaw/pitch (what the player
     * is actually looking at), so target selection feels natural.
     */
    private LivingEntity findTarget() {
        double r = range.getValue();
        float halfFov = (float) fov.getValue() / 2f;

        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        for (net.minecraft.entity.Entity e : mc.world.getEntities()) {
            if (!(e instanceof PlayerEntity le)) continue;
            if (le == mc.player || !le.isAlive() || le.isRemoved()) continue;

            double dist = mc.player.distanceTo(le);
            if (dist > r) continue;

            if (friendCheck.getValue()
                    && Rocky.INSTANCE.getFriendManager().isFriend(le.getUuidAsString())) continue;

            // FOV check against client-visible rotation
            float[] rot = calcRotation(le);
            float yawDiff   = Math.abs(MathHelper.wrapDegrees(rot[0] - mc.player.getYaw()));
            float pitchDiff = Math.abs(rot[1] - mc.player.getPitch());
            if (yawDiff + pitchDiff > halfFov) continue;

            if (dist < bestDist) {
                bestDist = dist;
                best = le;
            }
        }
        return best;
    }

    /**
     * True when the player is in a valid Minecraft crit frame:
     *  - falling (velocity.y < 0)
     *  - not on ground
     *  - not in water / lava
     *  - not climbing
     *  - not riding a vehicle
     * This is identical to vanilla's own crit check so every attack here
     * produces a legitimate critical hit with full 150% damage.
     */
    private boolean isCritFrame() {
        return mc.player.getVelocity().y < 0
                && !mc.player.isOnGround()
                && !mc.player.isTouchingWater()
                && !mc.player.isInLava()
                && !mc.player.isClimbing()
                && mc.player.getVehicle() == null;
    }

    /**
     * Calculates [yaw, pitch] from the player's eye position to the target's
     * eye position.
     */
    private float[] calcRotation(LivingEntity target) {
        Vec3d eyes = mc.player.getEyePos();
        Vec3d tgt  = target.getEyePos();
        double dx     = tgt.x - eyes.x;
        double dy     = tgt.y - eyes.y;
        double dz     = tgt.z - eyes.z;
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist2d)));
        return new float[]{yaw, pitch};
    }

    /**
     * Picks a fresh random delay so the attack interval is never constant.
     */
    private void rollDelay() {
        int lo = Math.max(1, cps.getMinInt());
        int hi = Math.max(lo, cps.getMaxInt());
        int thisCps = lo + (int) (rng.nextDouble() * (hi - lo + 1));
        currentDelay = 1000 / Math.max(1, thisCps);
    }

    /** Used by KillAura / Strafe to share target info. */
    public LivingEntity getTarget() {
        return isEnabled() ? null : null; // SilentAim targets are per-tick; expose if needed
    }
}
