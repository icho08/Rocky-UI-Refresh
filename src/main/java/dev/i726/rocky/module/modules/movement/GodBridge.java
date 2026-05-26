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
 * GodBridge — Grim/NCP bypass for 1.21.x
 *
 * Fall protection strategy: suppress the backward (S) key entirely while
 * bridging is active. This produces completely normal movement packets —
 * no clipAtLedge velocity clamping, no invisible-sneak signature, nothing
 * for Grim to flag. If the player holds S they just don't move backward;
 * the server sees unmodified forward motion the whole time.
 *
 * Burst detection bypass: after N consecutive placements, force a
 * randomised multi-tick pause so the server never sees a machine-perfect
 * infinite streak (the #1 cause of god-bridge bans).
 *
 * Sneak sync: 25 % chance per placement to fire a 1-tick sneak, breaking
 * the "never-sneak + ledge-place" bot pattern Grim tracks.
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
            .setDescription(EncryptedString.of("Key suppression and sprint only activate when holding blocks"));

    // ── State ─────────────────────────────────────────────────────────────────
    private int   cooldown              = 0;
    private int   consecutivePlacements = 0;
    private int   burstPauseCooldown    = 0;
    private boolean sneakReleaseNext    = false;

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

    /** No longer uses clipAtLedge — kept for compatibility; always returns false. */
    public static boolean shouldSafeWalk() {
        return false;
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
        // Release any keys we may have suppressed
        if (mc.options != null) {
            mc.options.sneakKey.setPressed(false);
            // backKey is released naturally — we only ever force it to false,
            // so when we stop calling setPressed(false) the binding reads the
            // real physical key state again on the next tick.
        }
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

        // Release sneak pressed by sneak-sync last tick
        if (sneakReleaseNext) {
            mc.options.sneakKey.setPressed(false);
            sneakReleaseNext = false;
        }

        boolean hasBlocks = resolveBlockSlot() != -1;
        boolean protect   = !requireBlocks.getValue() || hasBlocks;

        // ── Backward-key suppression (replaces clipAtLedge entirely) ──────────
        // While on ground with blocks, physically stop the backward key from
        // registering. Movement packets stay 100 % normal — no velocity clamping,
        // no invisible-sneak signature.
        if (p.isOnGround() && protect) {
            mc.options.backKey.setPressed(false);
        }

        if (!hasBlocks || !p.isOnGround()) {
            consecutivePlacements = 0;
            stepVirtualTowardReal(p);
            return;
        }

        Vec3d v = p.getVelocity();
        if (Math.abs(v.x) + Math.abs(v.z) < 0.005) {
            consecutivePlacements = 0;
            stepVirtualTowardReal(p);
            return;
        }

        if (autoSprint.getValue() && !p.isSprinting()) mc.options.sprintKey.setPressed(true);

        // Burst pause
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

        // ── Jittered aim point on block face ──────────────────────────────────
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
        float   pitchMin  = 52f + rng.nextFloat() * 6f;
        float   pitchMax  = 80f + rng.nextFloat() * 6f;
        float   needPitch = MathHelper.clamp(needed[1], pitchMin, pitchMax);

        // ── Silent server-side rotation (camera stays still) ──────────────────
        if (Float.isNaN(virtualYaw)) {
            virtualYaw   = p.getYaw();
            virtualPitch = p.getPitch();
        }

        float maxStep = rotSpeed.getValueInt() + rng.nextFloat() * 2f - 1f;
        virtualYaw   += MathHelper.clamp(MathHelper.wrapDegrees(needYaw   - virtualYaw),   -maxStep, maxStep);
        virtualPitch += MathHelper.clamp(MathHelper.wrapDegrees(needPitch - virtualPitch), -maxStep, maxStep);
        virtualPitch  = MathHelper.clamp(virtualPitch, -90f, 90f);

        RotationOverride.serverYaw         = virtualYaw;
        RotationOverride.serverPitch       = virtualPitch;
        RotationOverride.active            = true;
        RotationOverride.afterPacketAction = null;

        float threshold = alignThreshold.getValueInt();
        if (Math.abs(MathHelper.wrapDegrees(needYaw   - virtualYaw)) > threshold) return;
        if (Math.abs(MathHelper.wrapDegrees(needPitch - virtualPitch)) > threshold) return;

        int useSlot = resolveBlockSlot();
        if (useSlot == -1) return;

        final BlockHitResult bhr      = new BlockHitResult(aimPoint, placeDir, standing, false);
        final int            fUseSlot = useSlot;
        final int            fPrev    = p.getInventory().getSelectedSlot();

        if (fUseSlot != fPrev) p.getInventory().setSelectedSlot(fUseSlot);

        // Sneak-sync: random 1-tick sneak to break the never-sneak bot pattern
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

                    cooldown = placeDelay.getValueInt()
                             + (int)(Math.random() * (placeJitter.getValueInt() + 1));

                    int limit = burstLimit.getValueInt();
                    if (consecutivePlacements >= limit) {
                        consecutivePlacements = 0;
                        int pMin = burstPauseMin.getValueInt();
                        int pMax = Math.max(pMin, burstPauseMax.getValueInt());
                        burstPauseCooldown = pMin + (int)(Math.random() * (pMax - pMin + 1));
                    }
                }
            } finally {
                Clutch.placing = false;
                if (fUseSlot != fPrev && mc.player != null)
                    mc.player.getInventory().setSelectedSlot(fPrev);
            }
        };
    }

    private void stepVirtualTowardReal(ClientPlayerEntity p) {
        if (Float.isNaN(virtualYaw) || p == null) { disarmOverride(); return; }
        float realYaw   = p.getYaw();
        float realPitch = p.getPitch();
        if (Math.abs(MathHelper.wrapDegrees(realYaw - virtualYaw)) < 4f
                && Math.abs(MathHelper.wrapDegrees(realPitch - virtualPitch)) < 4f) {
            virtualYaw = Float.NaN; virtualPitch = Float.NaN;
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
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);
        return new float[]{
            (float) Math.toDegrees(Math.atan2(-dx, dz)),
            MathHelper.clamp((float) Math.toDegrees(-Math.atan2(dy, hDist)), -90f, 90f)
        };
    }
}
