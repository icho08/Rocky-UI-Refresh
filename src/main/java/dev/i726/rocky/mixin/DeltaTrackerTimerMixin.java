package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.misc.Timer;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DeltaTracker.Timer.class)
public class DeltaTrackerTimerMixin {

    @ModifyVariable(method = "advanceGameTime", at = @At("HEAD"), argsOnly = true, index = 1)
    private long rocky$scaleGameTime(long currentTimeMs) {
        if (Rocky.INSTANCE == null) return currentTimeMs;
        Timer timer = Rocky.INSTANCE.getModuleManager().getModule(Timer.class);
        if (timer == null || !timer.isEnabled()) return currentTimeMs;
        return timer.transformTime(currentTimeMs);
    }
}
