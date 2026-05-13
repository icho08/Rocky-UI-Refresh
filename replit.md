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
  - `auth/AuthManager.java` — HWID-based auth
- `build.gradle`, `settings.gradle`, `gradle.properties` — Gradle build config
- `proguard.pro` — ProGuard obfuscation rules
- `assets/minecraft/` — mod textures and resources

## Gotchas

- `ModeSetting.cycleBack()` was added in this rewrite — it does not exist in the original repo.
- `RenderUtils.deltaTime()` returns `1/fps`; multiply by a speed constant (e.g. `12f`) for smooth animation interpolation.

## User preferences

_Populate as you build._
