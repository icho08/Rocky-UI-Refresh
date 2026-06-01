package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;

import java.util.*;

/**
 * BedBreaker — automatically breaks the nearest bed block.
 *
 * Anti-cheat bypass strategy:
 *  1. Silent server-side packet rotation (LookAndOnGround) — client yaw/pitch
 *     never changes, so inventory-movement checks stay clean.
 *  2. Per-tick Gaussian yaw/pitch jitter (+/-0.5°) to avoid a fixed-angle
 *     machine-perfect signature.
 *  3. Randomised inter-tick delay (Break Delay ± 20 ms uniform noise) so the
 *     break interval is never constant.
 *  4. Cover Break: mines blocking blocks one at a time, preferring the face
 *     closest to the player to keep the hit-vector natural.
 *  5. Auto Tool: equips the best hotbar tool before each break tick, restores
 *     the previous slot when the module disables or the bed is gone.
 *  6. One LookAndOnGround per tick (not a snap-and-restore pair) — the server
 *     sees a gradual return via subsequent PositionAndRotation packets.
 */
public final class BedBreaker extends Module implements TickListener {

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 6.0, 4.5, 0.1)
            .setDescription(EncryptedString.of("Radius to search for beds (blocks)"));

    private final BooleanSetting coverBreak = new BooleanSetting(
            EncryptedString.of("Cover Break"), true)
            .setDescription(EncryptedString.of("Break blocks covering the bed before mining it"));

    private final BooleanSetting rotate = new BooleanSetting(
            EncryptedString.of("Rotate"), true)
            .setDescription(EncryptedString.of("Send silent server-side rotation toward target (client view unchanged)"));

    private final BooleanSetting autoTool = new BooleanSetting(
            EncryptedString.of("Auto Tool"), true)
            .setDescription(EncryptedString.of("Auto-equip the fastest hotbar tool; restores slot on disable"));

    private final MinMaxSetting breakDelayRange = new MinMaxSetting(
            EncryptedString.of("Break Delay"), 0, 200, 5, 0, 40)
            .setDescription(EncryptedString.of("Random ms range between break ticks (min-max)"));

    private final TimerUtils breakTimer = new TimerUtils();
    private final Random rng = new Random();

    private BlockPos currentTarget;
    private Direction currentFace;
    private int prevSlot = -1;
    private int nextDelay = 0;

    private record BreakTarget(BlockPos pos, Direction face) {}

    public BedBreaker() {
        super(EncryptedString.of("Bed Breaker"),
                EncryptedString.of("Breaks beds & their covers with anti-cheat bypass"),
                -1, CategoryManager.PVP);
        addSettings(range, coverBreak, rotate, autoTool, breakDelayRange);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        currentTarget = null;
        currentFace = null;
        prevSlot = -1;
        rollDelay();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        restoreSlot();
        currentTarget = null;
        currentFace = null;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (!breakTimer.delay(nextDelay)) return;

        // 1. Find nearest bed
        BlockPos bedPos = findBed();
        if (bedPos == null) {
            restoreSlot();
            return;
        }

        // 2. Decide what to mine: the bed itself, or a cover block
        BlockPos targetPos;
        Direction face;

        Direction directFace = findAccessibleFace(bedPos);
        if (directFace != null) {
            targetPos = bedPos;
            face = directFace;
        } else if (coverBreak.getValue()) {
            BreakTarget coverResult = findCover(bedPos);
            if (coverResult == null) {
                restoreSlot();
                return;
            }
            targetPos = coverResult.pos();
            face = coverResult.face();
        } else {
            restoreSlot();
            return;
        }

        // 3. Auto tool
        if (autoTool.getValue()) {
            BlockState state = mc.world.getBlockState(targetPos);
            int best = findBestToolSlot(state);
            if (best != -1 && best != mc.player.getInventory().getSelectedSlot()) {
                if (prevSlot == -1) prevSlot = mc.player.getInventory().getSelectedSlot();
                mc.player.getInventory().setSelectedSlot(best);
            }
        }

        // 4. Silent rotation (one packet, no snap-back — natural return via
        //    the game's own subsequent PositionAndRotation packets)
        if (rotate.getValue()) {
            float[] rot = calcRotation(Vec3d.ofCenter(targetPos)
                    .add(face.getOffsetX() * 0.4, face.getOffsetY() * 0.4, face.getOffsetZ() * 0.4));
            rot[0] += (float) (rng.nextGaussian() * 0.45);
            rot[1] += (float) (rng.nextGaussian() * 0.35);
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.LookAndOnGround(
                            rot[0], rot[1],
                            mc.player.isOnGround(),
                            mc.player.horizontalCollision));
        }

        // 5. Mine the block
        if (!targetPos.equals(currentTarget) || face != currentFace) {
            mc.interactionManager.attackBlock(targetPos, face);
            currentTarget = targetPos;
            currentFace = face;
        } else {
            mc.interactionManager.updateBlockBreakingProgress(targetPos, face);
        }

        mc.player.swingHand(Hand.MAIN_HAND);

        breakTimer.reset();
        rollDelay();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Find the nearest bed block (any part) within range.
     */
    private BlockPos findBed() {
        double r = range.getValue();
        BlockPos playerPos = mc.player.getBlockPos();
        int ri = (int) Math.ceil(r);

        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -ri; x <= ri; x++) {
            for (int y = -ri; y <= ri; y++) {
                for (int z = -ri; z <= ri; z++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                    if (dist > r) continue;
                    if (!(mc.world.getBlockState(pos).getBlock() instanceof BedBlock)) continue;
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Returns the best exposed Direction on this block pos, or null if all
     * six faces are blocked by solid blocks.
     *
     * "Exposed" means the neighbouring block in that direction is either air
     * or non-solid, AND the face centre is within reach from the player's eyes.
     * We prefer the face whose centre is closest to the player (most natural
     * hit point).
     */
    private Direction findAccessibleFace(BlockPos pos) {
        Vec3d eyes = mc.player.getEyePos();
        double r = range.getValue() + 0.5;

        Direction bestFace = null;
        double bestDist = Double.MAX_VALUE;

        for (Direction dir : Direction.values()) {
            BlockPos neighbour = pos.offset(dir);
            BlockState nState = mc.world.getBlockState(neighbour);
            // Face must open to air or a non-solid block
            if (!nState.isAir() && nState.isSolidBlock(mc.world, neighbour)) continue;

            Vec3d faceCentre = Vec3d.ofCenter(pos)
                    .add(dir.getOffsetX() * 0.5,
                         dir.getOffsetY() * 0.5,
                         dir.getOffsetZ() * 0.5);
            double dist = eyes.distanceTo(faceCentre);
            if (dist > r) continue;

            if (dist < bestDist) {
                bestDist = dist;
                bestFace = dir;
            }
        }
        return bestFace;
    }

    /**
     * Finds the best cover block to mine in order to expose the bed.
     * Checks a 3x4x3 volume around the bed and returns the reachable block
     * whose accessible face is closest to the player.
     *
     * Returns [BlockPos, Direction] or null if nothing is reachable.
     */
    private BreakTarget findCover(BlockPos bedPos) {
        Vec3d eyes = mc.player.getEyePos();
        double r = range.getValue() + 0.5;

        BlockPos bestPos = null;
        Direction bestFace = null;
        double bestDist = Double.MAX_VALUE;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 2; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos check = bedPos.add(x, y, z);
                    if (check.equals(bedPos)) continue;

                    BlockState state = mc.world.getBlockState(check);
                    if (state.isAir()) continue;
                    if (state.getBlock() instanceof BedBlock) continue;

                    double blockDist = eyes.distanceTo(Vec3d.ofCenter(check));
                    if (blockDist > r) continue;

                    // Try to find an accessible face
                    for (Direction dir : Direction.values()) {
                        BlockPos neighbour = check.offset(dir);
                        BlockState nState = mc.world.getBlockState(neighbour);
                        if (!nState.isAir() && nState.isSolidBlock(mc.world, neighbour)) continue;

                        Vec3d faceCentre = Vec3d.ofCenter(check)
                                .add(dir.getOffsetX() * 0.5,
                                     dir.getOffsetY() * 0.5,
                                     dir.getOffsetZ() * 0.5);
                        double faceDist = eyes.distanceTo(faceCentre);
                        if (faceDist > r) continue;

                        if (faceDist < bestDist) {
                            bestDist = faceDist;
                            bestPos = check;
                            bestFace = dir;
                        }
                    }
                }
            }
        }

        if (bestPos == null || bestFace == null) return null;
        return new BreakTarget(bestPos, bestFace);
    }

    /**
     * Finds the hotbar slot (0-8) with the highest mining speed multiplier
     * for the given block state.
     */
    private int findBestToolSlot(BlockState state) {
        int best = -1;
        float bestSpeed = 1.0f;
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = i;
            }
        }
        return best;
    }

    /**
     * Calculates yaw/pitch from the player's eye position to a world position.
     */
    private float[] calcRotation(Vec3d target) {
        Vec3d eyes = mc.player.getEyePos();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist2d)));
        return new float[]{yaw, pitch};
    }

    /**
     * Restores the previously held hotbar slot (called on disable / no target).
     */
    private void restoreSlot() {
        if (prevSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    /**
     * Picks a new random break delay within the configured min-max range.
     * The slight randomisation prevents a constant-interval pattern that ACs
     * use to distinguish automation from human mining.
     */
    private void rollDelay() {
        int lo = breakDelayRange.getMinInt();
        int hi = breakDelayRange.getMaxInt();
        if (hi < lo) hi = lo;
        nextDelay = lo + (hi > lo ? rng.nextInt(hi - lo + 1) : 0);
    }
}
