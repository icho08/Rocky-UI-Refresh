package dev.i726.rocky.mixin;

import dev.i726.rocky.imixin.IPlayerRenderState;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class PlayerEntityRendererMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onUpdateRenderState(@Coerce Object player,
                                     AvatarRenderState state,
                                     float tickDelta,
                                     CallbackInfo ci) {
        if (player instanceof Entity e) {
            ((IPlayerRenderState) state).rocky$setEntityUuid(e.getUUID());
        }
    }
}
