package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.MinMaxSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class MinMaxSettingComponent extends SettingComponent<MinMaxSetting> {
    private boolean draggingMin, draggingMax;

    private static final int PAD_L = 8;

    public MinMaxSettingComponent(MinMaxSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double barX = x + PAD_L;
        double barW = width - PAD_L - 8;

        if (draggingMin) {
            double pct = Math.max(0, Math.min(1, (mouseX - barX) / barW));
            double val = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
            if (val < setting.getMaxValue()) setting.setMinValue(val);
        } else if (draggingMax) {
            double pct = Math.max(0, Math.min(1, (mouseX - barX) / barW));
            double val = setting.getMin() + (setting.getMax() - setting.getMin()) * pct;
            if (val > setting.getMinValue()) setting.setMaxValue(val);
        }

        boolean hovered = isHovered(mouseX, mouseY);

        // Background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(9, 9, 9, 225).getRGB());
        if (hovered || draggingMin || draggingMax)
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 7).getRGB());
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(45, 45, 50, 200).getRGB());
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 7).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        context.drawText(mc.textRenderer, setting.getName().toString(),
                (int)(x + PAD_L), (int)(y + 3),
                VapeTheme.TEXT_MUTED.getRGB(), false);

        String range = (int)setting.getMinValue() + " - " + (int)setting.getMaxValue();
        int rw = mc.textRenderer.getWidth(range);
        context.drawText(mc.textRenderer, range,
                (int)(x + width - rw - 6), (int)(y + 3),
                VapeTheme.ACCENT.getRGB(), false);

        // Track
        int trackY = (int)(y + height - 8);
        int trackH = 4;
        context.fill((int)barX, trackY, (int)(barX + barW), trackY + trackH,
                new Color(35, 35, 40, 255).getRGB());

        double minPct = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double maxPct = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double fillX  = barX + minPct * barW;
        double fillW  = (maxPct - minPct) * barW;
        if (fillW > 0) {
            context.fillGradient((int)fillX, trackY, (int)(fillX + fillW), trackY + trackH,
                    new Color(34, 211, 238, 140).getRGB(), VapeTheme.ACCENT.getRGB());
        }

        // Thumbs — flat rectangles
        int thumbW = 6, thumbH = 10;
        int tmx = (int)(barX + minPct * barW - thumbW / 2.0);
        int tmax = (int)(barX + maxPct * barW - thumbW / 2.0);
        int thumbY = trackY - (thumbH - trackH) / 2;
        context.fill(tmx,  thumbY, tmx  + thumbW, thumbY + thumbH, draggingMin ? Color.WHITE.getRGB() : VapeTheme.ACCENT.getRGB());
        context.fill(tmax, thumbY, tmax + thumbW, thumbY + thumbH, draggingMax ? Color.WHITE.getRGB() : VapeTheme.ACCENT.getRGB());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0) {
            double barX = x + PAD_L;
            double barW = width - PAD_L - 8;
            double minPct = (setting.getMinValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double maxPct = (setting.getMaxValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
            double tmx  = barX + minPct * barW;
            double tmax = barX + maxPct * barW;
            if (Math.abs(mouseX - tmx) <= Math.abs(mouseX - tmax)) draggingMin = true;
            else draggingMax = true;
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) { draggingMin = false; draggingMax = false; }
    }
}
