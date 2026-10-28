# Instrumented UI tests

The instrumented UI tests run the real Android app on an emulator. The app talks to the real API code.
The API runs on the Firebase Auth and Firestore emulators on your computer.
No test touches the production project.

## What runs where

| Part | Where it lives | What it does |
| --- | --- | --- |
| Local backend | `firebase/functions/src/scripts/startE2eBackend.ts` | Starts the API on port 5055. Fills the emulators with an EMA registry, accounts and the `test-*` tournaments. |
| Seed data | `firebase/functions/src/scripts/seedTestTournaments.ts` | Writes 13 tournaments for the normal, corner and error cases. |
| Admin app tests | `androidApp/src/androidTest/.../e2e` | Compose UI tests of the admin app. |
| Player app tests | `playerAndroidApp/src/androidTest/.../player/e2e` | Compose UI tests of the player app, including hand entry. |
| Test runner script | `scripts/run-e2e-tests.sh` | Runs the tests and starts again the tests that hit the harness error. |
| Backend address | `BackendUrlOverride` in `data/src/androidMain` | Tests point the app to `http://10.0.2.2:5055/api`. Release builds never set it. |
| Debug network rule | `androidApp/src/debug/res/xml/network_security_config.xml` | Allows plain http to `10.0.2.2` in debug builds only. |

## Run the tests

1. Start the local backend. Keep the terminal open. Use JDK 21.
   `cd firebase/functions && npm run e2e:backend`
2. Start an emulator.
   - Admin app: use the tablet image `Pixel_Tablet`. The admin app is a desktop style tool. On a phone it hides the buttons in menus.
   - Player app: use a phone. Set the size to a normal phone with `adb shell wm size 1080x2400` and `adb shell wm density 420`.
     On a very small screen the sign-in button leaves the visible area.
3. Turn off the Google autofill service on the emulator. Its "Save password?" window covers the app after a sign-in.
   `adb shell settings put secure autofill_service null`
   The run script does this for you.
4. Run the tests.
   - Admin app: `scripts/run-e2e-tests.sh androidApp`
   - Player app: `scripts/run-e2e-tests.sh playerAndroidApp`
   - One class: `scripts/run-e2e-tests.sh androidApp com.etologic.mahjongtournamentsuite.e2e.SignInTest`
   - Set `ANDROID_SERIAL` when more than one emulator runs.

The plain Gradle task `./gradlew :androidApp:connectedDebugAndroidTest` also works. It does not start failed tests again.

The tests use the AndroidX Test Orchestrator. Every test starts in a new process with empty app data.
A failed test saves a screenshot in `/sdcard/Download/e2e` on the device. The error message also lists the texts that were on the screen.

## What the tests cover

189 tests: 138 for the admin app and 51 for the player app. All pass on the emulator.

| Class | Tests | Area |
| --- | ---: | --- |
| `SignInTest` | 12 | Sign in, wrong password, empty fields, disabled account, no network, reset password, session restore, logout. |
| `TournamentListTest` | 13 | List rows, switches, roles (admin, editor, editor without tournaments, player), permission error for a player. |
| `TournamentScreenTest` | 13 | Rounds, table chips, finished, running and not started tournaments, totals tables, back, editor access. |
| `TableStatesTest` | 10 | Penalty explanation, hand counts, totals with a wrong sum, incomplete rounds, chicken column on and off. |
| `PlayersScreenTest` | 13 | Columns, filters, searches, open slots, linked accounts, large tournament. |
| `TeamsScreenTest` | 6 | Team list, searches, unicode team names, open slots. |
| `PlayerAssignmentTest` | 8 | Clear, substitute and team edit dialogs. |
| `EditTournamentTest` | 12 | Edit dialog values, cancel, save, agenda rows, delete with confirmation, editor is refused. |
| `CreateTournamentTest` | 10 | Form defaults, validation messages, and a valid creation that opens the new tournament. |
| `TableEditingTest` | 7 | Hand entry in the admin table editor, the Completed switch, an editor save, and conflicts with another editor. |
| `ToolsDialogsTest` | 4 | ID card and invitation dialogs. |
| `InvitationsTest` | 4 | Send from the invitations dialog: the dialog asks for a missing email, a typed email, a valid email, email refused by the mailbox. |
| `GlobalScreensTest` | 12 | App users, usage, EMA registry, statistics, theme. |
| `StatisticsTest` | 6 | Tournament statistics tabs. |
| `RankingAndTimerTest` | 10 | Ranking board paging and rows, timer round and countdown. |
| `PlayerSignInTest` | 6 | Player app sign in and errors. |
| `PlayerHomeTest` | 12 | Tournament card, schedule, roles, theme, sign out. |
| `PlayerTournamentTest` | 13 | Tabs, my tables, all tables, ranking, info. |
| `PlayerStatisticsTest` | 10 | Tournament and personal statistics, and the message for an account with no EMA number. |
| `PlayerHandEntryTest` | 10 | Add draw, add win (discard and self draw), penalty dialog, review, cancel. |

## Test accounts

Every account uses the password `Passw0rd!-e2e`.

| Email | Role | Notes |
| --- | --- | --- |
| `admin@e2e.test` | Admin | Sees every tournament. |
| `editor@e2e.test` | Editor | Member of `test-live-ongoing`, `test-min-16`, `test-incomplete-states` and `test-last-table`. |
| `outsider@e2e.test` | Editor | Member of no tournament. |
| `disabled@e2e.test` | Editor | The account is disabled. |
| `player@e2e.test` | Player | Sits at a table of the live tournament that has 11 hands. |
| `seated@e2e.test` | Player | Sits at a table that has seats and no hands. |
| `waiting@e2e.test` | Player | Has no seat in the running round yet. |
| `done@e2e.test` | Player | Sits at a finished table. |
| `stranger@e2e.test` | Player | Plays in no tournament. |

## Restore data after a test

A test that changes data calls `E2eApi.reset("test-id")`. The backend then writes the original data of that tournament again.
The route `POST /__e2e/reset?only=test-a,test-b` exists only in the local backend script. It is not part of the real API.

## Known limits

- The Compose test harness sometimes resumes an app coroutine on a thread that is not the main thread.
  The app then fails with "must be called on the main thread" or "State must be at least 'CREATED'".
  The same code works on a device. `scripts/run-e2e-tests.sh` starts only those tests again, up to four times. Other errors fail at once.
- Most tests start with a saved session file, not with the sign-in screen. Only `SignInTest` and `PlayerSignInTest` use the sign-in form.
- Round 4 of the live tournament starts 15 minutes before the seed run. The apps allow hands only while a round plays (120 minutes by default). Tests that enter hands call `E2eApi.reset("test-live-ongoing")` first. Start the backend again if it ran for a long time.
- The emulator must use the time zone of the computer, because the seed writes round times in that zone. The run script sets it.
- A test never closes the activity at its end. The orchestrator ends the process.
- Dates show in the language of the emulator. Tests do not check dates.
- The tests do not send real emails. The local backend has no mail server.
