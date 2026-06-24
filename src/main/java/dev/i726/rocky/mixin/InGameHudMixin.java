package dev.i726.rocky.mixin;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.HudListener;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class InGameHudMixin {
	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void onRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
		HudListener.HudEvent event = new HudListener.HudEvent(context, tickCounter.getGameTimeDeltaPartialTick(true));

		EventManager.fire(event);
	}
}
