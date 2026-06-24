package dev.i726.rocky.event.events;

import dev.i726.rocky.event.CancellableEvent;
import dev.i726.rocky.event.Listener;
import java.util.ArrayList;
import net.minecraft.network.protocol.Packet;


public interface PacketSendListener extends Listener {
	void onPacketSend(PacketSendEvent event);

	class PacketSendEvent extends CancellableEvent<PacketSendListener> {
		public Packet packet;
		public final net.minecraft.network.Connection connection;

		public PacketSendEvent(Packet packet, net.minecraft.network.Connection connection) {
			this.packet = packet;
			this.connection = connection;
		}

		@Override
		public void fire(ArrayList<PacketSendListener> listeners) {
			listeners.forEach(e -> e.onPacketSend(this));
		}

		@Override
		public Class<PacketSendListener> getListenerType() {
			return PacketSendListener.class;
		}
	}
}
