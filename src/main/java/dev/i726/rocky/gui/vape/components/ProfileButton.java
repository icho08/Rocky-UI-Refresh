package dev.i726.rocky.gui.vape.components;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.gui.vape.VapeTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.awt.Color;

public class ProfileButton extends Component {
    private final String name;
    private final Runnable onClick;

    public ProfileButton(String name, double x, double y, double width, double height, Runnable onClick) {
        super(x, y, width, height);
        this.name = name;
        this.onClick = onClick;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered(mouseX, mouseY);
        
        // Background
        int bgColor = hovered ? new Color(40, 40, 40, 180).getRGB() : new Color(25, 25, 25, 150).getRGB();
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), bgColor);

        // Text
        context.drawText(MinecraftClient.getInstance().textRenderer, name, (int) (x + 8), (int) (y + (height - 8) / 2), -1, false);

        // Subtle bottom line
        context.fill((int) x, (int) (y + height - 1), (int) (x + width), (int) (y + height), new Color(255, 255, 255, 10).getRGB());
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int) mouseX, (int) mouseY) && button == 0) {
            onClick.run();
        }
    }
}
