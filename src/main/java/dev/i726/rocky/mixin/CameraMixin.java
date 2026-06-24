package dev.i726.rocky.mixin;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.CameraUpdateListener;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public class CameraMixin {
        @ModifyArgs(
                method = "alignWithEntity(F)V",
                at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V")
        )
        private void update(Args args) {
                CameraUpdateListener.CameraUpdateEvent event = new CameraUpdateListener.CameraUpdateEvent(args.get(0), args.get(1), args.get(2));
                EventManager.fire(event);

                args.set(0, event.getX());
                args.set(1, event.getY());
                args.set(2, event.getZ());
        }
}
