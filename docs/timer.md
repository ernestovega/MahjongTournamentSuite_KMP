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

When a clock starts, resumes or changes its time left, the server plans the close of the round at the new end (Cloud Tasks queue `closeRoundTablesTask`). A pause plans nothing, and a close that comes too early does nothing. There is no daily planning job any more. A change that ends the round at once (Set time to zero, "started N minutes ago" longer than the match, a shorter match in the settings) gets no task: the server closes the round right away. It only does so for a round that was not over before the change, so an organizer can still fill an old round.

### Without network

The Timer shows an action at once and says "No connection". It sends the action again every few seconds, with the time that passed since the press (`agoSeconds`, or a smaller `remainingSeconds`), so the server applies it at the instant of the press. The server accepts a delay of up to 10 minutes for a pause or a resume.

## One round at a time

A round starts only once the round before it is closed (Ernesto, 2026-11-25). The server checks it on every Play
(`POST /tournaments/{id}/rounds/{roundId}/clock`, action `start`), also with "The round started earlier":

- While the round before plays, is paused or is in its extra time and has open tables, the Play answers `409`
  `PREVIOUS_ROUND_IN_PLAY` (`details.roundId`, `details.tables`). The Timer and Round control say "Round N is still in
  play. Stop it before starting the next round."
- When every table of the round before finished before its time, the Play ends it at once (as Stop) and starts the round.
- When the time of the round before is over (or it never started) but it has open tables, the Play answers `409`
  `PREVIOUS_ROUND_OPEN_TABLES` (`details.roundId`, `details.tables`, `details.started`). The screen asks "Start round
  N?" with the tables and "Close them and start" (red), which sends the Play again with `"closePrevious": true`: the
  server completes those tables, as the review of Round control does, and starts the round.
- A table with a pending problem is closed like the others and corrected later in the table editor, which edits
  completed tables. A Play of a round that already started is never refused.

## Seats before the round

A player of a table can set the four seats from 10 minutes before the planned start, or once the round started, until the first saved hand or penalty, while the round is not over (`POST /player/tournaments/{id}/tables/{roundId}/{tableId}/seats`). A round that never got its gong is over for the players once its planned match time (plus the extra time of "Finish the hand") has passed, once a later round has started, or once the tournament is completed: the seats answer `SEATS_TIME_OVER`, the hands `ROUND_TIME_OVER`, and the table shows "The time of this match is over". Before the 10 minutes the table says when the seats open. A gong after the planned time opens the round again for its whole match. The window is computed on every request from the current data, so a new planned time, a new match duration, time added on the Timer, a pause, a Stop, a Reset (the round waits for a new gong) or another time zone apply at once. A tournament without a time zone has no planned instant: only a later round or the end of the tournament closes a round without gong. The player picks East, South and West; the player who is left takes North. The button says "Change the seats" once they are set. Organizers can still set seats at any time.

## Reminder of the own table (player app)

When the player app opens or returns to the front, a signed-in player who plays a round that starts soon or is in play sees a dialog in the middle of the screen: the tournament, the table icon, "Table 7 · Round 3", then "Starts in 6 minutes", "Waiting for the starting gong" or "Started 12 minutes ago" in the pink accent. The "Go to my table" button is pink too, centered, with "Later" below it. "Go to my table" opens the table and "Later" closes it. A tap outside or Back does not close it: the player must answer.

- It shows from 10 minutes before the planned start (the same lead as the seats) until the end of the match, extra time included. The countdown uses the planned time; "Started ... ago" uses the round clock (pauses do not count).
- A round with no clock shows "Waiting for the starting gong" for at most one match duration after the planned time.
- A completed table gives no reminder. Closing the reminder or opening the table hides it until the next round. The web keeps that through a page refresh (session storage); Android keeps it while the app process lives.
- Before the gong the app checks the clocks again every minute while the reminder shows, so it turns into "Started ... ago" without leaving the app.
- While the round plays (after the gong) and the table of the player is not completed, every screen but the table shows a floating pink button "Go to my table" at the bottom right, also after the player closed the dialog. Leaving a table checks again, so a table that the players just completed loses its button.
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

## Round control (plan B)

The "Round control" button of the tournament screen (admins and the editors of the tournament) opens a screen of its own that works without the Timer (it was a dialog over the table editor: a table it closed popped up the "Table updated" notice of the editor behind it; now the tournament screen loads the tables again when the admin goes back). On top, one chip per round with the status icon of its tables and a play mark on the round whose clock runs. Below, the clock (an ended round shows its time and "Time is over" in red, like the Timer), "What the players can do" (hand entry and seats: Follow the clock, Open or Closed) and the tables. Info buttons explain each part. It holds the clock buttons of a round (Start, "The round started earlier", Pause, Resume, Stop, Set time, Reset, each with the same confirmations as the Timer) and the manual control of what the players can do. Picking a round there changes nothing on the server.

