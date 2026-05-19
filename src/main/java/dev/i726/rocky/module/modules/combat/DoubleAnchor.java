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
import org.lwjgl.glfw.GLFW;

/**
 * Places a Respawn Anchor in front of the player, charges it with glowstone,
 * then explodes it — all automatically in sequence.
 *
 * State flow:
 *   IDLE → PLACE → CHARGE → EXPLODE → IDLE
 */
public final class DoubleAnchor extends Module implements TickListener {

    private enum State { IDLE, PLACE, CHARGE, EXPLODE }

    private final BooleanSetting onRightClick = new BooleanSetting(
            EncryptedString.of("On Right Click"), true)
            .setDescription(EncryptedString.of("Only run while holding right mouse button"));

    private final NumberSetting placeDelay = new NumberSetting(
            EncryptedString.of("Place Delay"), 0, 20, 1, 1)
            .setDescription(EncryptedString.of("Ticks to wait after placing before charging"));

    private final NumberSetting chargeDelay = new NumberSetting(
            EncryptedString.of("Charge Delay"), 0, 20, 1, 1)
            .setDescription(EncryptedString.of("Ticks to wait after charging before exploding"));

    private final BooleanSetting switchBack = new BooleanSetting(
            EncryptedString.of("Switch Back"), true)
            .setDescription(EncryptedString.of("Return to original slot after exploding"));

    private final BooleanSetting clickSimulation = new BooleanSetting(
            EncryptedString.of("Click Simulation"), false)
            .setDescription(EncryptedString.of("Simulate right-click for CPS counters"));

    private State   state       = State.IDLE;
    private BlockPos anchorPos  = null;
    private int     delayClock  = 0;
    private int     prevSlot    = 0;

    public DoubleAnchor() {
        super(EncryptedString.of("Double Anchor"),
                EncryptedString.of("Places, charges, and explodes a Respawn Anchor in front of you"),
                -1, CategoryManager.CRYSTAL);
        addSettings(onRightClick, placeDelay, chargeDelay, switchBack, clickSimulation);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        reset();
        super.onDisable();
    }

    private void reset() {
        state      = State.IDLE;
        anchorPos  = null;
        delayClock = 0;
    }

    @Override
    public void onTick() {
        if (mc.currentScreen != null || mc.player == null || mc.world == null) { reset(); return; }

        // Gate: only run while holding right-click (if setting is on)
        if (onRightClick.getValue()
                && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                != GLFW.GLFW_PRESS) {
            if (state != State.IDLE) reset();
            return;
        }

        switch (state) {
            case IDLE    -> handleIdle();
            case PLACE   -> handlePlace();
            case CHARGE  -> handleCharge();
            case EXPLODE -> handleExplode();
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // IDLE: find a valid target position in front of the player and kick off
    // ──────────────────────────────────────────────────────────────────────
    private void handleIdle() {
        // Only activate when the player is already holding a Respawn Anchor
        if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) return;
        if (!hasGlowstone()) return;

        BlockPos target = findPlacementPos();
        if (target == null) return;

        prevSlot   = mc.player.getInventory().getSelectedSlot();
        anchorPos  = target;
        delayClock = 0;
        state      = State.PLACE;
    }

    // ──────────────────────────────────────────────────────────────────────
    // PLACE: switch to anchor and place it at anchorPos
    // ──────────────────────────────────────────────────────────────────────
    private void handlePlace() {
        if (!InventoryUtils.selectItemFromHotbar(Items.RESPAWN_ANCHOR)) { reset(); return; }

        if (delayClock < placeDelay.getValueInt()) { delayClock++; return; }
        delayClock = 0;

        // Anchor not yet in world — place it
        if (!isAnchorAt(anchorPos)) {
            BlockHitResult hit = buildPlaceHit(anchorPos);
            if (hit == null) { reset(); return; }

            if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        // Advance when the block appears in the world
        if (isAnchorAt(anchorPos)) {
            state      = State.CHARGE;
            delayClock = 0;
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // CHARGE: switch to glowstone and right-click the anchor to charge it
    // ──────────────────────────────────────────────────────────────────────
    private void handleCharge() {
        if (!isAnchorAt(anchorPos)) { reset(); return; }
        if (BlockUtils.isAnchorCharged(anchorPos)) { state = State.EXPLODE; delayClock = 0; return; }

        if (!InventoryUtils.selectItemFromHotbar(Items.GLOWSTONE)) { reset(); return; }

        if (delayClock < chargeDelay.getValueInt()) { delayClock++; return; }
        delayClock = 0;

        if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        BlockHitResult hit = buildActivateHit(anchorPos);
        if (hit == null) { reset(); return; }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    // ──────────────────────────────────────────────────────────────────────
    // EXPLODE: right-click the charged anchor to explode it
    // ──────────────────────────────────────────────────────────────────────
    private void handleExplode() {
        if (!BlockUtils.isAnchorCharged(anchorPos)) {
            // Anchor was already broken or uncharged — abort
            if (switchBack.getValue()) InventoryUtils.setInvSlot(prevSlot);
            reset();
            return;
        }

        if (clickSimulation.getValue()) MouseSimulation.mouseClick(GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        BlockHitResult hit = buildActivateHit(anchorPos);
        if (hit != null) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        if (switchBack.getValue()) InventoryUtils.setInvSlot(prevSlot);
        reset();
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Finds the first replaceable air block directly in front of the player
     * (at foot level or one above) that has a solid neighbor to place against.
     */
    private BlockPos findPlacementPos() {
        Direction facing = mc.player.getHorizontalFacing();
        BlockPos foot    = mc.player.getBlockPos();

        // Priorities: in front at foot, in front one up, directly below facing block
        BlockPos[] candidates = {
            foot.offset(facing),
            foot.offset(facing).up(),
            foot.offset(facing).down()
        };

        for (BlockPos candidate : candidates) {
            if (!mc.world.getBlockState(candidate).isReplaceable()) continue;
            // Need at least one solid neighbour to place against
            if (hasSolidNeighbour(candidate)) return candidate;
        }
        return null;
    }

    /** Returns true if at least one adjacent block is solid (can be placed against). */
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

    /**
     * Builds a BlockHitResult aimed at the solid neighbour of {@code pos},
     * so that Minecraft places the block at {@code pos}.
     */
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

    /**
     * Builds a BlockHitResult pointing at the top face of {@code pos} itself,
     * used for charging (glowstone) and exploding (any item).
     */
    private BlockHitResult buildActivateHit(BlockPos pos) {
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return new BlockHitResult(center, Direction.UP, pos, false);
    }

    private boolean isAnchorAt(BlockPos pos) {
        return pos != null && mc.world.getBlockState(pos).getBlock() == Blocks.RESPAWN_ANCHOR;
    }

    private boolean hasGlowstone() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.GLOWSTONE)) return true;
        }
        return false;
    }
}
