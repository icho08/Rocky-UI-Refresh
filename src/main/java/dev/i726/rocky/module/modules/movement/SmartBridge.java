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
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;

public final class SmartBridge extends Module implements TickListener {

    private static final double EDGE_DISTANCE = 0.25;
    private static final int    MIN_HEIGHT    = 1;

    private final NumberSetting godBridgeBlocks = new NumberSetting(
            EncryptedString.of("God Bridge Blocks"), 1, 64, 16, 1)
            .setDescription(EncryptedString.of("Blocks to god bridge before switching to assist mode"));

    private final BooleanSetting godAutoSprint = new BooleanSetting(
            EncryptedString.of("God Auto Sprint"), true)
            .setDescription(EncryptedString.of("Force-sprint while in god bridge phase"));

    /**
     * Fall protection modes.
     *
     * SafeWalk — uses the clipAtLedge mixin to clip movement at the block edge,
     *            exactly like vanilla sneaking. No packet manipulation. Safest.
     * Sneak    — presses the sneak key when you're about to fall. Sends real
     *            sneak packets. Legitimate and safe on all anticheats.
     * Off      — no fall protection; relies on your own timing.
     *
     * (Snap and Smooth velocity-zeroing modes were removed — they manipulate
     * client-side velocity without informing the server, which causes Grim's
     * position-prediction check to flag an "invalid move".)
     */
    public enum ProtectionMode { SafeWalk, Sneak, Off }

    private final ModeSetting<ProtectionMode> godFallMode = new ModeSetting<>(
            EncryptedString.of("God Fall Mode"), ProtectionMode.SafeWalk, ProtectionMode.class)
            .setDescription(EncryptedString.of("SafeWalk: clips at edge (safest). Sneak: auto-sneaks. Off: disabled"));

    private final NumberSetting godLookAhead = new NumberSetting(
            EncryptedString.of("God Look-Ahead"), 1, 10, 3, 1)
            .setDescription(EncryptedString.of("Ticks of velocity to project when checking for a fall"));

    private final NumberSetting assistMinBlocks = new NumberSetting(
            EncryptedString.of("Assist Min Blocks"), 1, 32, 4, 1)
            .setDescription(EncryptedString.of("Minimum blocks to bridge in assist mode (random per cycle)"));

    private final NumberSetting assistMaxBlocks = new NumberSetting(
            EncryptedString.of("Assist Max Blocks"), 1, 64, 12, 1)
            .setDescription(EncryptedString.of("Maximum blocks to bridge in assist mode (random per cycle)"));

    private final BooleanSetting stopOnDamage = new BooleanSetting(
            EncryptedString.of("Stop On Damage"), true)
            .setDescription(EncryptedString.of("Disable the module when you take damage"));

    private final NumberSetting damageThreshold = new NumberSetting(
            EncryptedString.of("Damage Threshold"), 0.0, 10.0, 0.5, 0.5)
            .setDescription(EncryptedString.of("Half-hearts of damage in one tick that trigger Stop On Damage"));

