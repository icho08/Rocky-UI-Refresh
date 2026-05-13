package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RotationUtils;
import dev.i726.rocky.utils.rotation.Rotation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

public final class SmartBridge extends Module implements TickListener {

    // ----- BridgeAssist constants (copied verbatim from BridgeAssist) -----
    private static final double EDGE_DISTANCE = 0.25;
    private static final int MIN_HEIGHT = 1;

    // ----- Settings -----
    private final NumberSetting godBridgeBlocks = new NumberSetting(
            EncryptedString.of("God Bridge Blocks"), 1, 64, 16, 1)
            .setDescription(EncryptedString.of("How many blocks to god bridge before switching to assist mode"));

    private final BooleanSetting godAutoSprint = new BooleanSetting(EncryptedString.of("God Auto Sprint"), true)
            .setDescription(EncryptedString.of("Force-sprint while in god bridge phase"));

    public enum ProtectionMode { SafeWalk, Sneak, Snap, Smooth, Off }
    private final ModeSetting<ProtectionMode> godFallMode = new ModeSetting<>(
            EncryptedString.of("God Fall Mode"), ProtectionMode.Snap, ProtectionMode.class)
            .setDescription(EncryptedString.of("How to react when about to fall in god phase: Snap zeros velocity, Smooth slows it, SafeWalk clips movement, Sneak auto-sneaks, Off disables"));

