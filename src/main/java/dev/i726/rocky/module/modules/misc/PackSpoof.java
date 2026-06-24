package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

public class PackSpoof extends Module implements PacketReceiveListener {
    public PackSpoof() {
        super(EncryptedString.of("Pack Spoof"),
                EncryptedString.of("Spoofs resource pack status"), -1, CategoryManager.NETWORK);
    }

    @Override
    public void onEnable() {
        eventManager.add(PacketReceiveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PacketReceiveListener.class, this);
        super.onDisable();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.getConnection() == null || mc.player == null) return;
        
        Packet<?> packet = event.packet;
        if (packet instanceof ClientboundResourcePackPushPacket resourcePack) {
            event.cancel();
            
            UUID playerId = mc.player.getUUID();
            UUID packId = resourcePack.id();
            
            // Send acceptance with slight delay for realism
            new Thread(() -> {
                try {
                    Thread.sleep(50 + (long)(Math.random() * 100));
                    mc.getConnection().send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.ACCEPTED));
                    
                    Thread.sleep(100 + (long)(Math.random() * 200));
                    mc.getConnection().send(new ServerboundResourcePackPacket(packId, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }
}
