package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.PostAttackListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import java.util.Random;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * SilentAim — sends extra silent damage hits to whoever you are currently
 * attacking, without any visible cooldown flash or swing animation on your end.
 *
 * Behaviour:
 *  - Locks on to the player you just hit (via PostAttackListener).
 *  - Every CPS tick it sends ONE silent LookAndOnGround packet (so the server
 *    validates the hit direction) followed by a raw PlayerInteractEntityC2SPacket
 *    attack — the server registers the damage, but your client never sees a
 *    cooldown reset or arm swing.
 *  - Lock clears automatically when the target dies, walks out of range, or
 *    you stop clicking (On Click Only = on).
 *
 * Why this avoids bans:
 *  - No continuous rotation stream; one rotation packet per extra hit only.
 *  - Gaussian jitter on yaw/pitch — never a machine-perfect angle.
 *  - Randomised CPS so the interval is never constant.
 *  - No interactionManager.attackEntity() call — the client cooldown bar never
 *    resets, so there is no visual or packet anomaly on your own side.
 *  - Crit hits (optional): only fires on a natural falling frame → looks like
 *    a skilled player hitting crits in a combo.
 */
public final class SilentAim extends Module implements TickListener, PostAttackListener {

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 6.0, 4.0, 0.1)
            .setDescription(EncryptedString.of("Max distance at which the locked target is kept (blocks)"));

    private final MinMaxSetting cps = new MinMaxSetting(
            EncryptedString.of("CPS"), 1, 20, 1, 8, 12)
            .setDescription(EncryptedString.of("Extra silent hits per second — randomised every hit"));

    private final BooleanSetting requireCrit = new BooleanSetting(
            EncryptedString.of("Require Crit"), true)
            .setDescription(EncryptedString.of("Only send extra hits on a falling frame for 150%% crit bonus damage"));

    private final BooleanSetting forceCrit = new BooleanSetting(
            EncryptedString.of("Force Crit"), false)
            .setDescription(EncryptedString.of("Auto-jumps to guarantee a crit frame when standing (slightly more detectable)"));

    private final BooleanSetting onClickOnly = new BooleanSetting(
            EncryptedString.of("On Click Only"), true)
            .setDescription(EncryptedString.of("Only send extra hits while left mouse button is held"));

    private final BooleanSetting friendCheck = new BooleanSetting(
            EncryptedString.of("Friend Check"), true)
            .setDescription(EncryptedString.of("Skip players on your friends list"));

    private final TimerUtils hitTimer   = new TimerUtils();
    private final Random     rng        = new Random();
    private int              currentDelay;

    // The player that was last manually hit — we silently extend damage to them
    private LivingEntity lockedTarget = null;

    // Force-crit state: 0=idle, 1=jumped-ascending, 2=falling-ready
    private int forceCritState = 0;

    public SilentAim() {
        super(EncryptedString.of("Silent Aim"),
                EncryptedString.of("Sends silent extra damage hits to whoever you are attacking"),
                -1, CategoryManager.PVP);
        addSettings(range, cps, requireCrit, forceCrit, onClickOnly, friendCheck);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        eventManager.add(PostAttackListener.class, this);
        lockedTarget   = null;
        forceCritState = 0;
        rollDelay();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        eventManager.remove(PostAttackListener.class, this);
        lockedTarget   = null;
        forceCritState = 0;
        super.onDisable();
    }

    // ── PostAttackListener — lock onto whoever the player just hit ────────────

    @Override
    public void onPostAttack(PostAttackEvent event) {
        if (mc.player == null) return;
        Entity e = event.getTarget();
        if (!(e instanceof Player pe)) return;
        if (pe == mc.player) return;
        if (friendCheck.getValue()
                && Rocky.INSTANCE.getFriendManager().isFriend(pe.getStringUUID())) return;

        // Update the lock every time the user manually hits someone
        lockedTarget = pe;
        forceCritState = 0;
    }

    // ── TickListener — send silent extra hits to the locked target ────────────

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        // Validate the lock each tick
        if (lockedTarget == null) return;
        if (!lockedTarget.isAlive() || lockedTarget.isRemoved()) {
            clearLock(); return;
        }
        if (mc.player.distanceTo(lockedTarget) > range.getValue()) {
            clearLock(); return;
        }

        // On-click gate — clear lock when the player lifts the mouse button
        if (onClickOnly.getValue()) {
            boolean clicking = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    mc.getWindow().handle(),
                    org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                    == org.lwjgl.glfw.GLFW.GLFW_PRESS;
            if (!clicking) {
                clearLock(); return;
            }
        }

        // CPS timer gate
        if (!hitTimer.delay(currentDelay)) return;

        // ── Critical hit logic ────────────────────────────────────────────────
        if (requireCrit.getValue() || forceCrit.getValue()) {
            if (forceCrit.getValue()) {
                switch (forceCritState) {
                    case 0 -> {
                        if (mc.player.onGround()) {
                            mc.player.jumpFromGround();
                            forceCritState = 1;
                        }
                        return;
                    }
                    case 1 -> {
                        if (!mc.player.onGround() && mc.player.getDeltaMovement().y <= 0) {
                            forceCritState = 2;
                        } else {
                            return;
                        }
                    }
                    // case 2: descending — fall through
                }
            }
            if (requireCrit.getValue() && !isCritFrame()) return;
        }
        forceCritState = 0;

        // ── Silent rotation — ONE packet, aimed with jitter ───────────────────
        // Server needs to see us looking at the target for the hit to register.
        // We send exactly one LookAndOnGround per extra hit; the game's own next
        // PositionAndRotation packet carries our real yaw/pitch back naturally.
        float[] rot = calcRotation(lockedTarget);
        rot[0] += (float) (rng.nextGaussian() * 0.5);
        rot[1] += (float) (rng.nextGaussian() * 0.4);
        mc.getConnection().send(
                new ServerboundMovePlayerPacket.Rot(
                        rot[0], rot[1],
                        mc.player.onGround(),
                        mc.player.horizontalCollision));

        // ── Silent attack packet ──────────────────────────────────────────────
        // PlayerInteractEntityC2SPacket.attack() sends the damage to the server
        // WITHOUT resetting the client-side attack cooldown and WITHOUT playing
        // the swing arm animation — entirely invisible on your screen.
        mc.getConnection().send(
                ServerboundInteractPacket.createAttackPacket(lockedTarget, mc.player.isShiftKeyDown()));

        rollDelay();
        hitTimer.reset();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void clearLock() {
        lockedTarget   = null;
        forceCritState = 0;
    }

    /**
     * True when the player is in a natural Minecraft crit frame:
     * falling, not on ground, not in fluid, not climbing, not riding.
     */
    private boolean isCritFrame() {
        return mc.player.getDeltaMovement().y < 0
                && !mc.player.onGround()
                && !mc.player.isInWater()
                && !mc.player.isInLava()
                && !mc.player.onClimbable()
                && mc.player.getVehicle() == null;
    }

    /** Yaw/pitch from player eye to target eye. */
    private float[] calcRotation(LivingEntity target) {
        Vec3 eyes = mc.player.getEyePosition();
        Vec3 tgt  = target.getEyePosition();
        double dx     = tgt.x - eyes.x;
        double dy     = tgt.y - eyes.y;
        double dz     = tgt.z - eyes.z;
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist2d)));
        return new float[]{yaw, pitch};
    }

    /** Randomises attack interval so the timing is never constant. */
    private void rollDelay() {
        int lo = Math.max(1, cps.getMinInt());
        int hi = Math.max(lo, cps.getMaxInt());
        int thisCps = lo + (int) (rng.nextDouble() * (hi - lo + 1));
        currentDelay = 1000 / Math.max(1, thisCps);
    }
}
