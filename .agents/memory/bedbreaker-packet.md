---
name: BedBreaker direct packet approach
description: Why BedBreaker should send PlayerActionC2SPacket directly instead of calling interactionManager.attackBlock.
---

## Rule
Use `mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(START_DESTROY_BLOCK, pos, face))` instead of `mc.interactionManager.attackBlock(pos, face)`.

## Why
`ClientPlayerInteractionManager.attackBlock()` performs client-side state checks (blockBreakingCooldown, current breaking state, etc.) that can silently drop the call when called from a TickListener outside the normal input flow. The direct packet bypasses all client-side validation and hits the server reliably every tick. The server itself handles hardness/progress validation — our rotation packet precedes it in the same tick so Grim's rotation check passes.

## How to apply
Each break tick:
1. Send `LookAndOnGround` with jittered rotation toward the target face.
2. Send `PlayerActionC2SPacket(START_DESTROY_BLOCK, targetPos, face)`.
3. Call `mc.player.swingHand(Hand.MAIN_HAND)` for the animation.

No need for ABORT_DESTROY_BLOCK between ticks; repeatedly sending START each tick is equivalent to spam left-clicking, which many bedwars servers handle as instant-break or rapid-progress mining.
