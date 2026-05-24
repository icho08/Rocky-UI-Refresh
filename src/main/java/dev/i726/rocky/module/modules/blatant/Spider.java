package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.math.Vec3d;

public final class Spider extends Module implements TickListener {

    private final NumberSetting climbSpeed = new NumberSetting(EncryptedString.of("Climb Speed"), 0.05, 0.5, 0.15, 0.01)
            .setDescription(EncryptedString.of("Upward velocity applied when against a wall"));

    public Spider() {
        super(EncryptedString.of("Spider"),
                EncryptedString.of("Climb up any wall by walking into it — blatant on any server"),
                -1, CategoryManager.BLATANT);
        addSettings(climbSpeed);
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
        if (mc.player == null) return;
        if (mc.player.isOnGround()) return;

        if (mc.player.horizontalCollision) {
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, climbSpeed.getValue(), vel.z);
        }
    }
}
