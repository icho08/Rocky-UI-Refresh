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
import org.joml.Quaternionf;
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
         * Build a MatrixStack for world-space ESP rendering.
         *
         * RenderUtils subtracts the camera world-position from every vertex so
         * all coordinates are already camera-relative (camera = origin).
         *
         * We apply the CONJUGATE (inverse) of the camera's rotation quaternion.
         * camera.getRotation() is the camera's world-space orientation; to
         * transform camera-relative world coords into view/eye space we need
         * the inverse rotation so that world objects stay anchored in place as
         * the player looks around, rather than rotating with the mouse.
         */
        MatrixStack matrices = new MatrixStack();
        Quaternionf viewRot = new Quaternionf(camera.getRotation()).conjugate();
        matrices.peek().getPositionMatrix().rotate(viewRot);

        EventManager.fire(new GameRenderListener.GameRenderEvent(matrices, tickCounter.getTickProgress(true)));

        net.minecraft.client.MinecraftClient.getInstance()
                .getBufferBuilders().getEntityVertexConsumers().draw();
    }
}
