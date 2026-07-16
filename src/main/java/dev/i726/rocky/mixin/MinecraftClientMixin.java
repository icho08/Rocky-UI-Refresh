package dev.i726.rocky.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.*;
import dev.i726.rocky.module.modules.misc.FastUse;
import dev.i726.rocky.utils.MouseSimulation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftClientMixin {
        @Shadow
        @Nullable
        public ClientLevel level;

        @Shadow
        @Final
        private Window window;

        @Shadow
        private int rightClickDelay;

        @Inject(method = "tick", at = @At("HEAD"))
        private void onTick(CallbackInfo ci) {
                if (level != null) {
                        TickListener.TickEvent event = new TickListener.TickEvent();

                        EventManager.fire(event);
                }
        }

        @Inject(method = "resizeGui", at = @At("HEAD"))
        private void onResolutionChanged(CallbackInfo ci) {
                EventManager.fire(new ResolutionListener.ResolutionEvent(this.window));
        }

        @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
        private void onItemUse(CallbackInfo ci) {
                ItemUseListener.ItemUseEvent event = new ItemUseListener.ItemUseEvent();

                EventManager.fire(event);
                if (event.isCancelled()) ci.cancel();

                if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                        MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_RIGHT, false);
                        ci.cancel();
                }
        }

        @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
        private void onAttack(CallbackInfoReturnable<Boolean> cir) {
                AttackListener.AttackEvent event = new AttackListener.AttackEvent(((Minecraft)(Object)this).hitResult instanceof EntityHitResult hit ? hit.getEntity() : null);

                EventManager.fire(event);
                if (event.isCancelled()) cir.setReturnValue(false);

                if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                        MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
                        cir.setReturnValue(false);
                }
        }

        @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
        private void onBlockBreaking(boolean breaking, CallbackInfo ci) {
                BlockBreakingListener.BlockBreakingEvent event = new BlockBreakingListener.BlockBreakingEvent();

                EventManager.fire(event);
                if (event.isCancelled()) ci.cancel();

                if (MouseSimulation.isMouseButtonPressed(GLFW.GLFW_MOUSE_BUTTON_1)) {
                        MouseSimulation.mouseButtons.put(GLFW.GLFW_MOUSE_BUTTON_1, false);
                        ci.cancel();
                }
        }

        @Inject(method = "destroy", at = @At("HEAD"))
        private void onClose(CallbackInfo ci) {
                if (Rocky.INSTANCE != null) {
                        Rocky.INSTANCE.getProfileManager().saveProfile("default");
                }
        }

        @Inject(method = "startUseItem", at = @At("RETURN"))
        private void onDoItemUsePost(CallbackInfo ci) {
                if (Rocky.INSTANCE != null) {
                        Minecraft mc = (Minecraft)(Object)this;
                        if (mc.screen != null) return;
                        FastUse fastUse = Rocky.INSTANCE.getModuleManager().getModule(FastUse.class);
                        if (fastUse != null && fastUse.isEnabled() && mc.player != null) {
                                int mainCooldown = fastUse.getItemUseCooldown(mc.player.getMainHandItem());
                                int offCooldown = fastUse.getItemUseCooldown(mc.player.getOffhandItem());
                                rightClickDelay = Math.min(mainCooldown, offCooldown);
                        }
                }
        }

}
