package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class CheckboxSetting extends SettingComponent<BooleanSetting> {

    private float switchAnim;

    public CheckboxSetting(BooleanSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
        switchAnim = setting.getValue() ? 1f : 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float target = setting.getValue() ? 1f : 0f;
        float diff   = target - switchAnim;
        switchAnim  += Math.signum(diff) * Math.min(Math.abs(diff), (float)(RenderUtils.deltaTime() * 14f));

        boolean hovered = isHovered(mouseX, mouseY);

        // Background — slightly darker than module rows to show hierarchy
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(8, 8, 8, 210).getRGB());
        if (hovered) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 10).getRGB());

        // Left hierarchy indent bar (thin, muted)
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(50, 50, 55, 180).getRGB());

        // Bottom separator
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 5).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();
        double swW = 24;
        double swH = 12;
        double swX = x + width - swW - 6;
        double swY = y + (height - swH) / 2.0;

        String text = setting.getName().toString();
        int maxW = (int)(swX - x - 14);
        if (mc.textRenderer.getWidth(text) > maxW)
            text = mc.textRenderer.trimToWidth(text, maxW - 4) + "..";

        context.drawText(mc.textRenderer, text,
                (int)(x + 10), (int)(y + (height - 8) / 2.0),
                VapeTheme.TEXT_DIM.getRGB(), false);

        RenderUtils.renderSwitch(context, setting.getValue(), switchAnim, swX, swY, swW, swH, VapeTheme.ACCENT);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0)
            setting.setValue(!setting.getValue());
    }
}
