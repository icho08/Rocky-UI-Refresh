package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.world.phys.Vec3;

public final class HighJump extends Module implements TickListener {

    private final NumberSetting jumpBoost = new NumberSetting(EncryptedString.of("Jump Boost"), 0.1, 5.0, 1.0, 0.05)
            .setDescription(EncryptedString.of("Extra upward velocity added the moment the player leaves the ground"));

    private final BooleanSetting noFall = new BooleanSetting(EncryptedString.of("No Fall"), true)
            .setDescription(EncryptedString.of("Prevent fall damage caused by the extra jump height"));

    private boolean wasOnGround = false;
    private boolean boosted      = false;

    public HighJump() {
        super(EncryptedString.of("HighJump"),
                EncryptedString.of("Jump much higher than normal — completely obvious to anti-cheat"),
                -1, CategoryManager.BLATANT);
        addSettings(jumpBoost, noFall);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        wasOnGround = false;
        boosted      = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        boolean onGround = mc.player.onGround();

        // Apply the boost the first tick the player leaves the ground
        if (!onGround && wasOnGround && !boosted) {
            Vec3 vel = mc.player.getDeltaMovement();
            if (vel.y > 0.01) {
                mc.player.setDeltaMovement(vel.x, vel.y + jumpBoost.getValue(), vel.z);
                boosted = true;
            }
        }

        // Reset so the next jump can be boosted again
        if (onGround) boosted = false;

        // Zero fallDistance while still ascending so only descent height counts
        if (noFall.getValue() && !onGround) {
            Vec3 vel = mc.player.getDeltaMovement();
            if (vel.y > 0) mc.player.fallDistance = 0f;
        }

        wasOnGround = onGround;
    }
}
