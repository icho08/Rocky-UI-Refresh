package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.ButtonListener;
import dev.i726.rocky.event.events.MouseMoveListener;
import dev.i726.rocky.event.events.MouseUpdateListener;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.SelfDestruct;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseMixin {
	@Shadow @Final private Minecraft minecraft;

	@Inject(method = "turnPlayer", at = @At("RETURN"))
	private void onMouseUpdate(CallbackInfo ci) {
		EventManager.fire(new MouseUpdateListener.MouseUpdateEvent());
	}

	@Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
	private void onMouseMove(long window, double x, double y, CallbackInfo ci) {
		MouseMoveListener.MouseMoveEvent event = new MouseMoveListener.MouseMoveEvent(window, x, y);

		EventManager.fire(event);
		if (event.isCancelled())
			ci.cancel();
	}

	@Inject(method = "onButton", at = @At("HEAD"))
	private void onMousePress(long window, net.minecraft.client.input.MouseButtonInfo input, int action, CallbackInfo ci) {
		EventManager.fire(new ButtonListener.ButtonEvent(input.button(), window, action));
	}
}