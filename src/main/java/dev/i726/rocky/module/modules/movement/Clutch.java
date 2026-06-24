package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.MouseSimulation;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Clutch — saves you from falling by silently rotating toward the nearest
 * placeable solid surface below (scanning up to 4 levels down), placing a
 * block there, then letting Minecraft's next natural PositionAndRotation
 * packet restore the original rotation.
 *
 * The server sees:
 *   1. LookAndOnGround  (aimed at the solid surface below)
 *   2. PlayerInteractBlockC2SPacket  (block placement)
 *
 * A public {@link #placing} flag is exposed so SilentAim skips its own
 * rotation injection during this window.
 */
public final class Clutch extends Module implements TickListener {

    public static volatile boolean placing = false;

    private final NumberSetting fallSpeed = new NumberSetting(
            EncryptedString.of("Fall Speed"), 0.0, 5.0, 0.1, 0.05)
            .setDescription(EncryptedString.of("Y velocity drop (negative) needed to trigger. 0 = any downward motion"));

    private final BooleanSetting onlyVoid = new BooleanSetting(
            EncryptedString.of("Only Void"), false)
            .setDescription(EncryptedString.of("Only trigger when there is no ground within Void Check blocks below"));

    private final NumberSetting voidCheck = new NumberSetting(
            EncryptedString.of("Void Check"), 2, 64, 5, 1)
            .setDescription(EncryptedString.of("Blocks to scan below before deciding it is a void/gap"));

    private final BooleanSetting onSneak = new BooleanSetting(
            EncryptedString.of("Only on Sneak"), false)
            .setDescription(EncryptedString.of("Only clutch while holding the sneak key"));

    private final BooleanSetting switchToBlock = new BooleanSetting(
            EncryptedString.of("Switch to Block"), true)
            .setDescription(EncryptedString.of("Auto-switch to the first block in your hotbar if not already holding one"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Return to original hotbar slot after placing"));

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), false)
            .setDescription(EncryptedString.of("Simulate right-click for CPS counters"));

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot only)"));

    private int prevSlot = -1;

    public Clutch() {
        super(EncryptedString.of("Clutch"),
                EncryptedString.of("Places a block under you when falling to save your life"),
                -1, CategoryManager.BRIDGING);
        addSettings(fallSpeed, onlyVoid, voidCheck, onSneak, switchToBlock, switchBack, clickSimulation, blockSlot);
    }

    @Override
    public void onEnable() {
        prevSlot = -1;
        placing  = false;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        placing = false;
        tryRestoreSlot();
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        if (mc.player.onGround()) {
            tryRestoreSlot();
            return;
        }

        if (onSneak.getValue() && !mc.player.isShiftKeyDown()) return;

        double vy = mc.player.getDeltaMovement().y;
        if (vy >= -fallSpeed.getValue()) return;

        if (onlyVoid.getValue() && hasGroundBelow((int) voidCheck.getValue())) return;

        // Ensure we hold a block
        if (!holdingBlock()) {
            if (!switchToBlock.getValue()) return;
            int slot = findBlockInHotbar();
            if (slot == -1) return;
            if (prevSlot == -1) prevSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtils.setInvSlot(slot);
            if (!holdingBlock()) return;
        }

        // foot = the block position AT the player's feet
        // Only bail if the player is already INSIDE a solid block (shouldn't happen normally).
        // We intentionally do NOT bail when foot.down() is solid — that's exactly when we
        // want to clutch (ground is one block below and the player is still falling).
        BlockPos foot = mc.player.blockPosition();
        if (isSolid(foot)) { tryRestoreSlot(); return; }

        // Scan downward up to 4 levels for the first solid surface we can click.
        // Priority: directly below foot, then one further, etc.
        BlockHitResult hit = null;
        for (int dy = 0; dy <= 3; dy++) {
            hit = buildPlaceHit(foot.below(dy));
            if (hit != null) break;
        }
        if (hit == null) return;

        // ── Silent rotation + placement ───────────────────────────────────────
        Vec3   hitVec     = hit.getLocation();
        Vec3   eye        = mc.player.getEyePosition();
        float[] look       = calcLook(eye, hitVec);
        float   blockYaw   = look[0];
        float   blockPitch = look[1];
        boolean onGround   = mc.player.onGround();
        boolean hCol       = mc.player.horizontalCollision;

        Connection conn = getConnection();
        if (conn == null) return;

        placing = true;
        try {
            conn.send(new ServerboundMovePlayerPacket.Rot(blockYaw, blockPitch, onGround, hCol));

            if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        } finally {
            placing = false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void tryRestoreSlot() {
        if (switchBack.getValue() && prevSlot != -1) {
            InventoryUtils.setInvSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private boolean holdingBlock() {
        return mc.player.getMainHandItem().getItem() instanceof BlockItem;
    }

    private int findBlockInHotbar() {
        int setting = blockSlot.getValueInt();
        if (setting >= 1 && setting <= 9) {
            int idx = setting - 1;
            ItemStack stack = mc.player.getInventory().getItem(idx);
            return (!stack.isEmpty() && stack.getItem() instanceof BlockItem) ? idx : -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private boolean hasGroundBelow(int depth) {
        BlockPos foot = mc.player.blockPosition();
        for (int i = 1; i <= depth; i++) {
            if (isSolid(foot.below(i))) return true;
        }
        return false;
    }

    private boolean isSolid(BlockPos pos) {
        var state = mc.level.getBlockState(pos);
        return !(state.getBlock() instanceof AirBlock)
                && !(state.getBlock() instanceof LiquidBlock)
                && !state.canBeReplaced();
    }

    /**
     * Builds a BlockHitResult that places a block at {@code pos} by hitting
     * the nearest solid neighbour face.
     * Priority: down (place on top of ground below), then N/S/W/E, then up.
     */
    private BlockHitResult buildPlaceHit(BlockPos pos) {
        Direction[] priority = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, Direction.UP
        };
        for (Direction dir : priority) {
            BlockPos nb = pos.relative(dir);
            if (!isSolid(nb)) continue;
            Direction face = dir.getOpposite();
            Vec3 hitVec = new Vec3(
                    nb.getX() + 0.5 + face.getStepX() * 0.3,
                    nb.getY() + 0.5 + face.getStepY() * 0.3,
                    nb.getZ() + 0.5 + face.getStepZ() * 0.3);
            return new BlockHitResult(hitVec, face, nb, false);
        }
        return null;
    }

    private static float[] calcLook(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));
        return new float[]{ yaw, Mth.clamp(pitch, -90f, 90f) };
    }

    private Connection getConnection() {
        try {
            Class<?> cls = ClientCommonPacketListenerImpl.class;
            while (cls != null) {
                for (Field f : cls.getDeclaredFields()) {
                    if (Connection.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (Connection) f.get(mc.getConnection());
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
