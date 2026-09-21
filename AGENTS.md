# AGENTS

## Project Summary

This repository is a Kotlin Multiplatform application for `MahjongTournamentSuite`.

Supported targets:

- Android
- Desktop (JVM)
- Web (Wasm)

Not in scope:

- iOS
- backend/server module
- interface/UI test suite

## Architecture

The project uses a simple Clean Architecture split:

- `domain`
  - pure business rules
  - repository interfaces
  - use cases
  - app-level result/error models
  - avoid platform/framework code here

- `data`
  - repository implementations
  - network setup
  - logging
  - date/time handling
  - platform-specific infrastructure
  - depends on `domain`

- `composeApp`
  - presentation layer
  - Compose UI
  - navigation
  - DI bootstrap
  - depends on `domain` and `data`

- `androidApp`
  - Android application entry point only

## Libraries Currently Intended

- Koin for DI
- Kotlin Coroutines
- kotlinx.serialization
- Ktor client
- Navigation Compose
- Kermit
- kotlinx-datetime
- Kotlin test
- kotlinx-coroutines-test

Do not add more libraries casually. Keep the setup lean and only add new dependencies when there is an actual feature need.

## Dependency And Build Expectations

- Kotlin: `2.3.20`
- AGP: `9.0.1`
- Gradle: `9.1.0`
- Compose Multiplatform: `1.10.2`

The project is already migrated to the AGP 9-compatible KMP structure:

- `androidApp` is isolated from KMP shared modules
- KMP Android modules use `com.android.kotlin.multiplatform.library`

## Common Commands

Build Android:

```bash
./gradlew :androidApp:assembleDebug
```

Run desktop:

```bash
./gradlew :composeApp:run
```

Run web (Wasm):

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

Run tests:

```bash
./gradlew :domain:allTests :data:allTests
```

## Current Known Warnings

These are currently tolerated unless they become actionable:

- Android packaging warning about `libandroidx.graphics.path.so` not being stripped
- Kotlin/webpack/npm warnings during JS/Wasm tasks
- Gradle configuration-time npm aggregation warnings for Kotlin web tasks

Treat those as tooling noise unless a specific failure appears.

## Editing Guidance

- Preserve the `domain` -> `data` -> `composeApp` direction.
- Avoid putting networking, logging, or platform checks in `domain`.
- Avoid leaking Ktor/Firebase/Android/Compose types into `domain`.
- Prefer plain Koin DSL wiring unless there is a strong reason to introduce compiler/codegen tooling.
- Keep Compose navigation and presentation concerns in `composeApp`.
- Keep `androidApp` thin.

## Testing Guidance

- Unit tests belong primarily in `domain` and `data`.
- There is no UI/interface test harness yet.
- If adding tests, prefer focused common tests before platform-specific ones.

## Keyboard Focus And Interaction

Apply these rules to every new or changed screen, dialog, list, and interactive component:

- Give each screen and dialog a logical initial focus target.
- Restore focus to the control that opened a dialog or sub-screen when the user returns.
- Support forward navigation with Tab and reverse navigation with Shift+Tab.
- Keep focus inside each active screen or dialog. Tab on the last control must return to the first control. Shift+Tab on the first control must return to the last control.
- Support logical spatial navigation with the arrow keys.
- In every text field, Left and Right move the caret within the text first. Move focus only when Left is at the start or Right is at the end. Preserve selections and do not move focus from an active selection.
- Keep list focus in place while the next item is visible.
- Scroll a list only when focus moves past its first or last visible item.
- Keep left and right navigation within the controls of the current list item when applicable.
- Activate buttons and custom clickable controls with Enter and Numpad Enter.
- Show the shared soft focus highlight around focused buttons and clickable controls.
- Use the shared focus components in `presentation/components/KeyboardFocus.kt`.
- Use `Modifier.appFocusGroup()` for new focus regions.
- Use the Tournament players screen and its assignment dialog as the reference behavior.

## Scrollable Lists And Pointer Input

- Use `LazyColumnWithScrollbar` for a vertical lazy list that needs a visible scrollbar.
- Use `ScrollableColumnWithScrollbar` for other vertical content that needs a visible scrollbar.
- A vertical scrollbar must fill only the height. Use `fillMaxHeight()` and a fixed width.
- A horizontal scrollbar must fill only the width. Use `fillMaxWidth()` and a fixed height.
- Never use `fillMaxSize()` on a scrollbar. It can cover the content and intercept pointer input.
- Keep each row's hover and click target within that row's bounds.
- Test list changes with mouse hover, mouse click, wheel scrolling, and keyboard navigation.
- For desktop regression tests, use coordinate-based mouse input. A semantics click can bypass pointer interception.

## Tournament Player Presentation

- Treat tournament player records as schedule slots.
- Use only the slot ID, team, and EMA assignment when presenting a slot.
- Never display the stored slot name or country. These fields can contain old data.
- Resolve names, countries, and photos from the assigned EMA player record.
- Show `Player <ID>` for an unassigned slot.
- Show 🌐 when a displayed player has no country.
- Treat the EMA pseudo-country code `EU` as an empty guest country and show 🌐.
