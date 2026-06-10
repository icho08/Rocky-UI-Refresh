package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

import java.lang.reflect.Field;

public final class TimeChanger extends Module implements TickListener {

    public enum WeatherMode { NORMAL, CLEAR, RAIN, THUNDER }

    private final NumberSetting time = new NumberSetting(
            EncryptedString.of("Time"), 0, 24000, 6000, 200)
            .setDescription(EncryptedString.of("Time of day: 0=midnight 6000=noon 12000=dusk 18000=midnight"));

    private final BooleanSetting lockTime = new BooleanSetting(
            EncryptedString.of("Lock Time"), true)
            .setDescription(EncryptedString.of("Freeze the sky at the set time"));

    private final ModeSetting<WeatherMode> weather = new ModeSetting<>(
            EncryptedString.of("Weather"), WeatherMode.NORMAL, WeatherMode.class)
            .setDescription(EncryptedString.of("Override client-side weather: NORMAL = server weather"));

    private Field cachedTimeField    = null;
    private Field cachedRainField    = null;
    private Field cachedRainPrevField    = null;
    private Field cachedThunderField = null;
    private Field cachedThunderPrevField = null;

    public TimeChanger() {
        super(EncryptedString.of("Time Changer"),
                EncryptedString.of("Change the time of day and weather client-side"),
                -1, CategoryManager.ESP);
        addSettings(time, lockTime, weather);
    }

    public long getTargetTime()     { return (long) time.getValue(); }
    public boolean isLockTime()     { return lockTime.getValue(); }
    public WeatherMode getWeather() { return weather.getMode(); }

    @Override public void onEnable()  { eventManager.add(TickListener.class, this); super.onEnable(); }
    @Override public void onDisable() { eventManager.remove(TickListener.class, this); super.onDisable(); }

    @Override
    public void onTick() {
        if (mc.world == null) return;

        if (lockTime.getValue()) setWorldTime(getTargetTime());

        WeatherMode w = weather.getMode();
        if (w != WeatherMode.NORMAL) setWeather(w);
    }

    // ── time ────────────────────────────────────────────────────────────────

    private void setWorldTime(long target) {
        try {
            Object props = mc.world.getLevelProperties();
            if (props == null) return;
            if (cachedTimeField == null)
                cachedTimeField = findField(props.getClass(), long.class,
                        "timeOfDay", "time", "dayTime", "worldTime");
            if (cachedTimeField != null)
                cachedTimeField.setLong(props, target);
        } catch (Exception ignored) {}
    }

    // ── weather ─────────────────────────────────────────────────────────────

    private void setWeather(WeatherMode mode) {
        float rain    = (mode == WeatherMode.CLEAR) ? 0f : 1f;
        float thunder = (mode == WeatherMode.THUNDER) ? 1f : 0f;

        Object world = mc.world;
        try {
            if (cachedRainField == null)
                cachedRainField = findField(world.getClass(), float.class,
                        "rainGradient", "rain", "precipitation", "rainLevel");
            if (cachedRainField != null) cachedRainField.setFloat(world, rain);

            if (cachedRainPrevField == null)
                cachedRainPrevField = findField(world.getClass(), float.class,
                        "rainGradientPrev", "rainPrev", "prevRain", "prevPrecipitation");
            if (cachedRainPrevField != null) cachedRainPrevField.setFloat(world, rain);

            if (cachedThunderField == null)
                cachedThunderField = findField(world.getClass(), float.class,
                        "thunderGradient", "thunder", "thunderLevel");
            if (cachedThunderField != null) cachedThunderField.setFloat(world, thunder);

            if (cachedThunderPrevField == null)
                cachedThunderPrevField = findField(world.getClass(), float.class,
                        "thunderGradientPrev", "thunderPrev", "prevThunder");
            if (cachedThunderPrevField != null) cachedThunderPrevField.setFloat(world, thunder);
        } catch (Exception ignored) {}
    }

    // ── reflection helper ────────────────────────────────────────────────────

    private Field findField(Class<?> start, Class<?> type, String... candidates) {
        Class<?> c = start;
        while (c != null && c != Object.class) {
            for (String name : candidates) {
                try {
                    Field f = c.getDeclaredField(name);
                    if (f.getType() == type) {
                        f.setAccessible(true);
                        return f;
                    }
                } catch (NoSuchFieldException ignored) {}
            }
            c = c.getSuperclass();
        }
        return null;
    }
}
