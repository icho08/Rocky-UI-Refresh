package dev.i726.rocky.utils;

/**
 * Silent rotation override for god bridging.
 *
 * HOW IT WORKS
 * ─────────────
 * Minecraft sends movement packets (PositionAndRotation / LookAndOnGround) from
 * ClientPlayerEntity.sendMovementPackets(), which is called AFTER our TickListener.
 * Anticheat (Grim) validates each InteractBlock against the rotation it most recently
 * received. If InteractBlock arrives before the position packet with the virtual
 * rotation, Grim validates against the real (wrong) rotation → flag.
 *
 * Solution:
 *   1. In TickListener: set serverYaw/serverPitch (the target) and optionally set
 *      afterPacketAction (a Runnable that does the actual block placement).
 *   2. ClientPlayerEntityMixin.beforeMovementPackets() swaps the player's yaw/pitch
 *      to serverYaw/serverPitch so the position packet carries the silent rotation.
 *   3. ClientPlayerEntityMixin.afterMovementPackets() runs afterPacketAction (placing
 *      the block AFTER Grim has already received the correct rotation), then restores
 *      the real camera yaw/pitch.
 *
 * Packet order Grim sees per placement tick:
 *   PositionAndRotation(virtualYaw, virtualPitch)  ← correct rotation first
 *   InteractBlock(...)                             ← validated against virtualYaw ✓
 *   [next tick] PositionAndRotation(virtualYaw or gradually-returning)
 *
 * Usage:
 *   RotationOverride.serverYaw        = targetYaw;
 *   RotationOverride.serverPitch      = targetPitch;
 *   RotationOverride.active           = true;    // arm the swap
 *   RotationOverride.afterPacketAction = () -> { ... interactBlock ... };  // optional
 */
public final class RotationOverride {

    private RotationOverride() {}

    /** When true the mixin swaps the player's yaw/pitch before the movement packet. */
    public static volatile boolean active = false;

    /** The yaw the server will see in the next position packet. */
    public static volatile float serverYaw   = 0f;

    /** The pitch the server will see in the next position packet. */
    public static volatile float serverPitch = 0f;

    /**
     * Optional action to run AFTER the position packet is sent and BEFORE the camera
     * rotation is restored.  Set this when you want to call interactBlock() on the
     * same tick, ensuring the rotation is already on the wire before the interact.
     * Cleared automatically after execution.
     */
    public static volatile Runnable afterPacketAction = null;

    // ── Internal — only written by the mixin ─────────────────────────────────

    /** Real yaw saved before the swap; restored after the packet. */
    public static float savedRealYaw   = Float.NaN;

    /** Real pitch saved before the swap; restored after the packet. */
    public static float savedRealPitch = Float.NaN;
}
