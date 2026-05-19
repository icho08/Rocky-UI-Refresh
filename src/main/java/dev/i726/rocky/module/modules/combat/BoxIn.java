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
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class BoxIn extends Module implements PlayerTickListener {

    public enum BlockType { OBSIDIAN, ENDER_CHEST, WOOL, ANY }

    private final ModeSetting<BlockType> blockType = new ModeSetting<>(
            EncryptedString.of("Block Type"), BlockType.OBSIDIAN, BlockType.class);

    private final BooleanSetting rotate = new BooleanSetting(EncryptedString.of("Rotate"), true)
            .setDescription(EncryptedString.of("Silently rotate to each block before placing"));

    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 1, 1)
            .setDescription(EncryptedString.of("Ticks between each block placement"));

    private final NumberSetting extraDelay = new NumberSetting(EncryptedString.of("Extra Random Delay"), 0, 5, 1, 1)
            .setDescription(EncryptedString.of("Additional random ticks added to each placement delay"));

    private final BooleanSetting feet = new BooleanSetting(EncryptedString.of("Feet"), true);
    private final BooleanSetting head = new BooleanSetting(EncryptedString.of("Head"), true);
    private final BooleanSetting roof = new BooleanSetting(EncryptedString.of("Roof"), true);
    private final BooleanSetting floor = new BooleanSetting(EncryptedString.of("Floor"), false);

    private final BooleanSetting skipFacing = new BooleanSetting(EncryptedString.of("Skip Facing Side"), true)
            .setDescription(EncryptedString.of("Skip placing the block on the side the player is already looking at (natural behaviour)"));

    private final BooleanSetting disableAfter = new BooleanSetting(EncryptedString.of("Auto Disable"), true);

    private final List<BlockPos> placeQueue = new ArrayList<>();
    private int tickCounter   = 0;
    private int currentDelay  = 0;

    public BoxIn() {
        super(EncryptedString.of("Auto Trap"),
                EncryptedString.of("Boxes yourself in blocks for protection"),
                -1, CategoryManager.CRYSTAL);
        addSettings(blockType, rotate, delay, extraDelay, feet, head, roof, floor, skipFacing, disableAfter);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) { setEnabled(false); return; }

        eventManager.add(PlayerTickListener.class, this);

        placeQueue.clear();
        tickCounter  = 0;
        currentDelay = nextDelay();

        BlockPos p = mc.player.getBlockPos();

        // Determine which horizontal direction the player is facing — used to skip that face
        Direction facing = mc.player.getHorizontalFacing();

        if (floor.getValue()) placeQueue.add(p.down());

        // Feet level — clockwise order, skipping the faced side if requested
        if (feet.getValue()) {
            addSide(p, Direction.NORTH, facing);
            addSide(p, Direction.EAST,  facing);
            addSide(p, Direction.SOUTH, facing);
            addSide(p, Direction.WEST,  facing);
        }

        // Head level — same order
        if (head.getValue()) {
            BlockPos h = p.up();
            addSide(h, Direction.NORTH, facing);
            addSide(h, Direction.EAST,  facing);
            addSide(h, Direction.SOUTH, facing);
            addSide(h, Direction.WEST,  facing);
        }

        if (roof.getValue()) placeQueue.add(p.up(2));

        super.onEnable();
    }

    private void addSide(BlockPos base, Direction dir, Direction facing) {
        // If skipFacing is on, skip the side the player is already looking at
        if (skipFacing.getValue() && dir == facing) return;
        placeQueue.add(base.offset(dir));
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        placeQueue.clear();
        tickCounter = 0;
        super.onDisable();
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.world == null || placeQueue.isEmpty()) {
            if (disableAfter.getValue() && isEnabled()) setEnabled(false);
            return;
        }

        tickCounter++;
        if (tickCounter < currentDelay) return;
        tickCounter  = 0;
        currentDelay = nextDelay();

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) { setEnabled(false); return; }

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        // Use proper slot switch with sync packet
        InventoryUtils.setInvSlot(blockSlot);

        BlockPos pos = placeQueue.remove(0);
        if (mc.world.getBlockState(pos).isReplaceable()) {
            placeBlock(pos);
        }

        InventoryUtils.setInvSlot(prevSlot);

        if (placeQueue.isEmpty() && disableAfter.getValue()) setEnabled(false);
    }

    private int nextDelay() {
        int base  = delay.getValueInt();
        int extra = extraDelay.getValueInt();
        return base + (extra > 0 ? (int)(Math.random() * (extra + 1)) : 0);
    }

    private boolean placeBlock(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            Direction side    = direction.getOpposite();

            if (mc.world.getBlockState(neighbor).isAir()
                    || mc.world.getBlockState(neighbor).isReplaceable()) continue;

            Vec3d hitVec = new Vec3d(
                    neighbor.getX() + 0.5 + side.getOffsetX() * 0.3,
                    neighbor.getY() + 0.5 + side.getOffsetY() * 0.3,
                    neighbor.getZ() + 0.5 + side.getOffsetZ() * 0.3);

            if (rotate.getValue()) {
                Rotation rotation = RotationUtils.getDirection(mc.player, hitVec);
                // Silent rotation — only send a look packet, don't change player visual yaw/pitch
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                        (float) rotation.yaw(), (float) rotation.pitch(),
                        mc.player.isOnGround(), false));
            }

            BlockHitResult hitResult = new BlockHitResult(hitVec, side, neighbor, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        }
        return false;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            Block b = bi.getBlock();
            if (blockType.isMode(BlockType.OBSIDIAN)    && b == Blocks.OBSIDIAN)         return i;
            if (blockType.isMode(BlockType.ENDER_CHEST) && b == Blocks.ENDER_CHEST)      return i;
            if (blockType.isMode(BlockType.WOOL)        && b.getTranslationKey().contains("wool")) return i;
            if (blockType.isMode(BlockType.ANY))                                          return i;
        }
        return -1;
    }
}
