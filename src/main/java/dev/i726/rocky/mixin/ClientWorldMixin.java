package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.TimeChanger;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void onGetRainGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (Rocky.INSTANCE == null) return;
        TimeChanger mod = Rocky.INSTANCE.getModuleManager().getModule(TimeChanger.class);
        if (mod == null || !mod.isEnabled()) return;
        switch (mod.getWeather()) {
            case CLEAR   -> cir.setReturnValue(0f);
            case RAIN    -> cir.setReturnValue(1f);
            case THUNDER -> cir.setReturnValue(1f);
            default      -> {}
        }
    }

    @Inject(method = "getThunderGradient", at = @At("HEAD"), cancellable = true)
    private void onGetThunderGradient(float delta, CallbackInfoReturnable<Float> cir) {
        if (Rocky.INSTANCE == null) return;
        TimeChanger mod = Rocky.INSTANCE.getModuleManager().getModule(TimeChanger.class);
        if (mod == null || !mod.isEnabled()) return;
        switch (mod.getWeather()) {
            case CLEAR, RAIN -> cir.setReturnValue(0f);
            case THUNDER     -> cir.setReturnValue(1f);
            default          -> {}
        }
    }
}
