package dev.i726.rocky.module.modules.client;

import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class BlatantModules extends Module {

    public BlatantModules() {
        super(EncryptedString.of("Blatant Modules"),
                EncryptedString.of("Shows the Blatant category — modules that only work on servers without anti-cheat"),
                -1, CategoryManager.GUI);
    }

    @Override
    public void onEnable() {
        ClickGuiScreen.refreshPanels();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        ClickGuiScreen.refreshPanels();
        super.onDisable();
    }
}
