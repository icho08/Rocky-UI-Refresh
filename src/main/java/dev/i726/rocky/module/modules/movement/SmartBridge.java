package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
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
import java.util.concurrent.ThreadLocalRandom;

public final class SmartBridge extends Module implements TickListener {

    /**
     * Set to true while the god phase is active so that PlayerEntityMixin.clipAtLedge
     * clips movement at the block edge (safe-walk behaviour) WITHOUT enabling the
     * standalone GodBridge module. This avoids the double-placement bug where both
     * SmartBridge and GodBridge independently place blocks in the same tick.
     */
    public static volatile boolean safeWalkActive = false;

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

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot only)"));

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
                godAutoSprint, godFallMode, godLookAhead, stopOnDamage, damageThreshold, blockSlot);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        phase               = Phase.GOD;
        phaseBlocksPlaced   = 0;
        currentPhaseTarget  = godBridgeBlocks.getValueInt();
        healthInitialized   = false;
        lastBlockCount      = -1;
        safeWalkActive      = false;
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        safeWalkActive = false;
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        Clutch.placing = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        // ── Damage threshold check ────────────────────────────────────────────
        float health = p.getHealth();
        if (!healthInitialized) {
            lastHealth = health;
            healthInitialized = true;
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

        boolean doGod    = mode.isMode(BridgeMode.SMART) ? phase == Phase.GOD : mode.isMode(BridgeMode.GOD_ONLY);
        boolean doAssist = mode.isMode(BridgeMode.SMART) ? phase == Phase.ASSIST : mode.isMode(BridgeMode.ASSIST_ONLY);

        if (doGod)         runGodPhase(p);
        else if (doAssist) runAssistPhase(p);
    }

    // ── God Phase ─────────────────────────────────────────────────────────────

    private void runGodPhase(ClientPlayerEntity p) {
        if (resolveBlockSlot() == -1) {
            safeWalkActive = false;
            mc.options.sneakKey.setPressed(false);
            return;
        }
        if (!p.isOnGround()) {
            mc.options.sneakKey.setPressed(false);
            return;
        }

        // ── Fall protection ──────────────────────────────────────────────────
        // SafeWalk: set our own static flag checked by PlayerEntityMixin.clipAtLedge.
        // This gives the same clip-at-edge behaviour as sneaking without enabling
        // GodBridge (which would cause double block placements).
        switch (godFallMode.getMode()) {
            case SafeWalk -> {
                safeWalkActive = true;
                mc.options.sneakKey.setPressed(false);
            }
            case Sneak -> {
                safeWalkActive = false;
                if (isAboutToFallOff()) {
                    mc.options.sneakKey.setPressed(true);
                    return;
                }
                mc.options.sneakKey.setPressed(false);
            }
            case Off -> {
                safeWalkActive = false;
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

        if (placeCooldown > 0) return;

        // Randomise hit point on the block face for human-looking rotation variance
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double faceOffX = placeDir.getOffsetX() * 0.5;
        double faceOffZ = placeDir.getOffsetZ() * 0.5;
        double jitterH  = rng.nextDouble(-0.12, 0.12);
        double jitterY  = rng.nextDouble(-0.15, 0.05);

        Vec3d aimPoint = Vec3d.ofCenter(standing).add(
                faceOffX + (placeDir.getOffsetX() == 0 ? jitterH : 0),
                -0.25 + jitterY,
                faceOffZ + (placeDir.getOffsetZ() == 0 ? jitterH : 0));

        int useSlot  = resolveBlockSlot();
        int prevSlot = p.getInventory().getSelectedSlot();
        if (useSlot != prevSlot) p.getInventory().setSelectedSlot(useSlot);

        BlockHitResult bhr = new BlockHitResult(aimPoint, placeDir, standing, false);
        Hand           hand = Hand.MAIN_HAND;

        // ── Single silent rotation packet ─────────────────────────────────────
        float[] look         = calcLook(p.getEyePos(), aimPoint);
        float   targetYaw    = look[0];
        float   naturalPitch = MathHelper.clamp(look[1], 50f, 80f)
                             + (float) rng.nextDouble(-4.0, 4.0);
        float   targetPitch  = MathHelper.clamp(naturalPitch, 50f, 82f);
        boolean onGround     = p.isOnGround();
        boolean hCol         = p.horizontalCollision;

        ClientConnection conn = getConnection();
        if (conn == null) {
            if (useSlot != prevSlot) p.getInventory().setSelectedSlot(prevSlot);
            return;
        }

        Clutch.placing = true;
        try {
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, onGround, hCol));
            if (mc.interactionManager.interactBlock(p, hand, bhr).isAccepted()) {
                p.swingHand(hand);
                phaseBlocksPlaced++;
                placeCooldown = 2 + (int)(Math.random() * 2);
                if (phaseBlocksPlaced >= currentPhaseTarget) advancePhase();
            }
        } finally {
            Clutch.placing = false;
            if (useSlot != prevSlot) p.getInventory().setSelectedSlot(prevSlot);
        }
    }

    // ── Assist Phase ──────────────────────────────────────────────────────────

    private void runAssistPhase(ClientPlayerEntity p) {
        safeWalkActive = false;
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
            safeWalkActive = false;
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

    /**
     * Returns the hotbar slot to use (0-8), or -1 if no usable block is available.
     * blockSlot=0 → auto-find first block in hotbar.
     * blockSlot=1-9 → use that fixed slot only (0-indexed = value-1).
     */
    private int resolveBlockSlot() {
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
        if (off.getItem() instanceof BlockItem)  n += off.getCount();
        return n;
    }

    private Direction cardinalFromMotion(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
    }

    private static float[] calcLook(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));
        return new float[]{ yaw, MathHelper.clamp(pitch, -90f, 90f) };
    }

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
