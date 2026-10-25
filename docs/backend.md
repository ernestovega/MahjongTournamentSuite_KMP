# Backend (Firebase Functions + Firestore)

See [users.md](users.md) for the complete user and role system.

This app does **not** access Firestore directly from clients.

- Firestore rules are `deny all`.
- All reads/writes go through HTTPS Firebase Cloud Functions.

## Why

- Single KMP client implementation (Ktor) for Android + Desktop + Web.
- Centralized authorization and role checks.
- Avoid platform Firebase SDK gaps (notably Desktop).

## Tournament schedule generation

Tournament rounds/tables are generated on the client (Android/Desktop/Web) and sent to the backend as part of
`POST /tournaments`. The backend validates the payload and persists it to Firestore; it does **not** generate schedules.
Hands are created lazily (when a table is first opened) to keep tournament creation write volume manageable.

## Tournament ID cards

Tournament records contain these ID-card branding fields:

- `shortName`: Required text with 1 to 10 characters.
- `primaryColor`: A color in `#RRGGBB` format.
- `associationLogoUrl`: The public URL for the optional association logo.

The create request accepts these fields. It can also contain a JPEG or PNG logo as Base64 data.

`PUT /tournaments/:tournamentId/settings` updates the name, short name, color, and logo.

`GET /tournaments/:tournamentId/id-cards` returns a two-sided PDF. Each assigned player gets one front page and one back page. The endpoint rejects tournaments with unassigned player slots.

## Auth

### Sign in

`POST /auth/signIn`

Body:

- `email` (required)
- `password`

The client sends the email as `email`.

### Password recovery

`POST /auth/passwordReset`

Body:

- `email` (required)

Firebase sends password reset instructions to the email address.

### Refresh

`POST /auth/refresh`

Body:

- `refreshToken`

Clients should refresh tokens when receiving `401 unauthenticated`.

## Roles

- Capabilities: `ADMIN`, `EDITOR` and `PLAYER`. An account can have more than one.
- Firebase custom claims: `admin: true`, `editor: true` and `player: true`. An account with no claim has no capability.
- Per-tournament editor assignment: `tournaments/{tournamentId}/members/{uid}`
- Per-tournament player registration: `users/{uid}/playerTournaments/{tournamentId}`

Admins manage all tournaments, the EMA registry and all editor accounts. Editors manage content and player accounts of assigned tournaments. An editor edits only the own editor account and cannot add or remove editors.
Only admins can create, delete, rename, or configure tournaments. Disabling an account keeps its profile and history.

Management routes use `requireEditor`. It accepts the `admin` and `editor` claims and refuses player-only accounts.
Read routes of a tournament use `requireTournamentReader`. It accepts admins, assigned editors and players who are registered in the tournament.
`GET /admin/whoami` and `GET /auth/me` return `roles`. `whoami` also returns `role`, the highest capability.
`GET /auth/me` also returns `emaId` and `photoUrl` (the photo of the EMA player of the account). `PUT /auth/me` with `{ alias }` lets every signed-in user change the own alias (1 to 60 characters). The user cannot change the EMA id or the photo.
`POST /venues/resolve-link` with `{ url }` (editors and admins) follows a short Google Maps link and returns `{ url }` with the full link. It follows only Google hosts and at most 5 redirects. The tournament form reads the venue name, the address, the country and the city from the full link.

## Player accounts

