package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class Scaffold extends Module implements TickListener {

    private final BooleanSetting safeWalk = new BooleanSetting(EncryptedString.of("Safe Walk"), true)
            .setDescription(EncryptedString.of("Forces sneak to prevent walking off edges while active"));
    private final BooleanSetting tower = new BooleanSetting(EncryptedString.of("Tower"), false)
            .setDescription(EncryptedString.of("Jumps automatically to build upward faster"));
    private final BooleanSetting sprint = new BooleanSetting(EncryptedString.of("Allow Sprint"), true)
            .setDescription(EncryptedString.of("Keeps sprint active while scaffolding"));

    public Scaffold() {
        super(EncryptedString.of("Scaffold"),
                EncryptedString.of("Automatically places blocks under your feet as you walk"),
                -1, CategoryManager.BRIDGING);
        addSettings(safeWalk, tower, sprint);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        mc.options.sneakKey.setPressed(safeWalk.getValue());

        BlockPos feetPos   = mc.player.getBlockPos();
        BlockPos belowFeet = feetPos.down();

        BlockState belowState = mc.world.getBlockState(belowFeet);
        if (!belowState.isAir() && !belowState.isReplaceable()) return;

        Direction[] order = {
            Direction.DOWN,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
            Direction.UP
        };

        BlockPos  placeAgainst = null;
        Direction placeDir     = null;

        for (Direction dir : order) {
            BlockPos nb = belowFeet.offset(dir);
            if (nb.equals(feetPos)) continue;
            BlockState ns = mc.world.getBlockState(nb);
            if (ns.isAir() || ns.isReplaceable() || ns.isLiquid()) continue;
            placeAgainst = nb;
            placeDir     = dir.getOpposite();
            break;
        }

        if (placeAgainst == null) return;

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.setInvSlot(blockSlot);

        Vec3d hitVec = new Vec3d(
                placeAgainst.getX() + 0.5 + placeDir.getOffsetX() * 0.5,
                placeAgainst.getY() + 0.5 + placeDir.getOffsetY() * 0.5,
                placeAgainst.getZ() + 0.5 + placeDir.getOffsetZ() * 0.5);

        BlockHitResult hit = new BlockHitResult(hitVec, placeDir, placeAgainst, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        InventoryUtils.setInvSlot(prevSlot);

        if (tower.getValue() && mc.player.isOnGround()) {
            mc.player.jump();
        }
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }
}
