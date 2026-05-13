package dev.i726.rocky.event.events;

import dev.i726.rocky.event.CancellableEvent;
import dev.i726.rocky.event.Listener;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;


public interface PacketSendListener extends Listener {
	void onPacketSend(PacketSendEvent event);

	class PacketSendEvent extends CancellableEvent<PacketSendListener> {
		public Packet packet;
		public final net.minecraft.network.ClientConnection connection;

		public PacketSendEvent(Packet packet, net.minecraft.network.ClientConnection connection) {
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
