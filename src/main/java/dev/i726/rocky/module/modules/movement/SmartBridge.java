package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
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
 * SmartBridge — three modes:
 *
 *  GOD_ONLY    Pure god bridging (same logic as GodBridge module):
 *              backward key suppressed, silent rotation, burst-limit bypass,
 *              sneak-sync. Runs indefinitely.
 *
 *  ASSIST_ONLY Pure assist: auto-sneaks at edges, no rotation, no placement.
 *              Mirrors BridgeAssist. Uses clipAtLedge via safeWalkActive flag
 *              (assist mode doesn't suppress keys — sneak is the correct human
 *              behaviour here and doesn't trigger god-bridge detection).
 *
 *  SMART       Alternates between the two above.
 *
 * Key suppression vs clipAtLedge:
 *   God phase → suppress backKey directly. Produces clean movement packets
 *               with zero velocity-clamping signature. GodBridge.safeWalkActive
 *               and PlayerEntityMixin.clipAtLedge are NOT used.
 *   Assist phase → safeWalkActive=false, sneakKey pressed at edges (human).
 */
public final class SmartBridge extends Module implements TickListener {

    /**
     * Used ONLY for legacy compatibility. God phase no longer sets this — it
     * suppresses the backward key instead. Kept so PlayerEntityMixin compiles.
     */
    public static volatile boolean safeWalkActive = false;

    // ── Mode ──────────────────────────────────────────────────────────────────

    public enum BridgeMode { SMART, GOD_ONLY, ASSIST_ONLY }

    private final ModeSetting<BridgeMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), BridgeMode.SMART, BridgeMode.class)
            .setDescription(EncryptedString.of("SMART: alternates God+Assist. GOD_ONLY: pure god bridge. ASSIST_ONLY: edge-sneak only"));

    // ── God-phase settings ─────────────────────────────────────────────────────

    private final NumberSetting godBridgeBlocks = new NumberSetting(
            EncryptedString.of("God Blocks"), 1, 64, 16, 1)
            .setDescription(EncryptedString.of("(SMART) Blocks to god bridge before switching to assist"));

    private final BooleanSetting godAutoSprint = new BooleanSetting(
            EncryptedString.of("God Auto Sprint"), true)
            .setDescription(EncryptedString.of("Force-sprint while in the god bridge phase"));

    private final NumberSetting burstLimit = new NumberSetting(
            EncryptedString.of("Burst Limit"), 2, 12, 5, 1)
            .setDescription(EncryptedString.of("Max consecutive god-bridge placements before a forced pause"));

    private final NumberSetting burstPauseMin = new NumberSetting(
            EncryptedString.of("Burst Pause Min"), 2, 15, 4, 1)
            .setDescription(EncryptedString.of("Min ticks to pause after a burst"));

    private final NumberSetting burstPauseMax = new NumberSetting(
            EncryptedString.of("Burst Pause Max"), 2, 20, 9, 1)
            .setDescription(EncryptedString.of("Max ticks to pause after a burst"));

    private final BooleanSetting sneakSync = new BooleanSetting(
            EncryptedString.of("Sneak Sync"), true)
            .setDescription(EncryptedString.of("Occasionally send a 1-tick sneak to break the never-sneak bot pattern"));

    // ── Assist-phase settings ─────────────────────────────────────────────────

    private final NumberSetting assistMinBlocks = new NumberSetting(
            EncryptedString.of("Assist Min Blocks"), 1, 32, 4, 1)
            .setDescription(EncryptedString.of("(SMART) Min blocks in assist phase before switching back"));

    private final NumberSetting assistMaxBlocks = new NumberSetting(
            EncryptedString.of("Assist Max Blocks"), 1, 64, 12, 1)
            .setDescription(EncryptedString.of("(SMART) Max blocks in assist phase before switching back"));

    private final NumberSetting assistEdgeDist = new NumberSetting(
            EncryptedString.of("Assist Edge Dist"), 0.05, 0.5, 0.25, 0.01)
            .setDescription(EncryptedString.of("How close to an edge before auto-sneak activates"));

    private final NumberSetting assistLookAhead = new NumberSetting(
            EncryptedString.of("Assist Look-Ahead"), 0, 10, 3, 1)
            .setDescription(EncryptedString.of("Ticks of velocity to project when checking for a fall"));

    // ── Shared settings ───────────────────────────────────────────────────────

    private final BooleanSetting stopOnDamage = new BooleanSetting(
            EncryptedString.of("Stop On Damage"), true)
            .setDescription(EncryptedString.of("Disable the module when you take damage"));

    private final NumberSetting damageThreshold = new NumberSetting(
            EncryptedString.of("Damage Threshold"), 0.0, 10.0, 0.5, 0.5)
            .setDescription(EncryptedString.of("Half-hearts of damage per tick that trigger Stop On Damage"));

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot)"));

    private final BooleanSetting requireBlocks = new BooleanSetting(
            EncryptedString.of("Require Blocks"), true)
            .setDescription(EncryptedString.of("Key suppression and sprint only activate when holding blocks"));

    // ── State ─────────────────────────────────────────────────────────────────

    private enum Phase { GOD, ASSIST }
    private Phase   phase               = Phase.GOD;
    private int     phaseBlocksPlaced   = 0;
    private int     currentPhaseTarget  = 16;
    private int     placeCooldown       = 0;
    private int     consecutivePlacements = 0;
    private int     burstPauseCooldown  = 0;
    private int     lastBlockCount      = -1;
    private float   lastHealth          = 20f;
    private boolean healthInitialized   = false;
    private boolean sneakReleaseNext    = false;

    private float godVirtualYaw   = Float.NaN;
    private float godVirtualPitch = Float.NaN;

    public SmartBridge() {
        super(EncryptedString.of("Smart Bridge"),
                EncryptedString.of("Intelligent bridging — god, assist, or combined"),
                -1, CategoryManager.BRIDGING);
        addSettings(
                mode,
                godBridgeBlocks, godAutoSprint, burstLimit, burstPauseMin, burstPauseMax, sneakSync,
                assistMinBlocks, assistMaxBlocks, assistEdgeDist, assistLookAhead,
                stopOnDamage, damageThreshold, blockSlot, requireBlocks
        );
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        phase                 = Phase.GOD;
        phaseBlocksPlaced     = 0;
        currentPhaseTarget    = godBridgeBlocks.getValueInt();
        consecutivePlacements = 0;
        burstPauseCooldown    = 0;
        sneakReleaseNext      = false;
        healthInitialized     = false;
        lastBlockCount        = -1;
        safeWalkActive        = false;
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        safeWalkActive       = false;
        sneakReleaseNext     = false;
        RotationOverride.active            = false;
        RotationOverride.afterPacketAction = null;
        godVirtualYaw        = Float.NaN;
        godVirtualPitch      = Float.NaN;
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            // backKey: we only ever forced it to false; releasing here so
            // the real physical key state is read on the next tick.
        }
        Clutch.placing = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        // Release sneak-sync from last tick
        if (sneakReleaseNext) {
            mc.options.sneakKey.setPressed(false);
            sneakReleaseNext = false;
        }

        // Damage check
        float health = p.getHealth();
        if (!healthInitialized) {
            lastHealth = health; healthInitialized = true;
        } else {
            float delta = lastHealth - health;
            if (stopOnDamage.getValue() && delta >= (float) damageThreshold.getValue()) {
                lastHealth = health;
                this.toggle();
                return;
            }
        }
        lastHealth = health;

        if (placeCooldown > 0) placeCooldown--;

        boolean isGod    = mode.isMode(BridgeMode.GOD_ONLY)
                        || (mode.isMode(BridgeMode.SMART) && phase == Phase.GOD);
        boolean isAssist = mode.isMode(BridgeMode.ASSIST_ONLY)
                        || (mode.isMode(BridgeMode.SMART) && phase == Phase.ASSIST);

        if (isGod)         runGodPhase(p);
        else if (isAssist) runAssistPhase(p);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // GOD PHASE — identical detection-bypass logic to standalone GodBridge
    // ═══════════════════════════════════════════════════════════════════════════

    private void runGodPhase(ClientPlayerEntity p) {
        boolean hasBlocks = isHoldingBlock();
        boolean protect   = !requireBlocks.getValue() || hasBlocks;

        // ── Backward key suppression — only when near the back edge ──────────
        // Suppress S only within 0.4 blocks of the dangerous ledge, not the
        // whole time. Player can move backward freely in the middle of a block.
        if (p.isOnGround() && protect) {
            Direction backDir = p.getHorizontalFacing().getOpposite();
            if (isNearBackEdge(p, backDir)) {
                mc.options.backKey.setPressed(false);
            }
        }

        if (!hasBlocks && requireBlocks.getValue()) {
            stepGodVirtualTowardReal(p);
            return;
        }

        if (!p.isOnGround()) {
            stepGodVirtualTowardReal(p);
            return;
        }

        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.005) {
            consecutivePlacements = 0;
            stepGodVirtualTowardReal(p);
            return;
        }

        if (godAutoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        if (burstPauseCooldown > 0) {
            burstPauseCooldown--;
            stepGodVirtualTowardReal(p);
            return;
        }

        if (placeCooldown > 0) { stepGodVirtualTowardReal(p); return; }

        // ── Target block behind player ─────────────────────────────────────────
        Direction placeDir = p.getHorizontalFacing().getOpposite();
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir())       { stepGodVirtualTowardReal(p); return; }
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) { stepGodVirtualTowardReal(p); return; }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double faceOffX = placeDir.getOffsetX() * 0.5;
        double faceOffZ = placeDir.getOffsetZ() * 0.5;
        double jitterH  = rng.nextDouble(-0.10, 0.10);
        double jitterY  = rng.nextDouble(-0.10, 0.06);

        Vec3d aimPoint = Vec3d.ofCenter(standing).add(
                faceOffX + (placeDir.getOffsetX() == 0 ? jitterH : 0),
                -0.2 + jitterY,
                faceOffZ + (placeDir.getOffsetZ() == 0 ? jitterH : 0));

        float[] needed    = calcLook(p.getEyePos(), aimPoint);
        float   needYaw   = needed[0];
        float   pitchMin  = 52f + rng.nextFloat() * 6f;
        float   pitchMax  = 80f + rng.nextFloat() * 6f;
        float   needPitch = MathHelper.clamp(needed[1], pitchMin, pitchMax);

        if (Float.isNaN(godVirtualYaw)) {
            godVirtualYaw   = p.getYaw();
            godVirtualPitch = p.getPitch();
        }

        float maxStep = 40f + rng.nextFloat() * 4f - 2f;
        godVirtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(needYaw   - godVirtualYaw),   -maxStep, maxStep);
        godVirtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(needPitch - godVirtualPitch), -maxStep, maxStep);
        godVirtualPitch  = MathHelper.clamp(godVirtualPitch, -90f, 90f);

        RotationOverride.serverYaw         = godVirtualYaw;
        RotationOverride.serverPitch       = godVirtualPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;

        if (Math.abs(MathHelper.wrapDegrees(needYaw   - godVirtualYaw)) > 18f) return;
        if (Math.abs(MathHelper.wrapDegrees(needPitch - godVirtualPitch)) > 18f) return;

        int useSlot = resolveBlockSlot();
        if (useSlot == -1) return;

        final BlockHitResult bhr      = new BlockHitResult(aimPoint, placeDir, standing, false);
        final int            fUseSlot = useSlot;
        final int            fPrev    = p.getInventory().getSelectedSlot();

        if (fUseSlot != fPrev) p.getInventory().setSelectedSlot(fUseSlot);

        // Sneak-sync: random 1-tick sneak, breaks never-sneak bot signature
        if (sneakSync.getValue() && rng.nextInt(4) == 0) {
            mc.options.sneakKey.setPressed(true);
            sneakReleaseNext = true;
        }

        RotationOverride.afterPacketAction = () -> {
            ClientPlayerEntity pp = mc.player;
            if (pp == null || mc.interactionManager == null) return;
            Clutch.placing = true;
            try {
                if (mc.interactionManager.interactBlock(pp, Hand.MAIN_HAND, bhr).isAccepted()) {
                    pp.swingHand(Hand.MAIN_HAND);
                    phaseBlocksPlaced++;
                    consecutivePlacements++;

                    placeCooldown = 2 + rng.nextInt(3);

                    int limit = burstLimit.getValueInt();
                    if (consecutivePlacements >= limit) {
                        consecutivePlacements = 0;
                        int pMin = burstPauseMin.getValueInt();
                        int pMax = Math.max(pMin, burstPauseMax.getValueInt());
                        burstPauseCooldown = pMin + (int)(Math.random() * (pMax - pMin + 1));
                    }

                    if (mode.isMode(BridgeMode.SMART) && phaseBlocksPlaced >= currentPhaseTarget) {
                        advancePhase();
                    }
                }
            } finally {
                Clutch.placing = false;
                if (fUseSlot != fPrev && mc.player != null)
                    mc.player.getInventory().setSelectedSlot(fPrev);
            }
        };
    }

    private void stepGodVirtualTowardReal(ClientPlayerEntity p) {
        if (Float.isNaN(godVirtualYaw) || p == null) {
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        float realYaw   = p.getYaw();
        float realPitch = p.getPitch();
        if (Math.abs(MathHelper.wrapDegrees(realYaw - godVirtualYaw)) < 4f
                && Math.abs(MathHelper.wrapDegrees(realPitch - godVirtualPitch)) < 4f) {
            godVirtualYaw   = Float.NaN;
            godVirtualPitch = Float.NaN;
            RotationOverride.active            = false;
            RotationOverride.afterPacketAction = null;
            return;
        }
        godVirtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(realYaw   - godVirtualYaw),   -40f, 40f);
        godVirtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(realPitch - godVirtualPitch), -40f, 40f);
        godVirtualPitch  = MathHelper.clamp(godVirtualPitch, -90f, 90f);
        RotationOverride.serverYaw         = godVirtualYaw;
        RotationOverride.serverPitch       = godVirtualPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ASSIST PHASE — mirrors BridgeAssist: edge-sneak only, no block placement
    // ═══════════════════════════════════════════════════════════════════════════

    private void runAssistPhase(ClientPlayerEntity p) {
        // Clear god-phase state
        godVirtualYaw   = Float.NaN;
        godVirtualPitch = Float.NaN;
        RotationOverride.active            = false;
        RotationOverride.afterPacketAction = null;
        consecutivePlacements = 0;
        safeWalkActive        = false;

        boolean hasBlocks = isHoldingBlock();
        boolean canSneak  = !requireBlocks.getValue() || hasBlocks;

        mc.options.sneakKey.setPressed(canSneak && isNearEdge(p));

        int currentCount = totalBlockCount(p);
        if (lastBlockCount < 0) {
            lastBlockCount = currentCount;
        } else if (currentCount < lastBlockCount) {
            phaseBlocksPlaced += lastBlockCount - currentCount;
            lastBlockCount = currentCount;
            if (mode.isMode(BridgeMode.SMART) && phaseBlocksPlaced >= currentPhaseTarget) {
                advancePhase();
            }
        } else if (currentCount > lastBlockCount) {
            lastBlockCount = currentCount;
        }
    }

    private boolean isNearEdge(ClientPlayerEntity p) {
        double x = p.getX(), z = p.getZ();
        double vx = p.getVelocity().x, vz = p.getVelocity().z;

        BlockPos currentBelow = BlockPos.ofFloored(x, p.getY() - 1, z);
        if (mc.world.getBlockState(currentBelow).isAir()) return true;

        int la = assistLookAhead.getValueInt();
        double nextX = x + vx * la;
        double nextZ = z + vz * la;
        double edgeX = Math.min(nextX - Math.floor(nextX), Math.ceil(nextX) - nextX);
        double edgeZ = Math.min(nextZ - Math.floor(nextZ), Math.ceil(nextZ) - nextZ);

        double edge = assistEdgeDist.getValue();
        if (edgeX <= edge || edgeZ <= edge) {
            BlockPos nextBelow = BlockPos.ofFloored(nextX, p.getY() - 1, nextZ);
            return mc.world.getBlockState(nextBelow).isAir();
        }
        return false;
    }

    // ── Phase management ──────────────────────────────────────────────────────

    private void advancePhase() {
        phaseBlocksPlaced     = 0;
        godVirtualYaw         = Float.NaN;
        godVirtualPitch       = Float.NaN;
        RotationOverride.active            = false;
        RotationOverride.afterPacketAction = null;
        consecutivePlacements = 0;
        burstPauseCooldown    = 0;

        if (phase == Phase.GOD) {
            phase = Phase.ASSIST;
            safeWalkActive = false;
            int min = assistMinBlocks.getValueInt();
            int max = Math.max(min, assistMaxBlocks.getValueInt());
            currentPhaseTarget = (min == max) ? min
                    : ThreadLocalRandom.current().nextInt(min, max + 1);
            lastBlockCount = mc.player != null ? totalBlockCount(mc.player) : -1;
        } else {
            phase = Phase.GOD;
            currentPhaseTarget = godBridgeBlocks.getValueInt();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveBlockSlot() {
        if (mc.player == null) return -1;
        int setting = blockSlot.getValueInt();
        if (setting >= 1 && setting <= 9) {
            int idx = setting - 1;
            ItemStack stack = mc.player.getInventory().getStack(idx);
            return (!stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getCount() > 0) ? idx : -1;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getCount() > 0) return i;
        }
        return -1;
    }

    private int totalBlockCount(ClientPlayerEntity p) {
        int n = 0;
        ItemStack main = p.getMainHandStack(), off = p.getOffHandStack();
        if (main.getItem() instanceof BlockItem) n += main.getCount();
        if (off.getItem()  instanceof BlockItem) n += off.getCount();
        return n;
    }

    private boolean isHoldingBlock() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }

    /**
     * Returns true when the player's centre is within 0.4 blocks of the edge
     * in {@code dir} AND the block beyond that edge is still air.
     * Backward-key suppression only fires inside this narrow danger zone.
     */
    private boolean isNearBackEdge(ClientPlayerEntity p, Direction dir) {
        if (mc.world == null) return false;
        BlockPos standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        if (!mc.world.getBlockState(standing.offset(dir)).isAir()) return false;

        double px = p.getX(), pz = p.getZ();
        double dist;
        switch (dir) {
            case NORTH -> dist = pz - Math.floor(pz);
            case SOUTH -> dist = Math.ceil(pz) - pz;
            case WEST  -> dist = px - Math.floor(px);
            default    -> dist = Math.ceil(px) - px;
        }
        return dist < 0.4;
    }

    private static float[] calcLook(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            MathHelper.clamp((float) Math.toDegrees(-Math.atan2(dy, hDist)), -90f, 90f)
        };
    }
}
