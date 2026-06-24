package dev.i726.rocky.mixin;

import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RemotePlayer.class)
public interface OtherClientPlayerEntityAccessor {
	@Accessor("lerpDeltaMovementSteps")
	int getVelocityLerpDivisor();

	@Accessor("lerpDeltaMovementSteps")
	void setVelocityLerpDivisor(int velocityLerpDivisor);

	@Accessor("lerpDeltaMovement")
	Vec3 getClientVelocity();

	@Accessor("lerpDeltaMovement")
	void setClientVelocity(Vec3 clientVelocity);
}
