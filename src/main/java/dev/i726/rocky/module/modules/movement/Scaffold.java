package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.InventoryUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;

/**
 * Scaffold — places blocks under your feet as you walk.
 *
 * Safe Walk uses the {@code PlayerEntityMixin.clipAtLedge} injection (same as
 * GodBridge) rather than pressing the sneak key. This means:
 *   - No extra sneak packets spamming the server every tick.
 *   - Movement stays full-speed (no vanilla sneak slow-down).
 *   - The server sees normal positions, nothing to flag.
 *
 * Block placement uses packet-level silent rotation so Grim's
 * "interact with what you look at" check passes every time.
 */
public final class Scaffold extends Module implements TickListener {

    // Re-uses GodBridge's shouldSafeWalk() flag which is already wired into
    // the clipAtLedge mixin. A dedicated Scaffold safe-walk flag routes through
    // the same mixin by checking both modules.
    private final BooleanSetting safeWalk = new BooleanSetting(
            EncryptedString.of("Safe Walk"), true)
            .setDescription(EncryptedString.of("Clips movement at block edges (uses vanilla physics, anticheat-safe)"));

    private final BooleanSetting tower = new BooleanSetting(
            EncryptedString.of("Tower"), false)
            .setDescription(EncryptedString.of("Jumps automatically to build upward faster"));

    private final BooleanSetting sprint = new BooleanSetting(
            EncryptedString.of("Allow Sprint"), true)
            .setDescription(EncryptedString.of("Keeps sprint active while scaffolding"));

    public static Scaffold INSTANCE;

    public Scaffold() {
        super(EncryptedString.of("Scaffold"),
                EncryptedString.of("Automatically places blocks under your feet as you walk"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(safeWalk, tower, sprint);
    }

    /** Called by PlayerEntityMixin.clipAtLedge to prevent walking off edges. */
    public static boolean shouldSafeWalk() {
        return INSTANCE != null && INSTANCE.isEnabled() && INSTANCE.safeWalk.getValue();
    }

    @Override
    public void onEnable() {
        Clutch.placing = false;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        Clutch.placing = false;
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        // SafeWalk is handled by the clipAtLedge mixin — no sneakKey spam needed.
        // We only press sneak here if Safe Walk is OFF and Tower mode needs it.

        BlockPos feetPos   = mc.player.getBlockPos();
        BlockPos belowFeet = feetPos.down();

        BlockState belowState = mc.world.getBlockState(belowFeet);
        if (!belowState.isAir() && !belowState.isReplaceable()) {
            // Ground exists — handle tower mode
            if (tower.getValue() && mc.player.isOnGround()) mc.player.jump();
            return;
        }

        // Find a solid neighbour face to place against
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
            placeDir     = dir.getOpposite(); // face of nb pointing toward belowFeet
            break;
        }

        if (placeAgainst == null) return;

        int blockSlot = findBlockSlot();
        if (blockSlot == -1) return;

        int prevSlot = mc.player.getInventory().getSelectedSlot();
        InventoryUtils.setInvSlot(blockSlot);

        // Hit point on the face of placeAgainst that faces belowFeet
        Vec3d hitVec = new Vec3d(
                placeAgainst.getX() + 0.5 + placeDir.getOffsetX() * 0.5,
                placeAgainst.getY() + 0.5 + placeDir.getOffsetY() * 0.5,
                placeAgainst.getZ() + 0.5 + placeDir.getOffsetZ() * 0.5);

        BlockHitResult hit = new BlockHitResult(hitVec, placeDir, placeAgainst, false);

        // ── Silent packet rotation ────────────────────────────────────────────
        // Calculate exact yaw/pitch from player's eye to the hit point, send it
        // before the interact packet, restore after. Never touches setYaw/setPitch
        // so lastSentYaw/Pitch stays in sync and there is no rotation desync.
        float[] look      = calcLook(mc.player.getEyePos(), hitVec);
        float targetYaw   = look[0];
        float targetPitch = MathHelper.clamp(look[1], -90f, 90f);
        float origYaw     = mc.player.getYaw();
        float origPitch   = mc.player.getPitch();
        boolean onGround  = mc.player.isOnGround();
        boolean hCol      = mc.player.horizontalCollision;

        ClientConnection conn = getConnection();
        if (conn != null) {
            Clutch.placing = true;
            try {
                conn.send(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, onGround, hCol));
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                conn.send(new PlayerMoveC2SPacket.LookAndOnGround(origYaw, origPitch, onGround, hCol));
            } finally {
                Clutch.placing = false;
            }
        } else {
            // Fallback: place without silent rotation (unlikely but safe)
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
            mc.player.swingHand(Hand.MAIN_HAND);
        }

        InventoryUtils.setInvSlot(prevSlot);

        if (tower.getValue() && mc.player.isOnGround()) mc.player.jump();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int findBlockSlot() {
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

    private ClientConnection getConnection() {
        try {
            Class<?> cls = ClientCommonNetworkHandler.class;
            while (cls != null) {
                for (Field f : cls.getDeclaredFields()) {
                    if (ClientConnection.class.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        return (ClientConnection) f.get(mc.getNetworkHandler());
                    }
                }
                cls = cls.getSuperclass();
            }
        } catch (Exception ignored) {}
        return null;
    }
}
