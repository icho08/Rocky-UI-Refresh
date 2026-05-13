package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.BooleanSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class CheckboxSetting extends SettingComponent<BooleanSetting> {

    public CheckboxSetting(BooleanSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean on      = setting.getValue();
        boolean hovered = isHovered(mouseX, mouseY);

        // Background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(9, 9, 9, 225).getRGB());
        if (hovered)
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 7).getRGB());

        // Left indent bar
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(45, 45, 50, 200).getRGB());

        // Bottom separator
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 7).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Label
        String label = setting.getName().toString();
        int maxW = (int)(width - 8 - 18);
        if (mc.textRenderer.getWidth(label) > maxW)
            label = mc.textRenderer.trimToWidth(label, maxW - 3) + "..";
        context.drawText(mc.textRenderer, label,
                (int)(x + 8), (int)(y + (height - 8) / 2.0),
                VapeTheme.TEXT_MUTED.getRGB(), false);

        // ── Flat checkbox ─────────────────────────────────────────────────
        int boxSize = 9;
        int boxX    = (int)(x + width - boxSize - 5);
        int boxY    = (int)(y + (height - boxSize) / 2.0);

        // Box border
        context.fill(boxX - 1, boxY - 1, boxX + boxSize + 1, boxY + boxSize + 1,
                on ? new Color(34, 211, 238, 160).getRGB() : new Color(60, 60, 65, 220).getRGB());
        // Box fill
        context.fill(boxX, boxY, boxX + boxSize, boxY + boxSize,
                on ? new Color(34, 211, 238, 60).getRGB() : new Color(20, 20, 22, 240).getRGB());
        // Inner tick/fill when on
        if (on) {
            context.fill(boxX + 2, boxY + 2, boxX + boxSize - 2, boxY + boxSize - 2,
                    VapeTheme.ACCENT.getRGB());
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0)
            setting.setValue(!setting.getValue());
    }
}
