package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;

public final class GodBridge extends Module {
    public static GodBridge INSTANCE;

    public GodBridge() {
        super(EncryptedString.of("God Bridge"),
                EncryptedString.of("Automated god bridging"), -1, CategoryManager.BRIDGING);
        INSTANCE = this;
    }

    public static boolean shouldSafeWalk() {
        return INSTANCE != null && INSTANCE.isEnabled();
    }
}
