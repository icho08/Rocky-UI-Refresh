package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class SliderSetting extends SettingComponent<NumberSetting> {
    private boolean dragging;

    private static final int INDENT = 10;  // left indent for hierarchy

    public SliderSetting(NumberSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double barX     = x + INDENT + 4;
        double barWidth = width - INDENT - 12;

        if (dragging) {
            double pct = Math.max(0.0, Math.min(1.0, (mouseX - barX) / barWidth));
            setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * pct);
        }

        boolean hovered = isHovered(mouseX, mouseY);

        // Background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(8, 8, 8, 210).getRGB());
        if (hovered || dragging) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 10).getRGB());

        // Left indent bar
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(50, 50, 55, 180).getRGB());

        // Bottom separator
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 5).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Setting name (top-left, padded from left indent)
        String name = setting.getName().toString();
        context.drawText(mc.textRenderer, name,
                (int)(x + INDENT + 2), (int)(y + 4),
                VapeTheme.TEXT_DIM.getRGB(), false);

        // Value pill — top-right
        String valStr = formatValue(setting.getValue(), setting.getStep());
        int valW = mc.textRenderer.getWidth(valStr) + 8;
        int valX = (int)(x + width - valW - 4);
        int valY = (int)(y + 3);
        RenderUtils.renderRoundedQuad(context, new Color(34, 211, 238, 22),
                valX, valY, valX + valW, valY + 11, 2, 8);
        RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 55),
                valX, valY, valX + valW, valY + 11, 2, 2, 2, 2, 0.5, 8);
        context.drawText(mc.textRenderer, valStr, valX + 4, valY + 2,
                VapeTheme.ACCENT.getRGB(), false);

        // Track (bottom portion of row)
        double barY = y + height - 10;
        double barH = 3;

        // Track background
        RenderUtils.drawRoundedRect(context,
                (float)barX, (float)barY,
                (float)(barX + barWidth), (float)(barY + barH), 1,
                new Color(40, 40, 45, 255).getRGB());

        // Filled portion
        double pct   = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double fillW = pct * barWidth;
        if (fillW > 0) {
            // Gradient fill: dimmer cyan → bright cyan
            context.fillGradient(
                    (int)barX, (int)barY,
                    (int)(barX + fillW), (int)(barY + barH),
                    new Color(34, 211, 238, 160).getRGB(),
                    VapeTheme.ACCENT.getRGB());
        }

        // Thumb — bright when dragging
        double thumbX = barX + fillW - 3;
        double thumbY = barY - 3;
        RenderUtils.renderRoundedQuad(context,
                dragging ? VapeTheme.TEXT : VapeTheme.ACCENT,
                thumbX, thumbY, thumbX + 7, thumbY + 9, 2, 8);
        if (dragging) {
            // Glow ring on active thumb
            RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 80),
                    thumbX - 1, thumbY - 1, thumbX + 8, thumbY + 10,
                    2, 2, 2, 2, 0.5, 8);
        }
    }

    private String formatValue(double val, double step) {
        return step < 1.0 ? String.format("%.2f", val) : String.valueOf((int) val);
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
