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

        // Background
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(8, 8, 8, 210).getRGB());
        if (hovered) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 10).getRGB());

        // Left indent bar
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(50, 50, 55, 180).getRGB());

        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 5).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();
        String label    = setting.getName().toString();
        String modeName = setting.getMode().name();

        // Truncate mode name if too long
        int maxModeW = (int)(width / 2 - 12);
        if (mc.textRenderer.getWidth(modeName) > maxModeW)
            modeName = mc.textRenderer.trimToWidth(modeName, maxModeW - 4) + "..";

        // Setting label
        context.drawText(mc.textRenderer, label,
                (int)(x + 10), (int)(y + (height - 8) / 2.0),
                VapeTheme.TEXT_DIM.getRGB(), false);

        // Mode pill — "< mode >" style with arrows
        String display = "< " + modeName + " >";
        int pillW = mc.textRenderer.getWidth(display) + 10;
        int pillX = (int)(x + width - pillW - 5);
        int pillY = (int)(y + (height - 12) / 2.0);

        RenderUtils.renderRoundedQuad(context, new Color(34, 211, 238, 22),
                pillX, pillY, pillX + pillW, pillY + 12, 3, 8);
        RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 70),
                pillX, pillY, pillX + pillW, pillY + 12, 3, 3, 3, 3, 0.5, 8);
        context.drawText(mc.textRenderer, display, pillX + 5, pillY + 2,
                VapeTheme.ACCENT.getRGB(), false);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY)) {
            if      (button == 0) setting.cycle();
            else if (button == 1) setting.cycleBack();
        }
    }
}
