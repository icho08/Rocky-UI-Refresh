package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class NoFall extends Module implements TickListener {

    private final NumberSetting minDist = new NumberSetting(EncryptedString.of("Min Distance"), 0, 10, 2, 0.5)
            .setDescription(EncryptedString.of("Fall distance before sending ground packet (0 = always)"));

    public NoFall() {
        super(EncryptedString.of("No Fall"),
                EncryptedString.of("Prevents fall damage by spoofing ground packets to the server"),
                -1, CategoryManager.MOVEMENT);
        addSettings(minDist);
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
        if (mc.player.isOnGround()) return;
        if (mc.player.isInFluid()) return;
        if (mc.player.getVelocity().y >= 0) return; // ascending — skip
        if (mc.player.fallDistance < minDist.getValueFloat()) return;

        // Tell the server the player is on the ground each tick they're falling.
        // This keeps the server-side fall distance at zero so no damage is dealt on landing.
        mc.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
    }
}
