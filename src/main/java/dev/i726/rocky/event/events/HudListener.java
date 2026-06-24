package dev.i726.rocky.event.events;

import dev.i726.rocky.event.Event;
import dev.i726.rocky.event.Listener;
import java.util.ArrayList;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface HudListener extends Listener {
	void onRenderHud(HudEvent event);

	class HudEvent extends Event<HudListener> {
		public GuiGraphicsExtractor context;
		public float delta;

		public HudEvent(GuiGraphicsExtractor context, float delta) {
			this.context = context;
			this.delta = delta;
		}

		@Override
		public void fire(ArrayList<HudListener> listeners) {
			listeners.forEach(e -> e.onRenderHud(this));
		}

		@Override
		public Class<HudListener> getListenerType() {
			return HudListener.class;
		}
	}
}