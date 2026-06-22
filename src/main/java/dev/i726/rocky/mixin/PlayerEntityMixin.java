package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.combat.Reach;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getBlockInteractionRange", at = @At("HEAD"), cancellable = true)
    private void onGetBlockInteractionRange(CallbackInfoReturnable<Double> cir) {
        Reach reach = Rocky.INSTANCE.getModuleManager().getModule(Reach.class);
        if (reach != null && reach.isEnabled()) {
            cir.setReturnValue(reach.getReach());
        }
    }

    @Inject(method = "getEntityInteractionRange", at = @At("HEAD"), cancellable = true)
    private void onGetEntityInteractionRange(CallbackInfoReturnable<Double> cir) {
        Reach reach = Rocky.INSTANCE.getModuleManager().getModule(Reach.class);
        if (reach != null && reach.isEnabled()) {
            cir.setReturnValue(reach.getReach());
        }
    }
}
