package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Speed extends Module implements TickListener {

    public enum Mode { Boost, SetSpeed }

    private static final Identifier SPEED_ID = Identifier.of("rocky", "blatant_speed");

    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.Boost, Mode.class)
            .setDescription(EncryptedString.of("Boost = multiply velocity, SetSpeed = set fixed move speed"));

    private final NumberSetting multiplier = new NumberSetting(EncryptedString.of("Multiplier"), 1.0, 10.0, 2.5, 0.1)
            .setDescription(EncryptedString.of("Speed multiplier applied to horizontal movement"));

    public Speed() {
        super(EncryptedString.of("Speed"),
                EncryptedString.of("Move significantly faster than normal — very obvious to anti-cheat"),
                -1, CategoryManager.BLATANT);
        addSettings(mode, multiplier);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        removeModifier();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        if (mode.isMode(Mode.SetSpeed)) {
            var attr = mc.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (attr == null) return;
            double base   = attr.getBaseValue();
            double target = base * multiplier.getValue();
            double needed = target - base;
            boolean hasMod = attr.getModifier(SPEED_ID) != null;
            if (!hasMod) {
                attr.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, needed, EntityAttributeModifier.Operation.ADD_VALUE));
            } else {
                double cur = attr.getModifier(SPEED_ID).value();
                if (Math.abs(cur - needed) > 0.0001) {
                    attr.removeModifier(SPEED_ID);
                    attr.addTemporaryModifier(new EntityAttributeModifier(SPEED_ID, needed, EntityAttributeModifier.Operation.ADD_VALUE));
                }
            }
        } else {
            removeModifier();
            if (!mc.player.isOnGround()) return;
            Vec3d vel = mc.player.getVelocity();
            double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hSpeed > 0.01) {
                float yaw = mc.player.getYaw() * ((float) Math.PI / 180f);
                double boost = multiplier.getValue() * 0.05;
                mc.player.addVelocity(-MathHelper.sin(yaw) * boost, 0, MathHelper.cos(yaw) * boost);
            }
        }
    }

    private void removeModifier() {
        if (mc != null && mc.player != null) {
            var attr = mc.player.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
            if (attr != null) attr.removeModifier(SPEED_ID);
        }
    }
}
