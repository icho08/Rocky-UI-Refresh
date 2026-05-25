# Rocky — Minecraft Fabric Utility Client

A Minecraft Fabric mod for version 1.21.x with a custom in-game GUI.

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
