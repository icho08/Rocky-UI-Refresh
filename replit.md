# Rocky — Minecraft Fabric Utility Client

A full-stack project combining a sleek dark web UI (React + Vite) and a redesigned in-game Minecraft Fabric mod GUI that share the same visual identity.

## Run & Operate

- `pnpm --filter @workspace/rocky-client run dev` — web UI preview (port from `$PORT`)
- `pnpm --filter @workspace/api-server run dev` — API server (port 5000)
- `pnpm run typecheck` — full typecheck across all packages
- `pnpm run build` — typecheck + build all packages
- `./gradlew build` — build the Minecraft Fabric mod (requires Java 21 + Fabric toolchain, run locally)

## Stack

### Web UI (`artifacts/rocky-client`)
- React 18 + Vite, TypeScript
- Tailwind CSS + custom CSS variables
- wouter for routing
- Dark gaming theme: `#0A0A0A` base, cyan accent `#22D3EE`

### Minecraft Mod (`src/main/java/dev/i726/rocky/`)
- Fabric mod for Minecraft 1.21.x
- In-game GUI: `RockyGui` → `Panel` → `ModuleButton` → setting components
- Rendering: `RenderUtils.java` — `renderRoundedQuad`, `renderSwitch`, `renderNeonQuad`, `renderRoundedOutline`
- Theme: `VapeTheme.java` — all color constants

## Where things live

- `artifacts/rocky-client/src/pages/home.tsx` — main web UI page with all module data
- `artifacts/rocky-client/src/index.css` — CSS variables and global styles
- `src/main/java/dev/i726/rocky/gui/vape/` — in-game ClickGUI
  - `VapeTheme.java` — color constants (source of truth for in-game colors)
  - `RockyGui.java` — main screen: header bar, search, panels
  - `components/Panel.java` — draggable category panel
  - `components/ModuleButton.java` — module row with toggle switch + expand
  - `components/settings/` — SliderSetting, CheckboxSetting, ModeSettingComponent, MinMaxSettingComponent, KeybindSetting, StringSettingComponent
- `src/main/java/dev/i726/rocky/utils/RenderUtils.java` — all GPU rendering helpers

## Architecture decisions

- In-game GUI uses `renderSwitch()` from `RenderUtils` for animated toggle switches on both module rows (CheckboxSetting) and boolean settings.
- `ModuleButton` uses a per-instance `switchAnim` float (0→1) animated with `RenderUtils.deltaTime()` for smooth toggle transitions.
- `ModeSettingComponent` supports left-click (cycle forward) and right-click (cycleBack) for mode navigation.
- `KeybindSetting.isAnyBinding` static flag tells `RockyGui` to switch from whitelist polling to full key range (32–348) only while a bind is pending — avoids performance hit when idle.
- Web UI is purely static (no backend) — all module data is hardcoded in `home.tsx` matching the Java source.

## Product

Rocky is a Minecraft Fabric utility client with:
- **Web UI**: dark gaming dashboard showing all modules by category, toggle switches, expandable settings panels with sliders/dropdowns, module search, active count
- **In-game GUI**: draggable dark panels per category, per-module toggle + settings expand, animated switches, cyan accents, keybind assignment per module

## User preferences

_Populate as you build._

## Gotchas

- Java mod cannot be compiled/tested in Replit — user must build with `./gradlew build` locally.
- `ModeSetting.cycleBack()` was added by Rocky's GUI rewrite — it does not exist in the original repo.
- Do not run `pnpm dev` at workspace root — use `restart_workflow` for artifacts.
- `RenderUtils.deltaTime()` returns `1/fps`; multiply by a speed constant (e.g. `12f`) for smooth animation interpolation.

## Pointers

- See `.local/skills/pnpm-workspace` for workspace structure details.
- Java source root: `src/main/java/dev/i726/rocky/`
- Gradle build files: `build.gradle`, `settings.gradle`, `gradle.properties`
