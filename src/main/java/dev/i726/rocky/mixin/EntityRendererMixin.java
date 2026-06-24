package dev.i726.rocky.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.NameTags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {

    @Inject(method = "submitNameDisplay", at = @At("HEAD"), cancellable = true)
    private void onRenderLabel(EntityRenderState state, PoseStack matrices,
                               SubmitNodeCollector commandQueue,
                               CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState)) return;
        if (Rocky.INSTANCE == null) return;
        NameTags nameTags = Rocky.INSTANCE.getModuleManager().getModule(NameTags.class);
        if (nameTags != null && nameTags.isEnabled()) {
            ci.cancel();
        }
    }
}
