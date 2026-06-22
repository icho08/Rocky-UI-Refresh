---
name: Clutch isSolid guard bug
description: Bug in Clutch.java where an early-return check prevented the clutch from firing in the most common scenario.
---

## Rule
Never guard clutch placement with `isSolid(foot.down())`. Only guard with `isSolid(foot)` (player inside a block).

## Why
Original code:
```java
if (isSolid(foot) || isSolid(below)) { tryRestoreSlot(); return; }
```
`isSolid(below)` = ground 1 block below player. This fired and returned early in the exact scenario clutch should save you — falling with ground one block beneath.
`buildPlaceHit(foot)` with priority DOWN would have correctly found that block and placed on its top face, but the guard prevented it.

## How to apply
```java
// Only bail if player is literally inside a solid block
if (isSolid(foot)) { tryRestoreSlot(); return; }

// Scan down up to 4 levels for the first solid neighbour to place against
BlockHitResult hit = null;
for (int dy = 0; dy <= 3; dy++) {
    hit = buildPlaceHit(foot.down(dy));
    if (hit != null) break;
}
if (hit == null) return;
```
