package dev.i726.rocky.mixin;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.GameRenderListener;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import net.minecraft.client.util.math.MatrixStack;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline,
                          Camera camera, Matrix4f matrix4f, Matrix4f matrix4f2, Matrix4f matrix4f3,
                          GpuBufferSlice gpuBufferSlice, Vector4f vector4f, boolean bl, CallbackInfo ci) {
        /*
         * Build a MatrixStack whose position matrix is the camera's rotation.
         *
         * RenderUtils subtracts the camera world-position from every vertex, so
         * the coordinates it emits are already camera-relative (camera = origin).
         * Applying the camera rotation here transforms those coords into proper
         * view-space so the perspective projection in RenderSystem renders them at
         * the correct screen position regardless of which direction you look.
         *
         * With an identity matrix the ESP would only look correct when facing a
         * specific direction; with the rotation it tracks head movement correctly.
         */
        MatrixStack matrices = new MatrixStack();
        matrices.peek().getPositionMatrix().rotate(camera.getRotation());

        EventManager.fire(new GameRenderListener.GameRenderEvent(matrices, tickCounter.getTickProgress(true)));

        net.minecraft.client.MinecraftClient.getInstance()
                .getBufferBuilders().getEntityVertexConsumers().draw();
    }
}
