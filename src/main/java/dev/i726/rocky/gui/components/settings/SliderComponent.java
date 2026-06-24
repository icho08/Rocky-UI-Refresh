package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class SliderComponent extends SettingComponent {

    private final NumberSetting setting;
    private boolean dragging = false;

    public SliderComponent(NumberSetting setting) {
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return 20;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY, float delta) {
        ctx.fill(x, y, x + width, y + 20, GuiTheme.settingBg());

        ctx.text(Minecraft.getInstance().font,
                setting.getName().toString(), x + 8, y + 2, GuiTheme.textSecondary(), false);

        String valStr = formatValue(setting.getValue());
        int valW = Minecraft.getInstance().font.width(valStr);
        ctx.text(Minecraft.getInstance().font,
                valStr, x + width - valW - 6, y + 2, GuiTheme.textAccent(), false);

        int tx = x + 8, ty = y + 13, tw = width - 16;
        ctx.fill(tx, ty, tx + tw, ty + 4, GuiTheme.sliderTrack());

        float pct = (float) ((setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        pct = Math.max(0f, Math.min(1f, pct));
        int fillW = (int) (pct * tw);
        if (fillW > 0) ctx.fill(tx, ty, tx + fillW, ty + 4, GuiTheme.accentInt());

        int thumbX = tx + fillW - 2;
        ctx.fill(thumbX, ty - 2, thumbX + 4, ty + 6, GuiTheme.toggleThumb());

        queueTooltipIfHovered(setting, x, y, width, 20, mouseX, mouseY);
    }

    private String formatValue(double v) {
        if (setting.getIncrement() >= 1.0) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    private void updateValue(double mx, int x, int width) {
        int tx = x + 8, tw = width - 16;
        float pct = (float) Math.max(0, Math.min(1, (mx - tx) / tw));
        setting.setValue(setting.getMin() + pct * (setting.getMax() - setting.getMin()));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        if (button == 0 && mx >= x + 8 && mx < x + width - 8 && my >= y + 11 && my < y + 19) {
            dragging = true;
            updateValue(mx, x, width);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int x, int y, int width) {
        if (dragging) {
            updateValue(mx, x, width);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button, int x, int y, int width) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }
}
