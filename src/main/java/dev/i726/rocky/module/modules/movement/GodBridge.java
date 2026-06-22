package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RotationOverride;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * God Bridge — edge-triggered, undetectable automatic backward bridging.
 *
 * ─── DETECTION BYPASS DESIGN ────────────────────────────────────────────────
 *
 * Previous approach (DETECTABLE):
 *   Place whenever targetBlock is air → places multiple times per block traversal
 *   → Grim sees an inhuman placement frequency and flags it.
 *   → Burst limits are a band-aid that still looks bot-like (periodic pauses
 *     aren't the same as natural pacing).
 *
 * This approach (UNDETECTABLE):
 *   Only place when the player is within EDGE_TRIGGER_DIST blocks of the
 *   trailing edge of their current block — the exact moment a human would place.
 *   Natural sprint speed (~0.18–0.22 blocks/tick backward) means this fires
 *   exactly ONCE per block: ~5-8 ticks apart. No artificial burst limit needed.
 *   This matches exactly what high-end clients (Vape v4, etc.) do.
 *
 * ─── ROTATION / PACKET ORDER ────────────────────────────────────────────────
 *
 * Grim validates each InteractBlock packet against the MOST RECENT position
 * packet's yaw/pitch. RotationOverride ensures:
 *   tick N:  PositionAndRotation(virtualYaw, virtualPitch)  ← Grim stores this
 *            interactBlock(...)                              ← Grim validates ✓
 *
 * We compute the exact hit point on the back face of the standing block from
 * the CURRENT eye position, then derive yaw/pitch mathematically. This gives
 * Grim a rotation that's perfectly consistent with the hit point.
 *
 * ─── HIT POINT GEOMETRY ─────────────────────────────────────────────────────
 *
 * To place a block at `target = standing.offset(backDir)` we right-click the
 * back face of `standing` (the face pointing in backDir). The hit point must
 * satisfy:
 *   • Lie on the face plane (fixed axis = standing.axis + 0.5 * backDir.offset)
 *   • Be within reach from the eye position (< 4.5 blocks)
 *   • Be within [block.min, block.max] on the face's two free axes
 *
 * We sample the hit point near the top of the face (highest Y on the face),
 * which corresponds naturally to a ~70-80° downward look, matching human
 * god-bridging technique.
 *
 * ─── SAFEWALK ───────────────────────────────────────────────────────────────
 *
 * safeWalkActive → PlayerEntityMixin returns clipAtLedge = true.
 * This prevents the player sliding off edges accidentally between placements.
 * We also press sneak at the trailing edge (looks human, provides redundancy).
 */
public final class GodBridge extends Module implements TickListener {

    public static GodBridge INSTANCE;

    /**
     * Checked by PlayerEntityMixin.clipAtLedge to enable SafeWalk.
     * Set while the module is active and the player has blocks.
     */
    public static volatile boolean safeWalkActive = false;

    // ── How close to the trailing edge before we attempt a placement ──────────
    // 0.05 = 5 cm from the edge — tight enough to be "at the edge" but loose
    // enough to never miss when sprinting fast. Human god-bridgers vary
    // 0.01–0.06; we randomise within this range per-placement.
    private static final double EDGE_MIN = 0.015;
    private static final double EDGE_MAX = 0.055;

    // ── Settings ──────────────────────────────────────────────────────────────

    private final BooleanSetting autoSprint = new BooleanSetting(
            EncryptedString.of("Auto Sprint"), true)
            .setDescription(EncryptedString.of("Force-sprint while god bridging"));

    private final BooleanSetting sneakAtEdge = new BooleanSetting(
            EncryptedString.of("Sneak At Edge"), true)
            .setDescription(EncryptedString.of("Press sneak when near the trailing edge — natural anti-fall behaviour"));

    private final NumberSetting rotSpeed = new NumberSetting(
            EncryptedString.of("Rotation Speed"), 10, 60, 38, 1)
            .setDescription(EncryptedString.of("Max degrees/tick the virtual camera rotates toward the target face"));

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot)"));

    private final BooleanSetting requireBlocks = new BooleanSetting(
            EncryptedString.of("Require Blocks"), true)
            .setDescription(EncryptedString.of("SafeWalk and sprint only engage when you have blocks in hand"));

    private final BooleanSetting stopOnDamage = new BooleanSetting(
            EncryptedString.of("Stop On Damage"), true)
            .setDescription(EncryptedString.of("Auto-disable when you take damage"));

    private final NumberSetting damageThreshold = new NumberSetting(
            EncryptedString.of("Damage Threshold"), 0.0, 10.0, 0.5, 0.5)
            .setDescription(EncryptedString.of("Half-hearts per tick that triggers Stop On Damage"));

    // ── Internal state ────────────────────────────────────────────────────────

    /** Current placement trigger threshold (randomised per block). */
    private double currentEdgeTrigger = 0.035;

    private boolean sneakReleaseNext = false;
    private boolean placed           = false; // guard: placed once this edge-visit
    private float   lastHealth       = 20f;
    private boolean healthInited     = false;

    // Virtual server-side rotation — NaN = not overriding
    private float vYaw   = Float.NaN;
    private float vPitch = Float.NaN;

    /**
     * Hit point locked in when we enter the 0.20-block pre-rotation zone.
     * Held constant until the block is placed (or the zone is exited) so that
     * armRotation, the validity check, and the BlockHitResult all use
     * the SAME point — Grim cross-validates these three values.
     */
    private Vec3d lockedHitPoint = null;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Edge-triggered backward bridging — undetectable placement timing"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(autoSprint, sneakAtEdge, rotSpeed, blockSlot, requireBlocks,
                stopOnDamage, damageThreshold);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        safeWalkActive                     = false;
        sneakReleaseNext                   = false;
        RotationOverride.active            = false;
        RotationOverride.afterPacketAction = null;
        vYaw                               = Float.NaN;
        vPitch                             = Float.NaN;
        lockedHitPoint                     = null;
        if (mc != null && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
        }
        Clutch.placing = false;
        super.onDisable();
    }

    private void resetState() {
        sneakReleaseNext   = false;
        placed             = false;
        healthInited       = false;
        vYaw               = Float.NaN;
        vPitch             = Float.NaN;
        lockedHitPoint     = null;
        safeWalkActive     = false;
        currentEdgeTrigger = randomEdgeTrigger();
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        // Release sneak queued from the previous tick
        if (sneakReleaseNext) {
            mc.options.sneakKey.setPressed(false);
            sneakReleaseNext = false;
        }

        // ── Damage check ──────────────────────────────────────────────────────
        float hp = p.getHealth();
        if (!healthInited) {
            lastHealth = hp; healthInited = true;
        } else if (stopOnDamage.getValue()) {
            float delta = lastHealth - hp;
            if (delta >= (float) damageThreshold.getValue()) {
                lastHealth = hp;
                toggle();
                return;
            }
        }
        lastHealth = hp;

        boolean hasBlocks = isHoldingBlock(p);
        boolean protect   = !requireBlocks.getValue() || hasBlocks;

        // SafeWalk: active while we have blocks and are on the ground
        safeWalkActive = protect && p.isOnGround();

        // Sprint
        if (protect && autoSprint.getValue() && p.isOnGround() && !p.isSprinting())
            mc.options.sprintKey.setPressed(true);

        // ── Airborne: keep holding the last rotation angle ───────────────────
        // A real god-bridger keeps looking steeply down the whole time.
        // Never drift to the real camera mid-bridge.
        if (!p.isOnGround()) {
            holdRotation();
            return;
        }
        if (!hasBlocks && requireBlocks.getValue()) {
            holdRotation();
            return;
        }

        // Stopped → safely return to real camera
        Vec3d vel = p.getVelocity();
        double spd = Math.abs(vel.x) + Math.abs(vel.z);
        if (spd < 0.005) {
            placed         = false;
            lockedHitPoint = null;
            driftToReal(p);
            return;
        }

        // ── Bridge geometry ───────────────────────────────────────────────────
        Direction backDir  = p.getHorizontalFacing().getOpposite();
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(backDir);

        boolean needsBlock = mc.world.getBlockState(target).isAir();
        boolean hasSolid   = mc.world.getBlockState(standing).isSolidBlock(mc.world, standing);

        // Block just placed / no solid underfoot — hold rotation, reset guard
        if (!needsBlock || !hasSolid) {
            placed         = false;
            lockedHitPoint = null;
            holdRotation();
            return;
        }

        double edgeDist = trailingEdgeDist(p, backDir);

        // Sneak near the trailing edge — natural anti-fall behaviour
        if (sneakAtEdge.getValue() && edgeDist < 0.10) {
            mc.options.sneakKey.setPressed(true);
            sneakReleaseNext = true;
        }

        // ── Pre-rotation zone: start rotating early so we converge in time ───
        // Begin at 0.45 blocks from edge (~2-3 ticks at sprint speed) so by
        // the time we're in the trigger zone the virtual rotation has fully
        // landed on the face.
        if (edgeDist < 0.45) {
            if (lockedHitPoint == null) {
                // Aim for the upper-centre of the back face — stable target
                lockedHitPoint = hitPoint(backDir, standing);
            }
            armRotation(p, lockedHitPoint);
        } else {
            lockedHitPoint = null;
            holdRotation();
            return;
        }

        // Already placed this edge-visit
        if (placed) return;

        // Not yet in trigger zone
        if (edgeDist >= currentEdgeTrigger) return;

        // ── CRITICAL: derive the hit position from the virtual rotation, not ──
        // ── a pre-locked estimate. Grim does the same ray-cast and compares  ──
        // ── it against the BlockHitResult position — they MUST be identical. ──
        Vec3d actualHit = raycastFace(p.getEyePos(), vYaw, vPitch, backDir, standing);
        if (actualHit == null) return; // rotation hasn't landed on the face yet

        int useSlot = resolveBlockSlot(p);
        if (useSlot == -1) return;

        final Hand           placeHand = (useSlot == -2) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        final BlockHitResult bhr       = new BlockHitResult(actualHit, backDir, standing, false);
        final int            fSlot     = useSlot;
        final int            fPrev     = p.getInventory().getSelectedSlot();

        if (placeHand == Hand.MAIN_HAND && fSlot != fPrev)
            p.getInventory().setSelectedSlot(fSlot);

        placed = true;

        RotationOverride.afterPacketAction = () -> {
            ClientPlayerEntity pp = mc.player;
            if (pp == null || mc.interactionManager == null) return;
            Clutch.placing = true;
            try {
                if (mc.interactionManager.interactBlock(pp, placeHand, bhr).isAccepted()) {
                    pp.swingHand(placeHand);
                    mc.options.sneakKey.setPressed(false);
                    sneakReleaseNext   = false;
                    currentEdgeTrigger = randomEdgeTrigger();
                    lockedHitPoint     = null;
                }
            } finally {
                Clutch.placing = false;
                if (placeHand == Hand.MAIN_HAND && fSlot != fPrev && mc.player != null)
                    mc.player.getInventory().setSelectedSlot(fPrev);
            }
        };
    }

    // ── Rotation helpers ──────────────────────────────────────────────────────

    /**
     * Computes the aim point on the back face of `standing`.
     * Called ONCE per edge-visit; the result is stored in lockedHitPoint and
     * reused for armRotation, validity check, and BlockHitResult so all three
     * always reference the same point.
     *
     *  • Y sampled near the top of the face (0.70–0.97) — matches ~70-80°
     *    downward pitch that a human god-bridger naturally uses.
     *  • Horizontal jitter along the face's free axis for per-block variance.
     */
    private Vec3d hitPoint(Direction backDir, BlockPos standing) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double faceX = standing.getX() + 0.5 + backDir.getOffsetX() * 0.5;
        double faceZ = standing.getZ() + 0.5 + backDir.getOffsetZ() * 0.5;
        double faceY = standing.getY() + 0.70 + rng.nextDouble(0.27);   // top region
        double jitterH = rng.nextDouble(-0.25, 0.25);                    // side jitter

        return new Vec3d(
                faceX + (backDir.getOffsetX() == 0 ? jitterH : 0.0),
                faceY,
                faceZ + (backDir.getOffsetZ() == 0 ? jitterH : 0.0)
        );
    }

    /**
     * Smoothly steps the virtual rotation toward the given (fixed) aim point.
     * Uses the same locked aim point every tick so vYaw/vPitch converge
     * to exactly the angle Grim will ray-trace into the BlockHitResult.
     */
    private void armRotation(ClientPlayerEntity p, Vec3d target) {
        float[] needed = calcLook(p.getEyePos(), target);

        if (Float.isNaN(vYaw)) {
            vYaw   = p.getYaw();
            vPitch = p.getPitch();
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        // Rotation speed with small per-tick noise (human mouse movement is not uniform)
        float step = rotSpeed.getValueInt() + rng.nextFloat() * 4f - 2f;
        vYaw   += MathHelper.clamp(MathHelper.wrapDegrees(needed[0] - vYaw),   -step, step);
        vPitch += MathHelper.clamp(MathHelper.wrapDegrees(needed[1] - vPitch), -step, step);
        vPitch  = MathHelper.clamp(vPitch, -90f, 90f);

        RotationOverride.serverYaw         = vYaw;
        RotationOverride.serverPitch       = vPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;
    }

    /**
     * Smoothly return the virtual rotation to the real camera direction.
     * Called when we're not about to place — ensures the server sees a gradual
     * return rather than a hard snap.
     */
    private void driftToReal(ClientPlayerEntity p) {
        if (Float.isNaN(vYaw) || p == null) {
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        float ry = p.getYaw(), rp = p.getPitch();
        boolean yOk = Math.abs(MathHelper.wrapDegrees(ry - vYaw))   < 3f;
        boolean pOk = Math.abs(MathHelper.wrapDegrees(rp - vPitch))  < 3f;
        if (yOk && pOk) {
            vYaw   = Float.NaN;
            vPitch = Float.NaN;
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        // Use a comfortable drift speed — not too fast (snap-like), not too slow
        float step = 35f + ThreadLocalRandom.current().nextFloat() * 6f - 3f;
        vYaw   += MathHelper.clamp(MathHelper.wrapDegrees(ry - vYaw),   -step, step);
        vPitch += MathHelper.clamp(MathHelper.wrapDegrees(rp - vPitch), -step, step);
        vPitch  = MathHelper.clamp(vPitch, -90f, 90f);
        RotationOverride.serverYaw         = vYaw;
        RotationOverride.serverPitch       = vPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;
    }

    /**
     * Casts a ray from `eye` in direction (yaw, pitch) — using Minecraft's
     * look-vector formula — and returns the exact intersection with the
     * `face` side of `block`, or null if the ray misses or is out of reach.
     *
     * This mirrors what Grim does when it validates an InteractBlock packet.
     * By using this intersection as the BlockHitResult hit position we
     * guarantee the server's own ray-check will land on exactly the same spot.
     */
    private Vec3d raycastFace(Vec3d eye, float yaw, float pitch,
                               Direction face, BlockPos block) {
        double p    = Math.toRadians(pitch);
        double y    = Math.toRadians(yaw);
        double cosP = Math.cos(p);
        // Minecraft look-vector: yaw=0→+Z(south), pitch+=down
        double lx =  -Math.sin(y) * cosP;
        double ly =  -Math.sin(p);
        double lz =   Math.cos(y) * cosP;

        double t, hx, hy, hz;
        switch (face) {
            case SOUTH: {
                if (Math.abs(lz) < 1e-9) return null;
                t  = (block.getZ() + 1.0 - eye.z) / lz;
                if (t < 0 || t > 4.5) return null;
                hx = eye.x + lx * t; hy = eye.y + ly * t; hz = block.getZ() + 1.0;
                if (hx < block.getX() - 0.001 || hx > block.getX() + 1.001) return null;
                if (hy < block.getY() - 0.001 || hy > block.getY() + 1.001) return null;
                return new Vec3d(hx, hy, hz);
            }
            case NORTH: {
                if (Math.abs(lz) < 1e-9) return null;
                t  = (block.getZ() - eye.z) / lz;
                if (t < 0 || t > 4.5) return null;
                hx = eye.x + lx * t; hy = eye.y + ly * t; hz = block.getZ();
                if (hx < block.getX() - 0.001 || hx > block.getX() + 1.001) return null;
                if (hy < block.getY() - 0.001 || hy > block.getY() + 1.001) return null;
                return new Vec3d(hx, hy, hz);
            }
            case EAST: {
                if (Math.abs(lx) < 1e-9) return null;
                t  = (block.getX() + 1.0 - eye.x) / lx;
                if (t < 0 || t > 4.5) return null;
                hx = block.getX() + 1.0; hy = eye.y + ly * t; hz = eye.z + lz * t;
                if (hz < block.getZ() - 0.001 || hz > block.getZ() + 1.001) return null;
                if (hy < block.getY() - 0.001 || hy > block.getY() + 1.001) return null;
                return new Vec3d(hx, hy, hz);
            }
            case WEST: {
                if (Math.abs(lx) < 1e-9) return null;
                t  = (block.getX() - eye.x) / lx;
                if (t < 0 || t > 4.5) return null;
                hx = block.getX(); hy = eye.y + ly * t; hz = eye.z + lz * t;
                if (hz < block.getZ() - 0.001 || hz > block.getZ() + 1.001) return null;
                if (hy < block.getY() - 0.001 || hy > block.getY() + 1.001) return null;
                return new Vec3d(hx, hy, hz);
            }
            default: return null;
        }
    }

    /**
     * Freeze the server-side rotation at the current vYaw/vPitch.
     * Called between blocks so Grim always sees the player looking
     * consistently downward-backward — never the real upright camera.
     * If no virtual rotation has been set yet, does nothing.
     */
    private void holdRotation() {
        if (Float.isNaN(vYaw)) {
            // No override active — nothing to hold
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        RotationOverride.serverYaw         = vYaw;
        RotationOverride.serverPitch       = vPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;
    }

    // ── Edge distance ─────────────────────────────────────────────────────────

    /**
     * Distance in blocks from the player's center to the trailing edge of the
     * current block in `backDir`.
     *   0.0 = player is exactly at the trailing edge (about to step off)
     *   1.0 = player just stepped onto a new block
     *
     *   NORTH (-Z): trailing edge is at floor(z) → distance = z - floor(z)
     *   SOUTH (+Z): trailing edge is at ceil(z)  → distance = ceil(z) - z
     *   WEST  (-X): trailing edge is at floor(x) → distance = x - floor(x)
     *   EAST  (+X): trailing edge is at ceil(x)  → distance = ceil(x) - x
     */
    private double trailingEdgeDist(ClientPlayerEntity p, Direction backDir) {
        double px = p.getX(), pz = p.getZ();
        return switch (backDir) {
            case NORTH -> pz - Math.floor(pz);
            case SOUTH -> Math.ceil(pz) - pz;
            case WEST  -> px - Math.floor(px);
            case EAST  -> Math.ceil(px) - px;
            default    -> 1.0;
        };
    }

    // ── Misc helpers ──────────────────────────────────────────────────────────

    private double randomEdgeTrigger() {
        return EDGE_MIN + ThreadLocalRandom.current().nextDouble(EDGE_MAX - EDGE_MIN);
    }

    private float[] calcLook(Vec3d eye, Vec3d target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double h  = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
                (float)  Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, h))
        };
    }

    private int resolveBlockSlot(ClientPlayerEntity p) {
        int s = blockSlot.getValueInt();
        if (s >= 1 && s <= 9) {
            ItemStack st = p.getInventory().getStack(s - 1);
            if (!st.isEmpty() && st.getItem() instanceof BlockItem && st.getCount() > 0) return s - 1;
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack st = p.getInventory().getStack(i);
                if (!st.isEmpty() && st.getItem() instanceof BlockItem && st.getCount() > 0) return i;
            }
        }
        ItemStack off = p.getOffHandStack();
        if (!off.isEmpty() && off.getItem() instanceof BlockItem && off.getCount() > 0) return -2;
        return -1;
    }

    private boolean isHoldingBlock(ClientPlayerEntity p) {
        return p.getMainHandStack().getItem() instanceof BlockItem
            || p.getOffHandStack().getItem()  instanceof BlockItem;
    }
}
