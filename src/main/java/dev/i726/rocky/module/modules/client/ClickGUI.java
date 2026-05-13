package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class ClickGUI extends Module {

    public final ModeSetting<GuiTheme.ThemeColor> color = new ModeSetting<>(
            EncryptedString.of("Color"), GuiTheme.ThemeColor.PURPLE, GuiTheme.ThemeColor.class
    );

    public ClickGUI() {
        super(
                EncryptedString.of("ClickGUI"),
                EncryptedString.of("Opens the in-game module menu"),
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                CategoryManager.GUI
        );
        addSetting(color);
    }

    @Override
    public void onEnable() {
        GuiTheme.setTheme(color.getMode());
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            mc.setScreen(new ClickGuiScreen());
        }
        setEnabledStatus(false);
    }
}
