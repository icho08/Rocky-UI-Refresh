package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class Velocity extends Module implements TickListener {

    private final NumberSetting horizontal = new NumberSetting(
            EncryptedString.of("Horizontal"), 0, 100, 20, 1)
            .setDescription(EncryptedString.of("Horizontal knockback percentage"));

    private final NumberSetting vertical = new NumberSetting(
            EncryptedString.of("Vertical"), 0, 100, 20, 1)
            .setDescription(EncryptedString.of("Vertical knockback percentage"));

    private int hitTicks = 0;

    public Velocity() {
        super(EncryptedString.of("Anti Knockback"),
                EncryptedString.of("Reduces or removes knockback"),
                -1,
                CategoryManager.PVP);
        addSettings(horizontal, vertical);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        
        if (mc.player.hurtTime > 0 && hitTicks == 0) {
            hitTicks = mc.player.hurtTime;
            double h = horizontal.getValue() / 100.0;
            double v = vertical.getValue() / 100.0;
            mc.player.setVelocity(
                mc.player.getVelocity().x * h,
                mc.player.getVelocity().y * v,
                mc.player.getVelocity().z * h
            );
        }
        
        if (mc.player.hurtTime == 0) {
            hitTicks = 0;
        }
    }
}
