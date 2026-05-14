package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
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
    private final NumberSetting reach = new NumberSetting(EncryptedString.of("Reach"), 1, 4, 2, 1)
            .setDescription(EncryptedString.of("Max blocks below player to search for a support surface"));

    public Scaffold() {
        super(EncryptedString.of("Scaffold"),
                EncryptedString.of("Automatically places blocks under your feet as you walk"),
                -1, CategoryManager.BRIDGING);
        addSettings(safeWalk, tower, sprint, reach);
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

        // Safe walk: hold sneak key to prevent accidental fall
        mc.options.sneakKey.setPressed(safeWalk.getValue());

        BlockPos feetPos  = mc.player.getBlockPos();
        BlockPos belowFeet = feetPos.down();

        // Only scaffold when block directly below feet is absent / replaceable
        BlockState belowState = mc.world.getBlockState(belowFeet);
        if (!belowState.isAir() && !belowState.isReplaceable()) return;

        // Find the uppermost solid block within reach range to place against
        BlockPos support = null;
        for (int i = 1; i <= reach.getValueInt() + 1; i++) {
            BlockPos candidate = belowFeet.down(i);
            BlockState cs = mc.world.getBlockState(candidate);
            if (!cs.isAir() && !cs.isReplaceable()) {
                support = candidate;
                break;
            }
        }
        if (support == null) return;

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.setInvSlot(blockSlot);

        // Click the top face of the support block to place on top of it
        BlockHitResult hit = new BlockHitResult(
                Vec3d.ofCenter(support).add(0, 0.5, 0),
                Direction.UP, support, false);

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        InventoryUtils.setInvSlot(prevSlot);

        // Tower: jump to gain height while placing
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
