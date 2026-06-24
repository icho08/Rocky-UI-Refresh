package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.misc.Freecam;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

        @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
        private void onShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
                if (Rocky.INSTANCE != null && Rocky.INSTANCE.getModuleManager().getModule(Freecam.class).isEnabled())
                        cir.setReturnValue(false);
        }
}
