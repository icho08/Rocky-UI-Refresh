package dev.i726.rocky.module.modules.movement;

import dev.i726.rocky.event.events.PacketSendListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.lang.reflect.Field;

/**
 * NoFall — prevents fall damage by spoofing the onGround flag.
 *
 * Mode "Packet" (default/bypass-friendly):
 *   Intercepts every outgoing PlayerMoveC2SPacket and replaces it with an
 *   identical packet that has onGround=true while the player is falling past
 *   the threshold.  No extra packets are ever injected — the server only sees
 *   normal movement packets with a slightly different ground flag, which is
 *   much harder for anticheats to distinguish from a real landing.
 *
 * Mode "Tick":
 *   Legacy approach — sends a single OnGroundOnly(true) packet the moment fall
 *   distance crosses the threshold (NOT every tick like the old implementation).
 *   Use this as a fallback if Packet mode causes desync on a specific server.
 */
public final class NoFall extends Module implements PacketSendListener {

    public enum Mode { Packet, Tick }

    private final ModeSetting<Mode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), Mode.Packet, Mode.class)
            .setDescription(EncryptedString.of("Packet = replace existing packets (bypass-friendly) | Tick = single ground packet on threshold"));

    private final NumberSetting minDist = new NumberSetting(EncryptedString.of("Min Distance"), 0, 10, 2, 0.5)
            .setDescription(EncryptedString.of("Fall distance before activating (0 = always)"));

    // Guard that prevents our own replacement send from re-triggering this listener
    private static volatile boolean bypassing = false;

    // Tick-mode state: only send once per fall, not every tick
    private boolean sentThisFall = false;

    public NoFall() {
        super(EncryptedString.of("No Fall"),
                EncryptedString.of("Prevents fall damage by spoofing ground packets"),
                -1, CategoryManager.MOVEMENT);
        addSettings(mode, minDist);
    }

    @Override
    public void onEnable() {
        eventManager.add(PacketSendListener.class, this);
        sentThisFall = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(PacketSendListener.class, this);
        super.onDisable();
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        // Recursion guard — our replacement send must not loop
        if (bypassing) return;
        if (mc.player == null || mc.world == null) return;

        // Reset per-fall state when the player lands
        if (mc.player.isOnGround()) {
            sentThisFall = false;
            return;
        }

        if (mc.player.isInFluid()) return;
        if (mc.player.getVelocity().y >= 0) return;  // ascending
        if (mc.player.fallDistance < minDist.getValueFloat()) return;
        if (!(event.packet instanceof PlayerMoveC2SPacket pkt)) return;

        switch (mode.getMode()) {
            case Packet -> handlePacketMode(event, pkt);
            case Tick   -> handleTickMode(event);
        }
    }

    // ── Packet mode ───────────────────────────────────────────────────────────

    /**
     * Cancel the original packet and resend an identical one with onGround=true.
     * Reads the packet fields via reflection so we don't need accessor mixins.
     */
    private void handlePacketMode(PacketSendEvent event, PlayerMoveC2SPacket pkt) {
        PlayerMoveC2SPacket replacement = buildGroundPacket(pkt);
        if (replacement == null) return;

        event.cancel();
        bypassing = true;
        try {
            event.connection.send(replacement);
        } finally {
            bypassing = false;
        }
    }

    // ── Tick mode ─────────────────────────────────────────────────────────────

    /**
     * Only inject ONE OnGroundOnly(true) packet when fall distance first crosses
     * the threshold — not every tick.  Much less pattern-obvious than the old impl.
     */
    private void handleTickMode(PacketSendEvent event) {
        if (sentThisFall) return;
        sentThisFall = true;

        // Cancel whatever movement packet triggered this and inject the ground spoof
        event.cancel();
        bypassing = true;
        try {
            event.connection.send(
                new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
        } finally {
            bypassing = false;
        }
    }

    // ── Reflection helper ─────────────────────────────────────────────────────

    /**
     * Reads fields from {@code pkt} via reflection and constructs a replacement
     * packet with onGround forced to true, preserving all other values.
     */
    private static PlayerMoveC2SPacket buildGroundPacket(PlayerMoveC2SPacket pkt) {
        try {
            boolean changesPosition = readBool(pkt, "changesPosition");
            boolean changesLook     = readBool(pkt, "changesLook");
            boolean hCol            = readBool(pkt, "horizontalCollision");

            if (changesPosition && changesLook) {
                return new PlayerMoveC2SPacket.Full(
                        readDouble(pkt, "x"), readDouble(pkt, "y"), readDouble(pkt, "z"),
                        readFloat(pkt, "yaw"), readFloat(pkt, "pitch"),
                        true, hCol);
            } else if (changesPosition) {
                return new PlayerMoveC2SPacket.PositionAndOnGround(
                        readDouble(pkt, "x"), readDouble(pkt, "y"), readDouble(pkt, "z"),
                        true, hCol);
            } else if (changesLook) {
                return new PlayerMoveC2SPacket.LookAndOnGround(
                        readFloat(pkt, "yaw"), readFloat(pkt, "pitch"),
                        true, hCol);
            } else {
                return new PlayerMoveC2SPacket.OnGroundOnly(true, hCol);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static double readDouble(Object obj, String name) throws Exception {
        return (double) field(obj, name).get(obj);
    }

    private static float readFloat(Object obj, String name) throws Exception {
        return (float) field(obj, name).get(obj);
    }

    private static boolean readBool(Object obj, String name) throws Exception {
        return (boolean) field(obj, name).get(obj);
    }

    private static Field field(Object obj, String name) throws Exception {
        Class<?> cls = obj.getClass();
        // Walk up the hierarchy — subclass fields shadow superclass fields
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " not found in " + obj.getClass());
    }
}
