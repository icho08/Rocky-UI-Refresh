package dev.i726.rocky.utils.lunar;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Static hooks called by the Lunar-mode Netty packet interceptor and the
 * Lunar HyperEngine polling loop.  These methods must ONLY reference classes
 * that are guaranteed to exist in both Lunar and the JDK — no Fabric /
 * intermediary names allowed here.
 */
public final class LunarHooks {

    // ── Cached reflection handles ─────────────────────────────────────────────

    // net.minecraft.client.Minecraft
    static Class<?> CLS_MC;
    static Method   MC_GET_INSTANCE;
    static Field    MC_PLAYER;
    static Field    MC_LEVEL;
    static Field    MC_CONNECTION; // ClientPacketListener / Connection wrapper

    // net.minecraft.client.player.LocalPlayer / AbstractClientPlayer / Entity
    static Field    PLAYER_FALL_DISTANCE;
    static Field    PLAYER_ON_GROUND;       // fallback — newer has isOnGround()
    static Method   PLAYER_IS_ON_GROUND;
    static Method   PLAYER_IS_IN_FLUID;
    static Method   PLAYER_GET_VELOCITY;
    static Method   PLAYER_SET_SPRINTING;
    static Method   PLAYER_IS_SPRINTING;
    static Field    PLAYER_HURT_TIME;
    static Field    PLAYER_INPUT;           // may be null — checked before use

    // net.minecraft.network.Connection
    static Class<?> CLS_CONNECTION;
    static Method   CONNECTION_SEND;
    static Field    CONNECTION_CHANNEL;     // io.netty.channel.Channel

    // ServerboundMovePlayerPacket hierarchy
    static Class<?> CLS_MOVE_PKT;
    static Field    MOVE_ON_GROUND;
    static Field    MOVE_HAS_POS;
    static Field    MOVE_HAS_ROT;
    static Field    MOVE_X, MOVE_Y, MOVE_Z;
    static Field    MOVE_X_ROT, MOVE_Y_ROT; // pitch, yaw
    static Class<?> CLS_STATUS_ONLY;
    static Class<?> CLS_POS;
    static Class<?> CLS_POS_ROT;
    static Class<?> CLS_ROT;

    // Vec3 (for velocity)
    static Method   VEC3_X, VEC3_Y, VEC3_Z;
    static Class<?> CLS_VEC3;
    static Method   VEC3_CTOR; // Vec3(x,y,z) — used for setVelocity replacement

    // io.netty.channel.Channel
    static Class<?> CLS_CHANNEL;
    static Method   CHANNEL_PIPELINE;

    // ── Module enable flags (set by LunarCompat based on loaded module state) ─
    static volatile boolean noFallEnabled   = false;
    static volatile float   noFallMinDist   = 2.0f;
    static volatile boolean sprintEnabled   = false;
    static volatile boolean velocityEnabled = false;
    static volatile float   velocityH       = 0.0f; // horizontal multiplier
    static volatile float   velocityV       = 0.0f; // vertical multiplier

    // Guard: prevents our replacement send from re-entering this handler
    static volatile boolean bypassing = false;

    // ── Init ──────────────────────────────────────────────────────────────────

