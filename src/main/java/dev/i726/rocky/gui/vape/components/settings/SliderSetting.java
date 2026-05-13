package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.NumberSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class SliderSetting extends SettingComponent<NumberSetting> {
    private boolean dragging;

    private static final int PAD_L = 8;  // left padding (indent)

    public SliderSetting(NumberSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        double barX = x + PAD_L;
        double barW = width - PAD_L - 8;

        if (dragging) {
            double pct = Math.max(0, Math.min(1, (mouseX - barX) / barW));
            setting.setValue(setting.getMin() + (setting.getMax() - setting.getMin()) * pct);
        }

        boolean hovered = isHovered(mouseX, mouseY);

        // ── Background ────────────────────────────────────────────────────
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(9, 9, 9, 225).getRGB());
        if (hovered || dragging)
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 7).getRGB());

        // Left indent accent
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(45, 45, 50, 200).getRGB());

        // Bottom separator
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 7).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // ── Label ─────────────────────────────────────────────────────────
        String label = setting.getName().toString();
        context.drawText(mc.textRenderer, label,
                (int)(x + PAD_L), (int)(y + 3),
                VapeTheme.TEXT_MUTED.getRGB(), false);

        // ── Value — right-aligned ─────────────────────────────────────────
        String val = formatValue(setting.getValue(), setting.getIncrement());
        int valW = mc.textRenderer.getWidth(val);
        context.drawText(mc.textRenderer, val,
                (int)(x + width - valW - 6), (int)(y + 3),
                VapeTheme.ACCENT.getRGB(), false);

        // ── Track ─────────────────────────────────────────────────────────
        int trackY = (int)(y + height - 8);
        int trackH = 4;

        // Track background
        context.fill((int)barX, trackY, (int)(barX + barW), trackY + trackH,
                new Color(35, 35, 40, 255).getRGB());

        // Filled portion
        double pct   = (setting.getValue() - setting.getMin()) / (setting.getMax() - setting.getMin());
        double fillW = pct * barW;
        if (fillW > 0) {
            context.fillGradient((int)barX, trackY, (int)(barX + fillW), trackY + trackH,
                    new Color(34, 211, 238, 140).getRGB(),
                    VapeTheme.ACCENT.getRGB());
        }

        // ── Thumb ─────────────────────────────────────────────────────────
        int thumbW = 6, thumbH = 10;
        int thumbX = (int)(barX + fillW - thumbW / 2.0);
        int thumbY = trackY - (thumbH - trackH) / 2;
        context.fill(thumbX, thumbY, thumbX + thumbW, thumbY + thumbH,
                dragging ? Color.WHITE.getRGB() : VapeTheme.ACCENT.getRGB());
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
