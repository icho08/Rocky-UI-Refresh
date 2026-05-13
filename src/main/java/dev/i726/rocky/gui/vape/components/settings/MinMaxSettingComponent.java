package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class MinMaxSettingComponent extends SettingComponent<MinMaxSetting> {
    private boolean draggingMin, draggingMax;

    public MinMaxSettingComponent(MinMaxSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double barX     = x + 8;
        double barWidth = width - 16;

        if (draggingMin) {
            double pct = Math.max(0, Math.min(1, (mouseX - barX) / barWidth));
            double val = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
            if (val < setting.getMaxValue()) setting.setMinValue(val);
        } else if (draggingMax) {
            double pct = Math.max(0, Math.min(1, (mouseX - barX) / barWidth));
            double val = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
            if (val > setting.getMinValue()) setting.setMaxValue(val);
        }

        boolean hovered = isHovered(mouseX, mouseY);

        // Row background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.SETTING_BG.getRGB());
        if (hovered || draggingMin || draggingMax) {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.HOVER_OVERLAY.getRGB());
        }
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height), VapeTheme.SEPARATOR.getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Name
        context.drawText(mc.textRenderer, setting.getName().toString(),
                (int)(x + 8), (int)(y + 4), VapeTheme.TEXT_DIM.getRGB(), false);

        // Range pill
        String range = (int)setting.getMinValue() + " – " + (int)setting.getMaxValue();
        int rw = mc.textRenderer.getWidth(range) + 6;
        int rx = (int)(x + width - rw - 4);
        int ry = (int)(y + 3);
        RenderUtils.drawRoundedRect(context, rx, ry, rx + rw, ry + 10, 2,
                new Color(34, 211, 238, 25).getRGB());
        context.drawText(mc.textRenderer, range, rx + 3, ry + 1, VapeTheme.ACCENT.getRGB(), false);

        // Track
        double barY = y + height - 9;
        double barH = 2;
        RenderUtils.drawRoundedRect(context,
                (float)barX, (float)barY,
                (float)(barX + barWidth), (float)(barY + barH), 1,
                new Color(38, 38, 42, 255).getRGB());

        // Fill between min and max
        double minPct = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double maxPct = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double fillX  = barX + minPct * barWidth;
        double fillW  = (maxPct - minPct) * barWidth;
        if (fillW > 0) {
            RenderUtils.drawRoundedRect(context,
                    (float)fillX, (float)barY,
                    (float)(fillX + fillW), (float)(barY + barH), 1,
                    VapeTheme.ACCENT.getRGB());
        }

        // Thumbs
        double thumbMinX = barX + minPct * barWidth - 3;
        double thumbMaxX = barX + maxPct * barWidth - 3;
        double thumbY    = barY - 3;
        Color minCol = draggingMin ? VapeTheme.TEXT   : VapeTheme.ACCENT;
        Color maxCol = draggingMax ? VapeTheme.TEXT   : VapeTheme.ACCENT;
        RenderUtils.drawRoundedRect(context,
                (float)thumbMinX, (float)thumbY,
                (float)(thumbMinX + 6), (float)(thumbY + 8), 2, minCol.getRGB());
        RenderUtils.drawRoundedRect(context,
                (float)thumbMaxX, (float)thumbY,
                (float)(thumbMaxX + 6), (float)(thumbY + 8), 2, maxCol.getRGB());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0) {
            double barX     = x + 8;
            double barWidth = width - 16;
            double minPct   = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double maxPct   = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double tmx      = barX + minPct * barWidth;
            double tmax     = barX + maxPct * barWidth;
            if (Math.abs(mouseX - tmx) <= Math.abs(mouseX - tmax)) draggingMin = true;
            else draggingMax = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) { draggingMin = false; draggingMax = false; }
    }
}
