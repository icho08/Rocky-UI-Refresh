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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

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
            mc.options.sneakKey.setPressed(false);
        }
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // Safe walk — only if the player is HOLDING a block when Require Blocks is ON
        boolean doSafeWalk = safeWalk.getValue() && (!requireBlocks.getValue() || isHoldingBlock());
        mc.options.sneakKey.setPressed(doSafeWalk);

        BlockPos feetPos   = mc.player.getBlockPos();
        BlockPos belowFeet = feetPos.down();

        BlockState belowState = mc.world.getBlockState(belowFeet);
        if (!belowState.isAir() && !belowState.isReplaceable()) return;

        // Find a solid neighbour to place against
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

        int useSlot = resolveBlockSlot();
        if (useSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.setInvSlot(useSlot);

        // Randomise hit point slightly to avoid always hitting dead centre
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double hitX = placeAgainst.getX() + 0.5 + placeDir.getOffsetX() * 0.5 + rng.nextDouble(-0.1, 0.1);
        double hitY = placeAgainst.getY() + 0.5 + placeDir.getOffsetY() * 0.5 + rng.nextDouble(-0.1, 0.1);
        double hitZ = placeAgainst.getZ() + 0.5 + placeDir.getOffsetZ() * 0.5 + rng.nextDouble(-0.1, 0.1);

        Vec3d hitVec = new Vec3d(hitX, hitY, hitZ);
        BlockHitResult hit = new BlockHitResult(hitVec, placeDir, placeAgainst, false);

        // Silent single-packet rotation — same approach as GodBridge/SmartBridge/Clutch:
        // send ONE LookAndOnGround aimed at the block face, place, no snap-back packet.
        Vec3d eyePos = mc.player.getEyePos();
        float[] look = calcLook(eyePos, hitVec);
        float targetYaw   = look[0];
        float targetPitch = MathHelper.clamp(look[1] + (float) rng.nextDouble(-3.0, 3.0), 60f, 90f);
        boolean onGround = mc.player.isOnGround();
        boolean hCol     = mc.player.horizontalCollision;

        ClientConnection conn = getConnection();
        if (conn != null) {
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, onGround, hCol));
        }

        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);

        InventoryUtils.setInvSlot(prevSlot);

        if (tower.getValue() && mc.player.isOnGround()) {
            mc.player.jump();
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
            ItemStack stack = mc.player.getInventory().getStack(idx);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return idx;
            return -1; // pinned slot is empty or not a block — don't use anything else
        }
        // Auto: find first block in hotbar
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) return i;
        }
        return -1;
    }

    private static float[] calcLook(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));
        return new float[]{ yaw, MathHelper.clamp(pitch, -90f, 90f) };
    }

    /** True only when the item the player is currently holding is a placeable block. */
    private boolean isHoldingBlock() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }
}
