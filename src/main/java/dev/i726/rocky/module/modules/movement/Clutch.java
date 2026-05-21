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
import net.minecraft.item.BlockItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * Clutch — places a block under your feet when you are falling to save you.
 *
 * Replicates Vape's Clutch behaviour:
 *   1. Watches for Y velocity below the trigger threshold while airborne.
 *   2. Optionally restricts activation to when no ground exists within
 *      "Void Check" blocks below (so it doesn't fire over normal terrain).
 *   3. Switches to a block in the hotbar if needed, places, and switches back.
 */
public final class Clutch extends Module implements TickListener {

    private final NumberSetting fallSpeed = new NumberSetting(
            EncryptedString.of("Fall Speed"), 0.0, 5.0, 0.1, 0.05)
            .setDescription(EncryptedString.of("Y velocity drop (negative) required to trigger. 0 = any downward motion"));

    private final BooleanSetting onlyVoid = new BooleanSetting(
            EncryptedString.of("Only Void"), false)
            .setDescription(EncryptedString.of("Only trigger when there is no ground within Void Check blocks below"));

    private final NumberSetting voidCheck = new NumberSetting(
            EncryptedString.of("Void Check"), 2, 64, 5, 1)
            .setDescription(EncryptedString.of("How many blocks to look below before deciding it is a void/gap"));

    private final BooleanSetting onSneak = new BooleanSetting(
            EncryptedString.of("Only on Sneak"), false)
            .setDescription(EncryptedString.of("Only clutch while holding the sneak key"));

    private final BooleanSetting switchToBlock = new BooleanSetting(
            EncryptedString.of("Switch to Block"), true)
            .setDescription(EncryptedString.of("Auto-switch to the first block in your hotbar if you are not already holding one"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Return to the original hotbar slot after placing"));

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), false)
            .setDescription(EncryptedString.of("Simulate right-click for CPS counters"));

    private int prevSlot = -1;

    public Clutch() {
        super(EncryptedString.of("Clutch"),
                EncryptedString.of("Places a block under you when falling to save your life"),
                -1, CategoryManager.MOVEMENT);
        addSettings(fallSpeed, onlyVoid, voidCheck, onSneak, switchToBlock, switchBack, clickSimulation);
    }

    @Override
    public void onEnable() {
        prevSlot = -1;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        tryRestoreSlot();
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Restore slot as soon as we land
        if (mc.player.isOnGround()) {
            tryRestoreSlot();
            return;
        }

        // Gate: sneak required?
        if (onSneak.getValue() && !mc.player.isSneaking()) return;

        // Gate: must be falling fast enough
        double vy = mc.player.getVelocity().y;
        if (vy >= -fallSpeed.getValue()) return;

        // Gate: void check
        if (onlyVoid.getValue() && hasGroundBelow((int) voidCheck.getValue())) return;

        // Ensure we're holding a block item
        if (!holdingBlock()) {
            if (!switchToBlock.getValue()) return;
            int blockSlot = findBlockInHotbar();
            if (blockSlot == -1) return;
            if (prevSlot == -1) prevSlot = mc.player.getInventory().getSelectedSlot();
            InventoryUtils.setInvSlot(blockSlot);
            if (!holdingBlock()) return; // switch failed somehow
        }

        // Find the position one block below feet and build a placement hit
        BlockPos below = mc.player.getBlockPos().down();

        // Don't place if something is already there
        if (isSolid(below)) {
            tryRestoreSlot();
            return;
        }

        BlockHitResult hit = buildPlaceHit(below);
        if (hit == null) return;

        if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        // Restore immediately if the block was placed (we'll be on ground next tick)
        tryRestoreSlot();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void tryRestoreSlot() {
        if (switchBack.getValue() && prevSlot != -1) {
            InventoryUtils.setInvSlot(prevSlot);
            prevSlot = -1;
        }
    }

    /** True if the player's main hand holds a block item. */
    private boolean holdingBlock() {
        return mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }

    /** Returns the first hotbar slot (0–8) that contains a placeable block, or -1. */
    private int findBlockInHotbar() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof BlockItem) {
                return i;
            }
        }
        return -1;
    }

    /** True if there is any solid (non-air, non-fluid) block within {@code depth} blocks below feet. */
    private boolean hasGroundBelow(int depth) {
        BlockPos foot = mc.player.getBlockPos();
        for (int i = 1; i <= depth; i++) {
            if (isSolid(foot.down(i))) return true;
        }
        return false;
    }

    private boolean isSolid(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return !(state.getBlock() instanceof AirBlock) && !(state.getBlock() instanceof FluidBlock)
                && !state.isReplaceable();
    }

    /**
     * Builds a BlockHitResult that places a block at {@code pos} by aiming
     * at the nearest solid neighbour's face.
     *
     * Priority: below (place on top of the block one further down),
     * then the four cardinal sides, then above.
     */
    private BlockHitResult buildPlaceHit(BlockPos pos) {
        // Preferred: stand on top of the block directly below pos
        Direction[] priority = {
            Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP
        };

        for (Direction dir : priority) {
            BlockPos nb = pos.offset(dir);
            if (!isSolid(nb)) continue;

            Direction face = dir.getOpposite(); // face of the neighbour we are placing against
            Vec3d hitVec = new Vec3d(
                    nb.getX() + 0.5 + face.getOffsetX() * 0.3,
                    nb.getY() + 0.5 + face.getOffsetY() * 0.3,
                    nb.getZ() + 0.5 + face.getOffsetZ() * 0.3
            );
            return new BlockHitResult(hitVec, face, nb, false);
        }
        return null;
    }
}
