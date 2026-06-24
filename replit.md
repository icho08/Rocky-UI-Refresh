# Rocky — Minecraft Fabric Utility Client

A Minecraft Fabric mod for version 26.1.2 with a custom in-game GUI.

## Build

- `./gradlew build` — build the mod (requires Java 21 + Fabric toolchain, run locally or via Replit shell)

## Stack

- **Language**: Java 21
- **Framework**: Fabric (Fabric Loader + Fabric API)
- **Key libs**: Mixin (bytecode manipulation), ProGuard (obfuscation), Lombok

## Where things live

- `src/main/java/dev/i726/rocky/` — all mod source code
  - `gui/vape/` — in-game ClickGUI (VapeTheme, RockyGui, Panel, ModuleButton, settings components)
  - `utils/RenderUtils.java` — GPU rendering helpers
  - `utils/NotificationManager.java` — toast notification system (call push/success/info/error/warn)
  - `auth/AuthManager.java` — HWID-based auth
- `build.gradle`, `settings.gradle`, `gradle.properties` — Gradle build config
- `proguard.pro` — ProGuard obfuscation rules
- `assets/minecraft/` — mod textures and resources

## Modules added in this session

### Combat
- **Kill Aura** (PvP) — auto-attacks, settings: Targets/Sort/Rotate/Range/CPS/FullCooldown/FriendCheck/ThroughWalls
- **Strafe** (PvP) — auto-strafes around nearest player; integrates with KillAura/AimAssist target detection
- **Surround** (Crystal) — places obsidian at feet, inner + optional outer ring, hotbar-aware slot selection

### Movement / Misc
- **Long Jump** (Movement) — applies horizontal velocity boost on jump frame
- **Inv Move** (Automation) — mirrors GLFW key states to MC bindings while any HandledScreen is open
- **Timer** (Automation) — modifies `renderTickCounter.msPerTick` via reflection; gracefully no-ops if field name changes

### Render
- **Fullbright** (ESP) — gamma-based; bypasses OptionInstance [0,1] validator via reflection; undetectable
- **Name Tags** (ESP) — world-to-screen projection in HUD space; shows name/HP/ping/distance with friend tint
- **HUD** (enhanced) — added: XYZ + facing, BPS meter, armor durability bars (color-coded), potion effects, clock; + notification toasts

### Client
- **Theme** (GUI) — in-game GUI accent color picker via ModeSetting<ThemeColor>
- **Self Destruct** — added always-on ButtonListener for a configurable Panic Key (default: END)
- **Module.toggle()** — now calls NotificationManager.success/info on every enable/disable

## Gotchas

- `ModeSetting.cycleBack()` was added in this rewrite — it does not exist in the original repo.
- `RenderUtils.deltaTime()` returns `1/fps`; multiply by a speed constant (e.g. `12f`) for smooth animation interpolation.
- `Fullbright` uses reflection to write to `OptionInstance.value` — if Fabric remapping changes the field name, add candidates to the array in `cacheField()`.
- `Timer` uses reflection on `renderTickCounter` — field candidates: `msPerTick`, `tickLength`, `timerSpeed`, `timeScale`, plus several intermediary names.
- `NightVision` (potion-based) is now named "Night Vision" in-game to avoid clash with `Fullbright`.

## Bridging module fixes (latest session)

### GodBridge + SmartBridge — detection bypass & forward-movement fix

#### GodBridge — Grim/NCP burst-limit bypass
- **Burst Limit** setting (default 5): after N consecutive placements, force a randomised pause (`Burst Pause Min`/`Max` ticks). Breaks the "infinite machine-perfect streak" pattern that gets flagged after ~5 blocks.
- **Sneak Sync** setting (default ON): 25% chance per placement to press sneak for 1 tick (released next tick). This breaks the "never-sneak + god-place" bot signature on Grim.
- **Jittered pitch range**: target pitch range is randomised each swing (`52–58°` min, `80–86°` max), not a fixed 55–85°.
- **Variable rotation speed**: `rotSpeed` ± 1°/tick of noise each tick for human feel.
- New settings added: `Burst Limit`, `Burst Pause Min`, `Burst Pause Max`, `Sneak Sync`.

#### PlayerEntityMixin — SafeWalk no longer blocks forward movement
- `clipAtLedge` override now only returns `true` when `mc.options.forwardKey.isPressed()` is **false**.
- Forward movement (W key) bypasses the ledge clip entirely — player can walk freely in facing direction.
- Backward/sideways ledge fall is still prevented when the forward key is not held.

