package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;
import org.lwjgl.glfw.GLFW;

public final class Search extends Module {
    public Search() {
        super(EncryptedString.of("Search"),
                EncryptedString.of("Search for modules"),
                GLFW.GLFW_KEY_UNKNOWN,
                CategoryManager.GUI);
    }

    @Override
    public void onEnable() {
        // Will be implemented in GUI
        this.setEnabledStatus(false);
    }
}
