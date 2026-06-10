package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.NoRender;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class NoRenderMixin {

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderWeather(CallbackInfo ci) {
        if (Rocky.INSTANCE == null) return;
        NoRender mod = Rocky.INSTANCE.getModuleManager().getModule(NoRender.class);
        if (mod != null && mod.isEnabled() && mod.isNoWeather()) ci.cancel();
    }

    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderClouds(CallbackInfo ci) {
        if (Rocky.INSTANCE == null) return;
        NoRender mod = Rocky.INSTANCE.getModuleManager().getModule(NoRender.class);
        if (mod != null && mod.isEnabled() && mod.isNoClouds()) ci.cancel();
    }

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderSky(CallbackInfo ci) {
        if (Rocky.INSTANCE == null) return;
        NoRender mod = Rocky.INSTANCE.getModuleManager().getModule(NoRender.class);
        if (mod != null && mod.isEnabled() && mod.isNoSky()) ci.cancel();
    }
}
