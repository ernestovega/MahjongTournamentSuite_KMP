# Activity log

The apps never write to Firestore directly: the security rules deny every client write, and every change goes
through the `api` function. The function keeps two append-only logs of who wrote what and when.

| Log | Where | What it records |
| --- | --- | --- |
| Table changes | `tournaments/{id}/changes` | Every table save, reset and refused save (conflict): the person (`uid`, `name`), the origin (`editor`, `player` or `system` for the automatic close at the end of the match time), the table version, and the old and new value of every table and hand field. It is written in the same transaction as the save. |
| Activity | `activity` | Every other change: tournament settings, players, teams, editors, accounts, users and the EMA player registry. It keeps the action, the person, the tournament, the target and the short values of the request. E-mail addresses, passwords and images stay out. A failed write never fails the change. |

## Maintenance scripts

The scripts in `firebase/functions/src/scripts` change data outside the API. Each run that writes (with `--apply`
when the script has it) leaves one `script.run` record in `activity`: the script, the machine user, the project, the
arguments (without e-mail addresses), the outcome and a short summary such as the number of additions. A preview
leaves no record. New scripts that write must run through `runScript` in `scripts/scriptRun.ts`.

## Activity screen

Administrators read both logs in one list in the admin app: **Activity** in the top bar of the tournament list, or
in the top bar of a tournament, which opens it filtered by that tournament. The tournament menu keeps the order of the
tournament list, newest first.
The list is newest first and loads 50 entries at a time. It filters by tournament, by kind (table saves or
other changes), by origin (admin app, player app, or automatic: the server and the maintenance scripts), by a period
with a start and an end date and time (local time of the device, the end minute included), by person (from the
detail of an entry) and by a free text search: any entry that holds the text anywhere (the person, the tournament, the
target, a value, a changed field), without case or accents. Table saves store player ids, not names, so a player's
name finds their own saves from the player app and the records that name them, not every hand they won. The detail shows every changed value.

The screen reads `GET /admin/activity` (admin only). Its parameters are `limit`, `tournamentId`, `uid`,
`source` (`all`, `tables` or `other`), `origin` (`admin`, `player` or `system`), `from` (inclusive) and `to`
(exclusive) in milliseconds, `q` (the search text) and `cursor`. Firestore cannot search text, so with `q` the server
reads page after page and keeps the matches, up to 10 pages per request; such a page can be shorter than the limit
and still have a `nextCursor`. The answer has `entries` and a `nextCursor`, which the app
sends back unchanged. The cursor keeps the place of each log, so the merged pages never skip or repeat an entry.
The queries need the indexes in `firebase/firestore.indexes.json` (`activity` and `changes`).

## Not recorded

- A person's own profile changes (`PUT /auth/me` and the profile photo). Left out on purpose (Ernesto, 2026-11-12).
- Manual edits in the Firebase console. Left out on purpose: only the owner has console access (Ernesto, 2026-11-12).
- The table changes of a deleted tournament, which go with the tournament.