    /**
     * Called once by LunarCompat after Minecraft is fully loaded.
     * Caches all reflection handles.  Returns true on success.
     */
    public static boolean init(ClassLoader gameLoader) {
        try {
            CLS_MC          = Class.forName("net.minecraft.client.Minecraft", true, gameLoader);
            MC_GET_INSTANCE = CLS_MC.getMethod("getInstance");

            // player field name may vary — try common Mojang names
            MC_PLAYER = findField(CLS_MC, "player",
                    "net.minecraft.client.player.LocalPlayer");
            MC_LEVEL  = findField(CLS_MC, "level",
                    "net.minecraft.client.multiplayer.ClientLevel");

            // LocalPlayer → Entity ancestry for the fields we need
            Class<?> playerCls = Class.forName(
                    "net.minecraft.client.player.LocalPlayer", true, gameLoader);
            PLAYER_FALL_DISTANCE = findFieldUp(playerCls, "fallDistance");
            PLAYER_HURT_TIME     = findFieldUp(playerCls, "hurtTime");
            PLAYER_IS_ON_GROUND  = findMethodUp(playerCls, "isOnGround");
            PLAYER_IS_IN_FLUID   = findMethodUp(playerCls, "isInFluid",
                    "isInWaterOrBubble", "isInWater");
            PLAYER_SET_SPRINTING = findMethodUp(playerCls, "setSprinting", boolean.class);
            PLAYER_GET_VELOCITY  = findMethodUp(playerCls, "getDeltaMovement",
                    "getVelocity");

            // Input field for sprint direction check (optional — may not exist)
            try { PLAYER_INPUT = findFieldUp(playerCls, "input"); } catch (Throwable ignored) {}

            // Vec3
            CLS_VEC3 = Class.forName("net.minecraft.world.phys.Vec3", true, gameLoader);
            VEC3_X   = CLS_VEC3.getMethod("x");
            VEC3_Y   = CLS_VEC3.getMethod("y");
            VEC3_Z   = CLS_VEC3.getMethod("z");

            // Connection
            CLS_CONNECTION  = Class.forName("net.minecraft.network.Connection", true, gameLoader);
            CONNECTION_SEND = findMethodUp(CLS_CONNECTION, "send",
                    Class.forName("net.minecraft.network.protocol.Packet", true, gameLoader));
            CONNECTION_CHANNEL = findFieldUp(CLS_CONNECTION, "channel");

            // ServerboundMovePlayerPacket
            CLS_MOVE_PKT = Class.forName(
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket",
                    true, gameLoader);
            MOVE_ON_GROUND = findFieldUp(CLS_MOVE_PKT, "onGround");
            MOVE_HAS_POS   = findFieldUp(CLS_MOVE_PKT, "hasPos");
            MOVE_HAS_ROT   = findFieldUp(CLS_MOVE_PKT, "hasRot");
            tryCache(() -> MOVE_X     = findFieldUp(CLS_MOVE_PKT, "x"));
            tryCache(() -> MOVE_Y     = findFieldUp(CLS_MOVE_PKT, "y"));
            tryCache(() -> MOVE_Z     = findFieldUp(CLS_MOVE_PKT, "z"));
            tryCache(() -> MOVE_X_ROT = findFieldUp(CLS_MOVE_PKT, "xRot"));
            tryCache(() -> MOVE_Y_ROT = findFieldUp(CLS_MOVE_PKT, "yRot"));

            CLS_STATUS_ONLY = Class.forName(
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$StatusOnly",
                    true, gameLoader);
            tryCache(() -> CLS_POS    = Class.forName(
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Pos",
                    true, gameLoader));
            tryCache(() -> CLS_POS_ROT = Class.forName(
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$PosRot",
                    true, gameLoader));
            tryCache(() -> CLS_ROT    = Class.forName(
                    "net.minecraft.network.protocol.game.ServerboundMovePlayerPacket$Rot",
                    true, gameLoader));

            System.out.println("[Rocky/Lunar] Reflection cache ready.");
            return true;
        } catch (Throwable t) {
            System.err.println("[Rocky/Lunar] Reflection init failed: " + t);
            t.printStackTrace();
            return false;
        }
    }

    // ── Netty outbound handler ────────────────────────────────────────────────

