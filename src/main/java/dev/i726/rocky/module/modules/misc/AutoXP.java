package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.Hand;

import java.util.Random;

public final class AutoXP extends Module implements TickListener {
        // Random per-throw delay in ticks
        private final MinMaxSetting delay = new MinMaxSetting(EncryptedString.of("Delay (ticks)"), 1, 40, 1, 3, 5)
                        .setDescription(EncryptedString.of("Random ticks between throws"));
        private final BooleanSetting onlyLowXP = new BooleanSetting(EncryptedString.of("Only Low XP"), true)
                        .setDescription(EncryptedString.of("Only throw when XP is below the threshold"));
        private final NumberSetting xpThreshold = new NumberSetting(EncryptedString.of("XP Threshold"), 1, 100, 30, 1)
                        .setDescription(EncryptedString.of("Stop throwing once you hit this level"));
        private final BooleanSetting smartThrow = new BooleanSetting(EncryptedString.of("Smart Throw"), true)
                        .setDescription(EncryptedString.of("Throw at optimal angles"));
        private final MinMaxSetting pitchJitter = new MinMaxSetting(EncryptedString.of("Pitch Jitter"), 0.0, 20.0, 0.1, 3.0, 7.0)
                        .setDescription(EncryptedString.of("Random ± pitch noise applied around -45° per throw"));
        private final MinMaxSetting yawJitter = new MinMaxSetting(EncryptedString.of("Yaw Jitter"), 0.0, 20.0, 0.1, 1.0, 3.0)
                        .setDescription(EncryptedString.of("Random ± yaw noise applied around current yaw per throw"));
        private final BooleanSetting restoreRotation = new BooleanSetting(EncryptedString.of("Restore Rotation"), true)
                        .setDescription(EncryptedString.of("Snap your view back after each throw (off = leaves the new look)"));

        private int clock = 0;
        private final Random random = new Random();

        public AutoXP() {
                super(EncryptedString.of("Auto XP"),
                EncryptedString.of("Automatically repairs with XP"),
                                -1,
                                CategoryManager.PLAYER);
                addSettings(delay, onlyLowXP, xpThreshold, smartThrow, pitchJitter, yawJitter, restoreRotation);
        }

        @Override
        public void onEnable() {
                eventManager.add(TickListener.class, this);
                clock = 0;
                super.onEnable();
        }

        @Override
        public void onDisable() {
                eventManager.remove(TickListener.class, this);
                super.onDisable();
        }

        @Override
        public void onTick() {
                if (mc.player == null || mc.currentScreen != null) return;

                if (clock > 0) {
                        clock--;
                        return;
                }

                if (mc.player.getMainHandStack().getItem() != Items.EXPERIENCE_BOTTLE) return;

                if (onlyLowXP.getValue() && mc.player.experienceLevel >= xpThreshold.getValueInt()) return;

                if (!mc.options.useKey.isPressed()) return;

                if (smartThrow.getValue()) {
                        // Sync the throwing rotation to the server before the use packet
                        // so the bottle actually launches at the optimal arc. The pitch
                        // and yaw deltas are randomized per-throw via MinMaxSettings so
                        // the rotation looks human and won't trip GCD / constant-pitch
                        // heuristics.
                        float prevPitch = mc.player.getPitch();
                        float prevYaw = mc.player.getYaw();
                        float pitchAmp = pitchJitter.getRandomValueFloat();
                        float yawAmp = yawJitter.getRandomValueFloat();
                        float pitchSign = random.nextBoolean() ? 1f : -1f;
                        float yawSign = random.nextBoolean() ? 1f : -1f;
                        float aimPitch = -45.0f + pitchSign * pitchAmp;
                        float aimYaw = prevYaw + yawSign * yawAmp;
                        mc.player.setPitch(aimPitch);
                        mc.player.setYaw(aimYaw);
                        mc.getNetworkHandler().sendPacket(
                                new PlayerMoveC2SPacket.LookAndOnGround(aimYaw, aimPitch, mc.player.isOnGround(), false));
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        if (restoreRotation.getValue()) {
                                mc.player.setPitch(prevPitch);
                                mc.player.setYaw(prevYaw);
                                mc.getNetworkHandler().sendPacket(
                                        new PlayerMoveC2SPacket.LookAndOnGround(prevYaw, prevPitch, mc.player.isOnGround(), false));
                        }
                } else {
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }

                clock = delay.getRandomValueInt();
        }
}
