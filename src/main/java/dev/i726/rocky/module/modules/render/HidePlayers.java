package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class HidePlayers extends Module {

    public HidePlayers() {
        super(
                EncryptedString.of("Hide Players"),
                EncryptedString.of("Stops rendering all other players on your screen"),
                -1,
                CategoryManager.ESP
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
    }

    @Override
    public void onDisable() {
        super.onDisable();
    }
}
