package dev.i726.rocky.mixin;

import com.mojang.authlib.GameProfile;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.MovementPacketListener;
import dev.i726.rocky.event.events.PlayerTickListener;
import dev.i726.rocky.utils.RotationOverride;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class ClientPlayerEntityMixin extends AbstractClientPlayer {

	@Shadow
	@Final
	protected Minecraft minecraft;

	public ClientPlayerEntityMixin(ClientLevel world, GameProfile profile) {
		super(world, profile);
	}

	/**
	 * HEAD: fire our movement-packet event, then silently swap the player's
	 * yaw/pitch to the virtual server-side values so the position packet carries
	 * the target rotation without the camera moving at all.
	 */
	@Inject(method = "sendPosition", at = @At("HEAD"))
	private void beforeMovementPackets(CallbackInfo ci) {
		EventManager.fire(new MovementPacketListener.MovementPacketEvent());

		if (RotationOverride.active) {
			LocalPlayer self = (LocalPlayer)(Object)this;
			RotationOverride.savedRealYaw   = self.yRot();
			RotationOverride.savedRealPitch = self.xRot();
			self.setYRot(RotationOverride.serverYaw);
			self.setXRot(RotationOverride.serverPitch);
		}
	}

	/**
	 * RETURN: the position packet has now been sent to the server with the virtual
	 * rotation.  Run any queued block-placement action FIRST (so Grim receives the
	 * rotation before the interact), then restore the real camera values.
	 *
	 * Packet order Grim sees on a placement tick:
	 *   PositionAndRotation(virtualYaw, virtualPitch)   ← rotation first
	 *   InteractBlock(...)                              ← validated OK ✓
	 *   [next tick] PositionAndRotation(virtual or return-step)
	 */
	@Inject(method = "sendPosition", at = @At("RETURN"))
	private void afterMovementPackets(CallbackInfo ci) {
		// Run queued placement action while Grim's rotation state = virtualYaw
		Runnable action = RotationOverride.afterPacketAction;
		RotationOverride.afterPacketAction = null;
		if (action != null) {
			try { action.run(); } catch (Exception ignored) {}
		}

		// Restore the real camera rotation — player sees no change
		if (!Float.isNaN(RotationOverride.savedRealYaw)) {
			LocalPlayer self = (LocalPlayer)(Object)this;
			self.setYRot(RotationOverride.savedRealYaw);
			self.setXRot(RotationOverride.savedRealPitch);
			RotationOverride.savedRealYaw   = Float.NaN;
			RotationOverride.savedRealPitch = Float.NaN;
		}
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void onPlayerTick(CallbackInfo ci) {
		EventManager.fire(new PlayerTickListener.PlayerTickEvent());
	}
}
