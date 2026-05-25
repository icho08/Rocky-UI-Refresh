package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

import java.lang.reflect.Field;

public final class Timer extends Module implements TickListener {

    private final NumberSetting speed = new NumberSetting(
            EncryptedString.of("Speed"), 0.1, 10.0, 2.0, 0.1)
            .setDescription(EncryptedString.of("Tick speed multiplier — 1.0 = normal, 2.0 = 2x faster"));

    private final BooleanSetting resetOnDisable = new BooleanSetting(
            EncryptedString.of("Reset on Disable"), true)
            .setDescription(EncryptedString.of("Restores normal speed when the module turns off"));

    // Cached reflection field for the timer ms-per-tick value
    private Field timerField;
    private float originalMsPerTick = 50f;
    private boolean fieldFound = false;

    public Timer() {
        super(EncryptedString.of("Timer"),
                EncryptedString.of("Speeds up or slows down the game tick rate"),
                -1, CategoryManager.BLATANT);
        addSettings(speed, resetOnDisable);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        findTimerField();
        originalMsPerTick = getMs();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        if (resetOnDisable.getValue()) setMs(originalMsPerTick);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (!fieldFound) return;
        setMs(50f / (float) speed.getValue());
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static final String[] CANDIDATE_FIELDS = {
            "msPerTick", "tickLength", "timerSpeed", "timeScale",
            "field_1724", "field_2012"
    };

    private void findTimerField() {
        Object counter = mc.getRenderTickCounter();
        if (counter == null) return;
        for (String name : CANDIDATE_FIELDS) {
            for (Class<?> c = counter.getClass(); c != null; c = c.getSuperclass()) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (f.getType() == float.class || f.getType() == double.class) {
                        f.setAccessible(true);
                        timerField = f;
                        fieldFound = true;
                        return;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
        }
    }

    private float getMs() {
        Object counter = mc.getRenderTickCounter();
        if (!fieldFound || counter == null) return 50f;
        try {
            return timerField.getFloat(counter);
        } catch (Exception e) {
            return 50f;
        }
    }

    private void setMs(float ms) {
        Object counter = mc.getRenderTickCounter();
        if (!fieldFound || counter == null) return;
        try {
            timerField.setFloat(counter, ms);
        } catch (Exception ignored) {}
    }
}
