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
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
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
        PoseStack matrices = new PoseStack();
        Quaternionf viewRot = new Quaternionf(cameraRenderState.orientation).conjugate();
        matrices.last().pose().rotate(viewRot);

        EventManager.fire(new GameRenderListener.GameRenderEvent(matrices, tickCounter.getGameTimeDeltaPartialTick(true)));
        // NOTE: do NOT call endBatch() here — it forces a full GPU buffer flush every frame
        // and causes sky flicker + severe lag. Modules that need a flush must call it themselves.
    }
}
