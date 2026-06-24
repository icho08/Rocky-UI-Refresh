package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.PlayerTickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import dev.i726.rocky.utils.RotationUtils;
import dev.i726.rocky.utils.rotation.Rotation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class BoxIn extends Module implements PlayerTickListener {

    public enum BlockType { OBSIDIAN, ENDER_CHEST, WOOL, ANY }

    private final ModeSetting<BlockType> blockType = new ModeSetting<>(
            EncryptedString.of("Block Type"), BlockType.OBSIDIAN, BlockType.class);

    private final BooleanSetting rotate = new BooleanSetting(EncryptedString.of("Rotate"), true)
            .setDescription(EncryptedString.of("Silently rotate toward each block before placing"));

    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 1, 1)
            .setDescription(EncryptedString.of("Ticks between placements"));

    private final NumberSetting extraDelay = new NumberSetting(EncryptedString.of("Extra Random Delay"), 0, 5, 1, 1);

    private final BooleanSetting feet  = new BooleanSetting(EncryptedString.of("Feet"),  true);
    private final BooleanSetting head  = new BooleanSetting(EncryptedString.of("Head"),  true);
    private final BooleanSetting roof  = new BooleanSetting(EncryptedString.of("Roof"),  true);
    private final BooleanSetting floor = new BooleanSetting(EncryptedString.of("Floor"), false);

    private final BooleanSetting skipFacing = new BooleanSetting(
            EncryptedString.of("Skip Facing Side"), false)
            .setDescription(EncryptedString.of("Skip the side the player is currently looking toward"));

    private final BooleanSetting disableAfter = new BooleanSetting(
            EncryptedString.of("Auto Disable"), true);

    // Queue holds positions that STILL need to be placed (with retry on failure)
    private final List<BlockPos> placeQueue = new ArrayList<>();
    private int tickCounter   = 0;
    private int currentDelay  = 0;
    private int blockSlot     = -1;
    private int prevSlot      = -1;

    public BoxIn() {
        super(EncryptedString.of("Auto Trap"),
                EncryptedString.of("Boxes yourself in blocks for protection"),
                -1, CategoryManager.CRYSTAL);
        addSettings(blockType, rotate, delay, extraDelay, feet, head, roof, floor, skipFacing, disableAfter);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) { setEnabled(false); return; }

        buildQueue();

        if (placeQueue.isEmpty()) { setEnabled(false); return; }

        blockSlot = findBlockSlot();
        if (blockSlot == -1) { setEnabled(false); return; }

        prevSlot     = mc.player.getInventory().getSelectedSlot();
        tickCounter  = 0;
        currentDelay = nextDelay();

        // Switch to block slot ONCE at enable — stay on it until done
        InventoryUtils.setInvSlot(blockSlot);

        eventManager.add(PlayerTickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        // Restore hotbar slot when done/disabled
        if (prevSlot != -1 && mc.player != null) {
            InventoryUtils.setInvSlot(prevSlot);
        }
        eventManager.remove(PlayerTickListener.class, this);
        placeQueue.clear();
        blockSlot = -1;
        prevSlot  = -1;
        super.onDisable();
    }

    private void buildQueue() {
        placeQueue.clear();
        BlockPos   p      = mc.player.blockPosition();
        Direction  facing = mc.player.getDirection();

        if (floor.getValue()) placeQueue.add(p.below());

        if (feet.getValue()) {
            addSide(p, Direction.NORTH, facing);
            addSide(p, Direction.EAST,  facing);
            addSide(p, Direction.SOUTH, facing);
            addSide(p, Direction.WEST,  facing);
        }
        if (head.getValue()) {
            BlockPos h = p.above();
            addSide(h, Direction.NORTH, facing);
            addSide(h, Direction.EAST,  facing);
            addSide(h, Direction.SOUTH, facing);
            addSide(h, Direction.WEST,  facing);
        }
        if (roof.getValue()) placeQueue.add(p.above(2));
    }

    private void addSide(BlockPos base, Direction dir, Direction facing) {
        if (skipFacing.getValue() && dir == facing) return;
        placeQueue.add(base.relative(dir));
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.level == null) { setEnabled(false); return; }

        // Remove positions that are already filled
        placeQueue.removeIf(pos ->
                !mc.level.getBlockState(pos).canBeReplaced()
                && mc.level.getBlockState(pos).isRedstoneConductor(mc.level, pos));

        if (placeQueue.isEmpty()) {
            if (disableAfter.getValue()) setEnabled(false);
            return;
        }

        // Verify we still have the right block
        if (mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot()).isEmpty()
                || !(mc.player.getInventory().getItem(mc.player.getInventory().getSelectedSlot()).getItem() instanceof BlockItem)) {
            // Our slot ran out — find a new one
            blockSlot = findBlockSlot();
            if (blockSlot == -1) { setEnabled(false); return; }
            InventoryUtils.setInvSlot(blockSlot);
        }

        tickCounter++;
        if (tickCounter < currentDelay) return;
        tickCounter  = 0;
        currentDelay = nextDelay();

        // Find the first block in queue we can actually place
        for (int i = 0; i < placeQueue.size(); i++) {
            BlockPos pos = placeQueue.get(i);

            // Skip already-occupied positions
            if (!mc.level.getBlockState(pos).canBeReplaced()) {
                placeQueue.remove(i);
                i--;
                continue;
            }

            boolean placed = tryPlace(pos);
            if (placed) {
                placeQueue.remove(i);
                break;         // one block per tick-cycle
            }
            // If tryPlace failed for this pos, leave it in queue and try next
        }
    }

    /**
     * Attempts to place a block at {@code pos} by finding a solid adjacent face.
     * Returns true if the interaction packet was sent successfully.
     */
    private boolean tryPlace(BlockPos pos) {
        // Priority order: try DOWN first (floor support), then horizontal, then UP
        Direction[] priorityOrder = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.UP
        };

        for (Direction dir : priorityOrder) {
            BlockPos  neighbour = pos.relative(dir);
            Direction placeSide = dir.getOpposite(); // face of neighbour we click on

            // Neighbour must exist and be a real solid block (not air, not replaceable)
            if (mc.level.getBlockState(neighbour).isAir()) continue;
            if (mc.level.getBlockState(neighbour).canBeReplaced()) continue;

            // Build hit vector at the center of the clicked face
            Vec3 hitVec = new Vec3(
                neighbour.getX() + 0.5 + placeSide.getStepX() * 0.4,
                neighbour.getY() + 0.5 + placeSide.getStepY() * 0.4,
                neighbour.getZ() + 0.5 + placeSide.getStepZ() * 0.4
            );

            if (rotate.getValue()) {
                Rotation rotation = RotationUtils.getDirection(mc.player, hitVec);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(
                        (float) rotation.yaw(), (float) rotation.pitch(),
                        mc.player.onGround(), false));
            }

            BlockHitResult hit = new BlockHitResult(hitVec, placeSide, neighbour, false);
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private int nextDelay() {
        int base  = delay.getValueInt();
        int extra = extraDelay.getValueInt();
        return base + (extra > 0 ? (int)(Math.random() * (extra + 1)) : 0);
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            if (blockType.isMode(BlockType.OBSIDIAN)    && b == Blocks.OBSIDIAN)    return i;
            if (blockType.isMode(BlockType.ENDER_CHEST) && b == Blocks.ENDER_CHEST) return i;
            if (blockType.isMode(BlockType.WOOL) && b.getDescriptionId().contains("wool")) return i;
            if (blockType.isMode(BlockType.ANY))                                    return i;
        }
        return -1;
    }
}
