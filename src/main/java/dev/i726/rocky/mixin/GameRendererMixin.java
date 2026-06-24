package dev.i726.rocky.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.modules.misc.Freecam;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow public abstract Matrix4f getProjectionMatrix(float fov);

	@Shadow protected abstract float getFov(Camera camera, float tickDelta, boolean changingFov);

	@Shadow @Final private Camera mainCamera;


	@Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
	private void onShouldRenderBlockOutline(CallbackInfoReturnable<Boolean> cir) {
		if (Rocky.INSTANCE != null && Rocky.INSTANCE.getModuleManager().getModule(Freecam.class).isEnabled())
			cir.setReturnValue(false);
	}
}