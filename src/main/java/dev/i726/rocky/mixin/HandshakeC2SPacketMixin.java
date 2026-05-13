package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.misc.VersionSpoof;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandshakeC2SPacket.class)
public class HandshakeC2SPacketMixin {
    @Shadow @Mutable private int protocolVersion;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        if (Rocky.INSTANCE != null && Rocky.INSTANCE.getModuleManager() != null) {
            VersionSpoof spoof = Rocky.INSTANCE.getModuleManager().getModule(VersionSpoof.class);
            if (spoof != null && spoof.isEnabled()) {
                this.protocolVersion = spoof.getSpoofedProtocol();
            }
        }
    }
}
