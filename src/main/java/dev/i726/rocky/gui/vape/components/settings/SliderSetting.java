package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class SliderSetting extends SettingComponent<NumberSetting> {
    private boolean dragging;

    public SliderSetting(NumberSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double barX     = x + 8;
        double barWidth = width - 16;

        if (dragging) {
            double pct = Math.max(0, Math.min(1, (mouseX - barX) / barWidth));
            setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * pct);
        }

        boolean hovered = isHovered(mouseX, mouseY);

        // Row background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.SETTING_BG.getRGB());
        if (hovered || dragging) {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.HOVER_OVERLAY.getRGB());
        }
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height), VapeTheme.SEPARATOR.getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Name
        String name = setting.getName().toString();
        context.drawText(mc.textRenderer, name,
                (int)(x + 8), (int)(y + 4),
                VapeTheme.TEXT_DIM.getRGB(), false);

        // Value pill — right aligned
        String valStr = formatValue(setting.getValue(), setting.getStep());
        int valW = mc.textRenderer.getWidth(valStr) + 6;
        int valX = (int)(x + width - valW - 4);
        int valY = (int)(y + 3);
        RenderUtils.drawRoundedRect(context, valX, valY, valX + valW, valY + 10, 2,
                new Color(34, 211, 238, 25).getRGB());
        context.drawText(mc.textRenderer, valStr, valX + 3, valY + 1,
                VapeTheme.ACCENT.getRGB(), false);

        // Track
        double barY     = y + height - 9;
        double barH     = 2;
        RenderUtils.drawRoundedRect(context,
                (float)barX, (float)barY,
                (float)(barX + barWidth), (float)(barY + barH), 1,
                new Color(38, 38, 42, 255).getRGB());

        // Fill
        double pct = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double fillW = pct * barWidth;
        if (fillW > 0) {
            RenderUtils.drawRoundedRect(context,
                    (float)barX, (float)barY,
                    (float)(barX + fillW), (float)(barY + barH), 1,
                    VapeTheme.ACCENT.getRGB());
        }

        // Thumb
        double thumbX = barX + fillW - 3;
        double thumbY = barY - 3;
        RenderUtils.drawRoundedRect(context,
                (float)thumbX, (float)thumbY,
                (float)(thumbX + 6), (float)(thumbY + 8), 2,
                dragging ? VapeTheme.TEXT.getRGB() : VapeTheme.ACCENT.getRGB());
    }

    private String formatValue(double val, double step) {
        if (step < 1.0) {
            // Show up to 2 decimal places
            return String.format("%.2f", val);
        }
        return String.valueOf((int) val);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0) dragging = true;
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
    }
}
