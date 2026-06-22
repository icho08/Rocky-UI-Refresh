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
 * God Bridge — automatic backward sprint-bridging with silent rotation and
 * Grim/NCP detection bypass.
 *
 * ── HOW GOD BRIDGING WORKS ─────────────────────────────────────────────────
 *
 * A human god-bridger:
 *   1. Holds W (forward) toward open air while actually facing away from it,
 *      OR holds S (backward) in the direction they came from.
 *      This mod uses the backward key approach (most common).
 *   2. Looks steeply downward (pitch ≈ 55-85°) so the crosshair hits
 *      the SIDE face of the block currently underfoot.
 *   3. Right-clicks that face → Minecraft places a new block one step
 *      behind the standing block, extending the bridge backward.
 *   4. Steps off the edge onto the new block and repeats.
 *
 * ── HOW THIS MODULE WORKS ──────────────────────────────────────────────────
 *
 *  Silent rotation (RotationOverride):
 *    Every tick we compute the yaw/pitch needed to hit the back face of the
 *    standing block. We store that in RotationOverride so the POSITION packet
 *    Minecraft sends carries the virtual rotation. Grim validates each
 *    InteractBlock against the rotation in the last position packet, so the
 *    block is placed AFTER the rotation is already on the wire.
 *    The camera never visually snaps — the client sees the real view.
 *
 *  Block target:
 *    standing = BlockPos(floor(x), floor(y) - 1, floor(z))   ← block underfoot
 *    backDir  = player.getHorizontalFacing().getOpposite()    ← movement direction
 *    target   = standing.offset(backDir)                      ← block to place
 *    We right-click the face of `standing` that points toward `backDir`.
 *    Placement succeeds → a new block appears at `target`.
 *
 *  Anti-detection:
 *    • Burst limit + random pause breaks the "machine-perfect infinite streak"
 *      Grim flags after ~5 consecutive placements.
 *    • Sneak-sync fires a 1-tick sneak near the edge to break the
 *      "never-sneak + perfect place" bot signature.
 *    • Pitch and hit-point are jittered per-tick within human ranges.
 *    • Rotation returns to real camera smoothly (no snap-back).
 *
 *  SafeWalk:
 *    safeWalkActive = true while on ground with blocks → PlayerEntityMixin
 *    returns true from clipAtLedge so the player does not slide off edges
 *    accidentally.
 */
public final class GodBridge extends Module implements TickListener {

    public static GodBridge INSTANCE;

    /**
     * Checked by PlayerEntityMixin.clipAtLedge to enable SafeWalk.
     * True while the module is active, on the ground, and has blocks.
     */
    public static volatile boolean safeWalkActive = false;

    // ── Settings ──────────────────────────────────────────────────────────────

    private final BooleanSetting autoSprint = new BooleanSetting(
            EncryptedString.of("Auto Sprint"), true)
            .setDescription(EncryptedString.of("Force-sprint while god bridging"));

    private final NumberSetting burstLimit = new NumberSetting(
            EncryptedString.of("Burst Limit"), 2, 12, 5, 1)
            .setDescription(EncryptedString.of("Max consecutive placements before a randomised pause"));

    private final NumberSetting burstPauseMin = new NumberSetting(
            EncryptedString.of("Burst Pause Min"), 2, 15, 4, 1)
            .setDescription(EncryptedString.of("Min pause ticks after hitting the burst limit"));

    private final NumberSetting burstPauseMax = new NumberSetting(
            EncryptedString.of("Burst Pause Max"), 3, 20, 9, 1)
            .setDescription(EncryptedString.of("Max pause ticks after hitting the burst limit"));

    private final BooleanSetting sneakSync = new BooleanSetting(
            EncryptedString.of("Sneak Sync"), true)
            .setDescription(EncryptedString.of("Send a 1-tick sneak near edges to break the never-sneak bot pattern"));

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

    private int     consecutivePlacements = 0;
    private int     burstPauseCooldown    = 0;
    private int     placeCooldown         = 0;
    private boolean sneakReleaseNext      = false;
    private float   lastHealth            = 20f;
    private boolean healthInitialized     = false;

