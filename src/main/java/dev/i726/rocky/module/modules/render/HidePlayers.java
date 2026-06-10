package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class HidePlayers extends Module {

    private final BooleanSetting showCombatTarget = new BooleanSetting(
            EncryptedString.of("Show Combat Target"), true)
            .setDescription(EncryptedString.of("Still render the player KillAura is currently targeting"));

    public HidePlayers() {
        super(
                EncryptedString.of("Hide Players"),
                EncryptedString.of("Stops rendering all other players on your screen"),
                -1,
                CategoryManager.ESP
        );
        addSettings(showCombatTarget);
    }

    public boolean isShowCombatTarget() {
        return showCombatTarget.getValue();
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