- Assigning a player sends no email. If an account already owns the EMA id, the assignment links the slot to that account at once (`accountUid`, `invitationStatus: ACCEPTED`). The player then sees the tournament, but nobody told the player.
- An admin can set the EMA id of an existing account (`PUT /admin/users/:uid` with `emaId`, an empty text clears it). `POST /admin/users` also accepts `emaId`. Both routes check the EMA player and the owner first. An EMA id that belongs to another account returns `409`, and nothing changes. Every tournament that has this EMA id in a slot links to the account at once. No email is sent.
- A send to the email of an existing account links that account at once and sends the reminder. It needs no invitation and no acceptance.
- An editor starts every email by hand. The actions work for one player or for many. The body lists the players: `{ players: [{ playerId, email? }] }`. The answer has one result per player (`INVITED`, `NOTIFIED`, `REVOKED`, `SKIPPED`, `FAILED`).
  - `POST /tournaments/:id/player-accounts/send`: invites a new player (uses the given or the saved email), or sends the "you are registered" email with a link to the tournament to a linked player. It skips players that were already contacted.
  - `POST /tournaments/:id/player-accounts/resend`: creates a new invitation link (the old link stops working), or sends the reminder again.
  - `POST /tournaments/:id/player-accounts/revoke`: cancels a pending invitation, or removes the account link.
  - `PUT /tournaments/:id/player-accounts/emails`: saves the email of players with no account, for a later send.
  - `POST /tournaments/:id/player-import/preview`: reads a CSV or Excel file (`fileName`, `dataBase64`) and returns what an import would do. It changes nothing.
  - `POST /tournaments/:id/player-import/apply`: reads the same file again and runs one step of the import. It needs `replaceExisting: true` to replace a player that is in a slot already.
    - The app runs the steps in order, so that no request lasts long: `phase: "CLEAR"` once (empties the slots whose player moves), `phase: "ASSIGN"` with up to 25 `rows` (line numbers) for each batch, and `phase: "FINISH"` once (saves the emails and renames the teams).
    - The first line of the file names the columns. Required: `id` (the slot number) and `ema id`. Optional: `email` and `team`.
    - The delimiter of a CSV file can be a comma, a semicolon or a tab. Excel files use the first sheet.
    - A row has an error when the slot or the EMA number is unknown, when a slot or an EMA number appears twice, or when the email or the team name is wrong. The import skips these rows and applies the others.
    - Players can move between slots in one file. The server clears the slots that give up a player first, so an EMA number is never in two slots.
    - The email is saved as a contact email and no message is sent. The preview warns when it differs from the saved email.
    - The team name renames the team of the slot. Team members stay in their fixed slots.
- The slot keeps `invitationStatus`, `invitedEmail` and the time of the last email (`contactedAt` in the players list). The editor app uses them to filter "not invited yet", "invited, waiting" and "contacted".
- The email has one link. The token is `{tournamentId}.{invitationId}.{secret}`. Only a hash of the secret is stored. The link works once and expires after 14 days.
- `POST /player/invitations/accept` is public. A new account sends `{ token, password }` and the server creates the account. An existing account signs in and sends `{ token }`.
- Accepting links the slot (`accountUid`, `invitationStatus`), registers the tournament for the account and reserves the EMA id (`emaIdIndex/{emaId}`). One account owns one EMA id.
- A new account from an invitation gets the full EMA name of the player (first name and last name) as its alias. An existing account with an empty alias gets the same name at its first link. The user can change the alias later with `PUT /auth/me`.
- Changing the EMA id of a slot ends its invitation and account link.
- The email goes out through `nodemailer` and the SMTP mailbox of the project (`mailService.ts`). The secret `MTS_SMTP_URI` holds the SMTP address. `MAIL_FROM` and `PLAYER_APP_URL` are in `firebase/functions/.env`. If the SMTP server fails, the invitation stays. The result of that player is `FAILED` and the editor presses "Resend".
- A player sees only the tournaments where the account is registered. `GET /tournaments` and `GET /ema-player-registry` return only that scope to a player-only account. The players list of a tournament hides accounts and emails for players.

## Replace, clear or substitute a player during a tournament

A slot (`tournaments/{id}/players/{n}`) is the identity for tables, hands and teams. These stay on the slot number when the person changes. There are two tools.

