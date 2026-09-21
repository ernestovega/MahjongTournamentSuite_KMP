# Theme settings

## Theme modes

The app supports three theme modes.

| Mode | Behavior |
| --- | --- |
| System | Uses the current operating-system light or dark mode. Open screens update when the operating-system mode changes. This is the default. |
| Light | Always uses the light theme. |
| Dark | Always uses the dark theme. |

The theme button cycles through System, Light, and Dark. Its label shows the selected mode.

## Persistence

The app saves the selected mode on the local device.

- Android uses application preferences.
- Desktop uses Java user preferences.
- Web uses browser local storage.

The saved mode loads when the app starts again.

On macOS, the desktop app checks the current appearance once per second while System mode is active.

## Standalone screens

New Timer and Rankings screens start with the saved app theme.

The Timer and Rankings screens have local light or dark buttons. Each selection changes only that standalone screen. It does not replace the saved app mode or change another window.

## Main source files

- Theme mode and controller: `composeApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/theme/ThemePreference.kt`
- Android storage: `composeApp/src/androidMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/theme/ThemePreferenceStorage.android.kt`
- Desktop storage: `composeApp/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/theme/ThemePreferenceStorage.jvm.kt`
- Web storage: `composeApp/src/wasmJsMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/theme/ThemePreferenceStorage.wasm.kt`
