# Timer screen

## Purpose

The Timer screen shows a tournament round timer on a separate display.

The screen keeps running when the user closes the main screen. Timer state stays in memory until the Timer screen closes.

## Controls

The timer starts at 1 hour and 55 minutes.

When opened from a tournament, the tournament screen selects the initial round. It uses one round after the latest round with complete or partial table data. It uses round 1 when no table has progress.

| Control | Behavior |
| --- | --- |
| Play icon | Starts or resumes the countdown. A finished timer restarts from the set duration. It becomes a Pause icon while running. |
| Pause icon | Stops the countdown without changing the remaining time. It uses 50% opacity while the timer runs. |
| Circular arrow icon | Stops the countdown and restores the set duration. It is at the bottom right. |
| Clock icon | Sets the remaining time from a positive number of minutes. It is at the bottom right. |
| Light or dark icon | Changes the Timer between light and dark themes. It is at the bottom right. |
| Small minus icon | Selects the previous round. Round 0 is the minimum. It is next to the round number. |
| Small plus icon | Selects the next round. It is next to the round number. |

The Play button gets initial keyboard focus. The controls support Tab, Shift+Tab, arrow keys, Enter, and Numpad Enter.

The remaining-time dialog selects the current minute value when it opens. Typing replaces the selected value. Enter, Numpad Enter, and the mobile Done action apply the new time.

Focus wraps inside the Timer. Moving forward from the theme button selects the first available round control. Moving backward from that control selects the theme button.

While the timer runs, the screen hides all controls except Pause. The round controls return after the user pauses the timer.

At round 0, the screen hides the round label but keeps both round controls visible.

## Display behavior

The timer uses the `hours:minutes:seconds` format. The time text scales to use the available display area.

The Timer screen does not use the shared application top bar. This leaves more space for the timer display.

The progress bar shows the elapsed part of the set duration. For durations of at least one hour, the display becomes red during the last 15 minutes.

The screen shows `Time is up.` when the countdown reaches zero.

## Independent launch behavior

Each platform opens the Timer outside the main navigation flow.

| Platform | Behavior |
| --- | --- |
| Desktop | Opens a separate Compose window. Closing the main window does not stop the application while the Timer window is open. |
| Android | Opens `TimerActivity` in a separate Android task. Removing the main task does not close the Timer task. |
| Web | Opens a separate browser tab with `?standalone=timer`. Closing the main tab does not close the Timer tab. |

An operating-system force stop or browser shutdown still stops the Timer.

## State and persistence

The Timer stores its countdown state inside the screen.

- The tournament screen passes the initial round into the Timer.
- The Timer does not read tournament data.
- A Timer opened from the global action starts at round 1.
- The timer does not save its state.
- The selected light or dark theme applies only to this Timer screen.
- A new Timer starts with the app's saved theme. System mode follows the operating-system theme.
- Closing the Timer resets its duration and round.

## Main source files

- `composeApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/TimerScreen.kt`
- `composeApp/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/StandaloneWindows.kt`
- `androidApp/src/main/kotlin/com/etologic/mahjongtournamentsuite/TimerActivity.kt`
- `composeApp/src/webMain/kotlin/com/etologic/mahjongtournamentsuite/main.kt`
- Platform launch functions under `composeApp/src/*Main/kotlin/.../presentation/platform/ExternalScreens.*.kt`
