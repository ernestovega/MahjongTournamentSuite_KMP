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
- Player access: the account owns an EMA id (`users/{uid}.emaId`, `emaIdIndex/{emaId}`). The account plays in every tournament that holds this EMA id in a slot. No other link is stored.

Admins manage all tournaments, the EMA registry and all editor accounts. Editors manage content and player accounts of assigned tournaments. An editor edits only the own editor account and cannot add or remove editors.
Only admins can create, delete, rename, or configure tournaments. Disabling an account keeps its profile and history.

Management routes use `requireEditor`. It accepts the `admin` and `editor` claims and refuses player-only accounts.
Read routes of a tournament use `requireTournamentReader`. It accepts admins, assigned editors and players who are registered in the tournament.
`GET /admin/whoami` and `GET /auth/me` return `roles`. `whoami` also returns `role`, the highest capability.
`GET /auth/me` also returns `emaId` and `photoUrl` (the photo of the EMA player of the account). `PUT /auth/me` with `{ alias }` lets every signed-in user change the own alias (1 to 60 characters). The user cannot change the EMA id or the photo.
`POST /venues/resolve-link` with `{ url }` (editors and admins) follows a short Google Maps link and returns `{ url }` with the full link. It follows only Google hosts and at most 5 redirects. The tournament form reads the venue name, the address, the country and the city from the full link.

## Player accounts

- **The import writes a pending email on the slot.** The slot field `email` holds the email of the import file. Only admins read it: `GET /tournaments/:id/players` returns `email` to admins only. Editors get `email: null` and the flags `hasAccount` and `hasEmail`. Players get `hasAccount: false`, `hasEmail: false` and `email: null`. For a slot with an account, `email` is the email of the account, and the pending email is ignored.
- Assigning a player sends no email. The account of a slot is the account that owns the EMA id of the slot. The server computes it on each read and stores no link (`accountUid` and `hasAccount` in the players list are computed). If an account owns the EMA id, the player sees the tournament at once, but nobody told the player.
- An admin can set the EMA id of an existing account (`PUT /admin/users/:uid` with `emaId`, an empty text clears it). `POST /admin/users` also accepts `emaId`. Both routes check the EMA player and the owner first. An EMA id that belongs to another account returns `409`, and nothing changes. Every tournament that has this EMA id in a slot shows the account at once. No email is sent.
- **An email never links an account to an EMA id.** An email that an account owns already is refused for any other player. The import checks this before it writes, so a file never holds two rows with the same email.
- **Invite creates the account.** For a slot with no account, `send` creates the account without a password and without an alias (`createPlayerAccount`): the player role, the EMA id, the pending email of the slot, and access to every tournament that holds the EMA id. Then it sends the password reset email of Firebase, so the person can choose a password. There is no invitation token, no accept route and no list of invitations.
- **`PUT /tournaments/:id/player-emails` sets the pending email of slots.** The body is `{ players: [{ playerId, email }] }` (up to 200). An editor of the tournament may call it, also after the tournament started. An empty `email` clears it. The email is saved in lower case. The answer has one result per player: `SAVED`, `CLEARED` or `FAILED` with a `reason`: `PLAYER_NOT_FOUND`, `NO_EMA_ID`, `HAS_ACCOUNT` (the email of the account wins), `INVALID_EMAIL` or `EMAIL_IN_USE` (an account of another EMA id owns it, or another slot of the tournament holds it, also inside one request). A refused item changes nothing. The app uses it when "Invite" finds a slot with no email: it asks for the email, saves it, and then sends.
- **There is one action: `POST /tournaments/:id/player-accounts/send`.** The body lists the players: `{ players: [{ playerId }] }` (up to 200). The server picks the action for each player by the state of the slot, so a list can mix both states: an invitation (new account and password reset email) for a slot with no account, a reminder for a slot with an account. The answer has one result per player: `{ playerId, outcome, reason?, sentAt? }`. `sentAt` is the time of the email (ISO 8601) for `INVITED` and `REMINDED`. The Invitations dialog shows it under the text of the result.
  - `INVITED`: the slot had no account. The server created it and sent the password reset email.
  - `REMINDED`: the slot had an account. The server sent the reminder, an email with a link to the tournament (`/?tournament=<id>`) in the language of the `Accept-Language` header. It never sends a password reset to an account, so a person who has a password is not pushed to change it.
  - `FAILED` with a `reason`: `PLAYER_NOT_FOUND`, `NO_EMA_ID`, `NO_EMAIL` (no account and no pending email, which the app avoids by asking for the email first), `EMAIL_IN_USE` (an account owns the email), `EMAIL_FAILED` (the email could not be sent, the account stays and the next `send` resets the password) or `UNEXPECTED`.
