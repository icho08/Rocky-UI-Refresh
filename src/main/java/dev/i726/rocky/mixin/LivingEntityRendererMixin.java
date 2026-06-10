package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.HidePlayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Shadow protected EntityModel<?> model;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderPre(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (state instanceof PlayerEntityRenderState && Rocky.INSTANCE != null) {
            HidePlayers hidePlayers = Rocky.INSTANCE.getModuleManager().getModule(HidePlayers.class);
            if (hidePlayers != null && hidePlayers.isEnabled()) {
                ci.cancel();
                return;
            }
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderPost(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue commandQueue, CameraRenderState cameraRenderState, CallbackInfo ci) {
    }
}
