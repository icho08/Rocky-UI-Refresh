package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class ThemePicker extends Module implements TickListener {

    private final ModeSetting<GuiTheme.ThemeColor> theme = new ModeSetting<>(
            EncryptedString.of("Color"), GuiTheme.ThemeColor.PURPLE, GuiTheme.ThemeColor.class)
            .setDescription(EncryptedString.of("GUI accent color"));

    public ThemePicker() {
        super(EncryptedString.of("Theme"),
                EncryptedString.of("Change the GUI accent color"),
                -1, CategoryManager.GUI);
        addSettings(theme);
    }

    @Override
    public void onEnable() {
        eventManager.add(TickListener.class, this);
        applyTheme();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(TickListener.class, this);
        super.onDisable();
    }

    @Override
    public void onTick() {
        applyTheme();
    }

    private void applyTheme() {
        GuiTheme.setTheme(theme.getMode());
    }
}
