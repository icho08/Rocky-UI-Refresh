package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class BooleanComponent extends SettingComponent {

    private final BooleanSetting setting;

    public BooleanComponent(BooleanSetting setting) {
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return 14;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int mouseX, int mouseY, float delta) {
        ctx.fill(x, y, x + width, y + 14, GuiTheme.settingBg());

        ctx.drawText(MinecraftClient.getInstance().textRenderer,
                setting.getName().toString(), x + 8, y + 3, GuiTheme.textSecondary(), false);

        int cbx = x + width - 16;
        int cby = y + 2;
        ctx.fill(cbx, cby, cbx + 10, cby + 10, GuiTheme.border());
        if (setting.getValue()) {
            ctx.fill(cbx + 1, cby + 1, cbx + 9, cby + 9, GuiTheme.accentInt());
            ctx.fill(cbx + 3, cby + 3, cbx + 7, cby + 7, GuiTheme.rgba(255, 255, 255, 210));
        } else {
            ctx.fill(cbx + 1, cby + 1, cbx + 9, cby + 9, GuiTheme.toggleOff());
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        if (button == 0 && inBounds(mx, my, x, y, width, 14)) {
            setting.toggle();
            return true;
        }
        return false;
    }
}
