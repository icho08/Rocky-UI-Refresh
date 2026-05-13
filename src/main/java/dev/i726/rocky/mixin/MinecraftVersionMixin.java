package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.misc.VersionSpoof;
import net.minecraft.SharedConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SharedConstants.class)
public class MinecraftVersionMixin {
    @Inject(method = "getProtocolVersion", at = @At("HEAD"), cancellable = true)
    private static void onGetProtocolVersion(CallbackInfoReturnable<Integer> cir) {
        if (Rocky.INSTANCE != null && Rocky.INSTANCE.getModuleManager() != null) {
            VersionSpoof spoofModule = Rocky.INSTANCE.getModuleManager().getModule(VersionSpoof.class);
            if (spoofModule != null && spoofModule.isEnabled()) {
                cir.setReturnValue(spoofModule.getSpoofedProtocol());
            }
        }
    }
}
