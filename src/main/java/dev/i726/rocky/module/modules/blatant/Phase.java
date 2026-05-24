package dev.i726.rocky.module.modules.blatant;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class Phase extends Module implements TickListener {

    private final NumberSetting speed = new NumberSetting(EncryptedString.of("Speed"), 0.1, 2.0, 0.5, 0.05)
            .setDescription(EncryptedString.of("Movement speed while phasing through blocks"));

    public Phase() {
        super(EncryptedString.of("Phase"),
                EncryptedString.of("Move through blocks using noClip — will kick you on most servers"),
                -1, CategoryManager.BLATANT);
        addSettings(speed);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        if (mc.player != null) mc.player.noClip = true;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.player != null) mc.player.noClip = false;
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        mc.player.noClip = true;

        float yaw = mc.player.getYaw() * ((float) Math.PI / 180f);
        double velX = 0, velY = 0, velZ = 0;
        var input = mc.player.input.playerInput;

        if (input.forward())  { velX -= MathHelper.sin(yaw) * speed.getValue(); velZ += MathHelper.cos(yaw) * speed.getValue(); }
        if (input.backward()) { velX += MathHelper.sin(yaw) * speed.getValue(); velZ -= MathHelper.cos(yaw) * speed.getValue(); }
        if (input.left())     { velX -= MathHelper.cos(yaw) * speed.getValue(); velZ -= MathHelper.sin(yaw) * speed.getValue(); }
        if (input.right())    { velX += MathHelper.cos(yaw) * speed.getValue(); velZ += MathHelper.sin(yaw) * speed.getValue(); }
        if (input.jump())     velY =  speed.getValue();
        if (input.sneak())    velY = -speed.getValue();

        mc.player.setVelocity(new Vec3d(velX, velY, velZ));
        mc.player.fallDistance = 0f;
    }
}
