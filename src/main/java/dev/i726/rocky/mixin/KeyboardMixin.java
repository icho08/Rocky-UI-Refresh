package dev.i726.rocky.mixin;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.ButtonListener;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
        @Shadow
        @Final
        private MinecraftClient client;

        @Inject(method = "onKey", at = @At("HEAD"))
        private void onPress(long window, int scancode, KeyInput input, CallbackInfo ci) {
                int key = input.key();
                int action = 1;
                EventManager.fire(new ButtonListener.ButtonEvent(key, window, action));
        }
}
