package dev.i726.rocky.mixin;

import com.mojang.authlib.GameProfile;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.combat.SilentAim;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.MovementPacketListener;
import dev.i726.rocky.event.events.PlayerTickListener;
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
	private void onSendMovementPackets(CallbackInfo ci) {
		EventManager.fire(new MovementPacketListener.MovementPacketEvent());
		SilentAim sa = Rocky.INSTANCE.getModuleManager().getModule(SilentAim.class);
		if (sa != null && sa.isEnabled() && sa.getRotation() != null) {
			lastYaw = this.getYaw();
			lastPitch = this.getPitch();
			this.setYaw((float) sa.getRotation().yaw());
			this.setPitch((float) sa.getRotation().pitch());
		}
	}

	@Inject(method = "sendMovementPackets", at = @At("RETURN"))
	private void onSendMovementPacketsReturn(CallbackInfo ci) {
		SilentAim sa = Rocky.INSTANCE.getModuleManager().getModule(SilentAim.class);
		if (sa != null && sa.isEnabled() && sa.getRotation() != null) {
			this.setYaw(lastYaw);
			this.setPitch(lastPitch);
		}
	}

	private float lastYaw, lastPitch;

	@Inject(method = "tick", at = @At("HEAD"))
	private void onPlayerTick(CallbackInfo ci) {
		EventManager.fire(new PlayerTickListener.PlayerTickEvent());
	}
	//@Inject(method = "sendMovementPackets", at = @At("HEAD"))
}
