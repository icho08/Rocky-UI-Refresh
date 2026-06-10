package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class NoRender extends Module {

    private final BooleanSetting noWeather = new BooleanSetting(
            EncryptedString.of("No Weather"), true)
            .setDescription(EncryptedString.of("Cancels rain and snow rendering — biggest FPS gain during storms"));

    private final BooleanSetting noClouds = new BooleanSetting(
            EncryptedString.of("No Clouds"), true)
            .setDescription(EncryptedString.of("Skips cloud rendering"));

    private final BooleanSetting noSky = new BooleanSetting(
            EncryptedString.of("No Sky"), false)
            .setDescription(EncryptedString.of("Skips sky rendering (makes sky black/void)"));

    public NoRender() {
        super(EncryptedString.of("No Render"),
                EncryptedString.of("Disables unnecessary visual elements for a significant FPS boost"),
                -1, CategoryManager.FPS);
        addSettings(noWeather, noClouds, noSky);
    }

    public boolean isNoWeather() { return noWeather.getValue(); }
    public boolean isNoClouds()  { return noClouds.getValue(); }
    public boolean isNoSky()     { return noSky.getValue(); }

    @Override public void onEnable()  { super.onEnable(); }
    @Override public void onDisable() { super.onDisable(); }
}
