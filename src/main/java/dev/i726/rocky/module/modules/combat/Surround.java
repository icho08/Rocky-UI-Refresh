package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class Surround extends Module implements TickListener {

    private final NumberSetting delay = new NumberSetting(
            EncryptedString.of("Delay"), 0, 500, 50, 10)
            .setDescription(EncryptedString.of("Milliseconds between placing each block"));

    private final BooleanSetting onlyGround = new BooleanSetting(
            EncryptedString.of("Only On Ground"), true)
            .setDescription(EncryptedString.of("Only place while standing on the ground"));

    private final BooleanSetting center = new BooleanSetting(
            EncryptedString.of("Center"), true)
            .setDescription(EncryptedString.of("Snap to block center before surrounding"));

    private final BooleanSetting renderPlaced = new BooleanSetting(
            EncryptedString.of("Extend"), false)
            .setDescription(EncryptedString.of("Also place a second ring one block out for better protection"));

    private final TimerUtils placeTimer = new TimerUtils();

    // Cardinal offsets at foot level (Y-1 for placing on top)
    private static final int[][] INNER = {
            { 1, 0,  0}, {-1, 0, 0}, {0, 0,  1}, {0, 0, -1}
    };
    private static final int[][] OUTER = {
            { 2, 0,  0}, {-2, 0, 0}, {0, 0,  2}, {0, 0, -2},
            { 1, 0,  1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1}
    };

    public Surround() {
        super(EncryptedString.of("Surround"),
                EncryptedString.of("Places blocks around your feet for crystal PvP protection"),
                -1, CategoryManager.CRYSTAL);
        addSettings(delay, onlyGround, center, renderPlaced);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        placeTimer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;
        if (onlyGround.getValue() && !mc.player.isOnGround()) return;
        if (!placeTimer.delay((float) delay.getValue())) return;

        // Snap to center of block
        if (center.getValue()) {
            double cx = Math.floor(mc.player.getX()) + 0.5;
            double cz = Math.floor(mc.player.getZ()) + 0.5;
            if (Math.abs(mc.player.getX() - cx) > 0.1 || Math.abs(mc.player.getZ() - cz) > 0.1) {
                mc.player.setVelocity(
                        (cx - mc.player.getX()) * 0.5,
                        mc.player.getVelocity().y,
                        (cz - mc.player.getZ()) * 0.5);
            }
        }

        // Find a block item to place
        int slot = findBlockSlot();
        if (slot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);

        BlockPos feet = BlockPos.ofFloored(mc.player.getX(), mc.player.getY(), mc.player.getZ());

        boolean placed = false;
        int[][] offsets = renderPlaced.getValue()
                ? concat(INNER, OUTER) : INNER;

        for (int[] off : offsets) {
            BlockPos target = feet.add(off[0], off[1], off[2]);
            if (!mc.world.getBlockState(target).isAir()) continue;

            // Need a solid support block beneath
            BlockPos support = target.down();
            if (mc.world.getBlockState(support).isAir()) continue;

            Vec3d hitVec = Vec3d.ofCenter(target).add(0, 0.5, 0);
            BlockHitResult bhr = new BlockHitResult(hitVec, Direction.UP, support, false);

            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, bhr);
            mc.player.swingHand(Hand.MAIN_HAND);
            placed = true;
            break;
        }

        mc.player.getInventory().setSelectedSlot(prevSlot);
        if (placed) placeTimer.reset();
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem bi)) continue;
            var block = bi.getBlock();
            // Prefer obsidian, then any solid block
            if (block == Blocks.OBSIDIAN || block == Blocks.CRYING_OBSIDIAN) return i;
        }
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof BlockItem)) continue;
            if (stack.getItem() == Items.DIRT || stack.getItem() == Items.COBBLESTONE
                    || stack.getItem() == Items.STONE || stack.getItem() == Items.SAND) return i;
        }
        // Any block item
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private static int[][] concat(int[][] a, int[][] b) {
        int[][] result = new int[a.length + b.length][];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
