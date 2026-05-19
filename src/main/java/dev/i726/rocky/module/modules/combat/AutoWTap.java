package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public final class AutoWTap extends Module implements PacketSendListener, HudListener {

    private final MinMaxSetting delay = new MinMaxSetting(EncryptedString.of("Delay"), 0, 1000, 1, 220, 280)
            .setDescription(EncryptedString.of("Random ms delay between releasing and re-pressing W"));

    private final BooleanSetting inAir = new BooleanSetting(EncryptedString.of("In Air"), false)
            .setDescription(EncryptedString.of("W-tap even while airborne"));

    private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance %"), 0, 100, 100, 1)
            .setDescription(EncryptedString.of("Per-hit chance to actually execute a W-tap"));

    private final BooleanSetting requireSprint = new BooleanSetting(EncryptedString.of("Require Sprint"), true)
            .setDescription(EncryptedString.of("Only W-tap when sprinting"));

    private final BooleanSetting sprintPackets = new BooleanSetting(EncryptedString.of("Sprint Packets"), true)
            .setDescription(EncryptedString.of("Send START/STOP sprint packets so the server registers the W-tap properly"));

    private final TimerUtils wtapTimer = new TimerUtils();
    private final Random     random    = new Random();
    private boolean isWTapping  = false;
    private int     currentDelay;

    public AutoWTap() {
        super(EncryptedString.of("Auto W-Tap"),
                EncryptedString.of("Automatically W-taps for combo extension"),
                -1, CategoryManager.PVP);
        addSettings(delay, inAir, chance, requireSprint, sprintPackets);
    }

    @Override
    public void onEnable() {
        eventManager.add(PacketSendListener.class, this);
        eventManager.add(HudListener.class, this);
        currentDelay = delay.getRandomValueInt();
        isWTapping   = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (isWTapping) {
            mc.options.forwardKey.setPressed(true);
            if (sprintPackets.getValue() && mc.player != null && mc.player.isSprinting()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                        mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
        }
        eventManager.remove(PacketSendListener.class, this);
        eventManager.remove(HudListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.player == null || mc.currentScreen != null) return;

        if (!shouldWTap() || !mc.options.forwardKey.isPressed()) {
            isWTapping = false;
            return;
        }

        if (isWTapping && wtapTimer.delay(currentDelay)) {
            // Re-press W
            mc.options.forwardKey.setPressed(true);
            // Re-start sprint on server
            if (sprintPackets.getValue()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                        mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
            }
            isWTapping   = false;
            currentDelay = delay.getRandomValueInt();
        }
    }

    private boolean shouldWTap() {
        return mc.player != null && (mc.player.isOnGround() || inAir.getValue());
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;

        packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
            @Override public void interact(Hand hand) {}
            @Override public void interactAt(Hand hand, Vec3d pos) {}

            @Override
            public void attack() {
                if (!shouldWTap() || !mc.options.forwardKey.isPressed()) return;
                if (requireSprint.getValue() && !mc.player.isSprinting()) return;

                int c = chance.getValueInt();
                if (c < 100 && random.nextInt(100) >= c) return;

                currentDelay = delay.getRandomValueInt();

                // Release W and tell the server we stopped sprinting
                mc.options.forwardKey.setPressed(false);
                if (sprintPackets.getValue()) {
                    mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(
                            mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                }
                wtapTimer.reset();
                isWTapping = true;
            }
        });
    }
}
