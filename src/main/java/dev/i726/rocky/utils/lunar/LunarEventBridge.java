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

    // Reflection cache
    private static Field itemUseCooldownField;
    private static boolean hooksInitialized = false;

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
        
        // Start the frame-based loop for rendering-intensive hooks (Chams/ESP)
        scheduler.schedule(LunarEventBridge::runFrameLoop, 3, TimeUnit.SECONDS);
    }

    private static void runFrameLoop() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || dev.i726.rocky.module.modules.client.SelfDestruct.destruct) return;

            mc.execute(() -> {
                try {
                    if (mc.player != null && mc.world != null) {
                        handleRenderHooks(mc);
                    }
                } catch (Throwable ignored) {}
                
                // Reschedule for next frame
                if (!dev.i726.rocky.module.modules.client.SelfDestruct.destruct) {
                    mc.execute(LunarEventBridge::runFrameLoop);
                }
            });
        } catch (Throwable ignored) {}
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

                    // Initialize hooks on first valid tick
                    if (!hooksInitialized) {
                        setupHooks(mc);
                        hooksInitialized = true;
                    }

                    // Maintain the Netty packet hook
                    ensureHooked(mc);

                    // Fire tick events (replace ClientPlayerEntityMixin)
                    EventManager.fire(new TickListener.TickEvent());
                    EventManager.fire(new PlayerTickListener.PlayerTickEvent());
                    EventManager.fire(new MovementPacketListener.MovementPacketEvent());

                    // Bridge FastUse and AutoTool
                    handleFastUse(mc);
                    handleAutoTool(mc);

                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private static void handleFastUse(MinecraftClient mc) {
        if (dev.i726.rocky.Rocky.INSTANCE == null) return;
        var fastUse = dev.i726.rocky.Rocky.INSTANCE.getModuleManager().getModule(dev.i726.rocky.module.modules.misc.FastUse.class);
        if (fastUse != null && fastUse.isEnabled() && itemUseCooldownField != null) {
            try {
                int current = itemUseCooldownField.getInt(mc);
                // Only override if we are actually on a cooldown (usually 4)
                if (current > 0) {
                    int mainCooldown = fastUse.getItemUseCooldown(mc.player.getMainHandStack());
                    int offCooldown = fastUse.getItemUseCooldown(mc.player.getOffHandStack());
                    int target = Math.min(mainCooldown, offCooldown);
                    if (target < current) {
                        itemUseCooldownField.setInt(mc, target);
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private static void handleAutoTool(MinecraftClient mc) {
        // Only fire block breaking events if we are actually looking at a block
        // This prevents 'auto slot swapping' while attacking players or air
        if (mc.options.attackKey.isPressed() && mc.crosshairTarget != null && 
            mc.crosshairTarget.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
            EventManager.fire(new dev.i726.rocky.event.events.BlockBreakingListener.BlockBreakingEvent());
        }
    }

    private static void handleRenderHooks(MinecraftClient mc) {
        if (dev.i726.rocky.Rocky.INSTANCE == null || mc.world == null) return;
        
        var chams = dev.i726.rocky.Rocky.INSTANCE.getModuleManager().getModule(dev.i726.rocky.module.modules.render.Chams.class);
        var esp = dev.i726.rocky.Rocky.INSTANCE.getModuleManager().getModule(dev.i726.rocky.module.modules.render.PlayerESP.class);
        
        boolean chamsOn = (chams != null && chams.isEnabled());
        boolean espGlow = false;
        if (esp != null && esp.isEnabled()) {
            espGlow = esp.getSettings().stream()
                .filter(s -> s.getName().toString().equals("Glow"))
                .filter(s -> s instanceof dev.i726.rocky.module.setting.BooleanSetting)
                .map(s -> ((dev.i726.rocky.module.setting.BooleanSetting) s).getValue())
                .findFirst()
                .orElse(false);
        }

        boolean shouldGlow = chamsOn || espGlow;

        // Use a more robust entity iterator
        for (net.minecraft.entity.Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof net.minecraft.entity.player.PlayerEntity player) || player == mc.player) continue;
            
            // method_5834 is setGlowing
            if (shouldGlow) {
                if (!player.isGlowing()) player.setGlowing(true);
            } else {
                // Only unset if we were the ones who set it (and they don't have the effect)
                if (player.isGlowing() && !player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.GLOWING)) {
                    player.setGlowing(false);
                }
            }
        }
    }

    private static void setupHooks(MinecraftClient mc) {
        try {
            // 1. FastUse reflection (field_1752 is itemUseCooldown)
            try {
                itemUseCooldownField = MinecraftClient.class.getDeclaredField("field_1752");
                itemUseCooldownField.setAccessible(true);
            } catch (Exception e) {
                // Try to find by name if mappings differ
                for (Field f : MinecraftClient.class.getDeclaredFields()) {
                    if (f.getType() == int.class && (f.getName().equals("itemUseCooldown") || f.getName().equals("field_1752"))) {
                        itemUseCooldownField = f;
                        f.setAccessible(true);
                        break;
                    }
                }
            }

            // 2. Keyboard callback hook
            long window = mc.getWindow().getHandle();
            org.lwjgl.glfw.GLFWKeyCallback oldKeyCallback = org.lwjgl.glfw.GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
                // Fire Rocky key event
                if (action == 1) { // GLFW_PRESS
                    // GUI Toggle (Right Shift)
                    if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
                        mc.execute(() -> {
                            if (mc.currentScreen instanceof dev.i726.rocky.gui.ClickGuiScreen) mc.setScreen(null);
                            else mc.setScreen(new dev.i726.rocky.gui.ClickGuiScreen());
                        });
                    }

                    EventManager.fire(new dev.i726.rocky.event.events.ButtonListener.ButtonEvent(key, win, action));
                }
                
                // Call original Lunar/MC callback if it existed
                // (Note: we don't need to recursively call ourself here)
            });

            // If there was already a callback, we MUST chain it
            if (oldKeyCallback != null) {
                org.lwjgl.glfw.GLFW.glfwSetKeyCallback(window, (win, key, scancode, action, mods) -> {
                    // Our logic
                    if (action == 1) {
                        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) {
                            mc.execute(() -> {
                                if (mc.currentScreen instanceof dev.i726.rocky.gui.ClickGuiScreen) mc.setScreen(null);
                                else mc.setScreen(new dev.i726.rocky.gui.ClickGuiScreen());
                            });
                        }
                        EventManager.fire(new dev.i726.rocky.event.events.ButtonListener.ButtonEvent(key, win, action));
                    }
                    // Lunar's logic
                    oldKeyCallback.invoke(win, key, scancode, action, mods);
                });
            }

        } catch (Throwable t) {
            System.err.println("[Rocky/Lunar] Hook setup failed: " + t.getMessage());
        }
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
