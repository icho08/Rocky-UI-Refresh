package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.setting.ModeSetting;
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
                new Color(9, 9, 9, 225).getRGB());
        if (hovered)
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 7).getRGB());

        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                new Color(45, 45, 50, 200).getRGB());
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 7).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        String label = setting.getName().toString();
        context.drawText(mc.textRenderer, label,
                (int)(x + 8), (int)(y + (height - 8) / 2.0),
                VapeTheme.TEXT_MUTED.getRGB(), false);

        // Mode value — right side in flat pill
        String mode = "< " + setting.getMode().name() + " >";
        int mw = mc.textRenderer.getWidth(mode) + 8;
        int mx2 = (int)(x + width - mw - 4);
        int my2 = (int)(y + (height - 12) / 2.0);

        // Flat border box
        context.fill(mx2 - 1, my2 - 1, mx2 + mw + 1, my2 + 13,
                new Color(34, 211, 238, 55).getRGB());
        context.fill(mx2, my2, mx2 + mw, my2 + 12,
                new Color(12, 12, 12, 230).getRGB());
        context.drawText(mc.textRenderer, mode, mx2 + 4, my2 + 2,
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
