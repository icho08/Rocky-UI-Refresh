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
 * GodBridge — undetectable god bridging for Minecraft 1.21.x (Grim/NCP bypass).
 *
 * Anti-detection measures:
 *  - Silent server-side rotation (RotationOverride) — the camera never moves.
 *  - Virtual yaw/pitch gradually step toward the target; no instant snap-back.
 *  - Burst limit: after N consecutive placements, force a randomised pause so
 *    the server never sees an infinite machine-perfect placement streak.
 *  - Sneak sync: on a random subset of placements, briefly press the sneak key
 *    (1 tick) to break the "never-sneak + god-place" bot signature.
 *  - Jittered hit-point on the block face so every interact packet looks subtly
 *    different to packet inspectors.
 *
 * SafeWalk: PlayerEntityMixin.clipAtLedge returns true when enabled AND the
 * player is NOT pressing the forward key.  Forward movement is therefore fully
 * free; only backward/sideways ledge fall is prevented.
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
            EncryptedString.of("Place Jitter"), 0, 6, 2, 1)
            .setDescription(EncryptedString.of("Random extra ticks per placement (humanisation)"));

    private final NumberSetting burstLimit = new NumberSetting(
            EncryptedString.of("Burst Limit"), 2, 12, 5, 1)
            .setDescription(EncryptedString.of("Max consecutive placements before a forced humanisation pause"));

    private final NumberSetting burstPauseMin = new NumberSetting(
            EncryptedString.of("Burst Pause Min"), 2, 15, 4, 1)
            .setDescription(EncryptedString.of("Min ticks to pause after a burst"));

    private final NumberSetting burstPauseMax = new NumberSetting(
            EncryptedString.of("Burst Pause Max"), 2, 20, 9, 1)
            .setDescription(EncryptedString.of("Max ticks to pause after a burst"));

    private final BooleanSetting sneakSync = new BooleanSetting(
            EncryptedString.of("Sneak Sync"), true)
            .setDescription(EncryptedString.of("Occasionally send a 1-tick sneak to break the never-sneak bot pattern"));

    private final NumberSetting rotSpeed = new NumberSetting(
            EncryptedString.of("Rot Speed"), 5, 20, 12, 1)
            .setDescription(EncryptedString.of("Server-side rotation speed toward block face (deg/tick)"));

    private final NumberSetting alignThreshold = new NumberSetting(
            EncryptedString.of("Align Threshold"), 3, 25, 12, 1)
            .setDescription(EncryptedString.of("Degrees within which virtual rotation must align before placing"));

    private final NumberSetting blockSlot = new NumberSetting(
            EncryptedString.of("Block Slot"), 0, 9, 0, 1)
            .setDescription(EncryptedString.of("Hotbar slot for blocks (0 = auto-find, 1-9 = fixed slot)"));

    private final BooleanSetting requireBlocks = new BooleanSetting(
            EncryptedString.of("Require Blocks"), true)
            .setDescription(EncryptedString.of("Safe-walk and sprint only activate when holding blocks"));

    // ── State ─────────────────────────────────────────────────────────────────
    private int   cooldown             = 0;
    private int   consecutivePlacements = 0;
    private int   burstPauseCooldown   = 0;
    private boolean sneakReleaseNext   = false;

    // Virtual server-side rotation — camera never sees these values.
    private float virtualYaw   = Float.NaN;
    private float virtualPitch = Float.NaN;

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Automated god bridging with Grim/NCP bypass"),
                -1, CategoryManager.BRIDGING);
        INSTANCE = this;
        addSettings(autoSprint, placeDelay, placeJitter,
                burstLimit, burstPauseMin, burstPauseMax,
                sneakSync, rotSpeed, alignThreshold,
                blockSlot, requireBlocks);
    }

    /**
     * Called by PlayerEntityMixin.clipAtLedge.
     * Returns true only when enabled AND the player is not pressing forward —
     * so forward movement is never blocked, only backward/sideways ledge-fall.
     */
    public static boolean shouldSafeWalk() {
        if (INSTANCE == null || !INSTANCE.isEnabled()) return false;
        if (INSTANCE.requireBlocks.getValue() && !INSTANCE.isHoldingBlock()) return false;
        return true;
    }

    private boolean isHoldingBlock() {
        if (mc.player == null) return false;
        return mc.player.getMainHandStack().getItem() instanceof BlockItem;
    }

    @Override
    public void onEnable() {
        cooldown              = 0;
        consecutivePlacements = 0;
        burstPauseCooldown    = 0;
        sneakReleaseNext      = false;
        virtualYaw            = Float.NaN;
        virtualPitch          = Float.NaN;
        Clutch.placing        = false;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        Clutch.placing   = false;
        virtualYaw       = Float.NaN;
        virtualPitch     = Float.NaN;
        sneakReleaseNext = false;
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

        // Release sneak that was pressed last tick for sneak-sync
        if (sneakReleaseNext) {
            mc.options.sneakKey.setPressed(false);
            sneakReleaseNext = false;
        }

        // No blocks or in the air — idle, gradually return virtual rotation
        if (resolveBlockSlot() == -1 || !p.isOnGround()) {
            consecutivePlacements = 0;
            stepVirtualTowardReal(p);
            return;
        }

        // No horizontal movement — idle
        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.005) {
            consecutivePlacements = 0;
            stepVirtualTowardReal(p);
            return;
        }

        if (autoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        // Burst pause: stop placing for a few ticks to look human
        if (burstPauseCooldown > 0) {
            burstPauseCooldown--;
            stepVirtualTowardReal(p);
            return;
        }

        if (cooldown > 0) { cooldown--; stepVirtualTowardReal(p); return; }

        // ── Target block behind player ─────────────────────────────────────────
        Direction placeDir = p.getHorizontalFacing().getOpposite();
        BlockPos  standing = BlockPos.ofFloored(p.getX(), p.getY() - 1, p.getZ());
        BlockPos  target   = standing.offset(placeDir);

        if (!mc.world.getBlockState(target).isAir())       { stepVirtualTowardReal(p); return; }
        if (!mc.world.getBlockState(standing).isSolidBlock(mc.world, standing)) { stepVirtualTowardReal(p); return; }

        // ── Aim point — jittered hit position on the side face ────────────────
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double faceOffX = placeDir.getOffsetX() * 0.5;
        double faceOffZ = placeDir.getOffsetZ() * 0.5;
        double jitterH  = rng.nextDouble(-0.10, 0.10);
        double jitterY  = rng.nextDouble(-0.10, 0.06);

        Vec3d aimPoint = Vec3d.ofCenter(standing).add(
                faceOffX + (placeDir.getOffsetX() == 0 ? jitterH : 0),
                -0.2 + jitterY,
                faceOffZ + (placeDir.getOffsetZ() == 0 ? jitterH : 0));

        float[] needed    = calcLook(p.getEyePos(), aimPoint);
        float   needYaw   = needed[0];
        // Randomise pitch target range slightly each time for variety
        float   pitchMin  = 52f + rng.nextFloat() * 6f;
        float   pitchMax  = 80f + rng.nextFloat() * 6f;
        float   needPitch = MathHelper.clamp(needed[1], pitchMin, pitchMax);

        // ── Virtual server-side rotation (camera stays still) ─────────────────
        if (Float.isNaN(virtualYaw)) {
            virtualYaw   = p.getYaw();
            virtualPitch = p.getPitch();
        }

        // Vary rotation speed slightly each tick for human feel
        float maxStep    = rotSpeed.getValueInt() + rng.nextFloat() * 2f - 1f;
        virtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(needYaw   - virtualYaw),   -maxStep, maxStep);
        virtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(needPitch - virtualPitch), -maxStep, maxStep);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);

        RotationOverride.serverYaw          = virtualYaw;
        RotationOverride.serverPitch        = virtualPitch;
        RotationOverride.active             = true;
        RotationOverride.afterPacketAction  = null;

        // Wait until aligned before placing
        float threshold = alignThreshold.getValueInt();
        if (Math.abs(MathHelper.wrapDegrees(needYaw   - virtualYaw)) > threshold) return;
        if (Math.abs(MathHelper.wrapDegrees(needPitch - virtualPitch)) > threshold) return;

        // ── Resolve slot ──────────────────────────────────────────────────────
        int useSlot = resolveBlockSlot();
        if (useSlot == -1) return;

        final BlockHitResult bhr      = new BlockHitResult(aimPoint, placeDir, standing, false);
        final int            fUseSlot = useSlot;
        final int            fPrev    = p.getInventory().getSelectedSlot();

        if (fUseSlot != fPrev) p.getInventory().setSelectedSlot(fUseSlot);

        // Sneak-sync: randomly press sneak this tick so next movement packet
        // carries sneak=true, breaking the "never-sneak" bot pattern.
        if (sneakSync.getValue() && rng.nextInt(4) == 0) {
            mc.options.sneakKey.setPressed(true);
            sneakReleaseNext = true;
        }

        RotationOverride.afterPacketAction = () -> {
            ClientPlayerEntity pp = mc.player;
            if (pp == null || mc.interactionManager == null) return;
            Clutch.placing = true;
            try {
                if (mc.interactionManager.interactBlock(pp, Hand.MAIN_HAND, bhr).isAccepted()) {
                    pp.swingHand(Hand.MAIN_HAND);
                    consecutivePlacements++;

                    // Base delay + random jitter
                    cooldown = placeDelay.getValueInt()
                             + (int)(Math.random() * (placeJitter.getValueInt() + 1));

                    // Burst limit — force a humanisation pause after N blocks
                    int limit = burstLimit.getValueInt();
                    if (consecutivePlacements >= limit) {
                        consecutivePlacements = 0;
                        int pauseMin = burstPauseMin.getValueInt();
                        int pauseMax = Math.max(pauseMin, burstPauseMax.getValueInt());
                        burstPauseCooldown = pauseMin + (int)(Math.random() * (pauseMax - pauseMin + 1));
                    }
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
     * Prevents a sudden yaw snap when bridging pauses or stops.
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
            virtualYaw   = Float.NaN;
            virtualPitch = Float.NaN;
            disarmOverride();
            return;
        }
        float maxStep = rotSpeed.getValueInt();
        virtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(realYaw   - virtualYaw),   -maxStep, maxStep);
        virtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(realPitch - virtualPitch), -maxStep, maxStep);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);
        RotationOverride.serverYaw         = virtualYaw;
        RotationOverride.serverPitch       = virtualPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;
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
