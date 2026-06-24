package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.module.setting.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class SettingComponent {

    public abstract int getHeight();

    public abstract void render(GuiGraphicsExtractor ctx, int x, int y, int width, int mouseX, int mouseY, float delta);

    protected void queueTooltipIfHovered(Setting<?> setting, int x, int y, int width, int height, int mouseX, int mouseY) {
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            CharSequence desc = setting.getDescription();
            if (desc != null && !desc.toString().isBlank()) {
                dev.i726.rocky.gui.ClickGuiScreen.queueTooltip(desc.toString(), mouseX, mouseY);
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int x, int y, int width) {
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button, int x, int y, int width) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        return false;
    }

    public boolean charTyped(char chr, int mods) {
        return false;
    }

    protected boolean inBounds(double mx, double my, int x, int y, int width, int height) {
        return mx >= x && mx < x + width && my >= y && my < y + height;
    }
}
