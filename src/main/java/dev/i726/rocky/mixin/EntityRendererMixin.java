package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.NameTags;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void onRenderLabel(EntityRenderState state, MatrixStack matrices,
                               OrderedRenderCommandQueue commandQueue,
                               CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState)) return;
        if (Rocky.INSTANCE == null) return;
        NameTags nameTags = Rocky.INSTANCE.getModuleManager().getModule(NameTags.class);
        if (nameTags != null && nameTags.isEnabled()) {
            ci.cancel();
        }
    }
}
