package dev.i726.rocky.event.events;

import dev.i726.rocky.event.CancellableEvent;
import dev.i726.rocky.event.Listener;
import java.util.ArrayList;
import net.minecraft.world.entity.Entity;

public interface AttackListener extends Listener {
	void onAttack(AttackEvent event);

	class AttackEvent extends CancellableEvent<AttackListener> {
		private final Entity target;

		public AttackEvent(Entity target) {
			this.target = target;
		}

		public Entity getTarget() {
			return target;
		}

		@Override
		public void fire(ArrayList<AttackListener> listeners) {
			listeners.forEach(e -> e.onAttack(this));
		}

		@Override
		public Class<AttackListener> getListenerType() {
			return AttackListener.class;
		}
	}
}