    // Virtual (server-side) rotation — NaN means not overriding
    private float virtualYaw   = Float.NaN;
    private float virtualPitch = Float.NaN;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Auto backward bridging with silent rotation and anti-detection"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(
                autoSprint,
                burstLimit, burstPauseMin, burstPauseMax,
                sneakSync,
                blockSlot, requireBlocks,
                stopOnDamage, damageThreshold
        );
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
        virtualYaw                         = Float.NaN;
        virtualPitch                       = Float.NaN;
        if (mc != null && mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            mc.options.sprintKey.setPressed(false);
        }
        Clutch.placing = false;
        super.onDisable();
    }

    private void resetState() {
        consecutivePlacements = 0;
        burstPauseCooldown    = 0;
        placeCooldown         = 0;
        sneakReleaseNext      = false;
        healthInitialized     = false;
        virtualYaw            = Float.NaN;
        virtualPitch          = Float.NaN;
        safeWalkActive        = false;
    }

    // ── Tick ─────────────────────────────────────────────────────────────────

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        // Release sneak-sync queued from the previous tick
        if (sneakReleaseNext) {
            mc.options.sneakKey.setPressed(false);
            sneakReleaseNext = false;
        }

        // ── Damage check ──────────────────────────────────────────────────────
        float health = p.getHealth();
        if (!healthInitialized) {
            lastHealth        = health;
            healthInitialized = true;
        } else if (stopOnDamage.getValue()) {
            float delta = lastHealth - health;
            if (delta >= (float) damageThreshold.getValue()) {
                lastHealth = health;
                toggle();
                return;
            }
        }
        lastHealth = health;

        // ── Cooldowns ─────────────────────────────────────────────────────────
        if (placeCooldown > 0)      placeCooldown--;
        if (burstPauseCooldown > 0) burstPauseCooldown--;

        // ── Block availability ────────────────────────────────────────────────
        boolean hasBlocks = isHoldingBlock(p);
        boolean protect   = !requireBlocks.getValue() || hasBlocks;

        // SafeWalk: on while we're grounded and bridging
        safeWalkActive = protect && p.isOnGround();

        // Sprint
        if (protect && autoSprint.getValue() && p.isOnGround() && !p.isSprinting()) {
            mc.options.sprintKey.setPressed(true);
        }

        // ── Early exits ───────────────────────────────────────────────────────
        if (!p.isOnGround()) {
            // Airborne — smoothly return camera
            stepVirtualTowardReal(p);
            return;
        }
        if (!hasBlocks && requireBlocks.getValue()) {
            stepVirtualTowardReal(p);
            return;
        }

        // Require the player to actually be moving
        Vec3d vel   = p.getVelocity();
        double spd  = Math.abs(vel.x) + Math.abs(vel.z);
        if (spd < 0.005) {
            consecutivePlacements = 0;
            stepVirtualTowardReal(p);
            return;
        }

        // Burst pause
        if (burstPauseCooldown > 0 || placeCooldown > 0) {
            stepVirtualTowardReal(p);
            return;
        }

        // ── Determine block targets ───────────────────────────────────────────
        //
        // backDir  = direction the player is physically moving (backward)
        // standing = block directly below the player's feet
        // target   = block one step in backDir from standing (needs to be filled)
        //
        // We right-click the face of `standing` that faces `backDir`.
        // Minecraft resolves this as placing a block at `target`.

        Direction backDir  = p.getHorizontalFacing().getOpposite();
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(backDir);

        // Predictive check: where will the player be next tick?
        BlockPos nextStand = BlockPos.ofFloored(
                p.getX() + vel.x, p.getY() - 1, p.getZ() + vel.z);

        boolean targetIsAir   = mc.world.getBlockState(target).isAir();
        boolean nextNeedsBlock = !nextStand.equals(standing)
                              && mc.world.getBlockState(nextStand).isAir();

        if (!targetIsAir && !nextNeedsBlock) {
            // Ground ahead is already solid — no placement needed
            stepVirtualTowardReal(p);
            return;
        }

        // Can only place if we're standing on solid ground
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) {
            stepVirtualTowardReal(p);
            return;
        }

        // ── Compute aim point on the back face of `standing` ─────────────────
        //
        // The hit point is on the face of `standing` that points toward `backDir`.
        // Center of that face is at:
        //   standing.x + 0.5 + backDir.offsetX * 0.5
        //   standing.y + 0.5        (mid-height of the 1m block)
        //   standing.z + 0.5 + backDir.offsetZ * 0.5
        //
        // We add jitter along the two axes of the face to look human.

        ThreadLocalRandom rng = ThreadLocalRandom.current();

        double faceCX = standing.getX() + 0.5 + backDir.getOffsetX() * 0.5;
        double faceCY = standing.getY() + 0.5;
        double faceCZ = standing.getZ() + 0.5 + backDir.getOffsetZ() * 0.5;

        double jitterH = rng.nextDouble(-0.12, 0.12);   // horizontal on the face
        double jitterV = rng.nextDouble(-0.15, 0.10);   // vertical on the face

        Vec3d hitPoint = new Vec3d(
                faceCX + (backDir.getOffsetX() == 0 ? jitterH : 0.0),
                faceCY + jitterV,
                faceCZ + (backDir.getOffsetZ() == 0 ? jitterH : 0.0)
        );

        // ── Rotation toward hit point ─────────────────────────────────────────
        float[] needed    = calcLook(p.getEyePos(), hitPoint);
        float   needYaw   = needed[0];
        // Jitter the acceptable pitch range each tick (human variance)
        float   pitchLo   = 52f + rng.nextFloat() * 6f;   // 52-58°
        float   pitchHi   = 80f + rng.nextFloat() * 6f;   // 80-86°
        float   needPitch = MathHelper.clamp(needed[1], pitchLo, pitchHi);

        if (Float.isNaN(virtualYaw)) {
            virtualYaw   = p.getYaw();
            virtualPitch = p.getPitch();
        }

        float maxStep = 40f + rng.nextFloat() * 4f - 2f;
        virtualYaw   += MathHelper.clamp(
                MathHelper.wrapDegrees(needYaw   - virtualYaw),   -maxStep, maxStep);
        virtualPitch += MathHelper.clamp(
                MathHelper.wrapDegrees(needPitch - virtualPitch), -maxStep, maxStep);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);

        RotationOverride.serverYaw         = virtualYaw;
        RotationOverride.serverPitch       = virtualPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;

        // Wait until virtual rotation is close enough to target before placing
        if (Math.abs(MathHelper.wrapDegrees(needYaw   - virtualYaw))   > 18f) return;
        if (Math.abs(MathHelper.wrapDegrees(needPitch - virtualPitch))  > 18f) return;

        // ── Resolve block slot ────────────────────────────────────────────────
        int useSlot = resolveBlockSlot(p);
        if (useSlot == -1) return;   // no blocks found

        final Hand placeHand = (useSlot == -2) ? Hand.OFF_HAND : Hand.MAIN_HAND;
        final BlockHitResult bhr      = new BlockHitResult(hitPoint, backDir, standing, false);
        final int            fSlot    = useSlot;
        final int            fPrev    = p.getInventory().getSelectedSlot();

        if (placeHand == Hand.MAIN_HAND && fSlot != fPrev)
            p.getInventory().setSelectedSlot(fSlot);

        // Sneak-sync: briefly sneak when near the edge to break bot pattern
        if (sneakSync.getValue() && isNearBackEdge(p, backDir, 0.35)) {
            mc.options.sneakKey.setPressed(true);
            sneakReleaseNext = true;
        }

        // ── Schedule block placement AFTER the position packet ────────────────
        //
        // RotationOverride.afterPacketAction runs inside ClientPlayerEntityMixin
        // AFTER Minecraft has already sent the PositionAndRotation packet carrying
        // virtualYaw/virtualPitch. This guarantees Grim sees:
        //   PositionAndRotation(virtualYaw, virtualPitch) ← arrives first
        //   InteractBlock(...)                            ← validated against above ✓

        RotationOverride.afterPacketAction = () -> {
            ClientPlayerEntity pp = mc.player;
            if (pp == null || mc.interactionManager == null) return;
            Clutch.placing = true;
            try {
                if (mc.interactionManager.interactBlock(pp, placeHand, bhr).isAccepted()) {
                    pp.swingHand(placeHand);

                    // Release sneak immediately so sprint resumes next tick
                    mc.options.sneakKey.setPressed(false);
                    sneakReleaseNext = false;

                    consecutivePlacements++;
                    // Small random gap between placements (1-3 ticks)
                    placeCooldown = 1 + rng.nextInt(3);

                    int limit = burstLimit.getValueInt();
                    if (consecutivePlacements >= limit) {
                        consecutivePlacements = 0;
                        int pMin = burstPauseMin.getValueInt();
                        int pMax = Math.max(pMin + 1, burstPauseMax.getValueInt());
                        burstPauseCooldown = pMin + rng.nextInt(pMax - pMin + 1);
                    }
                }
            } finally {
                Clutch.placing = false;
                if (placeHand == Hand.MAIN_HAND && fSlot != fPrev && mc.player != null)
                    mc.player.getInventory().setSelectedSlot(fPrev);
            }
        };
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Smoothly step the virtual rotation back toward the real camera rotation
     * when we're not actively placing. This produces a natural return arc
     * instead of a snap-back that Grim can flag.
     */
    private void stepVirtualTowardReal(ClientPlayerEntity p) {
        if (Float.isNaN(virtualYaw) || p == null) {
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        float realYaw   = p.getYaw();
        float realPitch = p.getPitch();
        boolean yawClose   = Math.abs(MathHelper.wrapDegrees(realYaw   - virtualYaw))   < 4f;
        boolean pitchClose = Math.abs(MathHelper.wrapDegrees(realPitch - virtualPitch))  < 4f;
        if (yawClose && pitchClose) {
            virtualYaw   = Float.NaN;
            virtualPitch = Float.NaN;
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        virtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(realYaw   - virtualYaw),   -40f, 40f);
        virtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(realPitch - virtualPitch), -40f, 40f);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);
        RotationOverride.serverYaw         = virtualYaw;
        RotationOverride.serverPitch       = virtualPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;
    }

    /**
     * Calculates the yaw and pitch (in degrees) needed to look from eyePos
     * toward target.
     *
     * @return float[] { yaw, pitch }
     */
    private float[] calcLook(Vec3d eyePos, Vec3d target) {
        double dx   = target.x - eyePos.x;
        double dy   = target.y - eyePos.y;
        double dz   = target.z - eyePos.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float  yaw  = (float)  Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, dist));
        return new float[]{ yaw, pitch };
    }

    /**
     * Returns true when the player is within {@code threshold} blocks of the
     * trailing edge in the given direction.
     */
    private boolean isNearBackEdge(ClientPlayerEntity p, Direction backDir, double threshold) {
        double px = p.getX();
        double pz = p.getZ();
        return switch (backDir) {
            case NORTH -> (pz - Math.floor(pz))  < threshold;
            case SOUTH -> (Math.ceil(pz) - pz)   < threshold;
            case WEST  -> (px - Math.floor(px))  < threshold;
            case EAST  -> (Math.ceil(px) - px)   < threshold;
            default    -> false;
        };
    }

    /**
     * Returns the hotbar slot index (0-8) to use for block placement,
     * -2 if the block is only in the offhand, or -1 if no blocks are available.
     */
    private int resolveBlockSlot(ClientPlayerEntity p) {
        int setting = blockSlot.getValueInt();
        if (setting >= 1 && setting <= 9) {
            int idx = setting - 1;
            ItemStack stack = p.getInventory().getStack(idx);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getCount() > 0)
                return idx;
            // Fixed slot is empty — fall through to offhand check
        } else {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = p.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getCount() > 0)
                    return i;
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
