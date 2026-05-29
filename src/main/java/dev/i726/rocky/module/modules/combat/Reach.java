package dev.i726.rocky.module.modules.combat;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;

public final class Reach extends Module {

    public enum ReachMode {
        /** 3.0–3.5: safe for most strict servers */
        Safe,
        /** 3.5–4.0: bypasses most NCP/AAC configs */
        Bypass,
        /** 4.0+: rage, detectable on Grim/Vulcan strict */
        Rage
    }

    private final ModeSetting<ReachMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), ReachMode.Bypass, ReachMode.class)
            .setDescription(EncryptedString.of("Safe = 3.0-3.5 | Bypass = 3.5-4.0 | Rage = 4.0-4.5"));

    private final NumberSetting distance = new NumberSetting(EncryptedString.of("Distance"), 3.0, 4.5, 3.7, 0.01)
            .setDescription(EncryptedString.of("Max reach in blocks; overridden by Mode presets"));

    private final BooleanSetting randomize = new BooleanSetting(EncryptedString.of("Randomize"), true)
            .setDescription(EncryptedString.of("Randomly vary reach each swing so the pattern is not constant"));

    private final NumberSetting randomization = new NumberSetting(EncryptedString.of("Randomization"), 0.0, 0.3, 0.08, 0.01)
            .setDescription(EncryptedString.of("Max random subtraction from reach distance"));

    private final BooleanSetting rerollOnHit = new BooleanSetting(EncryptedString.of("Reroll On Hit"), true)
            .setDescription(EncryptedString.of("Pick a new random reach value after each hit"));

    private final BooleanSetting playersOnly = new BooleanSetting(EncryptedString.of("Players Only"), true)
            .setDescription(EncryptedString.of("Only extend reach against player entities — skips mobs and NPCs so server plugins don't flag"));

    private final TimerUtils rerollTimer = new TimerUtils();

    private double currentReach = 3.0;

    public Reach() {
        super(EncryptedString.of("Reach"),
                EncryptedString.of("Extends attack reach distance with anticheat bypass"),
                -1, CategoryManager.PVP);
        addSettings(mode, distance, randomize, randomization, rerollOnHit, playersOnly);
    }

    @Override
    public void onEnable() {
        rerollReach();
        rerollTimer.reset();
        super.onEnable();
    }

    private void rerollReach() {
        double base;
        double jitter;
        switch (mode.getMode()) {
            case Safe   -> { base = 3.0 + Math.random() * 0.5;  jitter = 0.04; }
            case Bypass -> { base = 3.5 + Math.random() * 0.4;  jitter = 0.06; }
            case Rage   -> { base = distance.getValue();         jitter = randomization.getValue(); }
            default     -> { base = distance.getValue();         jitter = randomization.getValue(); }
        }
        currentReach = base - (randomize.getValue() ? Math.random() * jitter : 0);
    }

    /**
     * Returns true when the current crosshair target is a real player entity
     * (or playersOnly is disabled). Used to gate reach extension so it never
     * fires against mobs or NPCs — those are caught by server plugins.
     */
    private boolean targetIsPlayer() {
        if (!playersOnly.getValue()) return true;
        if (mc == null) return false;
        return mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity;
    }

    /** Called by the interaction mixin every swing. */
    public double getReach() {
        if (!isEnabled()) return 3.0;
        if (!targetIsPlayer()) return 3.0;

        // Re-roll after each hit (called from outside) or on a timer
        if (rerollOnHit.getValue() || rerollTimer.delay(300)) {
            rerollReach();
            rerollTimer.reset();
        }

        return currentReach;
    }

    /** Called by the interaction mixin to get the precise reach for THIS hit. */
    public double consumeReach() {
        if (!isEnabled()) return 3.0;
        if (!targetIsPlayer()) return 3.0;
        double val = currentReach;
        if (rerollOnHit.getValue()) rerollReach(); // immediately pick next value for next hit
        return val;
    }
}
