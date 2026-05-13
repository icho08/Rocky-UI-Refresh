package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.KeybindSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

public class KeybindComponent extends SettingComponent {

    private final KeybindSetting setting;

    public KeybindComponent(KeybindSetting setting) {
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return 14;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int mouseX, int mouseY, float delta) {
        int bg = setting.isListening() ? GuiTheme.accentFaint() : GuiTheme.settingBg();
        ctx.fill(x, y, x + width, y + 14, bg);

        ctx.drawText(MinecraftClient.getInstance().textRenderer,
                setting.getName().toString(), x + 8, y + 3, GuiTheme.textSecondary(), false);

        String keyLabel = setting.isListening() ? "..." : keyName(setting.getKey());
        int kw = MinecraftClient.getInstance().textRenderer.getWidth(keyLabel);
        int color = setting.isListening() ? GuiTheme.textPrimary() : GuiTheme.textAccent();
        ctx.drawText(MinecraftClient.getInstance().textRenderer, keyLabel, x + width - kw - 6, y + 3, color, false);
    }

    private String keyName(int key) {
        if (key == -1) return "NONE";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isEmpty()) return name.toUpperCase();
        return switch (key) {
            case GLFW.GLFW_KEY_LEFT_SHIFT  -> "LSHIFT";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RSHIFT";
            case GLFW.GLFW_KEY_LEFT_CONTROL  -> "LCTRL";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCTRL";
            case GLFW.GLFW_KEY_LEFT_ALT  -> "LALT";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RALT";
            case GLFW.GLFW_KEY_SPACE     -> "SPACE";
            case GLFW.GLFW_KEY_ESCAPE    -> "ESC";
            case GLFW.GLFW_KEY_F1  -> "F1";
            case GLFW.GLFW_KEY_F2  -> "F2";
            case GLFW.GLFW_KEY_F3  -> "F3";
            case GLFW.GLFW_KEY_F4  -> "F4";
            case GLFW.GLFW_KEY_F5  -> "F5";
            case GLFW.GLFW_KEY_F6  -> "F6";
            case GLFW.GLFW_KEY_F7  -> "F7";
            case GLFW.GLFW_KEY_F8  -> "F8";
            case GLFW.GLFW_KEY_F9  -> "F9";
            case GLFW.GLFW_KEY_F10 -> "F10";
            case GLFW.GLFW_KEY_F11 -> "F11";
            case GLFW.GLFW_KEY_F12 -> "F12";
            default -> "KEY" + key;
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        if (button == 0 && inBounds(mx, my, x, y, width, 14)) {
            setting.toggleListening();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (setting.isListening()) {
            if (key == GLFW.GLFW_KEY_ESCAPE) setting.setKey(-1);
            else setting.setKey(key);
            setting.setListening(false);
            return true;
        }
        return false;
    }
}
