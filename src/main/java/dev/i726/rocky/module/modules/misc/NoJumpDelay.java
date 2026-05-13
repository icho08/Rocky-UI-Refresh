package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public final class NoJumpDelay extends Module implements TickListener {
        // Vanilla cooldown is 10 ticks. We allow it to be lowered, but never
        // to "every tick" — that's physically impossible and is the obvious
        // giveaway any anti-cheat will pick up. Default 4–5 ticks is faster
        // than vanilla but still in human range.
        private final MinMaxSetting cooldown = new MinMaxSetting(EncryptedString.of("Cooldown"), 1, 10, 1, 4, 5)
                        .setDescription(EncryptedString.of("Random ticks between jumps. Vanilla is 10."));

        private final NumberSetting skipChance = new NumberSetting(EncryptedString.of("Skip Chance %"), 0, 100, 0, 1)
                        .setDescription(EncryptedString.of("Chance per attempt to skip a jump (looks like a missed input)"));

        private final BooleanSetting requireMoving = new BooleanSetting(EncryptedString.of("Require Moving"), false)
                        .setDescription(EncryptedString.of("Only autojump while you're actually moving"));

        private int clock = 0;
        private final Random random = new Random();

        public NoJumpDelay() {
                super(EncryptedString.of("Bunny Hop"),
                EncryptedString.of("Removes jump delay"),
                                -1,
                                CategoryManager.MOVEMENT);
                addSettings(cooldown, skipChance, requireMoving);
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
                if (mc.player == null) return;
                if (mc.currentScreen != null) return;

                if (clock > 0) { clock--; return; }

                if (!mc.player.isOnGround()) return;

                if (GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_SPACE) != GLFW.GLFW_PRESS)
                        return;

                if (requireMoving.getValue()) {
                        double vx = mc.player.getVelocity().x;
                        double vz = mc.player.getVelocity().z;
                        if (vx * vx + vz * vz < 0.005) return;
                }

                int sc = skipChance.getValueInt();
                if (sc > 0 && random.nextInt(100) < sc) {
                        // Spend at least one cycle of cooldown so we don't retry every tick
                        clock = cooldown.getRandomValueInt();
                        return;
                }

                mc.options.jumpKey.setPressed(false);
                mc.player.jump();
                clock = cooldown.getRandomValueInt();
        }
}