#### SmartBridge — clean mode separation
- **GOD_ONLY** mode: uses the same god-bridge logic as the standalone GodBridge module, including burst-limit and sneak-sync detection bypass.
- **ASSIST_ONLY** mode: uses BridgeAssist logic (edge-sneak only, no rotation, no block placement).
- **SMART** mode: alternates God phase → Assist phase using the above two code paths.
- Removed settings: `God Fall Mode`, `God Look-Ahead` (both replaced by the forward-key-aware mixin fix).
- Added to god phase: `Burst Limit`, `Burst Pause Min`, `Burst Pause Max`, `Sneak Sync`, `Assist Edge Dist`, `Assist Look-Ahead`.

## Bridging module fixes (previous)

### SmartBridge — fixed double-placement in GOD/SMART mode
- Root cause: `runGodPhase` was calling `GodBridge.INSTANCE.setEnabled(true)` to get SafeWalk behaviour, which caused GodBridge's own `TickListener` to place blocks at the same time as SmartBridge → "2 blocks back" glitch.
- Fix: Added `SmartBridge.safeWalkActive` public static volatile boolean. `PlayerEntityMixin.clipAtLedge` now checks `SmartBridge.safeWalkActive` in addition to `GodBridge.shouldSafeWalk()`. SmartBridge sets this flag directly instead of enabling GodBridge. GodBridge is no longer touched by SmartBridge at all.
- Damage threshold: comparison now uses explicit `float` cast (`delta >= (float) damageThreshold.getValue()`) to avoid float/double precision surprises.

### GodBridge, SmartBridge, Clutch — reduced detectability
- Removed the "snap-and-restore" double rotation packet pattern. All three now send ONE `LookAndOnGround` before the interact, then let Minecraft's own next `PositionAndRotation` packet naturally return the rotation. The server sees: look-toward-block → place → gradual return (human), not: look → place → instant snap-back (bot).
- GodBridge & SmartBridge god phase: randomise the exact hit point on the block face (±0.12 H, ±0.15 V jitter) and pitch (50–82° with ±4° noise) instead of always hitting the dead center at a fixed 60-90° angle.

## Changes applied in this session

### NameTags — fixed world-to-screen projection
- Rewrote to two-phase: `GameRenderListener` collects screen coords using real JOML GPU matrices (`proj * modelView`), then `HudListener` draws them. Tags now lock to player bodies and no longer drift with the crosshair.
- Import: `com.mojang.blaze3d.systems.RenderSystem`, `org.joml.Matrix4f/Vector4f`.

### HUD — draggable panels + HUD editor screen
- `HUD.java` now stores `storedX[5]` / `storedY[5]` (NaN = use computed default).
- Positions loaded from `rocky/hud_positions.txt` on enable; saved on editor close.
- New `HudEditorScreen.java` opens via the "HUD Editor Key" keybind (default: Numpad 0). Drag any panel, then ESC to save. "Reset All" clears overrides.
- `onRenderHud` uses `px(id, default)` / `py(id, default)` helpers for all 5 panels. Module arraylist supports absolute X anchor too.

### Blatant toggle — no more full GUI rebuild
- `BlatantModules` now calls `ClickGuiScreen.addBlatantPanel()` / `removeBlatantPanel()` instead of `refreshPanels()`, so other panels keep their scroll/expanded state.

### Search module removed
- `Search.java` deleted; `add(new Search())` removed from `ModuleManager`.

### CategoryPanel — header icon tooltips
- Hovering the ▼ (collapse) or ≡ (minor-filter) icons in any panel header shows a descriptive tooltip via `ClickGuiScreen.queueTooltip()`.

### KillAura & Strafe → BLATANT category
- Both now use `CategoryManager.BLATANT` so they only appear when "Blatant Modules" is enabled.

## Gotchas

- `ModeSetting.cycleBack()` was added in this rewrite — it does not exist in the original repo.
- `RenderUtils.deltaTime()` returns `1/fps`; multiply by a speed constant (e.g. `12f`) for smooth animation interpolation.
- `Fullbright` uses reflection to write to `OptionInstance.value` — if Fabric remapping changes the field name, add candidates to the array in `cacheField()`.
- `Timer` uses reflection on `renderTickCounter` — field candidates: `msPerTick`, `tickLength`, `timerSpeed`, `timeScale`, plus several intermediary names.
- `NightVision` (potion-based) is now named "Night Vision" in-game to avoid clash with `Fullbright`.
- `NameTags` uses `proj.mul(modelView)` (JOML): `proj = new Matrix4f(RenderSystem.getProjectionMatrix())`, `modelView = event.matrices.peek().getPositionMatrix()`. Combined transforms camera-relative coords to clip space.

## User preferences

_Populate as you build._