**Clear or replace the person** (`PUT /tournaments/:id/players/:playerId`). Use it for a correction or a no-show.
- It works at any time, also when the slot has played.
- A cleared slot shows as "Player N". It keeps its tables, hands, points and team. A new person assigned later inherits the data of the slot.
- The statistics follow the EMA id of the slot. The server removes the old person from the table statistics when the EMA id changes.
- The person who leaves loses the link to the tournament in the player app, and the substitution of the slot ends.
- The EMA report leaves out slots with no person.
- `PUT /tournaments/:id/teams/:teamId` still refuses to move people between slots after the first table has data.

**Substitute a player who played some rounds** (`PUT` and `DELETE /tournaments/:id/players/:playerId/substitution`). It follows section 4 of the EMA rules.
- Body: `{ reason: LATE | ILLNESS | DISQUALIFIED, fromRound, duringSession, excludedFromEma }`. `duringSession` is not allowed with `LATE`. `excludedFromEma` is only allowed with `ILLNESS`.
- The substitute is never recorded. The slot keeps the person and the rounds before `fromRound`.
- The slot gets no points and no score from the rounds `>= fromRound`. A disqualified slot gets no points and no score from any round.
- With `duringSession`, the server writes `forcedZeroPlayerId` on the table of `fromRound`. That seat gets 0 table points and the other three seats share 4, 2 and 1 (`pointsFromScores` in `handScoring.ts` and `TablePoints.kt`). The change raises the version of that table.
- In the other rounds the substitute plays like a normal player, so the other players get their normal places.
- Hands won by the substitute do not count for the person. The person has no personal statistics from those rounds (`DigestResult.countsForSlot`, `DIGEST_VERSION` 5). After a deploy, run `npm run backfill:statistics:apply`.
- The ranking keeps a position for the slot. The EMA report omits a player with `excludedFromEma`. A disqualified player stays in the report with 0.
- The app shows a text tag in the player list and the rankings. It shows strikethrough for a disqualified and for an excluded player.
- **Disqualified:** the ranking shows 0 points and 0 score. The EMA report has the row with 0. The person statistics count the tables played with 0 points and 0 score. This is a punishment.
- **Excluded:** the EMA report omits the player. The person statistics have no table of the player. The ranking keeps the player, struck through. This protects the player.

Each change writes an entry to `tournaments/{id}/playerLog` with `kind` (`replace`, `clear`, `substitute`, `restore`), the slot, the previous and next value, the user and the time. The app does not read this log.

## Hand entry by a player

One player who sits at the table writes the hands down. There is no start step and no confirmation by the other players.
The table must be in hands mode, not completed, and all four seats must be assigned. Hands mode is the default for new tables.

**Who and when.** A player writes only when all of these are true. If one fails, the server refuses the request and writes nothing:
- The account has the player claim, and its registration (`users/{uid}/playerTournaments/{id}`) matches the slot: same EMA id, and the slot links to this account.
- The player sits at the table (the slot is one of the four seats).
- The table is not completed, and the match is on: after the round start and before the end, plus the extra minutes of the finish-hand mode. Before the start, after the end, or without a round schedule, an editor enters the data.
- The table version is the one the player saw.

**Seats.** A player cannot set or change the seats. An editor sets them in the admin app. Without seats, the server answers `409`.

**Hand fields.** The body of preview and record has `playerWinnerId`, `playerLooserId`, `handScore` and `isChickenHand`, and these optional fields:
- `isDraw: true` records a hand with no winner and no loser. The server stores it with `handScore` `"0"`, so every seat scores zero.
- `isLastHand: true` records the hand and marks the table completed, like the Completed switch in the admin app. Use it when the time ends before hand 16.

**Penalties.** `POST /player/tournaments/:id/tables/:round/:table/penalties` with `playerEastPenalty`, `playerSouthPenalty`, `playerWestPenalty`, `playerNorthPenalty` and `expectedVersion`.
It adds penalties to the hand in play and does not finish that hand. The server appends one part to the penalty text of every seat, like the penalty rows of the admin app.
Each part is empty or a signed whole number. Every penalty needs at least one negative value. The other seats can receive positive values from a shared penalty.
A hand has at most 5 penalties. The penalties count in the table totals when the hand is saved. A later win or draw keeps them.
The app shows each penalty as its own item in the list of hands, under the hand that follows it.

