---
name: Timer mixin approach MC 1.21.10
description: How to implement a Timer module in MC 1.21.10 — the correct field names and mixin target for scaling tick speed.
---

## Rule
Do NOT use reflection to find `msPerTick`, `ticksPerSecond`, or similar field names in `RenderTickCounter$Dynamic`.

Use `@ModifyArg` in `MinecraftClientMixin` targeting the `beginRenderTick(JZ)I` call inside `render()`.

## Why
In MC 1.21.10, `RenderTickCounter$Dynamic` fields are:
- `dynamicDeltaTicks`, `tickProgress`, `fixedDeltaTicks`, `tickProgressBeforePause` (mutable floats)
- `lastTimeMillis`, `timeMillis` (mutable longs)
- `tickTime` (final float — base tick interval)
- `targetMillisPerTick` (final FloatUnaryOperator)

Neither `msPerTick` nor `ticksPerSecond` exist. Reflection candidates from older MC versions do nothing.

## How to apply
In `MinecraftClientMixin`:
```java
@ModifyArg(
    method = "render",
    at = @At(value = "INVOKE",
             target = "Lnet/minecraft/client/render/RenderTickCounter;beginRenderTick(JZ)I"),
    index = 0
)
private long rocky$modifyTimerSpeed(long timeMillis) {
    Timer timer = ...getModule(Timer.class);
    if (timer == null || !timer.isEnabled()) return timeMillis;
    return timer.transformTime(timeMillis);
}
```

In `Timer.java`, `transformTime(long rawTime)` tracks `lastRawTime` and `lastScaledTime`:
```java
long elapsed       = rawTime - lastRawTime;
long scaledElapsed = (long)(elapsed * speed.getValue());
lastRawTime    = rawTime;
lastScaledTime += scaledElapsed;
return lastScaledTime;
```
This scales elapsed wall-clock time, making the tick counter think more (or less) time has passed.
Reset `lastRawTime = -1` on `onDisable()` so the next enable starts fresh.
