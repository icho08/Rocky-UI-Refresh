package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public final class Teleport extends Module implements TickListener {

    private final BooleanSetting toTarget = new BooleanSetting(EncryptedString.of("To Crosshair"), true)
            .setDescription(EncryptedString.of("Teleport to the block you are looking at when enabled"));

    private final NumberSetting distance = new NumberSetting(EncryptedString.of("Step Distance"), 1.0, 50.0, 10.0, 1.0)
            .setDescription(EncryptedString.of("Distance to teleport forward when To Crosshair is off"));

    private boolean fired = false;

    public Teleport() {
        super(EncryptedString.of("Teleport"),
                EncryptedString.of("Instantly teleports you — completely blatant on any server with anti-cheat"),
                -1, CategoryManager.BLATANT);
        addSettings(toTarget, distance);
    }

    @Override
    public void onEnable() {
        fired = false;
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || fired) return;
        fired = true;

        if (toTarget.getValue()) {
            HitResult hit = mc.crosshairTarget;
            if (hit instanceof BlockHitResult bhr) {
                BlockPos bp = bhr.getBlockPos();
                mc.player.setPosition(bp.getX() + 0.5, bp.getY() + 1.0, bp.getZ() + 0.5);
            }
        } else {
            Vec3d look = mc.player.getRotationVector().normalize().multiply(distance.getValue());
            Vec3d pos  = mc.player.getEntityPos().add(look);
            mc.player.setPosition(pos.x, pos.y, pos.z);
        }

        mc.player.setVelocity(Vec3d.ZERO);
        // Auto-disable after firing
        eventManager.remove(TickListener.class, this);
        setEnabledStatus(false);
    }
}