    public enum BridgeMode { SMART, GOD_ONLY, ASSIST_ONLY }
    private final ModeSetting<BridgeMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), BridgeMode.SMART, BridgeMode.class)
            .setDescription(EncryptedString.of("SMART: alternates God and Assist. GOD_ONLY: pure god bridge. ASSIST_ONLY: edge-sneak only"));

    private enum Phase { GOD, ASSIST }
    private Phase phase              = Phase.GOD;
    private int   phaseBlocksPlaced  = 0;
    private int   currentPhaseTarget = 8;
    private int   placeCooldown      = 0;
    private int   lastBlockCount     = -1;
    private float lastHealth         = 20f;
    private boolean healthInitialized = false;

    public SmartBridge() {
        super(EncryptedString.of("Smart Bridge"),
                EncryptedString.of("Intelligent bridging assist"),
                -1, CategoryManager.BRIDGING);
        addSettings(mode, godBridgeBlocks, assistMinBlocks, assistMaxBlocks,
                godAutoSprint, godFallMode, godLookAhead, stopOnDamage, damageThreshold);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        phase               = Phase.GOD;
        phaseBlocksPlaced   = 0;
        currentPhaseTarget  = godBridgeBlocks.getValueInt();
        healthInitialized   = false;
        lastBlockCount      = -1;
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (GodBridge.INSTANCE != null) GodBridge.INSTANCE.setEnabled(false);
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        Clutch.placing = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

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

        boolean doGod    = mode.isMode(BridgeMode.SMART) ? phase == Phase.GOD : mode.isMode(BridgeMode.GOD_ONLY);
        boolean doAssist = mode.isMode(BridgeMode.SMART) ? phase == Phase.ASSIST : mode.isMode(BridgeMode.ASSIST_ONLY);

        if (doGod)    runGodPhase(p);
        else if (doAssist) runAssistPhase(p);
    }

    // ── God Phase ─────────────────────────────────────────────────────────────

    private void runGodPhase(ClientPlayerEntity p) {
        if (!isHoldingBlocks(p)) {
            if (GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(false);
            mc.options.sneakKey.setPressed(false);
            return;
        }
        if (!p.isOnGround()) { mc.options.sneakKey.setPressed(false); return; }

        // ── Fall protection ──────────────────────────────────────────────────
        switch (godFallMode.getMode()) {
            case SafeWalk -> {
                // clipAtLedge mixin returns true — Minecraft's own movement
                // code clips the player at the edge. No packets modified.
                if (!GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(true);
                mc.options.sneakKey.setPressed(false);
            }
            case Sneak -> {
                if (GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(false);
                if (isAboutToFallOff()) {
                    mc.options.sneakKey.setPressed(true);
                    return; // wait until we're safe to place
                }
                mc.options.sneakKey.setPressed(false);
            }
            case Off -> {
                if (GodBridge.INSTANCE.isEnabled()) GodBridge.INSTANCE.setEnabled(false);
                mc.options.sneakKey.setPressed(false);
            }
        }

        // ── Direction and target ──────────────────────────────────────────────
        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.01) return;

        if (godAutoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        Direction placeDir = cardinalFromMotion(v.x, v.z);
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir()) return;
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) return;

        // Aim at the center of the exposed side face of the standing block
        Vec3d aimPoint = Vec3d.ofCenter(standing)
                .add(placeDir.getOffsetX() * 0.5, -0.25, placeDir.getOffsetZ() * 0.5);

        BlockHitResult bhr  = new BlockHitResult(aimPoint, placeDir, standing, false);
        Hand           hand = p.getMainHandStack().getItem() instanceof BlockItem ? Hand.MAIN_HAND : Hand.OFF_HAND;

        if (placeCooldown > 0) return;

        // ── Silent packet-level rotation ──────────────────────────────────────
        // 1. Calculate the yaw/pitch that points at aimPoint from the player's eye.
        // 2. Send a LookAndOnGround packet with that rotation — the server now
        //    believes we are looking at the target face.
        // 3. Place the block (server validates the rotation → passes).
        // 4. Send LookAndOnGround to restore original rotation.
        //
        // We never call p.setYaw() / p.setPitch(), so Minecraft's
        // lastSentYaw/lastSentPitch tracking stays in sync with the real camera
        // and there is no rotation desync or "invalid interact" flag.

        Rotation desired     = RotationUtils.getDirection(p, aimPoint);
        float    targetYaw   = (float) desired.yaw();
        float    targetPitch = MathHelper.clamp((float) desired.pitch(), 60f, 90f);
        float    origYaw     = p.getYaw();
        float    origPitch   = p.getPitch();
        boolean  onGround    = p.isOnGround();
        boolean  hCol        = p.horizontalCollision;

        ClientConnection conn = getConnection();
        if (conn == null) return;

        Clutch.placing = true;
        try {
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, onGround, hCol));
            if (mc.interactionManager.interactBlock(p, hand, bhr).isAccepted()) {
                p.swingHand(hand);
                phaseBlocksPlaced++;
                placeCooldown = 2 + (int)(Math.random() * 2); // 2-3 ticks jitter
                if (phaseBlocksPlaced >= currentPhaseTarget) advancePhase();
            }
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(origYaw, origPitch, onGround, hCol));
        } finally {
            Clutch.placing = false;
        }
    }

    // ── Assist Phase ──────────────────────────────────────────────────────────

    private void runAssistPhase(ClientPlayerEntity p) {
        mc.options.sneakKey.setPressed(isNearEdge(p));

        int currentCount = totalBlockCount(p);
        if (lastBlockCount < 0) {
            lastBlockCount = currentCount;
        } else if (currentCount < lastBlockCount) {
            phaseBlocksPlaced += lastBlockCount - currentCount;
            lastBlockCount = currentCount;
            if (phaseBlocksPlaced >= currentPhaseTarget) advancePhase();
        } else if (currentCount > lastBlockCount) {
            lastBlockCount = currentCount;
        }
    }

    // ── Phase management ──────────────────────────────────────────────────────

    private void advancePhase() {
        phaseBlocksPlaced = 0;
        if (phase == Phase.GOD) {
            phase = Phase.ASSIST;
            int min = assistMinBlocks.getValueInt();
            int max = Math.max(min, assistMaxBlocks.getValueInt());
            currentPhaseTarget = (min == max) ? min
                    : java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
            lastBlockCount = mc.player != null ? totalBlockCount(mc.player) : -1;
        } else {
            phase = Phase.GOD;
            currentPhaseTarget = godBridgeBlocks.getValueInt();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isAboutToFallOff() {
        if (mc.player == null) return false;
        Vec3d pos = mc.player.getEntityPos();
        Vec3d vel = mc.player.getVelocity();
        int   la  = godLookAhead.getValueInt();
        BlockPos next = BlockPos.ofFloored(pos.x + vel.x * la, pos.y - 1, pos.z + vel.z * la);
        return mc.world.getBlockState(next).isAir();
    }

    private boolean isNearEdge(ClientPlayerEntity p) {
        double x = p.getX(), z = p.getZ();
        double vx = p.getVelocity().x, vz = p.getVelocity().z;

        BlockPos currentBelow = BlockPos.ofFloored(x, p.getY() - 1, z);
        if (mc.world.getBlockState(currentBelow).isAir() && hasMinFallHeight(currentBelow)) return true;

        double nextX = x + vx * 3;
        double nextZ = z + vz * 3;
        double edgeX = Math.min(nextX - Math.floor(nextX), Math.ceil(nextX) - nextX);
        double edgeZ = Math.min(nextZ - Math.floor(nextZ), Math.ceil(nextZ) - nextZ);

        if (edgeX <= EDGE_DISTANCE || edgeZ <= EDGE_DISTANCE) {
            BlockPos nextBelow = BlockPos.ofFloored(nextX, p.getY() - 1, nextZ);
            return mc.world.getBlockState(nextBelow).isAir() && hasMinFallHeight(nextBelow);
        }
        return false;
    }

    private boolean hasMinFallHeight(BlockPos pos) {
        int h = 0;
        while (h < MIN_HEIGHT && mc.world.getBlockState(pos).isAir()) { pos = pos.down(); h++; }
        return h >= MIN_HEIGHT;
    }

    private boolean isHoldingBlocks(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack(), off = p.getOffHandStack();
        return (main.getItem() instanceof BlockItem && main.getCount() > 0)
                || (off.getItem() instanceof BlockItem && off.getCount() > 0);
    }

    private int totalBlockCount(ClientPlayerEntity p) {
        int n = 0;
        ItemStack main = p.getMainHandStack(), off = p.getOffHandStack();
        if (main.getItem() instanceof BlockItem) n += main.getCount();
        if (off.getItem() instanceof BlockItem)  n += off.getCount();
        return n;
    }

    private Direction cardinalFromMotion(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /**
     * Gets the {@link ClientConnection} via reflection so we can send raw
     * packets (rotation spoofs) without going through any higher-level API
     * that might add unwanted side effects.
     */
    private ClientConnection getConnection() {
        try {
            Class<?> cls = ClientCommonNetworkHandler.class;
            while (cls != null) {
                for (Field f : cls.getDeclaredFields()) {
                    if (ClientConnection.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (ClientConnection) f.get(mc.getNetworkHandler());
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
