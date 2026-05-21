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

/**
 * GodBridge — standalone god bridge.
 *
 * SafeWalk is handled by the {@code PlayerEntityMixin.clipAtLedge} injection
 * which returns {@code true} whenever this module is enabled — that is the
 * safest possible fall-protection: Minecraft's own physics clips movement at
 * the block edge before any position packet is generated, so the server never
 * sees an invalid position.
 *
 * Block placement uses packet-level silent rotation:
 *   1. {@code LookAndOnGround} aimed at the block face
 *   2. {@code PlayerInteractBlockC2SPacket} (via interactBlock)
 *   3. {@code LookAndOnGround} restoring original rotation
 *
 * All three arrive in the same server tick, so Grim's "interact with what
 * you look at" check passes and the camera never visibly snaps.
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
     * Used by {@code PlayerEntityMixin.clipAtLedge} to keep the player from
     * walking off the edge — identical to sneaking but completely server-side-safe.
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

        // Only work when holding blocks
        if (!isHoldingBlocks(p)) return;

        // clipAtLedge mixin handles fall protection automatically when enabled.
        // No sneakKey manipulation needed — no extra packets, no AC flags.

        if (!p.isOnGround()) return;

        // Need to be moving to place
        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.01) return;

        if (autoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        if (cooldown > 0) { cooldown--; return; }

        // Place behind the player's walking direction
        Direction placeDir = cardinalFromMotion(v.x, v.z);
        BlockPos standing  = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos target    = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir()) return;
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) return;

        // Aim at the exposed side face of the standing block
        Vec3d aimPoint = Vec3d.ofCenter(standing)
                .add(placeDir.getOffsetX() * 0.5, -0.25, placeDir.getOffsetZ() * 0.5);

        Hand hand = p.getMainHandStack().getItem() instanceof BlockItem ? Hand.MAIN_HAND : Hand.OFF_HAND;
        BlockHitResult bhr = new BlockHitResult(aimPoint, placeDir, standing, false);

        // ── Silent packet rotation ────────────────────────────────────────────
        // Calculate the yaw/pitch that genuinely points at aimPoint, send it to
        // the server before the interact packet, then immediately restore.
        // p.setYaw/setPitch is never called — no desync with lastSentYaw/Pitch.
        float[] look     = calcLook(p.getEyePos(), aimPoint);
        float targetYaw   = look[0];
        float targetPitch = MathHelper.clamp(look[1], 60f, 90f);
        float origYaw     = p.getYaw();
        float origPitch   = p.getPitch();
        boolean onGround  = p.isOnGround();
        boolean hCol      = p.horizontalCollision;

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
            conn.send(new PlayerMoveC2SPacket.LookAndOnGround(origYaw, origPitch, onGround, hCol));
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
