package dev.i726.rocky.mixin;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.*;
import dev.i726.rocky.module.modules.misc.FastUse;
import dev.i726.rocky.module.modules.misc.Timer;
import dev.i726.rocky.utils.MouseSimulation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.Window;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
        @Shadow
        @Nullable
        public ClientWorld world;

        @Shadow
        @Final
        private Window window;

        @Shadow
        private int itemUseCooldown;

        @Inject(method = "tick", at = @At("HEAD"))
        private void onTick(CallbackInfo ci) {
                if (world != null) {
                        TickListener.TickEvent event = new TickListener.TickEvent();

                        EventManager.fire(event);
                }
        }

        @Inject(method = "onResolutionChanged", at = @At("HEAD"))
        private void onResolutionChanged(CallbackInfo ci) {
                EventManager.fire(new ResolutionListener.ResolutionEvent(this.window));
        }

        @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
        private void onItemUse(CallbackInfo ci) {
                ItemUseListener.ItemUseEvent event = new ItemUseListener.ItemUseEvent();

                EventManager.fire(event);
                if (event.isCancelled()) ci.cancel();

                if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                        MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
                        ci.cancel();
                }
        }

        @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
        private void onAttack(CallbackInfoReturnable<Boolean> cir) {
                AttackListener.AttackEvent event = new AttackListener.AttackEvent(((MinecraftClient)(Object)this).crosshairTarget instanceof EntityHitResult hit ? hit.getEntity() : null);

                EventManager.fire(event);
                if (event.isCancelled()) cir.setReturnValue(false);

                if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                        MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
                        cir.setReturnValue(false);
                }
        }

        @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
        private void onBlockBreaking(boolean breaking, CallbackInfo ci) {
                BlockBreakingListener.BlockBreakingEvent event = new BlockBreakingListener.BlockBreakingEvent();

                EventManager.fire(event);
                if (event.isCancelled()) ci.cancel();

                if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                        MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
                        ci.cancel();
                }
        }

        @Inject(method = "stop", at = @At("HEAD"))
        private void onClose(CallbackInfo ci) {
                if (Rocky.INSTANCE != null) {
                        Rocky.INSTANCE.getProfileManager().saveProfile("default");
                }
        }

        @Inject(method = "doItemUse", at = @At("RETURN"))
        private void onDoItemUsePost(CallbackInfo ci) {
                if (Rocky.INSTANCE != null) {
                        FastUse fastUse = Rocky.INSTANCE.getModuleManager().getModule(FastUse.class);
                        MinecraftClient mc = (MinecraftClient)(Object)this;
                        if (fastUse != null && fastUse.isEnabled() && mc.player != null) {
                                int mainCooldown = fastUse.getItemUseCooldown(mc.player.getMainHandStack());
                                int offCooldown = fastUse.getItemUseCooldown(mc.player.getOffHandStack());
                                itemUseCooldown = Math.min(mainCooldown, offCooldown);
                        }
                }
        }

        /**
         * Scales the wall-clock time argument that MinecraftClient passes to
         * RenderTickCounter.beginRenderTick so the tick counter thinks more (or less)
         * time has elapsed — effectively multiplying the game's tick rate by Timer.speed.
         */
        @ModifyArg(
                method = "render",
                at = @At(value = "INVOKE",
                         target = "Lnet/minecraft/client/render/RenderTickCounter;beginRenderTick(JZ)I"),
                index = 0
        )
        private long rocky$modifyTimerSpeed(long timeMillis) {
                if (Rocky.INSTANCE == null || Rocky.INSTANCE.getModuleManager() == null) return timeMillis;
                Timer timer = Rocky.INSTANCE.getModuleManager().getModule(Timer.class);
                if (timer == null || !timer.isEnabled()) return timeMillis;
                return timer.transformTime(timeMillis);
        }

}
