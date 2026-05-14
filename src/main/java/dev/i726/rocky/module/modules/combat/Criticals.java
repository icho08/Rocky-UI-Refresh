package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class Criticals extends Module implements AttackListener {

    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.Packet, Mode.class)
            .setDescription(EncryptedString.of("Packet = invisible 3-packet crit, Jump = micro-jump"));
    private final BooleanSetting onlyGround = new BooleanSetting(EncryptedString.of("Only On Ground"), true)
            .setDescription(EncryptedString.of("Skip if already airborne (natural crit in progress)"));

    public enum Mode { Packet, Jump }

    public Criticals() {
        super(EncryptedString.of("Criticals"),
                EncryptedString.of("Forces critical hits on every attack"),
                -1, CategoryManager.PVP);
        addSettings(mode, onlyGround);
    }

    @Override
    public void onEnable() {
        eventManager.add(AttackListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(AttackListener.class, this);
        super.onDisable();
    }

    @Override
    public void onAttack(AttackEvent event) {
        if (mc.player == null) return;
        if (!(event.getTarget() instanceof LivingEntity)) return;
        if (mc.player.isInFluid() || mc.player.isClimbing()) return;
        if (onlyGround.getValue() && !mc.player.isOnGround()) return;

        if (mode.isMode(Mode.Packet)) {
            // Classic 3-packet crit: send up → down → grounded before the attack lands.
            // The server sees the player briefly airborne with fallDistance > 0 → critical hit.
            double x  = mc.player.getX();
            double y  = mc.player.getY();
            double z  = mc.player.getZ();
            boolean hc = mc.player.horizontalCollision;

            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false, hc));
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, hc));
            mc.getNetworkHandler().sendPacket(
                    new PlayerMoveC2SPacket.OnGroundOnly(true, hc));

        } else { // Jump
            if (mc.player.isOnGround()) {
                mc.player.setVelocity(
                        mc.player.getVelocity().x,
                        0.42,
                        mc.player.getVelocity().z);
            }
        }
    }
}
