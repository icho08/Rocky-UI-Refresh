package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

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

    public TimeChanger() {
        super(EncryptedString.of("Time Changer"),
                EncryptedString.of("Change the time of day and weather client-side"),
                -1, CategoryManager.ESP);
        addSettings(time, lockTime, weather);
    }

    public long getTargetTime()       { return (long) time.getValue(); }
    public boolean isLockTime()       { return lockTime.getValue(); }
    public WeatherMode getWeather()   { return weather.getMode(); }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.world == null) return;
        if (lockTime.getValue()) {
            mc.world.setTimeOfDay(getTargetTime());
        }
    }
}
