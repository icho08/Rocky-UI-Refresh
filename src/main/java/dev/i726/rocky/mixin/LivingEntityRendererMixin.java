package dev.i726.rocky.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.imixin.IPlayerRenderState;
import dev.i726.rocky.module.modules.render.EntityCulling;
import dev.i726.rocky.module.modules.render.HidePlayers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Shadow protected EntityModel<?> model;

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void onRenderPre(LivingEntityRenderState state, PoseStack matrices,
                             SubmitNodeCollector commandQueue,
                             CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (Rocky.INSTANCE == null) return;

        if (state instanceof AvatarRenderState) {
            HidePlayers hidePlayers = Rocky.INSTANCE.getModuleManager().getModule(HidePlayers.class);
            if (hidePlayers != null && hidePlayers.isEnabled()) {
                if (hidePlayers.isShowCombatTarget()) {
                    UUID renderUuid = ((IPlayerRenderState) state).rocky$getEntityUuid();
                    if (renderUuid != null && Rocky.INSTANCE.getCombatManager().isInCombat(renderUuid)) {
                        return;
                    }
                }
                ci.cancel();
                return;
            }
        } else {
            EntityCulling ec = Rocky.INSTANCE.getModuleManager().getModule(EntityCulling.class);
            if (ec != null && ec.isEnabled()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void onRenderPost(LivingEntityRenderState state, PoseStack matrices,
                              SubmitNodeCollector commandQueue,
                              CameraRenderState cameraRenderState, CallbackInfo ci) {
    }
}
