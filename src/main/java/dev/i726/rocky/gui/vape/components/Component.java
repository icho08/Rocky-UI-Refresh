package dev.i726.rocky.gui.vape.components;

import net.minecraft.client.gui.DrawContext;

public abstract class Component {
    public double x, y, width, height;

    public Component(double x, double y, double width, double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public abstract void render(DrawContext context, int mouseX, int mouseY, float delta);

    public void mouseClicked(double mouseX, double mouseY, int button) {}

    public void mouseReleased(double mouseX, double mouseY, int button) {}

    public void charTyped(char codePoint, int modifiers) {}

    public void keyPressed(int keyCode, int scanCode, int modifiers) {}

    public boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
