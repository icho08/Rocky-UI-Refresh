package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.math.Vec3d;

public final class HighJump extends Module implements TickListener {

    private final NumberSetting jumpBoost = new NumberSetting(EncryptedString.of("Jump Boost"), 0.1, 5.0, 1.0, 0.05)
            .setDescription(EncryptedString.of("Extra upward velocity added when the player jumps"));

    private boolean wasOnGround = false;

    public HighJump() {
        super(EncryptedString.of("HighJump"),
                EncryptedString.of("Jump much higher than normal — completely obvious to anti-cheat"),
                -1, CategoryManager.BLATANT);
        addSettings(jumpBoost);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        wasOnGround = false;
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
        boolean onGround = mc.player.isOnGround();
        if (!onGround && wasOnGround) {
            Vec3d vel = mc.player.getVelocity();
            if (vel.y > 0.01) {
                mc.player.setVelocity(vel.x, vel.y + jumpBoost.getValue(), vel.z);
            }
        }
        wasOnGround = onGround;
    }
}
