package dev.i726.rocky.event.events;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.i726.rocky.event.Event;
import dev.i726.rocky.event.Listener;
import java.util.ArrayList;

public interface GameRenderListener extends Listener {
	void onGameRender(GameRenderEvent event);

	class GameRenderEvent extends Event<GameRenderListener> {
		public PoseStack matrices;
		public float delta;

		public GameRenderEvent(PoseStack matrices, float delta) {
			this.matrices = matrices;
			this.delta = delta;
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