    /**
     * Creates and returns the Netty handler that should be inserted at the
     * FRONT of the game's packet pipeline.
     */
    public static ChannelOutboundHandlerAdapter createPacketInterceptor() {
        return new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg,
                              ChannelPromise promise) throws Exception {
                if (!bypassing && CLS_MOVE_PKT != null
                        && CLS_MOVE_PKT.isInstance(msg)) {
                    Object replacement = handleMovePkt(msg);
                    if (replacement != null) {
                        msg = replacement;
                    }
                }
                super.write(ctx, msg, promise);
            }
        };
    }

    // ── NoFall packet logic ───────────────────────────────────────────────────

    private static Object handleMovePkt(Object pkt) {
        if (!noFallEnabled) return null;
        try {
            Object mc = MC_GET_INSTANCE.invoke(null);
            if (mc == null) return null;
            Object player = MC_PLAYER.get(mc);
            if (player == null) return null;

            // Skip if on ground or in fluid or ascending
            if ((boolean) PLAYER_IS_ON_GROUND.invoke(player)) return null;
            if (PLAYER_IS_IN_FLUID != null && (boolean) PLAYER_IS_IN_FLUID.invoke(player)) return null;

            // Check Y velocity
            Object vel = PLAYER_GET_VELOCITY.invoke(player);
            if (vel != null) {
                double vy = (double) VEC3_Y.invoke(vel);
                if (vy >= 0) return null; // ascending
            }

            float fallDist = PLAYER_FALL_DISTANCE.getFloat(player);
            if (fallDist < noFallMinDist) return null;

            // Build replacement with onGround = true
            return buildGroundPacket(pkt);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object buildGroundPacket(Object pkt) {
        try {
            boolean hasPos = MOVE_HAS_POS.getBoolean(pkt);
            boolean hasRot = MOVE_HAS_ROT.getBoolean(pkt);

            // Strategy 1: just flip the onGround field if accessible
            try {
                MOVE_ON_GROUND.setBoolean(pkt, true);
                return null; // mutated in-place — no replacement needed
            } catch (Throwable ignored) {}

            // Strategy 2: construct a new packet of matching type
            if (hasPos && hasRot && CLS_POS_ROT != null && MOVE_X != null) {
                double x = MOVE_X.getDouble(pkt), y = MOVE_Y.getDouble(pkt),
                       z = MOVE_Z.getDouble(pkt);
                float xr = MOVE_X_ROT.getFloat(pkt), yr = MOVE_Y_ROT.getFloat(pkt);
                return CLS_POS_ROT.getDeclaredConstructors()[0]
                        .newInstance(x, y, z, yr, xr, true);
            } else if (hasPos && CLS_POS != null && MOVE_X != null) {
                double x = MOVE_X.getDouble(pkt), y = MOVE_Y.getDouble(pkt),
                       z = MOVE_Z.getDouble(pkt);
                return CLS_POS.getDeclaredConstructors()[0].newInstance(x, y, z, true);
            } else if (hasRot && CLS_ROT != null && MOVE_X_ROT != null) {
                float xr = MOVE_X_ROT.getFloat(pkt), yr = MOVE_Y_ROT.getFloat(pkt);
                return CLS_ROT.getDeclaredConstructors()[0].newInstance(yr, xr, true);
            } else {
                return CLS_STATUS_ONLY.getDeclaredConstructors()[0].newInstance(true);
            }
        } catch (Throwable t) {
            return null;
        }
    }

    // ── Sprint / Velocity tick logic ──────────────────────────────────────────

    /**
     * Called from the Lunar HyperEngine thread every ~50 ms on the MC thread.
     */
    public static void onClientTick(Object mc) {
        try {
            Object player = MC_PLAYER.get(mc);
            if (player == null) return;

            // Sprint
            if (sprintEnabled) {
                boolean forward = isForwardHeld(mc);
                if (forward) PLAYER_SET_SPRINTING.invoke(player, true);
            }

            // Velocity reduction on hurt
            if (velocityEnabled) {
                int ht = PLAYER_HURT_TIME.getInt(player);
                if (ht > 0 && PLAYER_GET_VELOCITY != null) {
                    Object vel = PLAYER_GET_VELOCITY.invoke(player);
                    if (vel != null) {
                        double vx = (double) VEC3_X.invoke(vel);
                        double vy = (double) VEC3_Y.invoke(vel);
                        double vz = (double) VEC3_Z.invoke(vel);
                        setVelocity(player, vx * velocityH, vy * velocityV, vz * velocityH);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static boolean isForwardHeld(Object mc) {
        try {
            // Try Options → forwardKey → isDown()
            Object options = CLS_MC.getField("options").get(mc);
            Object forwardKey = options.getClass().getField("keyUp").get(options);
            return (boolean) forwardKey.getClass().getMethod("isDown").invoke(forwardKey);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void setVelocity(Object player, double x, double y, double z) {
        try {
            Object vec = CLS_VEC3.getConstructor(double.class, double.class, double.class)
                    .newInstance(x, y, z);
            player.getClass().getMethod("setDeltaMovement",
                    Class.forName("net.minecraft.world.phys.Vec3")).invoke(player, vec);
        } catch (Throwable ignored) {}
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    /** Find a field by name, searching the class hierarchy. */
    static Field findFieldUp(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(name);
    }

    /** Find a field by type (when name is unknown), walking the hierarchy. */
    static Field findField(Class<?> cls, String preferredName, String typeName) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.getName().equals(preferredName)
                        || f.getType().getName().equals(typeName)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        }
        return null;
    }

    /** Find a no-arg method by any of the provided candidate names. */
    static Method findMethodUp(Class<?> cls, String... names) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            for (String name : names) {
                try {
                    Method m = c.getDeclaredMethod(name);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {}
            }
        }
        return null;
    }

    /** Find a single-arg method. */
    static Method findMethodUp(Class<?> cls, String name, Class<?> param) {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, param);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }

    @FunctionalInterface
    interface Cacher { void run() throws Throwable; }

    static void tryCache(Cacher c) {
        try { c.run(); } catch (Throwable ignored) {}
    }
}
