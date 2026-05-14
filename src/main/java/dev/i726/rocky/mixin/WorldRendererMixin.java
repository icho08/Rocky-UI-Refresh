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
    private void onRender(ObjectAllocator allocator, RenderTickCounter tickCounter, boolean renderBlockOutline, Camera camera, Matrix4f matrix4f, Matrix4f matrix4f2, Matrix4f matrix4f3, GpuBufferSlice gpuBufferSlice, Vector4f vector4f, boolean bl, CallbackInfo ci) {
        // Use an identity MatrixStack — RenderUtils methods subtract camera position
        // from world coordinates internally, giving camera-relative vertices.
        // Applying matrix4f or an extra translate(-cam) here would double/triple-offset
        // every world coordinate and break all 3-D ESP rendering.
        MatrixStack matrices = new MatrixStack();

        EventManager.fire(new GameRenderListener.GameRenderEvent(matrices, tickCounter.getTickProgress(true)));

        net.minecraft.client.MinecraftClient.getInstance()
                .getBufferBuilders().getEntityVertexConsumers().draw();
    }
}
