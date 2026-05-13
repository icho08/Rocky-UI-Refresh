package dev.i726.rocky.module.modules.render;
import dev.i726.rocky.gui.GuiTheme;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.ClickGUI;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.ColorUtils;
import dev.i726.rocky.utils.EncryptedString;

import java.awt.*;

public final class Chams extends Module {
    public final BooleanSetting throughWalls = new BooleanSetting(EncryptedString.of("Through Walls"), true)
            .setDescription(EncryptedString.of("See players through solid blocks"));
    public final NumberSetting opacity = new NumberSetting(EncryptedString.of("Opacity"), 0, 255, 150, 5);

    public Chams() {
        super(EncryptedString.of("Chams"),
                EncryptedString.of("See players through walls"),
                -1,
                CategoryManager.ESP);
        addSettings(throughWalls, opacity);
    }

    public Color getColor() {
        Color a = GuiTheme.accent();
        return new Color(a.getRed(), a.getGreen(), a.getBlue(), opacity.getValueInt());
    }
}
