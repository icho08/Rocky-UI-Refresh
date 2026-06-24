package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.world.phys.Vec3;

public final class NoSlowdown extends Module implements TickListener {

    private final BooleanSetting soulsand = new BooleanSetting(EncryptedString.of("Soul Sand"), true)
            .setDescription(EncryptedString.of("Cancel slowdown on soul sand by boosting horizontal velocity"));

    private final BooleanSetting slime = new BooleanSetting(EncryptedString.of("Slime Blocks"), true)
            .setDescription(EncryptedString.of("Cancel bouncing and slowdown on slime blocks"));

    public NoSlowdown() {
        super(EncryptedString.of("NoSlowdown"),
                EncryptedString.of("Removes movement speed penalties from blocks like soul sand and slime"),
                -1, CategoryManager.BLATANT);
        addSettings(soulsand, slime);
    }

    @Override
    public void onEnable() {
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
        if (mc.player == null || mc.level == null) return;

        Vec3 vel = mc.player.getDeltaMovement();

        if (soulsand.getValue() && mc.player.onGround()) {
            double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hSpeed > 0 && hSpeed < 0.18) {
                double factor = 0.18 / hSpeed;
                mc.player.setDeltaMovement(vel.x * factor, vel.y, vel.z * factor);
            }
        }

        if (slime.getValue() && mc.player.onGround() && vel.y > 0) {
            mc.player.setDeltaMovement(vel.x, 0, vel.z);
        }
    }
}