1. `POST /player/tournaments/:id/tables/:round/:table/hands/preview` with the hand fields.
   The server picks the next free hand and returns the score change of every seat, the table scores and points after the hand, and `tableVersion`. It writes nothing.
2. The app shows the winner, the loser, the hand score, the change of each seat and the new table totals, so the player can show them to the table.
3. `POST /player/tournaments/:id/tables/:round/:table/hands` with the same fields and `expectedVersion` (the `tableVersion` of the preview).
   The server writes the hand and the new totals in one transaction and raises the table version.
   If the table changed after the preview, it answers `409`.
   The app keeps the entered hand, loads the server data and opens a window with both: the table now, the last saved hand and the
   hand of the player. The player closes it to go on, or calls a staff member first.

The change log records the player. Editors see the hand through the normal version check. A player cannot correct a saved hand. An editor does that.
The server calculates the totals with `handScoring.ts`. It must match `TableEditorState.kt`.

## User management

- `GET /admin/users` lists Firebase Auth users and their profiles.
- `POST /admin/users` creates an account and sends a password-reset email. It accepts an optional `emaId` from an admin.
- `PUT /admin/users/:uid` changes email, global role, and tournament assignments.
- `PUT /admin/users/:uid/disabled` disables or enables an account.

Users cannot disable themselves or change their own role. The last enabled admin cannot be demoted or disabled.

- `GET /tournaments/:tournamentId/users/lookup?email=...` finds an account for an assigned editor.
- Assigned editors can assign or remove other editors.
- Admin access is implicit and cannot be removed from one tournament.

Membership is stored at:

- `tournaments/{tournamentId}/members/{uid}`

## Firestore data model

- `users/{uid}`
- `emaPlayerRegistry/{emaId}` (shared EMA registry, populated by the EMA sync)
- `countries/{countryCode}` with `{ code, name }`, for example `ES` and `Spain`
- `tournaments/{tournamentId}`
- `tournaments/{tournamentId}/members/{uid}`
- `tournaments/{tournamentId}/players/{playerId}` with `accountUid`, `invitationStatus`, `invitedEmail` after an invitation
- `tournaments/{tournamentId}/playerInvitations/{invitationId}`
- `users/{uid}/playerTournaments/{tournamentId}` with `{ playerId, emaId }`
- `emaIdIndex/{emaId}` with `{ uid }`
- `tournaments/{tournamentId}/teams/{teamId}`
- `tournaments/{tournamentId}/tables/{tableKey}` (tableKey: `{roundId}_{tableId}`)
- `tournaments/{tournamentId}/tables/{tableKey}/hands/{handId}` with hand fields including `isChickenHand` and `isDone`. A new tournament stores the 16 hands of each table at creation.
- `tournaments/{tournamentId}/changes/{changeId}` is the change log. It has one entry for each table save or reset.
- (optional later) `tournaments/{tournamentId}/playerStats/{playerId}` for fast rankings

A tournament has a required venue and a required `contactEmail` for the organizers. The venue is a `venueMapUrl`, or a `venueName` together with a `venueAddress`. `POST /tournaments` and `PATCH /tournaments/:id` return 400 when the venue or the email is missing. `contactEmail` must be a valid email address.
`venueMapUrl` must be an `https` link to Google Maps. Agenda items (`agendaItems`) may use any date, also a day before or after the tournament days. Round schedules must stay inside the tournament dates. The player app shows the venue, the round times and the agenda on the Info tab.

Tournament documents store `eventStartDate` and `eventEndDate` as `YYYY-MM-DD`. Both dates are inclusive.

A team document has `id`, `name` and the time stamps. The players of a team come from the `team` field of the slots.

