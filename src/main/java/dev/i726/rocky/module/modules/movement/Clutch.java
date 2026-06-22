package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.MouseSimulation;
import net.minecraft.block.AirBlock;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.network.ClientCommonNetworkHandler;
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
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

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
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        if (mc.player.isOnGround()) {
            tryRestoreSlot();
            return;
        }

        if (onSneak.getValue() && !mc.player.isSneaking()) return;

        double vy = mc.player.getVelocity().y;
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
        BlockPos foot = mc.player.getBlockPos();
        if (isSolid(foot)) { tryRestoreSlot(); return; }

        // Scan downward up to 4 levels for the first solid surface we can click.
        // Priority: directly below foot, then one further, etc.
        BlockHitResult hit = null;
        for (int dy = 0; dy <= 3; dy++) {
            hit = buildPlaceHit(foot.down(dy));
            if (hit != null) break;
        }
        if (hit == null) return;

        // ── Silent rotation + placement ───────────────────────────────────────
        Vec3d   hitVec     = hit.getPos();
        Vec3d   eye        = mc.player.getEyePos();
        float[] look       = calcLook(eye, hitVec);
        float   blockYaw   = look[0];
        float   blockPitch = look[1];
        boolean onGround   = mc.player.isOnGround();
        boolean hCol       = mc.player.horizontalCollision;

        ClientConnection conn = getConnection();
        if (conn == null) return;

        placing = true;
        try {
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(blockYaw, blockPitch, onGround, hCol));

            if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
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
        return mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }

    private int findBlockInHotbar() {
        int setting = blockSlot.getValueInt();
        if (setting >= 1 && setting <= 9) {
            int idx = setting - 1;
            ItemStack stack = mc.player.getInventory().getStack(idx);
            return (!stack.isEmpty() && stack.getItem() instanceof BlockItem) ? idx : -1;
        }
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private boolean hasGroundBelow(int depth) {
        BlockPos foot = mc.player.getBlockPos();
        for (int i = 1; i <= depth; i++) {
            if (isSolid(foot.down(i))) return true;
        }
        return false;
    }

    private boolean isSolid(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return !(state.getBlock() instanceof AirBlock)
                && !(state.getBlock() instanceof FluidBlock)
                && !state.isReplaceable();
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
            BlockPos nb = pos.offset(dir);
            if (!isSolid(nb)) continue;
            Direction face = dir.getOpposite();
            Vec3d hitVec = new Vec3d(
                    nb.getX() + 0.5 + face.getOffsetX() * 0.3,
                    nb.getY() + 0.5 + face.getOffsetY() * 0.3,
                    nb.getZ() + 0.5 + face.getOffsetZ() * 0.3);
            return new BlockHitResult(hitVec, face, nb, false);
        }
        return null;
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
