package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class AntiFog extends Module {

    public AntiFog() {
        super(EncryptedString.of("Anti Fog"),
                EncryptedString.of("Removes terrain fog for a cleaner view and slight FPS gain"),
                -1, CategoryManager.ESP);
    }

    @Override
    public void onEnable() { super.onEnable(); }

    @Override
    public void onDisable() { super.onDisable(); }
}
