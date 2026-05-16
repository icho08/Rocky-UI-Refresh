package dev.i726.rocky.utils.lunar;

import dev.i726.rocky.event.EventManager;
import dev.i726.rocky.event.events.MovementPacketListener;
import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.event.events.PacketSendListener.PacketSendEvent;
import dev.i726.rocky.event.events.PlayerTickListener;
import dev.i726.rocky.event.events.TickListener;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Bridges Rocky's event system into Lunar Client.
 *
 * Rocky's Mixins never register when loaded as a -javaagent because Rocky
 * isn't in Lunar's mods/ directory — so ClientConnectionMixin,
 * ClientPlayerEntityMixin, etc. are dead code.  This class replaces them by:
 *
 *   • Adding a Netty ChannelOutboundHandlerAdapter to fire PacketSendEvent
 *     for every outgoing packet (replaces ClientConnectionMixin)
 *
 *   • Scheduling TickEvent + PlayerTickEvent on the MC main thread every
 *     50 ms (replaces ClientPlayerEntityMixin @tick inject)
 *
 * Compiled by Fabric Loom → class names are Fabric intermediary → same names
 * Lunar uses at runtime.  No Mojang-mapped reflection needed here.
 */
public final class LunarEventBridge {

    private static final String HANDLER_NAME = "rocky_event_bridge";

    private static ScheduledExecutorService scheduler;
    private static volatile Channel activeChannel;

    // ── Public API ────────────────────────────────────────────────────────────

    /** Call once after AgentTarget.init() completes in Lunar mode. */
    public static void setup() {
        System.out.println("[Rocky/Lunar] Starting event bridge...");
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Rocky-Lunar-Bridge");
            t.setDaemon(true);
            return t;
        });
        // 50 ms = 1 MC tick period; initial delay 2 s to let MC/Genesis finish loading
        scheduler.scheduleAtFixedRate(LunarEventBridge::tick, 2000, 50, TimeUnit.MILLISECONDS);
    }

    // ── Per-tick work (runs on the scheduler thread, then dispatches to MC) ──

    private static void tick() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) return;

            // All gameplay logic must run on the MC main thread
            mc.execute(() -> {
                try {
                    if (mc.player == null) return;

                    // Maintain the Netty packet hook
                    ensureHooked(mc);

                    // Fire tick events (replace ClientPlayerEntityMixin)
                    EventManager.fire(new TickListener.TickEvent());
                    EventManager.fire(new PlayerTickListener.PlayerTickEvent());
                    EventManager.fire(new MovementPacketListener.MovementPacketEvent());

                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    // ── Netty hook lifecycle ──────────────────────────────────────────────────

    /**
     * Checks if the hook is still in place (channel may close on server change)
     * and (re-)installs it if needed.
     */
    private static void ensureHooked(MinecraftClient mc) {
        // If we had a channel and it closed, clear the reference so we re-hook
        if (activeChannel != null && !activeChannel.isOpen()) {
            activeChannel = null;
        }
        if (activeChannel != null) return; // already hooked on an open channel

        ClientConnection conn = findConnection(mc);
        if (conn == null) return;

        Channel ch = findChannel(conn);
        if (ch == null || !ch.isOpen()) return;

        // Guard: don't add twice on the same channel object
        if (ch.pipeline().get(HANDLER_NAME) != null) {
            activeChannel = ch;
            return;
        }

        // Build the handler with a reference to the connection so NoFall can
        // call event.connection.send(replacement) correctly
        ch.pipeline().addFirst(HANDLER_NAME, new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise)
                    throws Exception {
                if (msg instanceof Packet<?> pkt) {
                    PacketSendEvent event = new PacketSendEvent(pkt, conn);
                    EventManager.fire(event);

                    if (event.isCancelled()) {
                        // Silently drop — NoFall has already sent the replacement
                        promise.setSuccess();
                        return;
                    }
                    // Allow listeners to swap the packet (event.packet is mutable)
                    msg = event.packet;
                }
                super.write(ctx, msg, promise);
            }
        });

        activeChannel = ch;
        System.out.println("[Rocky/Lunar] Event bridge hooked into Netty pipeline.");
    }

    // ── Reflection helpers — find Connection and Channel ─────────────────────

    /**
     * Finds the active {@link ClientConnection} from the player's network handler.
     * Scans declared fields by type rather than name so it survives intermediary
     * name changes between MC versions.
     */
    private static ClientConnection findConnection(MinecraftClient mc) {
        if (mc.player == null) return null;
        Object handler = mc.player.networkHandler;
        if (handler == null) return null;

        for (Class<?> c = handler.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ClientConnection.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (ClientConnection) f.get(handler);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    /**
     * Extracts the Netty {@link Channel} from a {@link ClientConnection} by
     * scanning for the first field whose type is assignable to {@code Channel}.
     */
    private static Channel findChannel(ClientConnection conn) {
        for (Class<?> c = conn.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Channel.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (Channel) f.get(conn);
                    } catch (Throwable ignored) {}
                }
            }
        }
        return null;
    }

    /** Cleans up on JVM exit / when no longer needed. */
    public static void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
        if (activeChannel != null && activeChannel.pipeline().get(HANDLER_NAME) != null) {
            activeChannel.pipeline().remove(HANDLER_NAME);
        }
        activeChannel = null;
    }
}
