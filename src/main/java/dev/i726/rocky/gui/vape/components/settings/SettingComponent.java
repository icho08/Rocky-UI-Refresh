package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.components.Component;
import dev.i726.rocky.module.setting.Setting;

public abstract class SettingComponent<T extends Setting<?>> extends Component {
    protected final T setting;

    public SettingComponent(T setting, double x, double y, double width, double height) {
        super(x, y, width, height);
        this.setting = setting;
    }
}
