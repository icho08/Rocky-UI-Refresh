package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

public final class KillAura extends Module implements TickListener {

    public enum TargetMode { Players, Mobs, All }
    public enum SortMode   { Closest, LowestHP, LookingAt }
    public enum RotateMode { None, Packet, Client }

    private final ModeSetting<TargetMode> targets = new ModeSetting<>(
            EncryptedString.of("Targets"), TargetMode.Players, TargetMode.class)
            .setDescription(EncryptedString.of("Which entity types to attack"));

    private final ModeSetting<SortMode> sort = new ModeSetting<>(
            EncryptedString.of("Sort"), SortMode.Closest, SortMode.class)
            .setDescription(EncryptedString.of("Closest = nearest, LowestHP = weakest, LookingAt = near crosshair"));

    private final ModeSetting<RotateMode> rotate = new ModeSetting<>(
            EncryptedString.of("Rotate"), RotateMode.Packet, RotateMode.class)
            .setDescription(EncryptedString.of("None = no rotation | Packet = silent server-side | Client = visible"));

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 1.0, 6.0, 3.5, 0.05)
            .setDescription(EncryptedString.of("Attack range in blocks"));

    private final MinMaxSetting cps = new MinMaxSetting(
            EncryptedString.of("CPS"), 1, 20, 1, 8, 12)
            .setDescription(EncryptedString.of("Clicks per second range (randomised per attack)"));

    private final BooleanSetting fullCooldown = new BooleanSetting(
            EncryptedString.of("Full Cooldown"), true)
            .setDescription(EncryptedString.of("Only attack when the attack meter is full (less detectable)"));

    private final BooleanSetting friendCheck = new BooleanSetting(
            EncryptedString.of("Friend Check"), true)
            .setDescription(EncryptedString.of("Skip players on your friends list"));

    private final BooleanSetting throughWalls = new BooleanSetting(
            EncryptedString.of("Through Walls"), false)
            .setDescription(EncryptedString.of("Attack entities behind solid blocks"));

    private final TimerUtils attackTimer = new TimerUtils();
    private int currentDelay;
    private LivingEntity currentTarget;

    public KillAura() {
        super(EncryptedString.of("Kill Aura"),
                EncryptedString.of("Automatically attacks nearby entities"),
                -1, CategoryManager.PVP);
        addSettings(targets, sort, rotate, range, cps, fullCooldown, friendCheck, throughWalls);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        rerollDelay();
        currentTarget = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        currentTarget = null;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        currentTarget = findTarget();
        if (currentTarget == null) return;

        if (fullCooldown.getValue() && mc.player.getAttackCooldownProgress(0f) < 1f) return;
        if (!attackTimer.delay(currentDelay)) return;

        if (!rotate.isMode(RotateMode.None)) {
            float[] rot = calcRotations(currentTarget);
            if (rotate.isMode(RotateMode.Packet)) {
                mc.getNetworkHandler().sendPacket(
                        new PlayerMoveC2SPacket.LookAndOnGround(
                                rot[0], rot[1], mc.player.isOnGround(), mc.player.horizontalCollision));
            } else {
                mc.player.setYaw(rot[0]);
                mc.player.setPitch(rot[1]);
            }
        }

        mc.interactionManager.attackEntity(mc.player, currentTarget);
        mc.player.swingHand(Hand.MAIN_HAND);
        rerollDelay();
        attackTimer.reset();
    }

    private LivingEntity findTarget() {
        double r = range.getValue();
        return mc.world.getEntitiesByClass(LivingEntity.class,
                        mc.player.getBoundingBox().expand(r), this::isValid)
                .stream()
                .min(this::compareTargets)
                .orElse(null);
    }

    private boolean isValid(LivingEntity e) {
        if (e == mc.player || !e.isAlive()) return false;
        if (e.distanceTo(mc.player) > range.getValue()) return false;
        if (targets.isMode(TargetMode.Players) && !(e instanceof PlayerEntity)) return false;
        if (targets.isMode(TargetMode.Mobs)    && !(e instanceof MobEntity)) return false;
        if (targets.isMode(TargetMode.All)     && !(e instanceof PlayerEntity) && !(e instanceof MobEntity)) return false;
        if (friendCheck.getValue() && e instanceof PlayerEntity p) {
            if (Rocky.INSTANCE.getFriendManager().isFriend(p.getUuidAsString())) return false;
        }
        if (!throughWalls.getValue() && !mc.player.canSee(e)) return false;
        return true;
    }

    private int compareTargets(LivingEntity a, LivingEntity b) {
        return switch (sort.getMode()) {
            case LowestHP  -> Float.compare(a.getHealth(), b.getHealth());
            case LookingAt -> {
                float[] pa = calcRotations(a), pb = calcRotations(b);
                float da = angleDiff(pa[0], mc.player.getYaw()) + angleDiff(pa[1], mc.player.getPitch());
                float db = angleDiff(pb[0], mc.player.getYaw()) + angleDiff(pb[1], mc.player.getPitch());
                yield Float.compare(da, db);
            }
            default -> Double.compare(a.distanceTo(mc.player), b.distanceTo(mc.player));
        };
    }

    private float[] calcRotations(LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dy = target.getEyeY() - mc.player.getEyeY();
        double dz = target.getZ() - mc.player.getZ();
        double dist2d = Math.sqrt(dx * dx + dz * dz);
        float yaw   = (float)(Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float)(-Math.toDegrees(Math.atan2(dy, dist2d)));
        return new float[]{yaw, pitch};
    }

    private static float angleDiff(float a, float b) {
        float d = Math.abs(a - b) % 360f;
        return d > 180f ? 360f - d : d;
    }

    private void rerollDelay() {
        int lo = Math.max(1, cps.getMinInt());
        int hi = Math.max(lo, cps.getMaxInt());
        int thisCps = lo + (int)(Math.random() * (hi - lo + 1));
        currentDelay = 1000 / thisCps;
    }

    public LivingEntity getCurrentTarget() { return currentTarget; }
}
