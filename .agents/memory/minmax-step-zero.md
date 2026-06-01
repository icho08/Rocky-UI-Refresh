---
name: MinMaxSetting step=0 divide-by-zero
description: MinMaxSetting.setMinValue/setMaxValue compute 1.0/increment — passing 0 as the increment crashes at runtime.
---

## Rule
Never pass `0` as the `increment` argument to `MinMaxSetting`. The internal rounding logic does `1.0 / increment`, which causes `Infinity` / divide-by-zero.

**Why:** `setMinValue` and `setMaxValue` both compute `double precision = 1.0D / this.increment` unconditionally. If increment is 0, precision becomes `Infinity` and all subsequent `Math.round` calls return 0.

**How to apply:** Use a sensible minimum step — `1` for integer counts, `5` for millisecond delays, `0.05` for float ranges. The step value only affects the GUI slider granularity, not runtime behaviour.
