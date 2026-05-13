package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.gui.vape.components.Component;
import dev.i726.rocky.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class StringSettingComponent extends Component {
    private final StringSetting setting;
    private boolean typing;

    public StringSettingComponent(StringSetting setting, double x, double y, double width, double height) {
        super(x, y, width, height);
        this.setting = setting;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill((int) x, (int) y, (int) (x + width), (int) (y + height), VapeTheme.SETTING_BG.getRGB());

        String text = setting.getName() + ": " + setting.getValue() + (typing ? "_" : "");
        context.drawText(MinecraftClient.getInstance().textRenderer, text, (int) (x + 8), (int) (y + (height - 8) / 2), typing ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT.getRGB(), false);
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int) mouseX, (int) mouseY) && button == 0) {
            typing = !typing;
        } else {
            typing = false;
        }
    }

    public boolean onKey(int key) {
        if (typing) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                String val = setting.getValue();
                if (!val.isEmpty()) setting.setValue(val.substring(0, val.length() - 1));
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_ESCAPE) {
                typing = false;
            } else {
                String name = GLFW.glfwGetKeyName(key, 0);
                if (name != null) {
                    boolean shift = GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                                   GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (!shift) name = name.toLowerCase();
                    setting.setValue(setting.getValue() + name);
                }
            }
            return true;
        }
        return false;
    }
}
