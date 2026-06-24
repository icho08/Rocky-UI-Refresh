package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.MinMaxSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class MinMaxComponent extends SettingComponent {

    private final MinMaxSetting setting;
    private boolean draggingMin = false;
    private boolean draggingMax = false;

    public MinMaxComponent(MinMaxSetting setting) {
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return 22;
    }

    @Override
    public void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY, float delta) {
        ctx.fill(x, y, x + width, y + 22, GuiTheme.settingBg());

        ctx.text(Minecraft.getInstance().font,
                setting.getName().toString(), x + 8, y + 2, GuiTheme.textSecondary(), false);

        String range = formatVal(setting.getMinValue()) + " - " + formatVal(setting.getMaxValue());
        int rw = Minecraft.getInstance().font.width(range);
        ctx.text(Minecraft.getInstance().font,
                range, x + width - rw - 6, y + 2, GuiTheme.textAccent(), false);

        int tx = x + 8, ty = y + 14, tw = width - 16;
        ctx.fill(tx, ty, tx + tw, ty + 4, GuiTheme.sliderTrack());

        float pctMin = (float) ((setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        float pctMax = (float) ((setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        pctMin = Math.max(0f, Math.min(1f, pctMin));
        pctMax = Math.max(0f, Math.min(1f, pctMax));

        int fillStart = tx + (int) (pctMin * tw);
        int fillEnd   = tx + (int) (pctMax * tw);
        if (fillEnd > fillStart) ctx.fill(fillStart, ty, fillEnd, ty + 4, GuiTheme.accentInt());

        ctx.fill(fillStart - 2, ty - 2, fillStart + 2, ty + 6, GuiTheme.toggleThumb());
        ctx.fill(fillEnd - 2,   ty - 2, fillEnd + 2,   ty + 6, GuiTheme.toggleThumb());

        queueTooltipIfHovered(setting, x, y, width, 22, mouseX, mouseY);
    }

    private String formatVal(double v) {
        if (setting.getIncrement() >= 1.0) return String.valueOf((int) v);
        return String.format("%.1f", v);
    }

    private double pctToValue(double mx, int x, int width) {
        int tx = x + 8, tw = width - 16;
        float pct = (float) Math.max(0, Math.min(1, (mx - tx) / tw));
        return setting.getMin() + pct * (setting.getMax() - setting.getMin());
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        if (button != 0 || !inBounds(mx, my, x + 8, y + 12, width - 16, 8)) return false;

        float pctMin = (float) ((setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        float pctMax = (float) ((setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin()));
        int tx = x + 8, tw = width - 16;
        int thumbMin = tx + (int) (pctMin * tw);
        int thumbMax = tx + (int) (pctMax * tw);

        double distMin = Math.abs(mx - thumbMin);
        double distMax = Math.abs(mx - thumbMax);
        if (distMin <= distMax) { draggingMin = true; setting.setMinValue(pctToValue(mx, x, width)); }
        else                    { draggingMax = true; setting.setMaxValue(pctToValue(mx, x, width)); }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int x, int y, int width) {
        if (draggingMin) { setting.setMinValue(pctToValue(mx, x, width)); return true; }
        if (draggingMax) { setting.setMaxValue(pctToValue(mx, x, width)); return true; }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button, int x, int y, int width) {
        if (draggingMin || draggingMax) { draggingMin = false; draggingMax = false; return true; }
        return false;
    }
}
