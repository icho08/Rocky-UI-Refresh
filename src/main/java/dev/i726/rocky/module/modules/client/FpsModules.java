package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class FpsModules extends Module {

    public FpsModules() {
        super(EncryptedString.of("FPS Modules"),
                EncryptedString.of("Shows the FPS category — performance tweaks"),
                -1, CategoryManager.GUI);
    }

    @Override
    public void onEnable() {
        ClickGuiScreen.addFpsPanel();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        ClickGuiScreen.removeFpsPanel();
        super.onDisable();
    }
}
