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

    private int cooldown = 0;

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Automated god bridging with anticheat-safe packet rotation"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(autoSprint, placeDelay, placeJitter);
    }

    /**
     * Used by PlayerEntityMixin.clipAtLedge to keep the player from walking
     * off the edge without sending sneak packets.
     */
    public static boolean shouldSafeWalk() {
        return INSTANCE != null && INSTANCE.isEnabled();
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

        if (!isHoldingBlocks(p)) return;
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

        Hand hand = p.getMainHandStack().getItem() instanceof BlockItem ? Hand.MAIN_HAND : Hand.OFF_HAND;
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
            // Single rotation packet — no snap-and-restore.
            // The server sees: look-toward-block → place. Natural human behaviour.
            // The original rotation returns via the next normal PositionAndRotation packet.
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(targetYaw, targetPitch, onGround, hCol));
            if (mc.interactionManager.interactBlock(p, hand, bhr).isAccepted()) {
                p.swingHand(hand);
                cooldown = placeDelay.getValueInt()
                         + (int)(Math.random() * (placeJitter.getValueInt() + 1));
            }
        } finally {
            Clutch.placing = false;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isHoldingBlocks(ClientPlayerEntity p) {
        ItemStack main = p.getMainHandStack(), off = p.getOffHandStack();
        return (main.getItem() instanceof BlockItem && main.getCount() > 0)
                || (off.getItem() instanceof BlockItem && off.getCount() > 0);
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
