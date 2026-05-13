package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;

import java.util.UUID;

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
        if (mc.getNetworkHandler() == null || mc.player == null) return;
        
        Packet<?> packet = event.packet;
        if (packet instanceof ResourcePackSendS2CPacket resourcePack) {
            event.cancel();
            
            UUID playerId = mc.player.getUuid();
            UUID packId = resourcePack.id();
            
            // Send acceptance with slight delay for realism
            new Thread(() -> {
                try {
                    Thread.sleep(50 + (long)(Math.random() * 100));
                    mc.getNetworkHandler().sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.ACCEPTED));
                    
                    Thread.sleep(100 + (long)(Math.random() * 200));
                    mc.getNetworkHandler().sendPacket(new ResourcePackStatusC2SPacket(packId, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED));
                } catch (InterruptedException ignored) {}
            }).start();
        }
    }
}
