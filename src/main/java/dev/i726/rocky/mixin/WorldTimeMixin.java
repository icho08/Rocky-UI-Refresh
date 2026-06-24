package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.TimeChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class WorldTimeMixin {

    @Inject(method = "getDefaultClockTime", at = @At("HEAD"), cancellable = true)
    private void rocky$overrideTimeOfDay(CallbackInfoReturnable<Long> cir) {
        if (!((Object) this instanceof ClientLevel)) return;
        if (Rocky.INSTANCE == null) return;
        TimeChanger tc = Rocky.INSTANCE.getModuleManager().getModule(TimeChanger.class);
        if (tc != null && tc.isEnabled()) {
            cir.setReturnValue(tc.getTargetTime());
        }
    }
}
