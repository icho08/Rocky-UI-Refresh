package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.movement.GodBridge;
import dev.i726.rocky.module.modules.movement.SmartBridge;
import dev.i726.rocky.module.modules.combat.Reach;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    /**
     * Safe-walk: clip at ledge only when the player is NOT pressing the forward key.
     *
     * This means:
     *  - Moving forward (W): no ledge clip — player walks freely toward the bridge destination.
     *  - Standing still / moving backward or sideways: ledge clip active — player won't fall
     *    off the back/side edge of the block they're standing on.
     *
     * Without this directional check, clipAtLedge=true blocks ALL edge movement including
     * forward, which prevents the player from stepping onto newly placed blocks ahead.
     */
    @Inject(method = "clipAtLedge", at = @At("HEAD"), cancellable = true)
    private void onClipAtLedge(CallbackInfoReturnable<Boolean> cir) {
        if (GodBridge.shouldSafeWalk() || SmartBridge.safeWalkActive) {
            MinecraftClient mc = MinecraftClient.getInstance();
            // Allow forward movement through — only clip when not pressing forward
            if (mc.options == null || !mc.options.forwardKey.isPressed()) {
                cir.setReturnValue(true);
            }
        }
    }

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
