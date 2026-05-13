package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class MinMaxSettingComponent extends SettingComponent<MinMaxSetting> {
    private boolean draggingMin, draggingMax;

    private static final int INDENT = 10;

    public MinMaxSettingComponent(MinMaxSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double barX     = x + INDENT + 4;
        double barWidth = width - INDENT - 12;

        if (draggingMin) {
            double pct = Math.max(0.0, Math.min(1.0, (mouseX - barX) / barWidth));
            double val = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
            if (val < setting.getMaxValue()) setting.setMinValue(val);
        } else if (draggingMax) {
            double pct = Math.max(0.0, Math.min(1.0, (mouseX - barX) / barWidth));
            double val = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
            if (val > setting.getMinValue()) setting.setMaxValue(val);
        }

        boolean hovered = isHovered(mouseX, mouseY);

        // Background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(8, 8, 8, 210).getRGB());
        if (hovered || draggingMin || draggingMax) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 10).getRGB());

        // Indent bar
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(50, 50, 55, 180).getRGB());

        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 5).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Name
        context.drawText(mc.textRenderer, setting.getName().toString(),
                (int)(x + INDENT + 2), (int)(y + 4),
                VapeTheme.TEXT_DIM.getRGB(), false);

        // Range pill
        String range = (int)setting.getMinValue() + " – " + (int)setting.getMaxValue();
        int rw = mc.textRenderer.getWidth(range) + 8;
        int rx = (int)(x + width - rw - 4);
        int ry = (int)(y + 3);
        RenderUtils.renderRoundedQuad(context, new Color(34, 211, 238, 22),
                rx, ry, rx + rw, ry + 11, 2, 8);
        RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 55),
                rx, ry, rx + rw, ry + 11, 2, 2, 2, 2, 0.5, 8);
        context.drawText(mc.textRenderer, range, rx + 4, ry + 2,
                VapeTheme.ACCENT.getRGB(), false);

        // Track
        double barY = y + height - 10;
        double barH = 3;
        RenderUtils.drawRoundedRect(context,
                (float)barX, (float)barY, (float)(barX + barWidth), (float)(barY + barH),
                1, new Color(40, 40, 45, 255).getRGB());

        // Fill between thumbs
        double minPct = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double maxPct = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double fillX  = barX + minPct * barWidth;
        double fillW  = (maxPct - minPct) * barWidth;
        if (fillW > 0) {
            context.fillGradient(
                    (int)fillX, (int)barY, (int)(fillX + fillW), (int)(barY + barH),
                    new Color(34, 211, 238, 160).getRGB(), VapeTheme.ACCENT.getRGB());
        }

        // Thumbs
        double tMinX = barX + minPct * barWidth - 3;
        double tMaxX = barX + maxPct * barWidth - 3;
        double tY    = barY - 3;
        RenderUtils.renderRoundedQuad(context, draggingMin ? VapeTheme.TEXT : VapeTheme.ACCENT,
                tMinX, tY, tMinX + 7, tY + 9, 2, 8);
        RenderUtils.renderRoundedQuad(context, draggingMax ? VapeTheme.TEXT : VapeTheme.ACCENT,
                tMaxX, tY, tMaxX + 7, tY + 9, 2, 8);
        if (draggingMin) RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 80),
                tMinX - 1, tY - 1, tMinX + 8, tY + 10, 2, 2, 2, 2, 0.5, 8);
        if (draggingMax) RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 80),
                tMaxX - 1, tY - 1, tMaxX + 8, tY + 10, 2, 2, 2, 2, 0.5, 8);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0) {
            double barX   = x + INDENT + 4;
            double barWidth = width - INDENT - 12;
            double minPct = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double maxPct = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double tmx    = barX + minPct * barWidth;
            double tmax   = barX + maxPct * barWidth;
            if (Math.abs(mouseX - tmx) <= Math.abs(mouseX - tmax)) draggingMin = true;
            else draggingMax = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) { draggingMin = false; draggingMax = false; }
    }
}
