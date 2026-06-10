package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class EntityCulling extends Module {

    private final BooleanSetting cullMobs = new BooleanSetting(
            EncryptedString.of("Cull Mobs"), true)
            .setDescription(EncryptedString.of("Skip rendering hostile and passive mobs"));

    private final BooleanSetting cullArmorStands = new BooleanSetting(
            EncryptedString.of("Cull Armor Stands"), true)
            .setDescription(EncryptedString.of("Skip rendering armor stands (shop holograms, NPC stands)"));

    public EntityCulling() {
        super(EncryptedString.of("Entity Culling"),
                EncryptedString.of("Skips rendering non-player entities to boost FPS"),
                -1, CategoryManager.ESP);
        addSettings(cullMobs, cullArmorStands);
    }

    public boolean isCullMobs() { return cullMobs.getValue(); }
    public boolean isCullArmorStands() { return cullArmorStands.getValue(); }

    @Override
    public void onEnable() { super.onEnable(); }

    @Override
    public void onDisable() { super.onDisable(); }
}
