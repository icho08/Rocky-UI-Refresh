package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.gui.vape.components.Component;
import dev.i726.rocky.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

public class KeybindSetting extends Component {

    private final Module module;
    private boolean binding;
    public static boolean isAnyBinding = false;
    private static final Map<Integer, String> KEY_CACHE = new HashMap<>();

    public KeybindSetting(Module module, double x, double y, double width, double height) {
        super(x, y, width, height);
        this.module = module;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = isHovered(mouseX, mouseY);

        // Background
        if (binding) {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(20, 35, 38, 230).getRGB());
            // Top border cyan when binding
            context.fill((int)x, (int)y, (int)(x + width), (int)y + 1, VapeTheme.ACCENT.getRGB());
        } else {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(9, 9, 9, 225).getRGB());
            if (hovered)
                context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                        new Color(255, 255, 255, 7).getRGB());
        }

        // Left indent bar — cyan when binding, muted otherwise
        context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                binding ? VapeTheme.ACCENT.getRGB() : new Color(45, 45, 50, 200).getRGB());

        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 7).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Label
        context.drawText(mc.textRenderer, "Keybind",
                (int)(x + 8), (int)(y + (height - 8) / 2.0),
                binding ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT_MUTED.getRGB(), false);

        // Key value — flat box on the right
        String keyStr = binding ? "PRESS KEY" : getSafeKeyName(module.getKey());
        int kw = mc.textRenderer.getWidth(keyStr) + 8;
        int kx = (int)(x + width - kw - 4);
        int ky = (int)(y + (height - 12) / 2.0);

        // Border
        context.fill(kx - 1, ky - 1, kx + kw + 1, ky + 13,
                binding ? VapeTheme.ACCENT.getRGB() : new Color(50, 50, 55, 220).getRGB());
        // Fill
        context.fill(kx, ky, kx + kw, ky + 12,
                binding ? new Color(20, 40, 44, 230).getRGB() : new Color(16, 16, 18, 240).getRGB());
        context.drawText(mc.textRenderer, keyStr, kx + 4, ky + 2,
                binding ? VapeTheme.ACCENT.getRGB()
                        : (module.getKey() > 0 ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT_MUTED.getRGB()),
                false);
    }

    private String getSafeKeyName(int key) {
        if (key <= 0) return "NONE";
        if (KEY_CACHE.containsKey(key)) return KEY_CACHE.get(key);
        String name;
        switch (key) {
            case GLFW.GLFW_KEY_UP:            name = "UP";     break;
            case GLFW.GLFW_KEY_DOWN:          name = "DOWN";   break;
            case GLFW.GLFW_KEY_LEFT:          name = "LEFT";   break;
            case GLFW.GLFW_KEY_RIGHT:         name = "RIGHT";  break;
            case GLFW.GLFW_KEY_LEFT_SHIFT:    name = "LSHIFT"; break;
            case GLFW.GLFW_KEY_RIGHT_SHIFT:   name = "RSHIFT"; break;
            case GLFW.GLFW_KEY_LEFT_CONTROL:  name = "LCTRL";  break;
            case GLFW.GLFW_KEY_RIGHT_CONTROL: name = "RCTRL";  break;
            case GLFW.GLFW_KEY_TAB:           name = "TAB";    break;
            case GLFW.GLFW_KEY_ENTER:         name = "ENTER";  break;
            case GLFW.GLFW_KEY_SPACE:         name = "SPACE";  break;
            case GLFW.GLFW_KEY_DELETE:        name = "DEL";    break;
            default:
                if (key >= 32 && key <= 96) {
                    try { String n = GLFW.glfwGetKeyName(key, 0); name = n == null ? "K" + key : n.toUpperCase(); }
                    catch (Exception e) { name = "K" + key; }
                } else { name = "K" + key; }
        }
        KEY_CACHE.put(key, name);
        return name;
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY) && button == 0) {
            binding = !binding;
            isAnyBinding = binding;
        }
    }

    public boolean onKey(int key) {
        if (!binding) return false;
        if      (key == GLFW.GLFW_KEY_ESCAPE)                         binding = false;
        else if (key == GLFW.GLFW_KEY_DELETE || key == GLFW.GLFW_KEY_BACKSPACE) { module.setKey(0); binding = false; }
        else                                                          { module.setKey(key); binding = false; }
        isAnyBinding = false;
        KEY_CACHE.clear();
        return true;
    }
}
