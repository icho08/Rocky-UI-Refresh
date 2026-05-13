package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

public final class AutoWTap extends Module implements PacketSendListener, HudListener {
        private final MinMaxSetting delay = new MinMaxSetting(EncryptedString.of("Delay"), 0, 1000, 1, 230, 270)
                        .setDescription(EncryptedString.of("Random ms delay between attack and W re-press"));
        private final BooleanSetting inAir = new BooleanSetting(EncryptedString.of("In Air"), false)
                        .setDescription(EncryptedString.of("Whether it should W tap in air"));
        private final NumberSetting chance = new NumberSetting(EncryptedString.of("Chance %"), 0, 100, 100, 1)
                        .setDescription(EncryptedString.of("Chance to actually wtap on a given hit (humanizes consistency)"));
        private final BooleanSetting requireSprint = new BooleanSetting(EncryptedString.of("Require Sprint"), true)
                        .setDescription(EncryptedString.of("Only wtap when actually sprinting (turn off for forced wtap)"));
        private final BooleanSetting resetOnTarget = new BooleanSetting(EncryptedString.of("Reset Per Target"), true)
                        .setDescription(EncryptedString.of("Re-roll the random delay every wtap rather than only on completion"));
        
        private final TimerUtils wtapTimer = new TimerUtils();
        private final Random random = new Random();
        private boolean isWTapping = false;
        private int currentDelay;

        public AutoWTap() {
                super(EncryptedString.of("Auto W-Tap"),
                EncryptedString.of("Automatically W-taps for combos"),
                                -1,
                                CategoryManager.PVP);
                addSettings(delay, inAir, chance, requireSprint, resetOnTarget);
        }

        @Override
        public void onEnable() {
                eventManager.add(PacketSendListener.class, this);
                eventManager.add(HudListener.class, this);
                currentDelay = delay.getRandomValueInt();
                isWTapping = false;
                super.onEnable();
        }

        @Override
        public void onDisable() {
                // Restore natural key state
                if (isWTapping && mc.options.forwardKey.isPressed()) {
                        mc.options.forwardKey.setPressed(true);
                }
                eventManager.remove(PacketSendListener.class, this);
                eventManager.remove(HudListener.class, this);
                super.onDisable();
        }

        @Override
        public void onRenderHud(HudEvent event) {
                if (mc.player == null || mc.currentScreen != null) return;
                
                // Only wtap if we should and are actually moving forward
                if (!shouldWTap() || !mc.options.forwardKey.isPressed()) {
                        isWTapping = false;
                        return;
                }

                if (isWTapping && wtapTimer.delay(currentDelay)) {
                        // Re-press W key to complete wtap
                        mc.options.forwardKey.setPressed(true);
                        isWTapping = false;
                        if (resetOnTarget.getValue())
                                currentDelay = delay.getRandomValueInt();
                }
        }

        private boolean shouldWTap() {
                return mc.player.isOnGround() || inAir.getValue();
        }

        @Override
        public void onPacketSend(PacketSendEvent event) {
                if (!(event.packet instanceof PlayerInteractEntityC2SPacket packet)) return;

                packet.handle(new PlayerInteractEntityC2SPacket.Handler() {
                        @Override
                        public void interact(Hand hand) {}

                        @Override
                        public void interactAt(Hand hand, Vec3d pos) {}

                        @Override
                        public void attack() {
                                if (!shouldWTap() || !mc.options.forwardKey.isPressed())
                                        return;
                                if (requireSprint.getValue() && !mc.player.isSprinting())
                                        return;

                                // Random skip — keeps the wtap pattern from looking metronome-perfect
                                int c = chance.getValueInt();
                                if (c < 100 && random.nextInt(100) >= c) return;

                                // Re-roll the delay every wtap so the cadence varies
                                currentDelay = delay.getRandomValueInt();

                                // Start wtap by releasing W
                                mc.options.forwardKey.setPressed(false);
                                wtapTimer.reset();
                                isWTapping = true;
                        }
                });
        }
}