- **The import overwrites all players and teams.** An editor of the tournament runs it.
  - `GET /tournaments/:id/player-import/export`: writes the players as an Excel file with the columns `id`, `ema id`, `email` and `team`. The email column is the account email, or the pending email when the slot has no account. Only admins get the emails. For everyone else the column is empty.
  - `POST /tournaments/:id/player-import/preview`: reads a CSV or Excel file (`fileName`, `dataBase64`) and checks it. It changes nothing. The answer has `rows` (with their `issues`), `valid`, `tournamentStarted`, `existingPlayers` and `importedPlayers`.
  - `POST /tournaments/:id/player-import/apply`: reads the same file again, checks it again and writes everything in one request. It answers `{ players, changedSlots, emptiedSlots }`. It creates no account and sends no email. It refuses with `409` and `details.code` `FILE_HAS_ERRORS` when the file has an error, and with `TOURNAMENT_STARTED` when a table has data.
  - The first line of the file names the columns. Required: `id` (the slot number) and `ema id`. Optional: `email` and `team`. The delimiter of a CSV file can be a comma, a semicolon or a tab. Excel files use the first sheet.
  - **Any error blocks the whole file.** The errors are: `INVALID_ID`, `UNKNOWN_SLOT`, `DUPLICATE_SLOT`, `MISSING_EMA_ID` (an email with no EMA number), `INVALID_EMA_ID`, `UNKNOWN_EMA_ID`, `DUPLICATE_EMA_ID`, `INVALID_EMAIL`, `DUPLICATE_EMAIL` (compared in lower case), `EMAIL_OWNED_BY_OTHER_ACCOUNT` (an account with another EMA id, or with none, owns the email), `TEAM_NAME_TOO_LONG`, `TEAM_NAME_CONFLICT` and `TEAM_NAME_IN_USE`. An account that owns the same EMA id may own the email. A team column in a tournament with no teams is only a warning (`TEAM_IGNORED`).
  - **The file replaces everything.** Every slot gets the EMA number, the pending email and the country of its row. A row with an id and nothing else, and a slot that the file does not list, become empty slots. A new person starts with no substitution. The log `playerLog` records each change of person. With a team column, every team gets the name of the file, or `Team N` when the file does not name it. Without a team column, the team names stay.
  - **A tournament with table data refuses the import** (`tablesWithProgress > 0`), because the tables, hands and statistics belong to the people of the slots. Use the substitution of a player instead.
- **Password resets for many accounts are an admin action in App users.** `POST /admin/users/password-resets` takes `{ uids: [...] }` (up to 200, repeated uids count once) and sends the password reset email to each account. Only admins call it. The answer has one result per account: `{ uid, outcome: SENT | FAILED, reason? }` with the reasons `NOT_FOUND`, `DISABLED`, `NO_EMAIL` and `EMAIL_FAILED`. A failed account does not stop the others. The app sends longer lists in several requests.
- An account has no alias at first. The person chooses it with `PUT /auth/me`.
- Giving an account an EMA id reserves it (`emaIdIndex/{emaId}`). One account owns one EMA id. `npm run audit:ema-ids` checks that the profiles and the index agree, and that the `player` claim goes with the EMA id (clearing the EMA id removes the claim). `npm run cleanup:player-claims` previews and `:apply` fixes accounts that broke this rule.
- Changing the EMA id of a slot gives the slot the account of the new EMA id and moves the pending email with the person.
- The reminder goes out through `nodemailer` and the SMTP mailbox of the project (`mailService.ts`, texts in `i18n.ts`). The secret `MTS_SMTP_URI` holds the SMTP address. `MAIL_FROM` and `PLAYER_APP_URL` are in `firebase/functions/.env`. The password reset email goes out through the Google password API (`firebaseAuthRest.ts`). If an email fails, the account stays. The result of that player is `FAILED` with `EMAIL_FAILED` and the editor sends again.
- A player sees only the tournaments where the account is registered. `GET /tournaments` and `GET /ema-player-registry` return only that scope to a player-only account. The players list of a tournament hides accounts and emails for players.
- A slot holds a registered EMA player or nothing. There are no non-member players and no provisional EMA ids.
- **Cleanup of old data.** `npm run cleanup:emails-and-guests` previews, and `npm run cleanup:emails-and-guests:apply` removes the retired data: `nonMember` (the slot becomes an open slot), `invitedEmail`, `invitationStatus`, `invitedAt` and `notifiedAt` of slots, and every `playerInvitations` document. `npm run cleanup:account-links` previews, and `npm run cleanup:account-links:apply` removes the stored account links: `accountUid` of slots and every `users/{uid}/playerTournaments` document.


## Firestore data model

- `users/{uid}`
- `emaPlayerRegistry/{emaId}` (shared EMA registry, populated by the EMA sync)
- `countries/{countryCode}` with `{ code, name }`, for example `ES` and `Spain`
- `tournaments/{tournamentId}`
- `tournaments/{tournamentId}/members/{uid}`
- `tournaments/{tournamentId}/players/{playerId}` with the pending `email` of the import (admins only read it)
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

- `/auth/signIn`, `/auth/passwordReset` and `/auth/refresh` allow a limited number of requests for each client address. The counter lives in one function instance.
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
