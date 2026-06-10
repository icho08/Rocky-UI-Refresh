package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class NoParticles extends Module {

    public enum ParticleMode { ALL, COMBAT }

    private final ModeSetting<ParticleMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), ParticleMode.ALL, ParticleMode.class)
            .setDescription(EncryptedString.of("ALL = cancel every particle | COMBAT = only cancel crit/explosion/damage particles"));

    public NoParticles() {
        super(EncryptedString.of("No Particles"),
                EncryptedString.of("Reduces or removes particle effects for better FPS"),
                -1, CategoryManager.ESP);
        addSettings(mode);
    }

    public boolean isAll() {
        return mode.isMode(ParticleMode.ALL);
    }

    @Override
    public void onEnable() { super.onEnable(); }

    @Override
    public void onDisable() { super.onDisable(); }
}
