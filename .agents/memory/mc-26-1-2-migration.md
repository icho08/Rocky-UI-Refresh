---
name: MC 26.1.2 API migration
description: Breaking API changes when porting a Fabric mod to MC 26.1.2 (Java 25 required).
---

## Java version
MC 26.1.2 outputs class file version 69.0 (Java 25). Must set `toolchain { languageVersion = JavaLanguageVersion.of(25) }` and `options.release = 25` in build.gradle.

## Entity rotation
`Entity.yRot()` / `Entity.xRot()` → `Entity.getYRot()` / `Entity.getXRot()` (players, mobs, etc.)
**BUT** `Camera.xRot()` / `Camera.yRot()` and `PositionMoveRotation.xRot()` / `PositionMoveRotation.yRot()` kept their original (non-get) names as Record accessors.

**How to apply:** Global sed replacing `.yRot()` → `.getYRot()` overcorrects — Camera and PositionMoveRotation must be reverted back to `.xRot()` / `.yRot()`.

## ServerboundInteractPacket
Now a Java Record. `ServerboundInteractPacket.Handler` and `.dispatch()` removed.
- Attack packets: `packet.hand() == null` (hand field is null for attacks)
- Interact packets: `packet.hand() != null`
- Create attack: `new ServerboundInteractPacket(entityId, null, null, sneaking)`

## Screen rendering
`Screen.render(GuiGraphics, int, int, float)` → `Screen.extractRenderState(GuiGraphicsExtractor, int, int, float)`

## ChunkPos
Now a Java Record. Fields `.x` and `.z` became private. Use accessor methods `.x()` and `.z()`.

## ResourceKey
`key.location()` → `key.identifier()`

## RenderType debug methods removed
All removed in 26.1.2 rendering pipeline overhaul:
- `RenderType.debugTriangleFan()` — use fill-based approximation for 2D GUI
- `RenderType.debugQuads()` — same
- `RenderType.debugLineStrip(double)` — stub 3D renders as no-ops
- `RenderType.debugFilledBox()` — same
- `RenderType.lines()` — same
- `DebugRenderer.renderFilledBox(PoseStack, BufferSource, ...)` — removed entirely

`GuiGraphicsExtractor` has no `bufferSource()` — uses `guiRenderState` internally. For 2D GUI corners/outlines, approximate with `context.fill()` calls.

## handleInventoryMouseClick
`handleInventoryMouseClick` → `handleContainerInput`

## CharacterEvent modifiers
`input.modifiers()` removed — pass `0` where modifiers are needed.