    private final NumberSetting godLookAhead = new NumberSetting(
            EncryptedString.of("God Look-Ahead"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("How many ticks of velocity to project when checking for a fall"));

    private final NumberSetting godSmoothFactor = new NumberSetting(
            EncryptedString.of("God Smooth Factor"), 0.1, 0.95, 0.75, 0.05)
            .setDescription(EncryptedString.of("Velocity multiplier when smooth fall protection activates"));

    private final NumberSetting assistMinBlocks = new NumberSetting(
            EncryptedString.of("Assist Min Blocks"), 1, 32, 4, 1)
            .setDescription(EncryptedString.of("Minimum blocks to bridge in assist mode (random per cycle)"));

    private final NumberSetting assistMaxBlocks = new NumberSetting(
            EncryptedString.of("Assist Max Blocks"), 1, 64, 12, 1)
            .setDescription(EncryptedString.of("Maximum blocks to bridge in assist mode (random per cycle)"));

    private final BooleanSetting stopOnDamage = new BooleanSetting(
            EncryptedString.of("Stop On Damage"), true)
            .setDescription(EncryptedString.of("Disable the module automatically when you take damage"));

    private final NumberSetting damageThreshold = new NumberSetting(
            EncryptedString.of("Damage Threshold"), 0.0, 10.0, 0.5, 0.5)
            .setDescription(EncryptedString.of("Half-hearts of damage in one tick that trigger Stop On Damage"));

    // ----- Phase state -----
    public enum BridgeMode { SMART, GOD_ONLY, ASSIST_ONLY }
    private final ModeSetting<BridgeMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), BridgeMode.SMART, BridgeMode.class)
            .setDescription(EncryptedString.of("SMART: Alternates God and Assist. GOD_ONLY: Pure god bridge. ASSIST_ONLY: Only edge-sneak assist."));

    private enum Phase { GOD, ASSIST }
    private Phase phase = Phase.GOD;
    private int phaseBlocksPlaced = 0;
    private int currentPhaseTarget = 8;
    private int placeCooldown = 0;

    // For detecting manual placements during assist phase
    private int lastBlockCount = -1;

    // Per-placement human jitter (rerolled after each god placement)
    private float currentJitterYaw = 0f;
    private float currentJitterPitch = 0f;

    // Damage tracking
    private float lastHealth = 20f;
    private boolean healthInitialized = false;

    public SmartBridge() {
        super(EncryptedString.of("Smart Bridge"),
                EncryptedString.of("Intelligent bridging assist"),
                -1,
                CategoryManager.BRIDGING);
        addSettings(mode, godBridgeBlocks, assistMinBlocks, assistMaxBlocks,
                godAutoSprint, godFallMode, godLookAhead, godSmoothFactor,
                stopOnDamage, damageThreshold);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        phase = Phase.GOD;
        phaseBlocksPlaced = 0;
        currentPhaseTarget = godBridgeBlocks.getValueInt();
        healthInitialized = false;
        lastBlockCount = -1;
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (GodBridge.INSTANCE != null) GodBridge.INSTANCE.setEnabled(false);
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
        }
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        // ---- Damage detection ----
        float health = p.getHealth();
        if (!healthInitialized) {
            lastHealth = health;
            healthInitialized = true;
        } else if (stopOnDamage.getValue() && (lastHealth - health) >= damageThreshold.getValue()) {
            lastHealth = health;
            this.toggle();
            return;
        }
        lastHealth = health;

        if (placeCooldown > 0) placeCooldown--;

        boolean doGod = false;
        boolean doAssist = false;

        if (mode.isMode(BridgeMode.SMART)) {
            if (phase == Phase.GOD) doGod = true;
            else doAssist = true;
        } else if (mode.isMode(BridgeMode.GOD_ONLY)) {
            doGod = true;
        } else if (mode.isMode(BridgeMode.ASSIST_ONLY)) {
            doAssist = true;
        }

        if (doGod) {
            runGodPhase(p);
        } else if (doAssist) {
            runAssistPhase(p);
        }
    }

    // ============================================================
    // GOD PHASE — legit: auto-places blocks while looking at them,
    // and uses sneak-based fall protection (server-friendly).
    // ============================================================
    private void runGodPhase(ClientPlayerEntity p) {
        if (!isHoldingBlocks(p)) {
            mc.options.sneakKey.setPressed(false);
            return;
        }

        if (!p.isOnGround()) {
            mc.options.sneakKey.setPressed(false);
            return;
        }

        // ---- Fall Protection (GodBridge / Sneak / Velocity) ----
        if (!godFallMode.isMode(ProtectionMode.Off)) {
            if (godFallMode.isMode(ProtectionMode.SafeWalk)) {
                if (!GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(true);
                mc.options.sneakKey.setPressed(false);
            } else {
                if (GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(false);
                
                if (p.isOnGround() && isAboutToFallOff()) {
                    Vec3d velocity = p.getVelocity();
                    if (godFallMode.isMode(ProtectionMode.Snap)) {
                        // Undetectable Velocity Snap: We instantly set velocity to 0 to provide that "sticky" god bridge feel.
                        // By wrapping it in this magnitude check, we ensure it only fires ONCE when you reach the edge,
                        // preventing the client from spamming zero-velocity packets (which is what anti-cheats flag).
                        if (Math.abs(velocity.x) > 0.001 || Math.abs(velocity.z) > 0.001) {
                            p.setVelocity(0, velocity.y, 0);
                        }
                    } else if (godFallMode.isMode(ProtectionMode.Smooth)) {
                        double speed = godSmoothFactor.getValue();
                        // Only slow down if moving fast enough, prevents jerky micro-stutters
                        if (Math.abs(velocity.x) > 0.05 || Math.abs(velocity.z) > 0.05) {
                            p.setVelocity(velocity.x * speed, velocity.y, velocity.z * speed);
                        }
                    } else if (godFallMode.isMode(ProtectionMode.Sneak)) {
                        mc.options.sneakKey.setPressed(true);
                        return;
                    }
                } else if (godFallMode.isMode(ProtectionMode.Sneak)) {
                    mc.options.sneakKey.setPressed(false);
                }
            }
        } else {
            if (GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(false);
            mc.options.sneakKey.setPressed(false);
        }

        // Pick the cardinal direction we are walking (backwards motion -> placement direction)
        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.01) return;

        if (godAutoSprint.getValue() && !p.isSprinting()) {
            mc.options.sprintKey.setPressed(true);
        }

        Direction placeDir = cardinalFromMotion(v.x, v.z);
        BlockPos standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos target = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir()) return;
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) return;

        Direction sideFace = placeDir;
        Vec3d aimPoint = Vec3d.ofCenter(standing)
                .add(sideFace.getOffsetX() * 0.5, -0.25, sideFace.getOffsetZ() * 0.5);

        // Rotation logic
        Rotation desired = RotationUtils.getDirection(p, aimPoint);
        float newYaw = lerpAngle(p.getYaw(), (float) desired.yaw(), 0.8f);
        float newPitch = lerpAngle(p.getPitch(), (float) Math.max(75.0, desired.pitch()), 0.8f);
        
        p.setYaw(newYaw);
        p.setPitch(newPitch);

        if (placeCooldown > 0) return;

        BlockHitResult bhr = new BlockHitResult(aimPoint, sideFace, standing, false);
        Hand hand = p.getMainHandStack().getItem() instanceof BlockItem ? Hand.MAIN_HAND : Hand.OFF_HAND;
        if (mc.interactionManager.interactBlock(p, hand, bhr).isAccepted()) {
            p.swingHand(hand);
            phaseBlocksPlaced++;
            placeCooldown = 2; // Fixed safe delay
            if (phaseBlocksPlaced >= currentPhaseTarget) advancePhase();
        }
    }

    private boolean isMoving() {
        return mc.options.forwardKey.isPressed() || mc.options.backKey.isPressed() ||
               mc.options.leftKey.isPressed() || mc.options.rightKey.isPressed();
    }

    private boolean isAboutToFallOff() {
        if (mc.player == null) return false;
        Vec3d pos = mc.player.getEntityPos();
        Vec3d velocity = mc.player.getVelocity();

        int la = godLookAhead.getValueInt();
        double nextX = pos.x + velocity.x * la;
        double nextZ = pos.z + velocity.z * la;

        BlockPos nextPos = BlockPos.ofFloored(nextX, pos.y - 1, nextZ);
        return mc.world.getBlockState(nextPos).isAir();
    }

    // ============================================================
    // ASSIST PHASE — exact sneak/unsneak logic from BridgeAssist.
    // Player places blocks themselves; we only count them.
    // ============================================================
    private void runAssistPhase(ClientPlayerEntity p) {
        // ---- Sneak management (verbatim from BridgeAssist.java) ----
        boolean shouldSneak = isNearEdge(p);
        mc.options.sneakKey.setPressed(shouldSneak);

        // ---- Track manually placed blocks via stack count drops ----
        int currentCount = totalBlockCount(p);
        if (lastBlockCount < 0) {
            lastBlockCount = currentCount;
        } else if (currentCount < lastBlockCount) {
            int delta = lastBlockCount - currentCount;
            phaseBlocksPlaced += delta;
            lastBlockCount = currentCount;
            if (phaseBlocksPlaced >= currentPhaseTarget) advancePhase();
        } else if (currentCount > lastBlockCount) {
            // Player picked up more blocks
            lastBlockCount = currentCount;
        }
    }

    // ---- Verbatim from BridgeAssist.java ----
    private boolean isNearEdge(ClientPlayerEntity p) {
        double x = p.getX();
        double z = p.getZ();
        double vx = p.getVelocity().x;
        double vz = p.getVelocity().z;

        BlockPos currentBelow = BlockPos.ofFloored(x, p.getY() - 1, z);
        if (mc.world.getBlockState(currentBelow).isAir() && hasMinFallHeight(currentBelow)) {
            return true;
        }

        double nextX = x + vx * 3;
        double nextZ = z + vz * 3;

        double distToEdgeX = Math.min(nextX - Math.floor(nextX), Math.ceil(nextX) - nextX);
        double distToEdgeZ = Math.min(nextZ - Math.floor(nextZ), Math.ceil(nextZ) - nextZ);

        if (distToEdgeX <= EDGE_DISTANCE || distToEdgeZ <= EDGE_DISTANCE) {
            BlockPos nextBelow = BlockPos.ofFloored(nextX, p.getY() - 1, nextZ);
            return mc.world.getBlockState(nextBelow).isAir() && hasMinFallHeight(nextBelow);
        }

        return false;
    }

    private boolean hasMinFallHeight(BlockPos pos) {
        int fallHeight = 0;
        BlockPos checkPos = pos;
        while (fallHeight < MIN_HEIGHT && mc.world.getBlockState(checkPos).isAir()) {
            checkPos = checkPos.down();
            fallHeight++;
        }
        return fallHeight >= MIN_HEIGHT;
    }

    // ============================================================
    // Phase management
    // ============================================================
    private void advancePhase() {
        phaseBlocksPlaced = 0;
        if (phase == Phase.GOD) {
            phase = Phase.ASSIST;
            int min = assistMinBlocks.getValueInt();
            int max = Math.max(min, assistMaxBlocks.getValueInt());
            currentPhaseTarget = (min == max) ? min : java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
            lastBlockCount = mc.player != null ? totalBlockCount(mc.player) : -1;
        } else {
            phase = Phase.GOD;
            currentPhaseTarget = godBridgeBlocks.getValueInt();
        }
    }

    // ============================================================
    // Helpers
    // ============================================================
    private boolean isHoldingBlocks(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        return (main.getItem() instanceof BlockItem && main.getCount() > 0)
                || (off.getItem() instanceof BlockItem && off.getCount() > 0);
    }

    private int totalBlockCount(ClientPlayerEntity p) {
        int n = 0;
        ItemStack main = p.getMainHandStack();
        ItemStack off = p.getOffHandStack();
        if (main.getItem() instanceof BlockItem) n += main.getCount();
        if (off.getItem() instanceof BlockItem) n += off.getCount();
        return n;
    }

    private Direction cardinalFromMotion(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }

    private float lerpAngle(float from, float to, float t) {
        return from + MathHelper.wrapDegrees(to - from) * t;
    }
}
