package dev.i726.rocky.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "updateRenderState", at = @At("RETURN"))
    private void onUpdateRenderState(@Coerce Object player,
                                     PlayerEntityRenderState state,
                                     float tickDelta,
                                     CallbackInfo ci) {
        if (player instanceof Entity e) {
            ((IPlayerRenderState) state).rocky$setEntityUuid(e.getUuid());
        }
    }
}
