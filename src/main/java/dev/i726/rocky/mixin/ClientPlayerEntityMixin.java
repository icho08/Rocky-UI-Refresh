package dev.i726.rocky.mixin;

import com.mojang.authlib.GameProfile;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.MovementPacketListener;
import dev.i726.rocky.event.events.PlayerTickListener;
import dev.i726.rocky.utils.RotationOverride;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public class ClientPlayerEntityMixin extends AbstractClientPlayerEntity {

	@Shadow
	@Final
	protected MinecraftClient client;

	public ClientPlayerEntityMixin(ClientWorld world, GameProfile profile) {
		super(world, profile);
	}

	@Inject(method = "sendMovementPackets", at = @At("HEAD"))
	private void beforeMovementPackets(CallbackInfo ci) {
		EventManager.fire(new MovementPacketListener.MovementPacketEvent());

		// Silent rotation: swap player yaw/pitch to server-side override so the
		// position packet carries our target rotation without moving the camera.
		if (RotationOverride.active) {
			ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
			RotationOverride.savedRealYaw   = self.getYaw();
			RotationOverride.savedRealPitch = self.getPitch();
			self.setYaw(RotationOverride.serverYaw);
			self.setPitch(RotationOverride.serverPitch);
		}
	}

	@Inject(method = "sendMovementPackets", at = @At("RETURN"))
	private void afterMovementPackets(CallbackInfo ci) {
		// Restore real camera rotation after the packet has been sent.
		if (!Float.isNaN(RotationOverride.savedRealYaw)) {
			ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
			self.setYaw(RotationOverride.savedRealYaw);
			self.setPitch(RotationOverride.savedRealPitch);
			RotationOverride.savedRealYaw   = Float.NaN;
			RotationOverride.savedRealPitch = Float.NaN;
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onPlayerTick(CallbackInfo ci) {
		EventManager.fire(new PlayerTickListener.PlayerTickEvent());
	}
}
