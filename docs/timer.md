# Timer screen

## Purpose

The Timer screen shows the clock of a tournament round on a separate display. The Play button of the Timer starts the round: the match time counts from that press, and not from the planned start time. Every Timer of the tournament and the player app follow the same clock, which the server keeps.

The Timer opens from a tournament only (the Timer button in the top bar of the tournament screen). The tournament list has no Timer.

## Round clocks

The server stores one clock for each round that started, in the tournament document:

```
roundClocks: { "<roundId>": { startedAtMs, pausedAtMs, pausedMs, adjustMs } }
```

All instants come from the clock of the server, so the clocks of the devices do not matter.

- A round without a clock has not started. The players see "Waiting for the starting gong" and cannot add hands. They can already set the seats of their table (see below).
- Play time is `(pausedAtMs ?? now) - startedAtMs - pausedMs`. The match lasts `matchDurationMinutes + adjustMs`.
- The phase follows from the clock: playing, extra time (end mode "finish the hand") or over.
- A round never starts by itself. When nobody presses Play, the round waits.

| Action | Route body (`POST /tournaments/{id}/rounds/{roundId}/clock`) | Effect |
| --- | --- | --- |
| Play | `{"action":"start"}` | Starts the clock now. A second Play changes nothing: the first Play wins. |
| Started N minutes ago | `{"action":"start","agoSeconds":N*60}` | Starts the clock in the past, at most one whole match ago. |
| Pause | `{"action":"pause"}` | Stops the clock. The players can still add hands. |
| Play after a pause | `{"action":"resume"}` | Runs the clock again. The pause does not count. |
| Set time | `{"action":"setRemaining","remainingSeconds":S}` | Changes the time left, from 0 to 12 hours. |
| Reset | `{"action":"reset"}` | Removes the clock. A round with saved hands answers 409 `ROUND_HAS_HANDS`; `"force":true` confirms. The hands stay, but the players cannot add more until the next Play. |

Editors of the tournament and admins can change a clock. Anyone can read them: `GET /tournaments/{id}/round-clocks`, and the public `GET /public/tournaments/{id}/round-clocks` with a 3 second cache. The tournament carries its clocks too. Each change bumps the `rounds` version of the tournament and the shared `tournaments` revision, and writes a `round.clock` entry in the activity log.

### Close of a round

When a clock starts, resumes or changes its time left, the server plans the close of the round at the new end (Cloud Tasks queue `closeRoundTablesTask`). A pause plans nothing, and a close that comes too early does nothing. There is no daily planning job any more.

### Without network

The Timer shows an action at once and says "No connection". It sends the action again every few seconds, with the time that passed since the press (`agoSeconds`, or a smaller `remainingSeconds`), so the server applies it at the instant of the press. The server accepts a delay of up to 10 minutes for a pause or a resume.

## Seats before the round

A player of a table can set the four seats from 10 minutes before the planned start, or once the round started, until the first saved hand or penalty, while the round is not over (`POST /player/tournaments/{id}/tables/{roundId}/{tableId}/seats`). The player picks East, South and West; the player who is left takes North. The button says "Change the seats" once they are set. Organizers can still set seats at any time.

## Reminder of the own table (player app)

When the player app opens or returns to the front, a signed-in player who plays a round that starts soon or is in play sees a dialog in the middle of the screen: the tournament, the table icon, "Table 7 · Round 3", then "Starts in 6 minutes", "Waiting for the starting gong" or "Started 12 minutes ago" in the pink accent. The "Go to my table" button is pink too, centered, with "Later" below it. "Go to my table" opens the table and "Later" closes it. A tap outside or Back does not close it: the player must answer.

- It shows from 10 minutes before the planned start (the same lead as the seats) until the end of the match, extra time included. The countdown uses the planned time; "Started ... ago" uses the round clock (pauses do not count).
- A round with no clock shows "Waiting for the starting gong" for at most one match duration after the planned time.
- A completed table gives no reminder. Closing the reminder or opening the table hides it until the next round. The web keeps that through a page refresh (session storage); Android keeps it while the app process lives.
- Before the gong the app checks the clocks again every minute while the reminder shows, so it turns into "Started ... ago" without leaving the app.
- Code: `playerApp/.../player/MyTableReminder.kt`.

