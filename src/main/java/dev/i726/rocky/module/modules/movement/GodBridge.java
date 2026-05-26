package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RotationOverride;
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
 * Rotation: silent server-side rotation via RotationOverride. A virtual yaw/pitch
 * gradually steps toward the block face each tick. ClientPlayerEntityMixin swaps
 * these into the movement packet silently then immediately restores the real camera
 * — the player sees zero camera movement. Block placed once virtual rotation aligns.
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

    private int   cooldown      = 0;
    // Virtual server-side rotation, gradually stepped toward the block face.
    // The camera never sees these — RotationOverride swaps them into the packet.
    private float virtualYaw   = Float.NaN;
    private float virtualPitch = Float.NaN;

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Automated god bridging with undetectable smooth rotation"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(autoSprint, placeDelay, placeJitter, rotSpeed, alignThreshold, blockSlot, requireBlocks);
    }

    public static boolean shouldSafeWalk() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (INSTANCE.requireBlocks.getValue()) return INSTANCE.isHoldingBlock();
        return true;
    }

    /** True only when the item the player is currently holding is a placeable block. */
    private boolean isHoldingBlock() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }

    @Override
    public void onEnable() {
        cooldown      = 0;
        virtualYaw    = Float.NaN;
        virtualPitch  = Float.NaN;
        Clutch.placing = false;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        Clutch.placing = false;
        virtualYaw   = Float.NaN;
        virtualPitch = Float.NaN;
        disarmOverride();
        if (mc.options != null) mc.options.sneakKey.setPressed(false);
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            disarmOverride();
            return;
        }

        // If not actively bridging, gradually return the virtual rotation to the
        // real camera direction so the server never sees a sudden snap-back.
        if (resolveBlockSlot() == -1 || !p.isOnGround()) {
            stepVirtualTowardReal(p);
            return;
        }

        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.005) {
            stepVirtualTowardReal(p);
            return;
        }

        if (autoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        if (cooldown > 0) { cooldown--; stepVirtualTowardReal(p); return; }

        // ── Direction: facing-based (stable), not velocity-based ──────────────
        Direction placeDir = p.getHorizontalFacing().getOpposite();
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir()) { stepVirtualTowardReal(p); return; }
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) { stepVirtualTowardReal(p); return; }

        // ── Aim point on the side face of the standing block ──────────────────
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double faceOffX = placeDir.getOffsetX() * 0.5;
        double faceOffZ = placeDir.getOffsetZ() * 0.5;
        double jitterH  = rng.nextDouble(-0.08, 0.08);
        double jitterY  = rng.nextDouble(-0.08, 0.04);

        Vec3d aimPoint = Vec3d.ofCenter(standing).add(
                faceOffX + (placeDir.getOffsetX() == 0 ? jitterH : 0),
                -0.2 + jitterY,
                faceOffZ + (placeDir.getOffsetZ() == 0 ? jitterH : 0));

        float[] needed    = calcLook(p.getEyePos(), aimPoint);
        float   needYaw   = needed[0];
        float   needPitch = MathHelper.clamp(needed[1], 55f, 85f);

        // ── Silent server-side rotation — camera never moves ──────────────────
        // virtualYaw/Pitch live independently of the camera. The mixin swaps them
        // into the movement packet before it is sent, then restores the real camera.
        // We KEEP them alive between placements so the server never sees a snap-back.
        if (Float.isNaN(virtualYaw)) {
            virtualYaw   = p.getYaw();
            virtualPitch = p.getPitch();
        }

        float maxStep = rotSpeed.getValueInt(); // degrees per tick
        virtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(needYaw   - virtualYaw),   -maxStep, maxStep);
        virtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(needPitch - virtualPitch), -maxStep, maxStep);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);

        RotationOverride.serverYaw   = virtualYaw;
        RotationOverride.serverPitch = virtualPitch;
        RotationOverride.active      = true;
        RotationOverride.afterPacketAction = null; // clear any stale queued action

        // Only place once the silent rotation is close enough to the target face
        float threshold = alignThreshold.getValueInt();
        if (Math.abs(MathHelper.wrapDegrees(needYaw   - virtualYaw)) > threshold) return;
        if (Math.abs(MathHelper.wrapDegrees(needPitch - virtualPitch)) > threshold) return;

        // ── Queue placement to fire AFTER position packet is sent ─────────────
        // Packet order Grim sees: PositionAndRotation(virtualYaw) → InteractBlock
        // Without this queue, InteractBlock would arrive before the rotation → flag.
        int useSlot  = resolveBlockSlot();
        if (useSlot == -1) return;

        final BlockHitResult bhr      = new BlockHitResult(aimPoint, placeDir, standing, false);
        final int            fUseSlot = useSlot;
        final int            fPrev    = p.getInventory().getSelectedSlot();

        if (fUseSlot != fPrev) p.getInventory().setSelectedSlot(fUseSlot);

        RotationOverride.afterPacketAction = () -> {
            ClientPlayerEntity pp = mc.player;
            if (pp == null || mc.interactionManager == null) return;
            Clutch.placing = true;
            try {
                if (mc.interactionManager.interactBlock(pp, Hand.MAIN_HAND, bhr).isAccepted()) {
                    pp.swingHand(Hand.MAIN_HAND);
                    cooldown = placeDelay.getValueInt()
                             + (int)(Math.random() * (placeJitter.getValueInt() + 1));
                }
            } finally {
                Clutch.placing = false;
                if (fUseSlot != fPrev && mc.player != null)
                    mc.player.getInventory().setSelectedSlot(fPrev);
            }
        };
    }

    /**
     * Gradually step virtualYaw/Pitch back toward the player's real camera rotation.
     * This prevents a sudden yaw snap when the player stops bridging.
     * Once close enough, disarm the override entirely.
     */
    private void stepVirtualTowardReal(ClientPlayerEntity p) {
        if (Float.isNaN(virtualYaw) || p == null) {
            disarmOverride();
            return;
        }
        float realYaw   = p.getYaw();
        float realPitch = p.getPitch();
        if (Math.abs(MathHelper.wrapDegrees(realYaw - virtualYaw)) < 4f
                && Math.abs(MathHelper.wrapDegrees(realPitch - virtualPitch)) < 4f) {
            // Close enough — fully disarm
            virtualYaw   = Float.NaN;
            virtualPitch = Float.NaN;
            disarmOverride();
            return;
        }
        float maxStep = rotSpeed.getValueInt();
        virtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(realYaw   - virtualYaw),   -maxStep, maxStep);
        virtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(realPitch - virtualPitch), -maxStep, maxStep);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);
        RotationOverride.serverYaw          = virtualYaw;
        RotationOverride.serverPitch        = virtualPitch;
        RotationOverride.active             = true;
        RotationOverride.afterPacketAction  = null;
    }

    private void disarmOverride() {
        RotationOverride.active            = false;
        RotationOverride.afterPacketAction = null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
