package dev.i726.rocky.managers;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.event.events.PostAttackListener;
import dev.i726.rocky.event.events.TickListener;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

public final class CombatManager implements PostAttackListener, TickListener, PacketReceiveListener {

    private static final long COMBAT_TIMEOUT_MS = 5_000;
    private static final float SWING_REACH = 8.0f;

    private final ConcurrentHashMap<UUID, Long> timestamps = new ConcurrentHashMap<>();
    private float lastHealth = -1f;
    private final Minecraft mc = Minecraft.getInstance();

    public CombatManager() {
        Rocky.INSTANCE.getEventManager().add(PostAttackListener.class, this);
        Rocky.INSTANCE.getEventManager().add(TickListener.class, this);
        Rocky.INSTANCE.getEventManager().add(PacketReceiveListener.class, this);
    }

    @Override
    public void onPostAttack(PostAttackEvent event) {
        Entity target = event.getTarget();
        if (target instanceof Player) {
            mark(target.getUUID());
        }
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;

        float hp = mc.player.getHealth();
        if (lastHealth > 0f && hp < lastHealth) {
            mc.level.getEntitiesOfClass(Player.class,
                            mc.player.getBoundingBox().inflate(6.0), e -> e != mc.player)
                    .forEach(p -> mark(p.getUUID()));
        }
        lastHealth = hp;

        long now = System.currentTimeMillis();
        timestamps.entrySet().removeIf(e -> now - e.getValue() > COMBAT_TIMEOUT_MS);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.packet instanceof ClientboundAnimatePacket pkt)) return;
        int anim = pkt.getAction();
        if (anim != 0 && anim != 3) return;

        if (mc.level == null || mc.player == null) return;
        Entity entity = mc.level.getEntity(pkt.getId());
        if (!(entity instanceof Player player) || entity == mc.player) return;
        if (player.distanceTo(mc.player) > SWING_REACH) return;

        mark(player.getUUID());
    }

    public void mark(UUID uuid) {
        timestamps.put(uuid, System.currentTimeMillis());
    }

    public boolean isInCombat(UUID uuid) {
        Long ts = timestamps.get(uuid);
        return ts != null && System.currentTimeMillis() - ts <= COMBAT_TIMEOUT_MS;
    }

    public void reset() {
        timestamps.clear();
        lastHealth = -1f;
    }
}
