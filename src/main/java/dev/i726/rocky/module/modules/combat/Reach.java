package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class Reach extends Module {
    // 3.1 is very safe, 3.3 is the typical 'legit' limit. 4.0+ is for rage.
    private final NumberSetting distance = new NumberSetting(EncryptedString.of("Distance"), 3.0, 4.5, 3.3, 0.01);
    private final BooleanSetting legit = new BooleanSetting(EncryptedString.of("Legit"), true)
            .setDescription(EncryptedString.of("Varies the reach distance to bypass anti-cheats"));
    private final NumberSetting randomization = new NumberSetting(EncryptedString.of("Randomization"), 0.0, 0.2, 0.05, 0.01);

    public Reach() {
        super(EncryptedString.of("Reach"),
                EncryptedString.of("Extends attack reach distance"),
                -1,
                CategoryManager.PVP);
        addSettings(distance, legit, randomization);
    }

    public double getReach() {
        if (!isEnabled()) return 3.0;
        
        double val = distance.getValue();
        if (legit.getValue()) {
            val -= Math.random() * randomization.getValue();
        }
        return val;
    }
}
