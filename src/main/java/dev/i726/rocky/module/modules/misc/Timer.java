package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;

public final class Timer extends Module {

    private final NumberSetting speed = new NumberSetting(
            EncryptedString.of("Speed"), 0.1, 10.0, 2.0, 0.1)
            .setDescription(EncryptedString.of("Tick speed multiplier — 1.0 = normal, 2.0 = 2x faster"));

    private final BooleanSetting resetOnDisable = new BooleanSetting(
            EncryptedString.of("Reset on Disable"), true)
            .setDescription(EncryptedString.of("Restores normal speed when the module turns off"));

    // State for transformTime() — accessed from MinecraftClientMixin via @ModifyArg
    private long lastRawTime  = -1L;
    private long lastScaledTime = -1L;

    public Timer() {
        super(EncryptedString.of("Timer"),
                EncryptedString.of("Speeds up or slows down the game tick rate"),
                -1, CategoryManager.BLATANT);
        addSettings(speed, resetOnDisable);
    }

    @Override
    public void onDisable() {
        // Reset state so the next enable starts fresh
        lastRawTime    = -1L;
        lastScaledTime = -1L;
        super.onDisable();
    }

    /**
     * Called from MinecraftClientMixin to scale the {@code timeMillis} argument
     * that is passed to {@code RenderTickCounter.beginRenderTick}.
     *
     * <p>Instead of passing the raw wall-clock time, we return a "synthetic" time
     * where each real millisecond is counted as {@code speed} milliseconds.
     * At speed 2.0 the tick counter thinks twice as much time has passed → 2× tick rate.
     *
     * @param rawTime the real wall-clock value from {@code Util.getMeasuringTimeMs()}
     * @return the scaled time to feed into the tick counter
     */
    public long transformTime(long rawTime) {
        if (lastRawTime < 0L) {
            lastRawTime    = rawTime;
            lastScaledTime = rawTime;
            return rawTime;
        }
        long elapsed       = rawTime - lastRawTime;
        long scaledElapsed = (long) (elapsed * speed.getValue());
        lastRawTime    = rawTime;
        lastScaledTime += scaledElapsed;
        return lastScaledTime;
    }

    /**
     * Whether the timer should reset to normal on disable.
     * Exposed so MinecraftClientMixin can pass 1.0× after disable.
     */
    public boolean shouldResetOnDisable() {
        return resetOnDisable.getValue();
    }

    public double getSpeedValue() {
        return speed.getValue();
    }
}
