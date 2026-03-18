# MahjongTournamentSuite KMP

Kotlin Multiplatform project targeting:

- Android
- Desktop (JVM)
- Web (Wasm)

## Project Structure

- [`androidApp`](./androidApp)  
  Android application entry point and Android resources.

- [`composeApp`](./composeApp)  
  Presentation layer. Compose UI, navigation, previews, and app bootstrap.

- [`domain`](./domain)  
  Clean Architecture domain layer. Use cases, repository contracts, and app-level result/error models.

- [`data`](./data)  
  Clean Architecture data layer. Repository implementations, Ktor client setup, logging, platform info, and date/time handling.

## Tooling

- Kotlin `2.3.20`
- AGP `9.0.1`
- Gradle `9.1.0`
- Compose Multiplatform `1.10.2`

## Libraries In Use

- Dependency injection: Koin
- Concurrency: Kotlin Coroutines
- Serialization: kotlinx.serialization
- Networking: Ktor client
- Navigation: JetBrains AndroidX Navigation Compose
- Logging: Kermit
- Date/time: kotlinx-datetime
- Testing: Kotlin test, kotlinx-coroutines-test
- Previews: Compose previews enabled for Android/Desktop presentation code

## Build And Run

### Android

```bash
./gradlew :androidApp:assembleDebug
```

Run from Android Studio using the `androidApp` configuration on an emulator/device.

### Desktop

```bash
./gradlew :composeApp:run
```

### Web (Wasm)

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

## Tests

Run current unit and multiplatform tests:

```bash
./gradlew :domain:allTests :data:allTests
```

There are currently no interface/UI test suites configured.

## Notes

- The project is already migrated to the AGP 9-compatible KMP structure.
- Android, desktop, and web builds are verified.
- There is no server module.
- There is no active iOS target.
- Some JS/Wasm warnings still come from the Kotlin/webpack/npm toolchain rather than project code.