## Controls

When opened, the Timer selects the round whose clock runs, or else the round after the last round that has a clock or hands. A round with hands but no clock (hands entered in the editor without the Timer) counts as played.

| Control | Behavior |
| --- | --- |
| Play icon | Starts the round, or resumes it after a pause. It becomes a Pause icon while the clock runs. |
| Pause icon | Pauses the clock of the round. It uses 50% opacity while the clock runs. |
| Circular arrow icon | Resets the clock of the round. It works once the round started, and asks first when the round has saved hands. |
| Clock icon | Sets the time left from a positive number of minutes. It shows once the round started. |
| History icon | "The round started earlier": starts the round N minutes ago, for a gong that rang without the Timer. It shows while the round has not started. |
| Light or dark icon | Changes the Timer between light and dark themes. |
| Small minus and plus icons | Select the previous or the next round, from 1 to the number of rounds. |

The Play button gets initial keyboard focus. The controls support Tab, Shift+Tab, arrow keys, Enter, and Numpad Enter. Focus wraps inside the Timer.

While the clock runs, the screen hides all controls except Pause. The other controls return after a pause.

## Display behavior

The timer uses the `hours:minutes:seconds` format. The time text scales to use the available display area. A round that has not started shows the whole match.

A status line under the time says what happens: waiting for the gong, paused, time is up, no connection, or the tournament could not be loaded.

The progress bar shows the elapsed part of the match. For matches of at least one hour, the display becomes red during the last 15 minutes. The player app shows the time left of the round on the table screen too.

The Timer reads the clocks every 5 seconds, so it follows a Play or a Pause on another Timer.

## Independent launch behavior

Each platform opens the Timer outside the main navigation flow.

| Platform | Behavior |
| --- | --- |
| Desktop | Opens a separate Compose window. Closing the main window does not stop the application while the Timer window is open. |
| Android | Opens `TimerActivity` in a separate Android task, with the tournament and the round as extras. |
| Web | Opens a separate browser tab with `?standalone=timer&tournamentId=…&initialRound=…`. |

Closing a Timer does not stop the round: the clock lives on the server.

## Moving stored tournaments

`npm run backfill:round-clocks` (preview) and `npm run backfill:round-clocks:apply` give a clock at the planned start to every round of a tournament that is not completed, whose planned start is past and that has no clock. The seed of the test tournaments does the same, so its rounds in play stay in play. From a computer, planning the close of a round in play needs `EMA_FIREBASE_SERVICE_ACCOUNT` set to the service account of the functions; without it the script writes the clocks but warns that the closes are not planned. Saving the settings of the tournament in the admin app plans them.

## Preparing an ongoing tournament for tests

`npm run prepare:ongoing-round [tournamentId]` (preview) and `npm run prepare:ongoing-round:apply [tournamentId]` leave a tournament ready to try the tables; the default is `madrid-open-2026`. The first round after the last round with hands starts at the time of the run: every planned time (rounds, agenda, event dates) moves by the same amount, so the rounds keep their order and the time between them. The rounds before it get a clock that is over, and the server closes their open tables with hands as at the end of a round. That round and the later ones lose their clock, so the Timer opens at it and Play starts it. Run it again whenever a new round is needed; when every round has hands, seed the tournament again. The production seed (`seed:production-tournaments`) leaves `madrid-open-2026` in the same state. Like any script, it targets production unless `FIRESTORE_EMULATOR_HOST` or `EMA_FIREBASE_PROJECT` says otherwise. Logic: `services/ongoingRound.ts`.

## Main source files

- `firebase/functions/src/services/roundClock.ts`, `roundClockService.ts`, `matchTime.ts`, `matchTimeTaskService.ts`
- `adminApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/TimerScreen.kt`
- `adminApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/RoundTimerController.kt`
- `playerApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/player/MatchTime.kt`, `SeatsAssignment.kt`, `HandEntryPanel.kt`
- `adminApp/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/StandaloneWindows.kt`
- `androidApp/src/main/kotlin/com/etologic/mahjongtournamentsuite/TimerActivity.kt`
- `adminApp/src/webMain/kotlin/com/etologic/mahjongtournamentsuite/main.kt`
