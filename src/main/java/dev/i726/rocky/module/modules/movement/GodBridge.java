package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * GodBridge — anticheat-safe automated god bridging.
 *
 * SafeWalk is handled by the PlayerEntityMixin.clipAtLedge injection which
 * returns true whenever this module is enabled — Minecraft's own physics clips
 * movement at the block edge, so the server never sees an invalid position.
 *
 * Block placement uses a single silent rotation packet:
 *   1. LookAndOnGround aimed at a randomised point on the block face
 *   2. PlayerInteractBlockC2SPacket (via interactBlock)
 *
 * Only ONE rotation packet is sent — no snap-and-restore pair. The server sees
 * a natural "quick look → place" sequence. The rotation is returned to its
 * original value by the very next normal movement packet that Minecraft's own
 * code sends (with lastSentYaw/lastSentPitch carrying originals), so there is
 * never a bot-signature instant reverse.
 */
public final class GodBridge extends Module implements TickListener {

    public static GodBridge INSTANCE;

    private final BooleanSetting autoSprint = new BooleanSetting(
            EncryptedString.of("Auto Sprint"), true)
            .setDescription(EncryptedString.of("Force-sprint while god bridging"));

    private final NumberSetting placeDelay = new NumberSetting(
            EncryptedString.of("Place Delay"), 0, 10, 2, 1)
            .setDescription(EncryptedString.of("Base ticks between block placements"));

    private final NumberSetting placeJitter = new NumberSetting(
            EncryptedString.of("Place Jitter"), 0, 4, 1, 1)
            .setDescription(EncryptedString.of("Random extra ticks per placement (humanisation)"));

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot only)"));

    private final BooleanSetting requireBlocks = new BooleanSetting(
            EncryptedString.of("Require Blocks"), true)
            .setDescription(EncryptedString.of("When ON: safe-walk and sprint only activate if you have blocks. When OFF: always active"));

    private int cooldown = 0;

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Automated god bridging with anticheat-safe packet rotation"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(autoSprint, placeDelay, placeJitter, blockSlot, requireBlocks);
    }

    /**
     * Used by PlayerEntityMixin.clipAtLedge to keep the player from walking
     * off the edge without sending sneak packets.
     * If Require Blocks is ON, only clips at ledge when blocks are in hand.
     */
    public static boolean shouldSafeWalk() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (INSTANCE.requireBlocks.getValue()) return INSTANCE.resolveBlockSlot() != -1;
        return true;
    }

    @Override
    public void onEnable() {
        cooldown = 0;
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
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        if (resolveBlockSlot() == -1) return;
        if (!p.isOnGround()) return;

        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.01) return;

        if (autoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        if (cooldown > 0) { cooldown--; return; }

        Direction placeDir = cardinalFromMotion(v.x, v.z);
        BlockPos standing  = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos target    = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir()) return;
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) return;

        // Randomise the exact hit point on the face — avoids always hitting the exact center
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double faceOffX = placeDir.getOffsetX() * 0.5;
        double faceOffZ = placeDir.getOffsetZ() * 0.5;
        double jitterH  = rng.nextDouble(-0.12, 0.12);
        double jitterY  = rng.nextDouble(-0.15, 0.05);

        Vec3d aimPoint = Vec3d.ofCenter(standing).add(
                faceOffX + (placeDir.getOffsetX() == 0 ? jitterH : 0),
                -0.25 + jitterY,
                faceOffZ + (placeDir.getOffsetZ() == 0 ? jitterH : 0));

        int useSlot = resolveBlockSlot();
        if (useSlot == -1) return;
        int prevSlot = p.getInventory().getSelectedSlot();
        if (useSlot != prevSlot) p.getInventory().setSelectedSlot(useSlot);

        Hand hand = Hand.MAIN_HAND;
        BlockHitResult bhr = new BlockHitResult(aimPoint, placeDir, standing, false);

        float[] look       = calcLook(p.getEyePos(), aimPoint);
        float targetYaw    = look[0];
        // Humanised pitch: 50–80° range with a little random wobble
        float naturalPitch = MathHelper.clamp(look[1], 50f, 80f)
                           + (float) rng.nextDouble(-4.0, 4.0);
        float targetPitch  = MathHelper.clamp(naturalPitch, 50f, 82f);
        boolean onGround   = p.isOnGround();
        boolean hCol       = p.horizontalCollision;

        ClientConnection conn = getConnection();
        if (conn == null) return;

        Clutch.placing = true;
        try {
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, onGround, hCol));
            if (mc.interactionManager.interactBlock(p, hand, bhr).isAccepted()) {
                p.swingHand(hand);
                cooldown = placeDelay.getValueInt()
                         + (int)(Math.random() * (placeJitter.getValueInt() + 1));
            }
        } finally {
            Clutch.placing = false;
            if (useSlot != prevSlot) p.getInventory().setSelectedSlot(prevSlot);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the hotbar slot to use (0-8), or -1 if no usable block is available.
     * blockSlot=0 → auto-find first block in hotbar.
     * blockSlot=1-9 → use that fixed slot only (0-indexed = value-1).
     */
    private int resolveBlockSlot() {
        int setting = blockSlot.getValueInt();
        if (setting >= 1 && setting <= 9) {
            int idx = setting - 1;
            ItemStack stack = mc.player.getInventory().getStack(idx);
            return (!stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getCount() > 0) ? idx : -1;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem && stack.getCount() > 0) return i;
        }
        return -1;
    }

    private Direction cardinalFromMotion(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST  : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
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
