package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.misc.VersionSpoof;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandshakeC2SPacket.class)
public class HandshakeC2SPacketMixin {

    @Inject(method = "protocolVersion", at = @At("RETURN"), cancellable = true)
    private void spoofProtocolVersion(CallbackInfoReturnable<Integer> cir) {
        if (Rocky.INSTANCE == null || Rocky.INSTANCE.getModuleManager() == null) return;
        VersionSpoof spoof = Rocky.INSTANCE.getModuleManager().getModule(VersionSpoof.class);
        if (spoof != null && spoof.isEnabled()) {
            cir.setReturnValue(spoof.getSpoofedProtocol());
        }
    }
}
