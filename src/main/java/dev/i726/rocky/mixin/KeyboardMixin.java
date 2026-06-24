package dev.i726.rocky.mixin;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.ButtonListener;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {
        @Shadow
        @Final
        private Minecraft minecraft;

        @Inject(method = "keyPress", at = @At("HEAD"))
        private void onPress(long window, int scancode, KeyEvent input, CallbackInfo ci) {
                int key = input.key();
                int action = 1;
                EventManager.fire(new ButtonListener.ButtonEvent(key, window, action));
        }
}
