package dev.i726.rocky.gui.vape.components.settings;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.gui.vape.components.Component;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.RenderUtils;
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

        // Background — pulse when binding
        if (binding) {
            context.fillGradient((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(34, 211, 238, 30).getRGB(),
                    new Color(8, 8, 8, 210).getRGB());
        } else {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(8, 8, 8, 210).getRGB());
            if (hovered) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 10).getRGB());
        }

        // Left accent or indent bar
        if (binding) {
            context.fill((int)x, (int)y, (int)x + 3, (int)(y + height), VapeTheme.ACCENT.getRGB());
            context.fill((int)x + 3, (int)y, (int)x + 7, (int)(y + height),
                    new Color(34, 211, 238, 40).getRGB());
        } else {
            context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                    new Color(50, 50, 55, 180).getRGB());
        }

        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 5).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // Label
        context.drawText(mc.textRenderer, "Keybind",
                (int)(x + 10), (int)(y + (height - 8) / 2.0),
                binding ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT_DIM.getRGB(), false);

        // Key pill
        String keyStr = binding ? "PRESS KEY..." : getSafeKeyName(module.getKey());
        int pillW = mc.textRenderer.getWidth(keyStr) + 10;
        int pillX = (int)(x + width - pillW - 5);
        int pillY = (int)(y + (height - 12) / 2.0);

        if (binding) {
            RenderUtils.renderRoundedQuad(context, new Color(34, 211, 238, 40),
                    pillX, pillY, pillX + pillW, pillY + 12, 3, 8);
            RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 140),
                    pillX, pillY, pillX + pillW, pillY + 12, 3, 3, 3, 3, 0.5, 8);
            context.drawText(mc.textRenderer, keyStr, pillX + 5, pillY + 2,
                    VapeTheme.ACCENT.getRGB(), false);
        } else {
            boolean hasKey = module.getKey() > 0;
            RenderUtils.renderRoundedQuad(context, new Color(28, 28, 32, 220),
                    pillX, pillY, pillX + pillW, pillY + 12, 3, 8);
            RenderUtils.renderRoundedOutline(context,
                    hasKey ? new Color(34, 211, 238, 60) : new Color(255, 255, 255, 15),
                    pillX, pillY, pillX + pillW, pillY + 12, 3, 3, 3, 3, 0.5, 8);
            context.drawText(mc.textRenderer, keyStr, pillX + 5, pillY + 2,
                    hasKey ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT_MUTED.getRGB(), false);
        }
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
            case GLFW.GLFW_KEY_LEFT_ALT:      name = "LALT";   break;
            case GLFW.GLFW_KEY_RIGHT_ALT:     name = "RALT";   break;
            case GLFW.GLFW_KEY_TAB:           name = "TAB";    break;
            case GLFW.GLFW_KEY_ENTER:         name = "ENTER";  break;
            case GLFW.GLFW_KEY_SPACE:         name = "SPACE";  break;
            case GLFW.GLFW_KEY_DELETE:        name = "DEL";    break;
            case GLFW.GLFW_KEY_INSERT:        name = "INS";    break;
            case GLFW.GLFW_KEY_HOME:          name = "HOME";   break;
            case GLFW.GLFW_KEY_END:           name = "END";    break;
            default:
                if (key >= 32 && key <= 96) {
                    try { String n = GLFW.glfwGetKeyName(key, 0); name = n == null ? "K_"+key : n.toUpperCase(); }
                    catch (Exception e) { name = "K_"+key; }
                } else { name = "K_"+key; }
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
        if      (key == GLFW.GLFW_KEY_ESCAPE)    binding = false;
        else if (key == GLFW.GLFW_KEY_DELETE
              || key == GLFW.GLFW_KEY_BACKSPACE) { module.setKey(0); binding = false; }
        else                                      { module.setKey(key); binding = false; }
        isAnyBinding = false;
        KEY_CACHE.clear();
        return true;
    }
}
