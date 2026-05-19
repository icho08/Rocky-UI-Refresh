package dev.i726.rocky.module.modules.misc;

import com.google.common.collect.Queues;
import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.event.events.PlayerTickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.KeybindSetting;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.KeyUtils;
import dev.i726.rocky.utils.TimerUtils;
import net.minecraft.item.Items;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Queue;

public final class FakeLag extends Module implements PlayerTickListener, PacketReceiveListener, PacketSendListener {

    /** Queued outgoing packets. ConcurrentLinkedQueue is thread-safe for poll/add. */
    public final Queue<Packet<?>> packetQueue = Queues.newConcurrentLinkedQueue();

    /** Guards against re-queuing packets that we are currently flushing. */
    public volatile boolean flushing = false;

    public Vec3d pos = Vec3d.ZERO;
    public TimerUtils timerUtil = new TimerUtils();

    private final MinMaxSetting lagDelay = new MinMaxSetting(
            EncryptedString.of("Lag Delay"), 0, 2000, 1, 100, 300)
            .setDescription(EncryptedString.of("Random ms to hold packets before flushing"));

    private final NumberSetting maxQueueSize = new NumberSetting(
            EncryptedString.of("Max Queue"), 10, 200, 60, 5)
            .setDescription(EncryptedString.of("Force-flush if this many packets accumulate"));

    private final BooleanSetting cancelOnElytra = new BooleanSetting(
            EncryptedString.of("Cancel on Elytra"), false);

    private final KeybindSetting burstKey = new KeybindSetting(
            EncryptedString.of("Burst Key"), -1, false)
            .setDescription(EncryptedString.of("Hold to instantly flush all queued packets"));

    private int delay;

    public FakeLag() {
        super(EncryptedString.of("Fake Lag"),
                EncryptedString.of("Simulates network lag by holding outgoing packets"),
                -1, CategoryManager.NETWORK);
        addSettings(lagDelay, maxQueueSize, cancelOnElytra, burstKey);
    }

    @Override
    public void onEnable() {
        eventManager.add(PlayerTickListener.class, this);
        eventManager.add(PacketSendListener.class, this);
        eventManager.add(PacketReceiveListener.class, this);

        timerUtil.reset();
        if (mc.player != null) pos = mc.player.getEntityPos();
        delay = lagDelay.getRandomValueInt();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PlayerTickListener.class, this);
        eventManager.remove(PacketSendListener.class, this);
        eventManager.remove(PacketReceiveListener.class, this);
        flush();
        super.onDisable();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (mc.world == null || mc.player == null || mc.player.isDead()) return;

        // Always flush immediately on explosion packets
        if (event.packet instanceof ExplosionS2CPacket) flush();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (mc.world == null || mc.player == null) return;
        if (flushing) return;   // don't re-queue packets we're currently flushing
        if (mc.player.isUsingItem() || mc.player.isDead()) return;

        // Interaction or inventory packets break the lag — flush immediately
        if (event.packet instanceof PlayerInteractEntityC2SPacket
                || event.packet instanceof HandSwingC2SPacket
                || event.packet instanceof PlayerInteractBlockC2SPacket
                || event.packet instanceof ClickSlotC2SPacket) {
            flush();
            return;
        }

        if (cancelOnElytra.getValue()
                && mc.player.getInventory().getStack(2 + 36).getItem() == Items.ELYTRA) {
            flush();
            return;
        }

        // Burst key — user is holding flush key
        if (burstKey.getKey() != -1 && KeyUtils.isKeyPressed(burstKey.getKey())) {
            flush();
            return;
        }

        // Max queue guard — prevent unbounded growth
        if (packetQueue.size() >= maxQueueSize.getValueInt()) {
            flush();
            return;
        }

        packetQueue.add(event.packet);
        event.cancel();
    }

    @Override
    public void onPlayerTick() {
        if (mc.player == null || mc.player.isUsingItem()) return;

        if (timerUtil.delay(delay)) {
            flush();
            delay = lagDelay.getRandomValueInt();
        }
    }

    private void flush() {
        if (mc.player == null || mc.world == null) {
            packetQueue.clear();
            return;
        }

        flushing = true;
        Packet<?> pkt;
        while ((pkt = packetQueue.poll()) != null) {
            mc.getNetworkHandler().getConnection().send(pkt, null, false);
        }
        flushing = false;
        timerUtil.reset();
        if (mc.player != null) pos = mc.player.getEntityPos();
    }
}