**Indexes.** `firebase/firestore.indexes.json` lists the single-field indexes that the queries need, and nothing else:
- `members.uid` and `players.assignedEmaId` in collection groups, for the editor lists and for a change of an EMA number.
- `playerInvitations.status` and `playerInvitations.emaId`, for the invitations of a person.

Deploy them with `firebase deploy --only firestore:indexes`. The emulator needs no index, so a query without an index fails only in production.

## Seed countries

The country picker reads `countries/{countryCode}` documents. To write the full ISO country list, set Application Default Credentials for the Firebase project, then run:

```bash
cd firebase/functions
npm run seed:countries
```

## Refresh strategy (client)

The table editor checks the server for changes while it is open. See [Multi-editor sync](multi-editor-sync.md).

- Load when a screen opens. A cached copy is used for 10 seconds. After that, one manifest request decides if a reload is needed.
- A save, a reset or a conflict marks the cached copies old.
- The table editor polls the manifest. Other screens do not poll.
- The player app checks the server every 15 seconds while a tournament screen is open and visible. It loads all tables again only when the table versions of the manifest are higher than the held versions.

## Public routes

These routes need no sign-in. Every other route needs a valid token and a role check:
- `GET /health` and `GET /config` (the feature flags).
- `POST /auth/signIn`, `/auth/passwordReset` and `/auth/refresh`.
- `POST /player/invitations/accept`. The invitation token is the secret.

- `/auth/signIn`, `/auth/passwordReset`, `/auth/refresh` and `/player/invitations/accept` allow a limited number of requests for each client address. The counter lives in one function instance.
- `/auth/passwordReset` answers `200` for an unknown email, so the answer does not show which emails have an account.
- Every write (`POST`, `PUT`, `DELETE`) is limited to 120 requests per minute for each address and token. The counter lives in one function instance.
- An editor cannot save a table of a completed tournament. Only an admin can.
- A tournament id in a route must not contain `/`.
- The hosting sites send `X-Content-Type-Options`, `Referrer-Policy`, `frame-ancestors 'none'` and `Permissions-Policy`.

## App Check

App Check proves that a request comes from one of the apps and not from a script. The API checks the header `X-Firebase-AppCheck`.
- The variable `APP_CHECK_MODE` in `firebase/functions/.env` sets the mode: `off` (default), `monitor` or `enforce`.
- In `monitor` mode a missing or wrong token goes to the log as `App Check failed` and the request goes on.
- In `enforce` mode the API answers `401 app_check_failed`. Only `GET /health` has no check.
- The apps always send a token when they have one. The local test backend runs with the mode `off`.

Turn it on in this order:
1. In the Firebase console, open App Check. Register the web app with reCAPTCHA v3. Register the two Android apps with Play Integrity and add the SHA-256 of the signing key.
2. Web: fill in `siteKey`, `apiKey` and `appId` at the top of `adminApp/src/webMain/resources/appcheck.js` and `playerApp/src/wasmJsMain/resources/appcheck.js`.
3. Android: fill in `mts.appCheck.appId` and `mts.appCheck.apiKey` in the manifest of `androidApp` and `playerAndroidApp`.
4. Set `APP_CHECK_MODE=monitor`, then deploy the API and both web sites. Publish the Android apps.
5. Read the log for a few days. When no real app fails, set `APP_CHECK_MODE=enforce` and deploy the API.

A debug build uses the debug provider. The console shows the debug token in the log of the app. Register it in App Check, under "Manage debug tokens". On localhost the web app prints its debug token in the browser console.

## First admin

The API has no route to create the first admin. Set the `admin: true` custom claim with the Firebase Admin SDK or the Firebase console, and create the profile document `users/{uid}`.

For the full role migration order, deployment commands, and permission checks, see [Firebase role rollout](firebase-role-rollout.md).

## Required env vars (Functions)

- `FIREBASE_API_KEY` (Web API key from Firebase project settings)
