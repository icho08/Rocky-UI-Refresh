package dev.i726.rocky.utils.lunar;

import io.netty.channel.Channel;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Lunar Client compatibility layer.
 *
 * Lunar uses Mojang-mapped class names (net.minecraft.client.Minecraft, etc.)
 * whereas Rocky's compiled bytecode uses Fabric intermediary names.  Directly
 * touching any Minecraft API from Rocky code would throw NoClassDefFoundError
 * inside Lunar.
 *
 * This class and LunarHooks use ONLY reflection / Class.forName so they stay
 * mapping-agnostic.  Once the Netty channel hook is in place the packet
 * pipeline works exactly like the Fabric ClientConnectionMixin would.
 */
public final class LunarCompat {

    private static volatile boolean lunar = false;
    private static volatile boolean initialized = false;
    private static ClassLoader gameLoader;

    // ── Detection ─────────────────────────────────────────────────────────────

    /**
     * Returns true if we are running inside Lunar Client (Mojang-mapped MC).
     * Must be called before any Rocky code that references Minecraft classes.
     */
    public static boolean detect(ClassLoader loader) {
        try {
            // Lunar uses net.minecraft.client.Minecraft; Fabric uses class_310
            Class.forName("net.minecraft.client.Minecraft", false, loader);
            // Extra confirmation: Lunar ships its own classes
            try {
                Class.forName("com.moonsworth.lunar.client.LunarClient", false, loader);
                System.out.println("[Rocky] Detected Lunar Client (LunarClient class present).");
            } catch (Throwable ignored) {
                System.out.println("[Rocky] Detected Mojang-mapped Minecraft (likely Lunar / vanilla launcher).");
            }
            // Confirm Fabric intermediary is NOT present
            try {
                Class.forName("net.minecraft.class_310", false, loader);
                // If this succeeds we're on Fabric, not Lunar
                System.out.println("[Rocky] Fabric intermediary found — not Lunar mode.");
                return false;
            } catch (ClassNotFoundException ok) { /* expected in Lunar */ }

            lunar = true;
            gameLoader = loader;
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isLunar() { return lunar; }

    // ── Entry point called from StandaloneBootstrap.agentmain ─────────────────

    /**
     * Initialises the Lunar compatibility engine.  Blocks until Minecraft is
     * ready, then hooks the Netty packet pipeline and starts the feature loop.
     */
    public static void init(Instrumentation inst, ClassLoader loader) {
        gameLoader = loader;
        System.out.println("[Rocky/Lunar] Starting Lunar compatibility engine...");

        new Thread(() -> {
            try {
                Object mc = waitForMC();
                if (mc == null) {
                    System.err.println("[Rocky/Lunar] Timed out waiting for Minecraft instance.");
                    return;
                }

                // Cache all reflection handles
                if (!LunarHooks.init(gameLoader)) {
                    System.err.println("[Rocky/Lunar] Reflection init failed — aborting.");
                    return;
                }

                // Hook the Netty pipeline for packet interception
                hookNetty(mc);

                // Sync module state from config / module toggles
                syncModuleState();

                // Start the lightweight feature polling loop
                startFeatureLoop(mc);

                initialized = true;
                System.out.println("[Rocky/Lunar] Lunar compatibility engine active.");
                System.out.println("[Rocky/Lunar] Modules: NoFall=" + LunarHooks.noFallEnabled
                        + "  Sprint=" + LunarHooks.sprintEnabled
                        + "  Velocity=" + LunarHooks.velocityEnabled);
            } catch (Throwable t) {
                System.err.println("[Rocky/Lunar] Fatal error: " + t);
                t.printStackTrace();
            }
        }, "Rocky-Lunar-Init").start();
    }

    // ── Wait for Minecraft ────────────────────────────────────────────────────

    private static Object waitForMC() throws Exception {
        Class<?> cls = Class.forName("net.minecraft.client.Minecraft", true, gameLoader);
        Method getInstance = cls.getMethod("getInstance");

        for (int i = 0; i < 200; i++) {
            try {
                Object mc = getInstance.invoke(null);
                if (mc != null) {
                    // Also wait for the window/display to be up
                    try {
                        Method getWindow = cls.getMethod("getWindow");
                        if (getWindow.invoke(mc) != null) return mc;
                    } catch (Throwable ignored) {
                        return mc; // no getWindow() — just return the instance
                    }
                }
            } catch (Throwable ignored) {}
            Thread.sleep(500);
        }
        return null;
    }

    // ── Netty hook ────────────────────────────────────────────────────────────

    /**
     * Finds the active Netty channel via the Connection object and prepends
     * Rocky's outbound packet interceptor to the pipeline.
     */
    private static void hookNetty(Object mc) {
        try {
            // Wait until the player is in a world (connection established)
            Object connection = findConnection(mc);
            for (int i = 0; i < 120 && connection == null; i++) {
                Thread.sleep(500);
                connection = findConnection(mc);
            }
            if (connection == null) {
                System.err.println("[Rocky/Lunar] Could not find active Connection — packet hook skipped.");
                return;
            }

            Field channelField = LunarHooks.findFieldUp(connection.getClass(), "channel");
            Channel channel = (Channel) channelField.get(connection);
            if (channel == null) {
                System.err.println("[Rocky/Lunar] Netty channel is null.");
                return;
            }

            // Only add once
            if (channel.pipeline().get("rocky_intercept") == null) {
                channel.pipeline().addFirst("rocky_intercept",
                        LunarHooks.createPacketInterceptor());
                System.out.println("[Rocky/Lunar] Netty packet interceptor installed.");
            }
        } catch (Throwable t) {
            System.err.println("[Rocky/Lunar] Netty hook failed: " + t);
            t.printStackTrace();
        }
    }

    /**
     * Re-hooks the Netty pipeline when the player changes server/world.
     * Called from the feature loop when we detect a new connection.
     */
    private static void rehookIfNeeded(Object mc) {
        try {
            Object conn = findConnection(mc);
            if (conn == null) return;
            Field channelField = LunarHooks.CONNECTION_CHANNEL != null
                    ? LunarHooks.CONNECTION_CHANNEL
                    : LunarHooks.findFieldUp(conn.getClass(), "channel");
            Channel ch = (Channel) channelField.get(conn);
            if (ch != null && ch.pipeline().get("rocky_intercept") == null) {
                ch.pipeline().addFirst("rocky_intercept",
                        LunarHooks.createPacketInterceptor());
                System.out.println("[Rocky/Lunar] Re-hooked Netty pipeline for new connection.");
            }
        } catch (Throwable ignored) {}
    }

    /** Locates the Connection object from the MC instance via reflection. */
    private static Object findConnection(Object mc) {
        // Approach 1: MC.getConnection() or MC.connection field
        try {
            Class<?> cls = mc.getClass();
            for (Method m : cls.getMethods()) {
                if ((m.getName().equals("getConnection") || m.getName().equals("getSingleplayerServer"))
                        && m.getParameterCount() == 0) {
                    Object result = m.invoke(mc);
                    if (result != null && result.getClass().getName().contains("Connection")) {
                        return result;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Approach 2: scan declared fields for a Connection type
        try {
            for (Class<?> c = mc.getClass(); c != null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().getName().contains("Connection")) {
                        f.setAccessible(true);
                        Object val = f.get(mc);
                        if (val != null) return val;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Approach 3: via player → connection field
        try {
            if (LunarHooks.MC_PLAYER != null && LunarHooks.CLS_CONNECTION != null) {
                Object player = LunarHooks.MC_PLAYER.get(mc);
                if (player != null) {
                    for (Class<?> c = player.getClass(); c != null; c = c.getSuperclass()) {
                        for (Field f : c.getDeclaredFields()) {
                            if (f.getType().getName().contains("Connection")) {
                                f.setAccessible(true);
                                Object val = f.get(player);
                                if (val != null) return val;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    // ── Feature polling loop ──────────────────────────────────────────────────

    private static void startFeatureLoop(Object mc) {
        new Thread(() -> {
            int rehookTimer = 0;
            while (true) {
                try {
                    Thread.sleep(50); // ~20 ticks/sec
                    Object player = LunarHooks.MC_PLAYER != null
                            ? LunarHooks.MC_PLAYER.get(mc) : null;
                    if (player == null) continue;

                    // Run tick-based module logic (sprint, velocity)
                    LunarHooks.onClientTick(mc);

                    // Periodically re-hook in case player changed server
                    if (++rehookTimer >= 100) {
                        rehookTimer = 0;
                        rehookIfNeeded(mc);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Throwable ignored) {}
            }
        }, "Rocky-Lunar-Engine").start();
    }

    // ── Module state sync ─────────────────────────────────────────────────────

    /**
     * Reads module on/off states from a simple config file (same as Rocky's
     * existing profile system) and sets the LunarHooks flags accordingly.
     *
     * Falls back to sensible defaults if the profile can't be read.
     */
    private static void syncModuleState() {
        // Default-on modules that are safe and commonly wanted
        LunarHooks.noFallEnabled   = readFlag("No Fall",     true);
        LunarHooks.noFallMinDist   = 2.0f;
        LunarHooks.sprintEnabled   = readFlag("Auto Sprint", true);
        LunarHooks.velocityEnabled = readFlag("Velocity",    false);
        LunarHooks.velocityH       = 0.0f;
        LunarHooks.velocityV       = 1.0f;
    }

    private static boolean readFlag(String name, boolean def) {
        // Simple approach: look for a saved profile JSON with the module state.
        // Rocky saves profiles to ~/.rocky/profiles/default.json
        try {
            java.io.File profile = new java.io.File(
                    System.getProperty("user.home"), ".rocky/profiles/default.json");
            if (!profile.exists()) return def;
            String json = new String(java.nio.file.Files.readAllBytes(profile.toPath()));
            // Pattern: "Name": { ... "enabled": true ... }
            int idx = json.indexOf("\"" + name + "\"");
            if (idx < 0) return def;
            int enabledIdx = json.indexOf("\"enabled\"", idx);
            if (enabledIdx < 0 || enabledIdx - idx > 300) return def;
            int colonIdx = json.indexOf(":", enabledIdx);
            if (colonIdx < 0) return def;
            String rest = json.substring(colonIdx + 1).trim();
            return rest.startsWith("true");
        } catch (Throwable ignored) {
            return def;
        }
    }

    // ── Toggle API (can be called via reflection from a GUI or chat command) ──

    public static void setNoFall(boolean enabled)   { LunarHooks.noFallEnabled   = enabled; }
    public static void setSprint(boolean enabled)   { LunarHooks.sprintEnabled   = enabled; }
    public static void setVelocity(boolean enabled, float h, float v) {
        LunarHooks.velocityEnabled = enabled;
        LunarHooks.velocityH = h;
        LunarHooks.velocityV = v;
    }
}
