package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.AttackListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;

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
        if (mc.player.isInLiquid() || mc.player.onClimbable()) return;
        if (onlyGround.getValue() && !mc.player.onGround()) return;

        if (mode.isMode(Mode.Packet)) {
            // Classic 3-packet crit: send up → down → grounded before the attack lands.
            // The server sees the player briefly airborne with fallDistance > 0 → critical hit.
            double x  = mc.player.getX();
            double y  = mc.player.getY();
            double z  = mc.player.getZ();
            boolean hc = mc.player.horizontalCollision;

            mc.getConnection().send(
                    new ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, false, hc));
            mc.getConnection().send(
                    new ServerboundMovePlayerPacket.Pos(x, y, z, false, hc));
            mc.getConnection().send(
                    new ServerboundMovePlayerPacket.StatusOnly(true, hc));

        } else { // Jump
            if (mc.player.onGround()) {
                mc.player.setDeltaMovement(
                        mc.player.getDeltaMovement().x,
                        0.42,
                        mc.player.getDeltaMovement().z);
            }
        }
    }
}
