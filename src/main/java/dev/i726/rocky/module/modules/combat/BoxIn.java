package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.PlayerTickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
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

    public enum BlockType {
        OBSIDIAN, ENDER_CHEST, WOOL, ANY
    }

    private final ModeSetting<BlockType> blockType = new ModeSetting<>(
            EncryptedString.of("Block Type"), BlockType.OBSIDIAN, BlockType.class)
            .setDescription(EncryptedString.of("The block to use for boxing yourself in"));

    private final BooleanSetting center = new BooleanSetting(EncryptedString.of("Center"), true)
            .setDescription(EncryptedString.of("Auto-center yourself in the block before placing"));

    private final BooleanSetting rotate = new BooleanSetting(EncryptedString.of("Rotate"), true)
            .setDescription(EncryptedString.of("Rotate to look at blocks before placing"));

    private final NumberSetting delay = new NumberSetting(EncryptedString.of("Delay"), 0, 10, 2, 1)
            .setDescription(EncryptedString.of("Ticks between each block placement"));
    
    private final BooleanSetting feet = new BooleanSetting(EncryptedString.of("Feet"), true);
    private final BooleanSetting head = new BooleanSetting(EncryptedString.of("Head"), true);
    private final BooleanSetting roof = new BooleanSetting(EncryptedString.of("Roof"), true);
    private final BooleanSetting floor = new BooleanSetting(EncryptedString.of("Floor"), false);

    private final BooleanSetting disableAfter = new BooleanSetting(EncryptedString.of("Auto Disable"), true);

    private final List<BlockPos> placeQueue = new ArrayList<>();
    private int tickCounter = 0;

    public BoxIn() {
        super(EncryptedString.of("Auto Trap"),
                EncryptedString.of("Traps players in obsidian"),
                -1,
                CategoryManager.CRYSTAL);
        addSettings(blockType, center, rotate, delay, feet, head, roof, floor, disableAfter);
    }

    @Override
    public void onEnable() {
        if (mc.player == null || mc.world == null) {
            setEnabled(false);
            return;
        }

        eventManager.add(PlayerTickListener.class, this);

        if (center.getValue()) {
            BlockPos pos = mc.player.getBlockPos();
            mc.player.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            mc.player.setVelocity(0, 0, 0);
        }

        placeQueue.clear();
        tickCounter = 0;
        BlockPos p = mc.player.getBlockPos();

        // Place in human-like order: floor -> feet (clockwise) -> head (clockwise) -> roof
        if (floor.getValue()) placeQueue.add(p.down());
        if (feet.getValue()) {
            placeQueue.add(p.north());
            placeQueue.add(p.east());
            placeQueue.add(p.south());
            placeQueue.add(p.west());
        }
        if (head.getValue()) {
            BlockPos h = p.up();
            placeQueue.add(h.north());
            placeQueue.add(h.east());
            placeQueue.add(h.south());
            placeQueue.add(h.west());
        }
        if (roof.getValue()) placeQueue.add(p.up(2));

        super.onEnable();
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
        if (tickCounter < delay.getValueInt()) return;
        tickCounter = 0;

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) {
            setEnabled(false);
            return;
        }

        int previousSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(blockSlot);

        BlockPos pos = placeQueue.remove(0);
        if (mc.world.getBlockState(pos).isReplaceable()) {
            placeBlock(pos);
        }

        mc.player.getInventory().setSelectedSlot(previousSlot);
        
        if (placeQueue.isEmpty() && disableAfter.getValue()) {
            setEnabled(false);
        }
    }

    private boolean placeBlock(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.offset(direction);
            Direction side = direction.getOpposite();

            if (mc.world.getBlockState(neighbor).isAir() || mc.world.getBlockState(neighbor).isReplaceable()) continue;

            Vec3d hitVec = new Vec3d(neighbor.getX() + 0.5 + side.getOffsetX() * 0.3,
                                     neighbor.getY() + 0.5 + side.getOffsetY() * 0.3,
                                     neighbor.getZ() + 0.5 + side.getOffsetZ() * 0.3);

            if (rotate.getValue()) {
                Rotation rotation = RotationUtils.getDirection(mc.player, hitVec);
                mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.LookAndOnGround(
                        (float) rotation.yaw(), (float) rotation.pitch(), mc.player.isOnGround(), false));
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
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (blockType.isMode(BlockType.OBSIDIAN) && block == Blocks.OBSIDIAN) return i;
                if (blockType.isMode(BlockType.ENDER_CHEST) && block == Blocks.ENDER_CHEST) return i;
                if (blockType.isMode(BlockType.WOOL) && block.getTranslationKey().contains("wool")) return i;
                if (blockType.isMode(BlockType.ANY)) return i;
            }
        }
        return -1;
    }
}
