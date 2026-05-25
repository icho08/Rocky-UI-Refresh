package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class Hitboxes extends Module {
    private final NumberSetting size = new NumberSetting(EncryptedString.of("Size"), 0.0, 0.5, 0.1, 0.01)
            .setDescription(EncryptedString.of("Hitbox expansion (0.1-0.3 recommended for legit play)"));

    public Hitboxes() {
        super(EncryptedString.of("Hitbox Expand"),
                EncryptedString.of("Expands player hitboxes"),
                -1,
                CategoryManager.BLATANT);
        addSetting(size);
    }

    public float getSize() {
        return isEnabled() ? (float) size.getValue() : 0.0f;
    }
}
