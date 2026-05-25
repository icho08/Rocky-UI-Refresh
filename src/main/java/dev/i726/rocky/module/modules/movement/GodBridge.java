package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/**
 * GodBridge — undetectable god bridging.
 *
 * Direction: always uses the player's FACING direction (opposite = behind them).
 * Velocity is NOT used for direction — diagonal movement makes it unreliable.
 *
 * Rotation: smoothly lerps the player's actual camera (setYaw/setPitch) toward
 * the block face each tick. The server receives this via normal movement packets
 * — no special LookAndOnGround packets, no instant snap, no bot signature.
 * The block is placed once the camera is within the alignment threshold.
 * After placement the camera snaps back immediately (1-tick correction is normal).
 *
 * SafeWalk: PlayerEntityMixin.clipAtLedge returns true when enabled, giving
 * the same edge-clip as vanilla sneaking with zero extra packets.
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

    private final NumberSetting rotSpeed = new NumberSetting(
            EncryptedString.of("Rot Speed"), 5, 20, 12, 1)
            .setDescription(EncryptedString.of("Camera rotation speed toward block face (degrees per tick, higher = faster)"));

    private final NumberSetting alignThreshold = new NumberSetting(
            EncryptedString.of("Align Threshold"), 5, 30, 15, 1)
            .setDescription(EncryptedString.of("Degrees within which the camera must be aligned before placing"));

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot only)"));

    private final BooleanSetting requireBlocks = new BooleanSetting(
            EncryptedString.of("Require Blocks"), true)
            .setDescription(EncryptedString.of("When ON: safe-walk and sprint only activate if you have blocks. When OFF: always active"));

    private int cooldown = 0;
    // Saved yaw/pitch before we started rotating toward the block — restored after place
    private float savedYaw   = Float.NaN;
    private float savedPitch = Float.NaN;

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Automated god bridging with undetectable smooth rotation"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(autoSprint, placeDelay, placeJitter, rotSpeed, alignThreshold, blockSlot, requireBlocks);
    }

    public static boolean shouldSafeWalk() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (INSTANCE.requireBlocks.getValue()) return INSTANCE.resolveBlockSlot() != -1;
        return true;
    }

    @Override
    public void onEnable() {
        cooldown  = 0;
        savedYaw  = Float.NaN;
        savedPitch = Float.NaN;
        Clutch.placing = false;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        restoreSavedRotation();
        Clutch.placing = false;
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;

        if (resolveBlockSlot() == -1) { restoreSavedRotation(); return; }
        if (!p.isOnGround()) { restoreSavedRotation(); return; }

        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.005) { restoreSavedRotation(); return; }

        if (autoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        if (cooldown > 0) { cooldown--; restoreSavedRotation(); return; }

        // ── Direction: facing-based (stable), not velocity-based ──────────────
        // In god bridge the player faces the destination and walks backward.
        // The block extends in the direction BEHIND the player = opposite of facing.
        Direction placeDir = p.getHorizontalFacing().getOpposite();
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir()) { restoreSavedRotation(); return; }
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) { restoreSavedRotation(); return; }

        // ── Aim point on the side face of the standing block ──────────────────
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double faceOffX = placeDir.getOffsetX() * 0.5;
        double faceOffZ = placeDir.getOffsetZ() * 0.5;
        double jitterH  = rng.nextDouble(-0.1, 0.1);
        double jitterY  = rng.nextDouble(-0.1, 0.05);

        Vec3d aimPoint = Vec3d.ofCenter(standing).add(
                faceOffX + (placeDir.getOffsetX() == 0 ? jitterH : 0),
                -0.2 + jitterY,
                faceOffZ + (placeDir.getOffsetZ() == 0 ? jitterH : 0));

        float[] needed = calcLook(p.getEyePos(), aimPoint);
        float   needYaw   = needed[0];
        float   needPitch = MathHelper.clamp(needed[1], 55f, 85f);

        // ── Smooth camera rotation ────────────────────────────────────────────
        // Save the player's real looking direction on first approach so we can
        // restore it cleanly after the placement.
        if (Float.isNaN(savedYaw)) {
            savedYaw   = p.getYaw();
            savedPitch = p.getPitch();
        }

        float speed     = rotSpeed.getValueInt();
        float curYaw    = p.getYaw();
        float curPitch  = p.getPitch();
        float newYaw    = lerpAngle(curYaw,   needYaw,   speed / 90f);
        float newPitch  = lerpAngle(curPitch, needPitch, speed / 90f);

        // Apply rotation via player state — carried to server in next position packet,
        // no separate LookAndOnGround packet needed.
        p.setYaw(newYaw);
        p.setPitch(newPitch);

        // Only place when camera is actually aligned with the target face
        float yawDiff   = Math.abs(MathHelper.wrapDegrees(needYaw   - newYaw));
        float pitchDiff = Math.abs(MathHelper.wrapDegrees(needPitch - newPitch));
        float threshold = alignThreshold.getValueInt();
        if (yawDiff > threshold || pitchDiff > threshold) return; // still rotating

        // ── Place block ───────────────────────────────────────────────────────
        int useSlot  = resolveBlockSlot();
        if (useSlot == -1) return;
        int prevSlot = p.getInventory().getSelectedSlot();
        if (useSlot != prevSlot) p.getInventory().setSelectedSlot(useSlot);

        BlockHitResult bhr = new BlockHitResult(aimPoint, placeDir, standing, false);

        Clutch.placing = true;
        try {
            if (mc.interactionManager.interactBlock(p, Hand.MAIN_HAND, bhr).isAccepted()) {
                p.swingHand(Hand.MAIN_HAND);
                cooldown = placeDelay.getValueInt()
                         + (int)(Math.random() * (placeJitter.getValueInt() + 1));
                // Restore real rotation immediately after the place
                restoreSavedRotation();
                p.setYaw(savedYaw);
                p.setPitch(savedPitch);
                savedYaw   = Float.NaN;
                savedPitch = Float.NaN;
            }
        } finally {
            Clutch.placing = false;
            if (useSlot != prevSlot) p.getInventory().setSelectedSlot(prevSlot);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void restoreSavedRotation() {
        if (Float.isNaN(savedYaw) || mc.player == null) return;
        mc.player.setYaw(savedYaw);
        mc.player.setPitch(savedPitch);
        savedYaw   = Float.NaN;
        savedPitch = Float.NaN;
    }

    /** Linearly interpolates an angle (handles 0°/360° wrap). t=1 = instant snap. */
    private static float lerpAngle(float from, float to, float t) {
        float delta = MathHelper.wrapDegrees(to - from);
        return from + delta * MathHelper.clamp(t, 0f, 1f);
    }

    private int resolveBlockSlot() {
        if (mc.player == null) return -1;
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

    private static float[] calcLook(Vec3d from, Vec3d to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, hDist));
        return new float[]{ yaw, MathHelper.clamp(pitch, -90f, 90f) };
    }
}
