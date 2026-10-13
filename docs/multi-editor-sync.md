# Multi-editor sync

Several people can edit one tournament at the same time. This page describes how the app and the backend keep their data in step.

## Rules

- **The server always wins.** When the server has a newer version of a table, the editor shows the server data. The app never merges fields.
- **Every replacement is shown.** A dialog names the table. If the user had typed values, the dialog lists them so the user can type them again.
- **A save names the version it is based on.** The backend refuses a save from an old version with HTTP 409. The 409 answer carries the current table.
- **A user must save or discard before leaving a table.** At most one table can have unsaved changes.

## How the app finds changes

1. The backend keeps `dataVersions.tableVersions` on the tournament document. Example: `{"1_3": 7}`. Each save and each reset writes the new version of its table.
2. `GET /tournaments/{id}/sync/manifest` returns these versions in one Firestore read.
3. The table editor calls the manifest on a schedule (`PollSchedule`):

| User state | Interval |
|---|---|
| Active, no unsaved edits | 10 s |
| Active, unsaved edits | 5 s |
| No input for 2 minutes | 30 s |
| No input for 5 minutes | Stops. A dialog asks the user to continue. |

4. `TableChangeDetector` compares the manifest with the versions the app holds. Only a **higher** server version is a change. A lower version means the manifest was read before the user's own save.
5. The app loads only the changed tables. `RoundEditorSync` skips a check while a save or a load runs.

## Cache rules

- `SessionRepositoryCache` returns a cached value for 10 seconds after the server confirmed it. After that it waits for one revision check. It never returns old data and refreshes in the background.
- Cached requests of one screen share one manifest request for 2 seconds (`shared`).
- A save, a reset or a conflict marks the cached copies old.
- `RefreshMode.CACHED` returns any held value and never asks the server. Only a write that made the value old, or an empty cache, causes a load.
- `TournamentTableStore` holds all tables with their hands for the rankings. The first ranking load makes one request. After that the ranking makes **no request**, also when it opens again. The table editor keeps the store current: its saves, its loads and its polling feed it. When polling finds a change in a round that is not open, it loads that table too.
- The manifest also carries the revisions that all tournaments share: tournaments, EMA players, countries and accounts. So one poll sees everything that can change.
- Polling watches every revision. A higher number marks the cached copies that depend on it as old, and the next read fetches them. The editor reloads the player names (players or EMA players changed) and the tournament settings (tournaments changed) at once. A reload that fails is tried again at the next poll.
- The generated files (ID cards, ID list, EMA report) follow the same rule. They are reused without any request until polling marks them old. The EMA export first runs one poll, then uses the held data like the ranking.
- The ranking screen loads once and stays still while it is open. The EMA export forces a fresh load.

## Backend rules

- A save runs in one Firestore transaction. It reads the table and its 16 hand documents **by document**, not by query. A query locks a range, and then editors of different tables wait for each other.
- Only a save that completes a table runs the "other incomplete tables" query. It uses `limit(2)`.
- The answer of a save is built from the data the transaction wrote. The client needs no second read.
- The global `_meta/dataVersions` document changes only when a table completes or reopens.
- `tablesWithProgress` on the tournament document counts the tables with data. Changing a player after results start is checked with it. `progressTracked: true` marks a tournament that keeps the counter. Older tournaments are scanned until the backfill runs.
- Reset is a transaction and **admin only**.
- The change log (`tournaments/{id}/changes`) records who changed which fields, with the old and the new value. Totals that follow from the hands are left out.

## Data revision and file names

- The **data revision** is the sum of the versions of all tables. Every save and every reset adds 1. It is written `r123`.
- The tournament screen shows it under the title: `Results r123 · checked 15:30:12`. The time is the last poll that the server answered. The ranking shows `Results r123 · loaded 15:30:12` in its top left corner.
- Generated files carry the date and time in their name: `<tournament>-<kind>-<yyyymmdd-hhmm>.<ext>`. The EMA report also has the revision of the results it holds: `Madrid-Open-ema-report-r123-20261011-1530.xlsx`. ID cards and the ID list depend on players, not on results, so they have no revision.
- The same `r123` in the screen and in a file name means the same data.

## Data requirements

- Every table has its 16 hand documents. A save needs all 16, so the backfill also completes tables that have only some of them. New tournaments get them at creation. `npm run backfill:table-progress` (preview) and `:apply` create the missing ones in older tournaments. It never overwrites a hand and can run again.
- The tournament document has `tablesWithProgress`. Saves and resets keep it. The same script sets it.
- A save of a table without hands fails with 404. A save always needs `expectedVersion`.

Rollout on 2026-10-10: the backfill ran on all 6 tournaments of `mahjong-tournament-suite`, then the Functions and the hosting were deployed.

## Tests

| Layer | Command |
|---|---|
| Backend unit tests | `cd firebase/functions && npm test` |
| Backend with the Firestore emulator | `cd firebase/functions && npm run test:integration` (needs JDK 21) |
| App logic and UI tests | `./gradlew :data:jvmTest :adminApp:jvmTest` |
| Web target | `./gradlew :data:wasmJsTest :adminApp:wasmJsTest` (needs headless Chrome) |

The emulator does not make a blind write wait for a transaction that read the same document. Production Firestore does. So the test for "change a player while the first result is saved" checks consistency only.

## Known limits

- The only ways to write a table are `PUT .../state` (needs a version) and `POST .../reset` (admin). The old routes without a version check are gone.
- Locks, autosave and a viewer for the change log are not built.
- A new tournament is written children first and the tournament document last. If any write fails, everything written is deleted, so no tournament is left without hands.
- Two last tables that complete at the same moment, and two saves of the same table, wait for each other for about 3 seconds on the emulator. They are rare.
