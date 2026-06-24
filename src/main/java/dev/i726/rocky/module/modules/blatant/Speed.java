package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class Speed extends Module implements TickListener {

    public enum Mode { Boost, SetSpeed }

    private static final Identifier SPEED_ID = Identifier.fromNamespaceAndPath("rocky", "blatant_speed");

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
            var attr = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr == null) return;
            double base   = attr.getBaseValue();
            double target = base * multiplier.getValue();
            double needed = target - base;
            boolean hasMod = attr.getModifier(SPEED_ID) != null;
            if (!hasMod) {
                attr.addTransientModifier(new AttributeModifier(SPEED_ID, needed, AttributeModifier.Operation.ADD_VALUE));
            } else {
                double cur = attr.getModifier(SPEED_ID).amount();
                if (Math.abs(cur - needed) > 0.0001) {
                    attr.removeModifier(SPEED_ID);
                    attr.addTransientModifier(new AttributeModifier(SPEED_ID, needed, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        } else {
            removeModifier();
            if (!mc.player.onGround()) return;
            Vec3 vel = mc.player.getDeltaMovement();
            double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            if (hSpeed > 0.01) {
                float yaw = mc.player.yRot() * ((float) Math.PI / 180f);
                double boost = multiplier.getValue() * 0.05;
                mc.player.push(-Mth.sin(yaw) * boost, 0, Mth.cos(yaw) * boost);
            }
        }
    }

    private void removeModifier() {
        if (mc != null && mc.player != null) {
            var attr = mc.player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null) attr.removeModifier(SPEED_ID);
        }
    }
}
