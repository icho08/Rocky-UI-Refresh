package dev.i726.rocky.event.events;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.i726.rocky.event.Event;
import dev.i726.rocky.event.Listener;
import java.util.ArrayList;
import org.joml.Matrix4f;

public interface GameRenderListener extends Listener {
    void onGameRender(GameRenderEvent event);

    class GameRenderEvent extends Event<GameRenderListener> {
        public PoseStack matrices;
        public float delta;
        /** The actual GPU projection matrix used this frame (from renderLevel). */
        public Matrix4f projMatrix;

        public GameRenderEvent(PoseStack matrices, float delta, Matrix4f projMatrix) {
            this.matrices = matrices;
            this.delta = delta;
            this.projMatrix = projMatrix;
        }

        @Override
        public void fire(ArrayList<GameRenderListener> listeners) {
            listeners.forEach(e -> e.onGameRender(this));
        }

        @Override
        public Class<GameRenderListener> getListenerType() {
            return GameRenderListener.class;
        }
    }
}