The control lives in the tournament document, apart from the settings:

```
roundControls: { "<roundId>": { hands?: "open"|"closed", seats?: "open"|"closed", tables?: { "<tableId>": "open"|"closed" } } }
```

A missing value is automatic: the clock decides, as above. `POST /tournaments/{id}/rounds/{roundId}/control` (admins only) takes `{"hands":"auto|open|closed"}`, `{"seats":...}`, `{"table":{"tableId":T,"hands":...}}` and `{"reopenTables":[T,...]}`, `{"completeTables":[T,...]}` in any mix, and answers like the clocks route (it includes `roundControls`). "auto" removes the value.

- **Hand entry.** A table uses its own mode, else the mode of the round. "open" lets the players add hands whatever the clock says (before the gong, during a pause, after the time); "closed" refuses them (409, and the player app says that an organizer closed the hand entry). The close at the end of the time skips a table whose hand entry is open.
- **Seats.** "open" lets the players set the seats at any time before the first hand; "closed" refuses them.
- **Completed / In play.** Each table has the same mark as the Completed switch of the table editor (`completeTables` and `reopenTables` in the body). A completed table refuses every hand, whatever its mode. The close mark stays set, so the end of the time never closes a table that an admin opened again. In play does not open the hand entry: after the time, pick Open for that table too. Reopening a table of a completed tournament puts the tournament back in progress.
- **Open tables of finished rounds.** A round is finished when its time ran out (extra time included), when a later round started (it has a clock or a table with data, so rounds played without the Timer count too), or when the tournament is completed. Each finished round that keeps tables not completed shows a red badge with their count on its chip. Above the round chips (it is about the whole tournament, not the selected round) a warning says "There are N open tables in ended rounds" with a Review button. Review opens "Open tables in ended rounds": one row per table with its round, its number, the hands entered (read from the hands of those rounds) and a Go button that closes the review, selects the round and scrolls to that table. The header row stays in place while the rows scroll. Each row has a checkbox (the header one checks them all). At the end, Close and Close selected: it closes the checked tables with a spinner and keeps the review open, so the admin sees the ones left; the review ends by itself when none is left. It sends `POST /tournaments/{id}/complete-open-tables` (admins only) with `{"tables":[{"roundId":R,"tableId":T},...]}`. The server marks each one as completed like the Completed switch, skips the ones that already are, answers `{"completed":N}` and writes one `round.control` entry in the activity log. Any table can be opened again in its round.
- The mode is per round and stays until an admin sets it back to automatic. The next round stays automatic. The round control screen and the Timer warn while a round is manual.
- Each change bumps the `rounds` version and the `tournaments` revision, and writes a `round.control` entry in the activity log.
- The player app works with the time of the server (`ServerTime`), learned from each answer of the clocks, so a wrong device clock does not change what it shows.

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

`npm run prepare:ongoing-round [tournamentId]` (preview) and `npm run prepare:ongoing-round:apply [tournamentId]` leave a tournament ready to try the tables; the default is `madrid-open-2026`. The first round after the last round with hands starts at the time of the run: every planned time (rounds, agenda, event dates) moves by the same amount, so the rounds keep their order and the time between them. The rounds before it get a clock that is over, and the server closes their open tables with hands as at the end of a round. That round and the later ones lose their clock, so the Timer opens at it and Play starts it. Run it again whenever a new round is needed; when every round has hands, seed the tournament again. The production seed (`seed:production-tournaments`) leaves `uppsala-mcr-open-2026` in the same state. The ongoing series of the testers (`madrid-open-2026`, `toledo-mcr-challenge-2026`, `alcala-team-cup-2026`: 2 days each, 7 rounds a day from 07:00 to 22:00) does not move: its rounds that are over are complete, and the round in play at the run gets a clock from its planned start. Like any script, it targets production unless `FIRESTORE_EMULATOR_HOST` or `EMA_FIREBASE_PROJECT` says otherwise. Logic: `services/ongoingRound.ts`.

## Main source files

- `firebase/functions/src/services/roundClock.ts`, `roundClockService.ts`, `roundControl.ts`, `matchTime.ts`, `matchTimeTaskService.ts`
- `adminApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/RoundControlScreen.kt`
- `adminApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/TimerScreen.kt`
- `adminApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/presentation/screen/RoundTimerController.kt`
- `playerApp/src/commonMain/kotlin/com/etologic/mahjongtournamentsuite/player/MatchTime.kt`, `SeatsAssignment.kt`, `HandEntryPanel.kt`
- `adminApp/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/StandaloneWindows.kt`
- `androidApp/src/main/kotlin/com/etologic/mahjongtournamentsuite/TimerActivity.kt`
- `adminApp/src/webMain/kotlin/com/etologic/mahjongtournamentsuite/main.kt`
