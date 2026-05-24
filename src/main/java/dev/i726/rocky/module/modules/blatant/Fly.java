package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Fly extends Module implements TickListener {

    public enum Mode { Vanilla, Velocity }

    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.Vanilla, Mode.class)
            .setDescription(EncryptedString.of("Vanilla = uses ability flags (less obvious), Velocity = raw speed override"));

    private final NumberSetting speed = new NumberSetting(EncryptedString.of("Speed"), 0.1, 5.0, 1.0, 0.1)
            .setDescription(EncryptedString.of("Horizontal fly speed (Velocity mode)"));

    private final NumberSetting vertSpeed = new NumberSetting(EncryptedString.of("Vert Speed"), 0.1, 3.0, 0.5, 0.1)
            .setDescription(EncryptedString.of("Vertical speed for jump/sneak (Velocity mode)"));

    public Fly() {
        super(EncryptedString.of("Fly"),
                EncryptedString.of("Fly in survival — only works on servers without anti-cheat"),
                -1, CategoryManager.BLATANT);
        addSettings(mode, speed, vertSpeed);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        if (mc.player != null) {
            mc.player.getAbilities().allowFlying = true;
            mc.player.getAbilities().flying = true;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.player != null) {
            mc.player.getAbilities().allowFlying = false;
            mc.player.getAbilities().flying = false;
        }
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;

        // Always clear fall damage while flying
        mc.player.fallDistance = 0f;
        mc.player.getAbilities().allowFlying = true;
        mc.player.getAbilities().flying = true;

        if (mode.isMode(Mode.Velocity)) {
            float yaw = mc.player.getYaw() * ((float) Math.PI / 180f);
            double velX = 0, velY = 0, velZ = 0;
            var input = mc.player.input.playerInput;

            if (input.forward())  { velX -= MathHelper.sin(yaw) * speed.getValue(); velZ += MathHelper.cos(yaw) * speed.getValue(); }
            if (input.backward()) { velX += MathHelper.sin(yaw) * speed.getValue(); velZ -= MathHelper.cos(yaw) * speed.getValue(); }
            if (input.left())     { velX -= MathHelper.cos(yaw) * speed.getValue(); velZ -= MathHelper.sin(yaw) * speed.getValue(); }
            if (input.right())    { velX += MathHelper.cos(yaw) * speed.getValue(); velZ += MathHelper.sin(yaw) * speed.getValue(); }
            if (input.jump())     velY =  vertSpeed.getValue();
            if (input.sneak())    velY = -vertSpeed.getValue();

            mc.player.setVelocity(new Vec3d(velX, velY, velZ));
        }
        // Vanilla mode: built-in creative flight handles movement via the ability flags above.
    }
}
