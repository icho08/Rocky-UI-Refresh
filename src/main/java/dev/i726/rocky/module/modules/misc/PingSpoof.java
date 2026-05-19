package dev.i726.rocky.module.modules.misc;

import dev.i726.rocky.event.events.PacketReceiveListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.MinMaxSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.network.packet.c2s.common.KeepAliveC2SPacket;
import net.minecraft.network.packet.s2c.common.KeepAliveS2CPacket;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PingSpoof extends Module implements PacketReceiveListener {

    private final MinMaxSetting ping = new MinMaxSetting(
            EncryptedString.of("Ping"), 0, 1000, 1, 0, 600)
            .setDescription(EncryptedString.of("Simulated ping range in milliseconds"));

    /** Single-thread scheduler — reused for all KeepAlive responses. */
    private ScheduledExecutorService scheduler;

    /** Track pending tasks so we can cancel them on disable. */
    private final CopyOnWriteArrayList<ScheduledFuture<?>> pendingTasks = new CopyOnWriteArrayList<>();

    public PingSpoof() {
        super(EncryptedString.of("Ping Spoof"),
                EncryptedString.of("Spoofs your ping by delaying KeepAlive responses"),
                -1, CategoryManager.NETWORK);
        addSettings(ping);
    }

    @Override
    public void onEnable() {
        // Use a ScheduledThreadPoolExecutor so tasks can be individually cancelled
        ScheduledThreadPoolExecutor exec = new ScheduledThreadPoolExecutor(1);
        exec.setRemoveOnCancelPolicy(true);
        exec.setThreadFactory(r -> {
            Thread t = new Thread(r, "PingSpoof-Scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler = exec;
        pendingTasks.clear();

        eventManager.add(PacketReceiveListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PacketReceiveListener.class, this);

        // Cancel all pending KeepAlive responses so they don't fire after disable
        for (ScheduledFuture<?> task : pendingTasks) task.cancel(false);
        pendingTasks.clear();

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        super.onDisable();
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (!(event.packet instanceof KeepAliveS2CPacket packet)) return;
        if (scheduler == null || scheduler.isShutdown()) return;

        long id         = packet.getId();
        int  delayMs    = ping.getRandomValueInt();

        ScheduledFuture<?>[] ref = new ScheduledFuture<?>[1];
        ref[0] = scheduler.schedule(() -> {
            try {
                if (!isEnabled() || mc.getNetworkHandler() == null) return;
                mc.getNetworkHandler().getConnection().send(new KeepAliveC2SPacket(id), null, false);
            } finally {
                pendingTasks.remove(ref[0]);
            }
        }, delayMs, TimeUnit.MILLISECONDS);

        pendingTasks.add(ref[0]);
        event.cancel(); // suppress the vanilla immediate response
    }
}
