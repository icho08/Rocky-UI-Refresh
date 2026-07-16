package dev.i726.rocky.mixin;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.GameRenderListener;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void onRender(GraphicsResourceAllocator allocator, DeltaTracker tickCounter, boolean renderBlockOutline,
                          CameraRenderState cameraRenderState, Matrix4fc projMatrix,
                          GpuBufferSlice gpuBufferSlice, Vector4f fogColor, boolean bl,
                          ChunkSectionsToRender sectionsToRender, CallbackInfo ci) {
        // Use the matrices MC already computed for this frame — no manual quaternion math.
        // viewRotationMatrix = world-relative → eye space rotation (camera's view rotation).
        // projectionMatrix   = the actual GPU projection matrix (handles sprint FOV, bow zoom, etc.).
        PoseStack matrices = new PoseStack();
        matrices.last().pose().set(cameraRenderState.viewRotationMatrix);

        Matrix4f proj = new Matrix4f(cameraRenderState.projectionMatrix);

        EventManager.fire(new GameRenderListener.GameRenderEvent(matrices, tickCounter.getGameTimeDeltaPartialTick(true), proj));
    }
}
