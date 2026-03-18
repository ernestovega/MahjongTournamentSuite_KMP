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
