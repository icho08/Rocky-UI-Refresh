package dev.i726.rocky.event.events;

import dev.i726.rocky.event.Event;
import dev.i726.rocky.event.Listener;
import java.util.ArrayList;
import net.minecraft.world.entity.Entity;

public interface PostAttackListener extends Listener {
	void onPostAttack(PostAttackEvent event);

	class PostAttackEvent extends Event<PostAttackListener> {
		private final Entity target;

		public PostAttackEvent(Entity target) {
			this.target = target;
		}

		public Entity getTarget() {
			return target;
		}

		@Override
		public void fire(ArrayList<PostAttackListener> listeners) {
			listeners.forEach(e -> e.onPostAttack(this));
		}

		@Override
		public Class<PostAttackListener> getListenerType() {
			return PostAttackListener.class;
		}
	}
}
