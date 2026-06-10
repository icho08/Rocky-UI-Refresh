package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.AntiFog;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void onApplyFog(Camera camera, BackgroundRenderer.FogType fogType,
                                   Vector4f color, float viewDistance,
                                   boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (Rocky.INSTANCE == null) return;
        AntiFog mod = Rocky.INSTANCE.getModuleManager().getModule(AntiFog.class);
        if (mod != null && mod.isEnabled()) {
            ci.cancel();
        }
    }
}
