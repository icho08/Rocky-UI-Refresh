package dev.i726.rocky.gui.components.settings;

import net.minecraft.client.gui.DrawContext;

public abstract class SettingComponent {

    public abstract int getHeight();

    public abstract void render(DrawContext ctx, int x, int y, int width, int mouseX, int mouseY, float delta);

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
