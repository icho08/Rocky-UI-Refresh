package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class CheckboxSetting extends SettingComponent<BooleanSetting> {

    private float switchAnim = 0f;

    public CheckboxSetting(BooleanSetting setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
        switchAnim = setting.getValue() ? 1f : 0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Animate
        float target = setting.getValue() ? 1f : 0f;
        float speed  = (float)(RenderUtils.deltaTime() * 12f);
        switchAnim   = switchAnim + Math.signum(target - switchAnim) * Math.min(Math.abs(target - switchAnim), speed);

        boolean hovered = isHovered(mouseX, mouseY);

        // Row background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.SETTING_BG.getRGB());
        if (hovered) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.HOVER_OVERLAY.getRGB());
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height), VapeTheme.SEPARATOR.getRGB());

        // Setting name
        MinecraftClient mc  = MinecraftClient.getInstance();
        double switchW      = 20;
        double switchH      = 10;
        double switchX      = x + width - switchW - 6;
        int maxTextW        = (int)(switchX - x - 10);
        String text         = setting.getName().toString();
        if (mc.textRenderer.getWidth(text) > maxTextW) {
            text = mc.textRenderer.trimToWidth(text, maxTextW - 6) + "..";
        }
        context.drawText(mc.textRenderer, text,
                (int)(x + 8), (int)(y + (height - 8) / 2),
                VapeTheme.TEXT_DIM.getRGB(), false);

        // Toggle switch
        double switchY = y + (height - switchH) / 2;
        RenderUtils.renderSwitch(context, setting.getValue(), switchAnim, switchX, switchY, switchW, switchH, VapeTheme.ACCENT);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0) {
            setting.setValue(!setting.getValue());
        }
    }
}
