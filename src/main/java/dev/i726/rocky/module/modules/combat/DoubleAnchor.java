package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.*;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Companion to Anchor Aura (AnchorMacro).
 *
 * Once Anchor Aura charges a nearby Respawn Anchor with glowstone,
 * Double Anchor automatically places a second anchor at a valid adjacent
 * position so Anchor Aura can immediately charge and explode that one too.
 *
 * Requirements:
 *   - Player must be holding a Respawn Anchor in their main hand.
 *   - A charged Respawn Anchor must exist within range.
 *   - There must be a valid air block with a solid neighbour nearby to place into.
 */
public final class DoubleAnchor extends Module implements TickListener {

    private final NumberSetting searchRange = new NumberSetting(
            EncryptedString.of("Search Range"), 1, 6, 4.0, 0.5)
            .setDescription(EncryptedString.of("Radius to search for a charged anchor"));

    private final NumberSetting placeDelay = new NumberSetting(
            EncryptedString.of("Place Delay"), 0, 20, 2, 1)
            .setDescription(EncryptedString.of("Ticks to wait after detecting charged anchor before placing"));

    private final BooleanSetting onlyWhenHolding = new BooleanSetting(
            EncryptedString.of("Only When Holding"), true)
            .setDescription(EncryptedString.of("Only place if main hand is already a Respawn Anchor"));

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), false)
            .setDescription(EncryptedString.of("Simulate right-click for CPS counters"));

    private int delayClock = 0;

    public DoubleAnchor() {
        super(EncryptedString.of("Double Anchor"),
                EncryptedString.of("Places a second anchor when Anchor Aura finishes charging one"),
                -1, CategoryManager.CRYSTAL);
        addSettings(searchRange, placeDelay, onlyWhenHolding, clickSimulation);
    }

    @Override
    public void onEnable() {
        delayClock = 0;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        delayClock = 0;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Must be holding a Respawn Anchor (when setting is on)
        if (onlyWhenHolding.getValue()
                && !mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
            delayClock = 0;
            return;
        }

        // Find a charged anchor nearby
        BlockPos chargedAnchor = findChargedAnchor();
        if (chargedAnchor == null) {
            delayClock = 0;
            return;
        }

        // Wait out the delay before placing
        if (delayClock < placeDelay.getValueInt()) {
            delayClock++;
            return;
        }
        delayClock = 0;

        // Find a valid empty position to place the second anchor
        BlockPos placePos = findPlacementPos();
        if (placePos == null) return;

        // Build a hit result against a solid neighbour so Minecraft places at placePos
        BlockHitResult hit = buildPlaceHit(placePos);
        if (hit == null) return;

        if (clickSimulation.getValue()) MouseSimulation.mouseClick(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Scans blocks near the player for a charged Respawn Anchor. */
    private BlockPos findChargedAnchor() {
        int r = (int) Math.ceil(searchRange.getValue());
        BlockPos origin = mc.player.getBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = origin.add(x, y, z);
                    if (mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR
                            && BlockUtils.isAnchorCharged(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds the nearest replaceable air block (with a solid neighbour) around
     * the player to place the second anchor into. Prefers foot level, then up/down.
     */
    private BlockPos findPlacementPos() {
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos foot    = mc.player.getBlockPos();

        BlockPos[] candidates = {
            foot.offset(facing),
            foot.offset(facing).up(),
            foot.offset(facing).down(),
            foot.offset(facing.rotateYClockwise()),
            foot.offset(facing.rotateYCounterclockwise()),
        };

        for (BlockPos pos : candidates) {
            if (!mc.world.getBlockState(pos).isReplaceable()) continue;
            if (mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR) continue;
            if (hasSolidNeighbour(pos)) return pos;
        }
        return null;
    }

    private boolean hasSolidNeighbour(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos nb = pos.offset(dir);
            if (!mc.world.getBlockState(nb).isReplaceable()
                    && mc.world.getBlockState(nb).isSolidBlock(mc.world, nb)) {
                return true;
            }
        }
        return false;
    }

    private BlockHitResult buildPlaceHit(BlockPos pos) {
        for (Direction dir : Direction.values()) {
            BlockPos nb = pos.offset(dir);
            if (mc.world.getBlockState(nb).isReplaceable()) continue;
            if (!mc.world.getBlockState(nb).isSolidBlock(mc.world, nb)) continue;

            Direction side = dir.getOpposite();
            Vec3d hitVec = new Vec3d(
                    nb.getX() + 0.5 + side.getOffsetX() * 0.3,
                    nb.getY() + 0.5 + side.getOffsetY() * 0.3,
                    nb.getZ() + 0.5 + side.getOffsetZ() * 0.3
            );
            return new BlockHitResult(hitVec, side, nb, false);
        }
        return null;
    }
}
