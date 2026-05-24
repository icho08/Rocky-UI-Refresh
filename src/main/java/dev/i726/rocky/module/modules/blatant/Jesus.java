package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.math.Vec3d;

public final class Jesus extends Module implements TickListener {

    private final BooleanSetting lava = new BooleanSetting(EncryptedString.of("Walk On Lava"), false)
            .setDescription(EncryptedString.of("Also walk on lava (very dangerous)"));

    public Jesus() {
        super(EncryptedString.of("Jesus"),
                EncryptedString.of("Walk on water (and optionally lava) — works on servers without anti-cheat"),
                -1, CategoryManager.BLATANT);
        addSettings(lava);
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
        if (mc.player == null || mc.world == null) return;

        boolean inWater = mc.player.isTouchingWater();
        boolean inLava  = mc.player.isInLava();

        if (inWater || (lava.getValue() && inLava)) {
            Vec3d vel = mc.player.getVelocity();
            if (vel.y < 0) {
                mc.player.setVelocity(vel.x, 0.05, vel.z);
            }
            mc.player.fallDistance = 0f;
        }
    }
}
