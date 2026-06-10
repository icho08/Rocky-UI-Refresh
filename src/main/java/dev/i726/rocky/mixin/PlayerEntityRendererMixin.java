package dev.i726.rocky.mixin;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void onUpdateRenderState(AbstractClientPlayerEntity player,
                                     PlayerEntityRenderState state,
                                     float tickDelta,
                                     CallbackInfo ci) {
        ((IPlayerRenderState) state).rocky$setEntityUuid(player.getUuid());
    }
}
