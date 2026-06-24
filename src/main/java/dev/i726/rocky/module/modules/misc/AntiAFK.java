package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;

public final class AntiAFK extends Module implements TickListener {

    private final NumberSetting interval = new NumberSetting(EncryptedString.of("Interval"), 5, 120, 30, 1)
            .setDescription(EncryptedString.of("Seconds between each anti-AFK action"));
    private final ModeSetting<Mode> mode = new ModeSetting<>(EncryptedString.of("Mode"), Mode.Rotate, Mode.class)
            .setDescription(EncryptedString.of("Which action to perform"));
    private final BooleanSetting rotateRandom = new BooleanSetting(EncryptedString.of("Random Rotation"), false)
            .setDescription(EncryptedString.of("Use a random rotation offset instead of a fixed 45 degrees"));
    private final BooleanSetting chat = new BooleanSetting(EncryptedString.of("Chat Ping"), false)
            .setDescription(EncryptedString.of("Sends a dot in chat alongside the action (bypasses some anti-AFK plugins)"));

    private final TimerUtils timer = new TimerUtils();
    private boolean sneakToggle = false;

    public enum Mode { Rotate, Jump, Sneak }

    public AntiAFK() {
        super(EncryptedString.of("Anti AFK"),
                EncryptedString.of("Performs periodic actions to prevent AFK kick"),
                -1, CategoryManager.AUTOMATION);
        addSettings(interval, mode, rotateRandom, chat);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        timer.reset();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (mc.options != null) mc.options.keyShift.setDown(false);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null) return;
        if (!timer.hasReached(interval.getValue() * 1000.0)) return;
        timer.reset();

        Mode m = mode.getMode();
        if (m == Mode.Rotate) {
            float delta = rotateRandom.getValue()
                    ? (float) (Math.random() * 360)
                    : 45f;
            mc.player.setYRot(mc.player.yRot() + delta);

        } else if (m == Mode.Jump) {
            if (mc.player.onGround()) mc.player.jumpFromGround();

        } else if (m == Mode.Sneak) {
            sneakToggle = !sneakToggle;
            mc.options.keyShift.setDown(sneakToggle);
        }

        if (chat.getValue() && mc.player.connection != null) {
            mc.player.connection.sendChat(".");
        }
    }
}
