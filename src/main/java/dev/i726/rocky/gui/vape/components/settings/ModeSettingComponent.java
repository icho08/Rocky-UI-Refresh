package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;

public class ModeSettingComponent extends SettingComponent<ModeSetting<?>> {

    public ModeSettingComponent(ModeSetting<?> setting, double x, double y, double width, double height) {
        super(setting, x, y, width, height);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered(mouseX, mouseY);

        // Row background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.SETTING_BG.getRGB());
        if (hovered) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), VapeTheme.HOVER_OVERLAY.getRGB());
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height), VapeTheme.SEPARATOR.getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();
        String label    = setting.getName().toString();
        String modeName = setting.getMode().name();

        // Truncate if needed
        int maxModeW = (int)(width / 2 - 10);
        if (mc.textRenderer.getWidth(modeName) > maxModeW) {
            modeName = mc.textRenderer.trimToWidth(modeName, maxModeW - 6) + "..";
        }

        // Setting label
        context.drawText(mc.textRenderer, label,
                (int)(x + 8), (int)(y + (height - 8) / 2),
                VapeTheme.TEXT_DIM.getRGB(), false);

        // Mode pill — right side with arrow indicators
        String display  = "< " + modeName + " >";
        int pillW       = mc.textRenderer.getWidth(display) + 8;
        int pillX       = (int)(x + width - pillW - 4);
        int pillY       = (int)(y + (height - 10) / 2);
        RenderUtils.drawRoundedRect(context, pillX, pillY, pillX + pillW, pillY + 10, 2,
                new Color(34, 211, 238, 20).getRGB());
        RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 50),
                pillX, pillY, pillX + pillW, pillY + 10, 2, 2, 2, 2, 0.5, 8);
        context.drawText(mc.textRenderer, display, pillX + 4, pillY + 1,
                VapeTheme.ACCENT.getRGB(), false);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY)) {
            if (button == 0) setting.cycle();
            else if (button == 1) setting.cycleBack();
        }
    }
}
