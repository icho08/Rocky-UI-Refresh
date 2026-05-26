package dev.i726.rocky.utils;

/**
 * Silent rotation override for god bridging.
 *
 * Set active=true and serverYaw/serverPitch before the movement packet is sent.
 * ClientPlayerEntityMixin swaps the player's actual yaw/pitch to these values
 * for the duration of sendMovementPackets(), then restores them immediately.
 * The camera (render thread) reads the real yaw/pitch and never sees a change.
 *
 * The server receives the overridden rotation via the normal position packet —
 * no separate LookAndOnGround packets, no instant snap, no bot signature.
 *
 * Usage:
 *   RotationOverride.serverYaw   = targetYaw;
 *   RotationOverride.serverPitch = targetPitch;
 *   RotationOverride.active      = true;   // set this last
 *   // ... next movement packet will carry serverYaw/serverPitch silently ...
 *   RotationOverride.active = false;       // clear after placement
 */
public final class RotationOverride {

    private RotationOverride() {}

    /** When true the mixin will swap player rotation before the movement packet. */
    public static volatile boolean active = false;

    /** The yaw the server will receive in the next position packet. */
    public static volatile float serverYaw   = 0f;

    /** The pitch the server will receive in the next position packet. */
    public static volatile float serverPitch = 0f;

    // ── Internal — only written by the mixin, do not touch from modules ──────

    /** Real yaw saved by the mixin before swap; restored after the packet. */
    public static float savedRealYaw   = Float.NaN;

    /** Real pitch saved by the mixin before swap; restored after the packet. */
    public static float savedRealPitch = Float.NaN;
}
