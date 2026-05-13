package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.module.modules.render.NoBounce;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.i726.rocky.Rocky.mc;

@Mixin(ItemStack.class)
public class ItemStackMixin {

	@Inject(method = "getBobbingAnimationTime", at = @At("HEAD"), cancellable = true)
	private void removeBounceAnimation(CallbackInfoReturnable<Integer> cir) {
		if (mc == null || mc.player == null || Rocky.INSTANCE == null) return;

		NoBounce noBounce = Rocky.INSTANCE.getModuleManager().getModule(NoBounce.class);
		if (noBounce != null && noBounce.isEnabled()) {
			ItemStack mainHandStack = mc.player.getMainHandStack();
			if (mainHandStack.isOf(Items.END_CRYSTAL)) {
				cir.setReturnValue(0);
			}
		}
	}
}
