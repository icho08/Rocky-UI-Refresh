package dev.i726.rocky.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.i726.rocky.Rocky.mc;

import net.minecraft.world.item.ItemStack;

@Mixin(ItemStack.class)
public class ItemStackMixin {

    @Inject(method = "getPopTime", at = @At("HEAD"), cancellable = true)
    private void removeBounceAnimation(CallbackInfoReturnable<Integer> cir) {
    }
}
