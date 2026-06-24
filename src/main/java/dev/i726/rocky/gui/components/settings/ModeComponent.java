package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.ModeSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModeComponent extends SettingComponent {

    private final ModeSetting<?> setting;

    public ModeComponent(ModeSetting<?> setting) {
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return 14;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY, float delta) {
        ctx.fill(x, y, x + width, y + 14, GuiTheme.settingBg());

        ctx.text(Minecraft.getInstance().font,
                setting.getName().toString(), x + 8, y + 3, GuiTheme.textSecondary(), false);

        String modeName = formatName(setting.getMode().name());
        int modeW = Minecraft.getInstance().font.width(modeName);
        ctx.text(Minecraft.getInstance().font,
                modeName, x + width - modeW - 6, y + 3, GuiTheme.textAccent(), false);

        queueTooltipIfHovered(setting, x, y, width, 14, mouseX, mouseY);
    }

    private String formatName(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.charAt(0) + name.substring(1).toLowerCase().replace('_', ' ');
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        if (inBounds(mx, my, x, y, width, 14)) {
            if (button == 0) setting.cycle();
            else if (button == 1) setting.cycleBack();
            return true;
        }
        return false;
    }
}
