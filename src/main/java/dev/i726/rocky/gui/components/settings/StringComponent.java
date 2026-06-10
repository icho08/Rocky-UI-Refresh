package dev.i726.rocky.gui.components.settings;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class StringComponent extends SettingComponent {

    private final StringSetting setting;
    private boolean focused = false;

    public StringComponent(StringSetting setting) {
        this.setting = setting;
    }

    @Override
    public int getHeight() {
        return 28;
    }

    @Override
    public void render(DrawContext ctx, int x, int y, int width, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Color ac = GuiTheme.accent();

        ctx.fill(x, y, x + width, y + 28, GuiTheme.settingBg());

        ctx.drawText(mc.textRenderer, setting.getName().toString(),
                x + 8, y + 3, GuiTheme.textSecondary(), false);

        int fieldX = x + 6;
        int fieldY = y + 14;
        int fieldW = width - 12;
        int fieldH = 11;

        int borderAlpha = focused ? 180 : 60;
        ctx.fill(fieldX - 1, fieldY - 1, fieldX + fieldW + 1, fieldY + fieldH + 1,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), borderAlpha));
        ctx.fill(fieldX, fieldY, fieldX + fieldW, fieldY + fieldH,
                GuiTheme.rgba(8, 7, 14, 200));

        if (focused) {
            ctx.fill(fieldX, fieldY, fieldX + 2, fieldY + fieldH,
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 220));
        }

        String val = setting.getValue();
        int textX = fieldX + 5;
        int textY = fieldY + 2;

        if (val.isEmpty() && !focused) {
            ctx.drawText(mc.textRenderer, "Click to edit...", textX, textY,
                    GuiTheme.textSecondary(), false);
        } else {
            int maxTextW = fieldW - 10;
            String displayed = val;
            int tw = mc.textRenderer.getWidth(displayed);
            if (tw > maxTextW) {
                while (mc.textRenderer.getWidth(displayed) > maxTextW && !displayed.isEmpty())
                    displayed = displayed.substring(1);
            }
            ctx.enableScissor(textX, fieldY, fieldX + fieldW - 2, fieldY + fieldH);
            ctx.drawText(mc.textRenderer, displayed, textX, textY, GuiTheme.textPrimary(), false);
            ctx.disableScissor();

            if (focused && (System.currentTimeMillis() / 530) % 2 == 0) {
                int cursorX = textX + mc.textRenderer.getWidth(displayed);
                ctx.fill(cursorX, fieldY + 2, cursorX + 1, fieldY + fieldH - 2,
                        GuiTheme.textPrimary());
            }
        }

        queueTooltipIfHovered(setting, x, y, width, 28, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button, int x, int y, int width) {
        boolean inField = inBounds(mx, my, x + 5, y + 13, width - 10, 12);
        if (button == 0) {
            focused = inField;
            return inField;
        }
        if (button == 1 && inField) {
            setting.setValue("");
            focused = true;
            return true;
        }
        focused = false;
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!focused) return false;

        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            focused = false;
            return true;
        }

        if (key == GLFW.GLFW_KEY_BACKSPACE) {
            String v = setting.getValue();
            if (!v.isEmpty()) {
                boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
                if (ctrl) {
                    setting.setValue("");
                } else {
                    setting.setValue(v.substring(0, v.length() - 1));
                }
            }
            return true;
        }

        return true;
    }

    @Override
    public boolean charTyped(char chr, int mods) {
        if (!focused) return false;
        if (chr >= 32 && chr != 127) {
            setting.setValue(setting.getValue() + chr);
        }
        return true;
    }

    public boolean isFocused() {
        return focused;
    }
}
