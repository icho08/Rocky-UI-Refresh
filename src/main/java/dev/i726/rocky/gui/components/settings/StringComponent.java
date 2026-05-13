package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public class StringComponent extends SettingComponent {

    private final StringSetting setting;

    public StringComponent(StringSetting setting) {
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

        String val = setting.getValue();
        int vw = MinecraftClient.getInstance().textRenderer.getWidth(val);
        ctx.drawText(MinecraftClient.getInstance().textRenderer,
                val, x + width - vw - 6, y + 3, GuiTheme.textPrimary(), false);
    }
}
