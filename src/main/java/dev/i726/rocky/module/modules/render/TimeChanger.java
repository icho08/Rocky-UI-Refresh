package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class TimeChanger extends Module implements TickListener {

    public enum WeatherMode { NORMAL, CLEAR, RAIN, THUNDER }

    private final NumberSetting time = new NumberSetting(
            EncryptedString.of("Time"), 0, 24000, 6000, 200)
            .setDescription(EncryptedString.of("Time of day: 0=midnight  6000=noon  12000=dusk  18000=midnight"));

    private final ModeSetting<WeatherMode> weather = new ModeSetting<>(
            EncryptedString.of("Weather"), WeatherMode.NORMAL, WeatherMode.class)
            .setDescription(EncryptedString.of("Override client-side weather: NORMAL = server weather"));

    public TimeChanger() {
        super(EncryptedString.of("Time Changer"),
                EncryptedString.of("Change the sky time and weather client-side"),
                -1, CategoryManager.ESP);
        addSettings(time, weather);
    }

    /** Called by ClientWorldMixin to get the time this module wants to display. */
    public long getTargetTime() {
        return (long) time.getValue();
    }

    @Override public void onEnable()  { eventManager.add(TickListener.class, this); super.onEnable(); }
    @Override public void onDisable() { eventManager.remove(TickListener.class, this); super.onDisable(); }

    @Override
    public void onTick() {
        if (mc.level == null) return;

        WeatherMode w = weather.getMode();
        if (w == WeatherMode.NORMAL) return;

        // Reflect-write the weather gradients each tick so the sky/audio matches
        float rain    = (w == WeatherMode.CLEAR) ? 0f : 1f;
        float thunder = (w == WeatherMode.THUNDER) ? 1f : 0f;

        Object world = mc.level;
        setFloat(world, rain,    "rainGradient", "rain", "precipitation", "rainLevel");
        setFloat(world, rain,    "rainGradientPrev", "rainPrev", "prevRain", "prevPrecipitation");
        setFloat(world, thunder, "thunderGradient", "thunder", "thunderLevel");
        setFloat(world, thunder, "thunderGradientPrev", "thunderPrev", "prevThunder");
    }

    private void setFloat(Object obj, float value, String... candidates) {
        Class<?> c = obj.getClass();
        while (c != null && c != Object.class) {
            for (String name : candidates) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(name);
                    if (f.getType() == float.class) {
                        f.setAccessible(true);
                        f.setFloat(obj, value);
                        return;
                    }
                } catch (NoSuchFieldException ignored) {
                } catch (Exception ignored) {}
            }
            c = c.getSuperclass();
        }
    }
}
