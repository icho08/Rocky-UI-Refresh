package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class Scaffold extends Module implements TickListener {

    private final BooleanSetting safeWalk = new BooleanSetting(EncryptedString.of("Safe Walk"), true)
            .setDescription(EncryptedString.of("Clips movement at block edges so you don't fall off while scaffolding"));
    private final BooleanSetting tower = new BooleanSetting(EncryptedString.of("Tower"), false)
            .setDescription(EncryptedString.of("Jumps automatically to build upward faster"));
    private final BooleanSetting sprint = new BooleanSetting(EncryptedString.of("Allow Sprint"), true)
            .setDescription(EncryptedString.of("Keeps sprint active while scaffolding"));
    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot to use for blocks (0 = auto-find first block, 1-9 = fixed slot)"));

    private final BooleanSetting requireBlocks = new BooleanSetting(
            EncryptedString.of("Require Blocks"), true)
            .setDescription(EncryptedString.of("When ON: safe-walk only applies when you have blocks. When OFF: always active"));

    public Scaffold() {
        super(EncryptedString.of("Scaffold"),
                EncryptedString.of("Automatically places blocks under your feet as you walk"),
                -1, CategoryManager.BLATANT);
        addSettings(safeWalk, tower, sprint, blockSlot, requireBlocks);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.options != null) {
            mc.options.keyShift.setDown(false);
        }
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || mc.screen != null) return;

        // Safe walk — only if the player is HOLDING a block when Require Blocks is ON
        boolean doSafeWalk = safeWalk.getValue() && (!requireBlocks.getValue() || isHoldingBlock());
        mc.options.keyShift.setDown(doSafeWalk);

        BlockPos feetPos   = mc.player.blockPosition();
        BlockPos belowFeet = feetPos.below();

        BlockState belowState = mc.level.getBlockState(belowFeet);
        if (!belowState.isAir() && !belowState.canBeReplaced()) return;

        // Find a solid neighbour to place against
        Direction[] order = {
            Direction.DOWN,
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST,
            Direction.UP
        };

        BlockPos  placeAgainst = null;
        Direction placeDir     = null;

        for (Direction dir : order) {
            BlockPos nb = belowFeet.relative(dir);
            if (nb.equals(feetPos)) continue;
            BlockState ns = mc.level.getBlockState(nb);
            if (ns.isAir() || ns.canBeReplaced() || ns.liquid()) continue;
            placeAgainst = nb;
            placeDir     = dir.getOpposite();
            break;
        }

        if (placeAgainst == null) return;

        int useSlot = resolveBlockSlot();
        if (useSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.setInvSlot(useSlot);

        // Randomise hit point slightly to avoid always hitting dead centre
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double hitX = placeAgainst.getX() + 0.5 + placeDir.getStepX() * 0.5 + rng.nextDouble(-0.1, 0.1);
        double hitY = placeAgainst.getY() + 0.5 + placeDir.getStepY() * 0.5 + rng.nextDouble(-0.1, 0.1);
        double hitZ = placeAgainst.getZ() + 0.5 + placeDir.getStepZ() * 0.5 + rng.nextDouble(-0.1, 0.1);

        Vec3 hitVec = new Vec3(hitX, hitY, hitZ);
        BlockHitResult hit = new BlockHitResult(hitVec, placeDir, placeAgainst, false);

        // Silent single-packet rotation — same approach as GodBridge/SmartBridge/Clutch:
        // send ONE LookAndOnGround aimed at the block face, place, no snap-back packet.
        Vec3 eyePos = mc.player.getEyePosition();
        float[] look = calcLook(eyePos, hitVec);
        float targetYaw   = look[0];
        float targetPitch = Mth.clamp(look[1] + (float) rng.nextDouble(-3.0, 3.0), 60f, 90f);
        boolean onGround = mc.player.onGround();
        boolean hCol     = mc.player.horizontalCollision;

        Connection conn = mc.getConnection().getConnection();
        if (conn != null) {
            conn.send(new ServerboundMovePlayerPacket.Rot(targetYaw, targetPitch, onGround, hCol));
        }

        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);

        InventoryUtils.setInvSlot(prevSlot);

        if (tower.getValue() && mc.player.onGround()) {
            mc.player.jumpFromGround();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the hotbar slot index (0-8) to use, respecting the Block Slot setting.
     * Setting value 0 = auto-find, 1-9 = fixed slot (converted to 0-8 index).
     */
    private int resolveBlockSlot() {
        int setting = blockSlot.getValueInt();
        if (setting >= 1 && setting <= 9) {
            int idx = setting - 1;
            ItemStack stack = mc.player.getInventory().getItem(idx);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return idx;
            return -1; // pinned slot is empty or not a block — don't use anything else
        }
        // Auto: find first block in hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private static float[] calcLook(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));
        return new float[]{ yaw, Mth.clamp(pitch, -90f, 90f) };
    }

    /** True only when the item the player is currently holding is a placeable block. */
    private boolean isHoldingBlock() {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().getItem() instanceof BlockItem;
    }
}
