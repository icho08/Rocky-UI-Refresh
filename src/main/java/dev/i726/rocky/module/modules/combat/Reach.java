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
        /**
         * 3.01–3.20 — squared-random distribution so most hits cluster near
         * vanilla. Nearly impossible to distinguish from latency-assisted hits.
         */
        Legit,
        /**
         * 3.10–3.50 — safe on most NCP / AAC configs. Streak-break keeps the
         * pattern non-uniform.
         */
        Safe,
        /**
         * 3.50–3.90 — bypasses common NCP / AAC builds. Still uses streak-break
         * to avoid flagging on stricter configs.
         */
        Bypass,
        /**
         * 4.0+ — rage / blatant. Grim / Vulcan strict WILL flag this.
         */
        Rage
    }

    private final ModeSetting<ReachMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), ReachMode.Safe, ReachMode.class)
            .setDescription(EncryptedString.of(
                    "Legit = 3.01-3.20 clustered near vanilla | Safe = 3.10-3.50 | " +
                    "Bypass = 3.50-3.90 NCP/AAC | Rage = 4.0+ detectable on Grim"));

    private final NumberSetting distance = new NumberSetting(EncryptedString.of("Distance"), 3.0, 4.5, 3.7, 0.01)
            .setDescription(EncryptedString.of("Max reach used in Rage mode only"));

    private final BooleanSetting randomize = new BooleanSetting(EncryptedString.of("Randomize"), true)
            .setDescription(EncryptedString.of("Vary reach per hit so the distance is never constant"));

    private final NumberSetting randomization = new NumberSetting(EncryptedString.of("Randomization"), 0.0, 0.3, 0.08, 0.01)
            .setDescription(EncryptedString.of("Extra jitter added in Rage mode; other modes use fixed internal ranges"));

    private final BooleanSetting rerollOnHit = new BooleanSetting(EncryptedString.of("Reroll On Hit"), true)
            .setDescription(EncryptedString.of("Pick a new reach value after every hit (recommended — prevents fixed-distance pattern)"));

    private final BooleanSetting streakBreak = new BooleanSetting(EncryptedString.of("Streak Break"), true)
            .setDescription(EncryptedString.of("Force one vanilla-range hit after several consecutive extended hits — breaks the flaggable constant-over-range pattern"));

    private final NumberSetting streakLimit = new NumberSetting(EncryptedString.of("  Streak Limit"), 2.0, 8.0, 4.0, 1.0)
            .setDescription(EncryptedString.of("Extended hits allowed before forcing a vanilla reset"));

    private final BooleanSetting playersOnly = new BooleanSetting(EncryptedString.of("Players Only"), true)
            .setDescription(EncryptedString.of("Only extend reach against player entities — skips mobs and NPCs so server plugins don't flag"));

    private final TimerUtils rerollTimer = new TimerUtils();

    private double currentReach = 3.0;
    private int consecutiveExtended = 0;

    public Reach() {
        super(EncryptedString.of("Reach"),
                EncryptedString.of("Extends attack reach with anticheat bypass"),
                -1, CategoryManager.PVP);
        addSettings(mode, distance, randomize, randomization, rerollOnHit,
                streakBreak, streakLimit, playersOnly);
    }

    @Override
    public void onEnable() {
        rerollReach();
        rerollTimer.reset();
        consecutiveExtended = 0;
        super.onEnable();
    }

    // ── Internal reach roll ───────────────────────────────────────────────

    private void rerollReach() {
        switch (mode.getMode()) {
            case Legit -> {
                // Squared-random: biases distribution toward the low end.
                // The majority of hits land at 3.01-3.08; only rare spikes reach
                // 3.20. This is indistinguishable from latency-assisted hits on a
                // vanilla client with 50-80 ms ping.
                double r = Math.random();
                r = r * r; // square → skew toward 0
                currentReach = 3.01 + r * 0.19;
            }
            case Safe -> {
                // Uniform in a moderate band with optional extra jitter.
                double base = 3.10 + Math.random() * 0.35;
                currentReach = base - (randomize.getValue() ? Math.random() * 0.05 : 0);
            }
            case Bypass -> {
                double base = 3.50 + Math.random() * 0.35;
                currentReach = base - (randomize.getValue() ? Math.random() * 0.07 : 0);
            }
            case Rage -> {
                double jitter = randomization.getValue();
                currentReach = distance.getValue() - (randomize.getValue() ? Math.random() * jitter : 0);
            }
        }
    }

    // ── Player-only gate ──────────────────────────────────────────────────

    /**
     * Returns true only when the crosshair is on a real player (or playersOnly
     * is off). Ensures reach never fires for mob / NPC attacks — server plugins
     * that protect NPCs run their own reach check and will flag any over-range
     * interaction against them.
     */
    private boolean targetIsPlayer() {
        if (!playersOnly.getValue()) return true;
        if (mc == null) return false;
        return mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity;
    }

    // ── Public API (called by PlayerEntityMixin) ──────────────────────────

    /**
     * Called by the interaction-range mixin on every entity interaction query.
     * DOES NOT trigger a reroll here — rerolls happen in consumeReach() so
     * the value is stable within a single frame / packet cycle.
     */
    public double getReach() {
        if (!isEnabled()) return 3.0;
        if (!targetIsPlayer()) return 3.0;

        // Timer-based reroll only (not on every query — that was a bug causing
        // the reach to change many times per frame and create a flickering pattern).
        if (rerollTimer.delay(250)) {
            rerollReach();
            rerollTimer.reset();
        }

        return currentReach;
    }

    /**
     * Called once per actual attack swing to get the reach for THIS hit.
     * Applies streak-break protection and triggers the next reroll.
     */
    public double consumeReach() {
        if (!isEnabled()) return 3.0;
        if (!targetIsPlayer()) return 3.0;

        double val = currentReach;

        // Streak-break: after N consecutive extended hits, force one vanilla hit.
        // This prevents the AC from seeing a long unbroken streak at >3.0 reach,
        // which is the primary pattern used to distinguish reach cheats from latency.
        if (streakBreak.getValue()) {
            boolean isExtended = val > 3.05;
            if (isExtended) {
                consecutiveExtended++;
                if (consecutiveExtended >= (int) streakLimit.getValue()) {
                    consecutiveExtended = 0;
                    val = 3.0; // vanilla fallback for this one hit
                }
            } else {
                consecutiveExtended = 0;
            }
        }

        if (rerollOnHit.getValue()) {
            rerollReach();
            rerollTimer.reset(); // keep timer and hit-reroll in sync
        }

        return val;
    }
}
