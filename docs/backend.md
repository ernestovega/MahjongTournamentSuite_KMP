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

- **The email lives only in the account.** A slot holds no email. The players list of a tournament (`GET /tournaments/:id/players`) adds `email`, the email of the linked account. A player who reads the list gets `email: null`.
- Assigning a player sends no email. If an account already owns the EMA id, the assignment links the slot to that account at once (`accountUid`). The player then sees the tournament, but nobody told the player.
- An admin can set the EMA id of an existing account (`PUT /admin/users/:uid` with `emaId`, an empty text clears it). `POST /admin/users` also accepts `emaId`. Both routes check the EMA player and the owner first. An EMA id that belongs to another account returns `409`, and nothing changes. Every tournament that has this EMA id in a slot links to the account at once. No email is sent.
- **An email never links an account to an EMA id.** Several people can share one email, for example a couple or a team that one person registers. An email that an account owns already is refused for any other player.
- **A player with no account gets one from an email.** The server creates the account without a password (`createPlayerAccount`): the player role, the EMA id, the EMA name as alias, a link to every tournament that holds the EMA id. The server sends no email at this step.
- **Invite** sends the password reset email of Firebase to an account that has no password yet. The person chooses a password with it. There is no invitation token, no accept route and no list of invitations.
- An editor starts every email by hand. The actions work for one player or for many. The body lists the players: `{ players: [{ playerId, email? }] }`. The answer has one result per player (`INVITED`, `NOTIFIED`, `REVOKED`, `SKIPPED`, `FAILED`).
  - `POST /tournaments/:id/player-accounts/send`: for a slot with no account, it needs an `email`, creates the account and sends the password reset email (`INVITED`). For an account with no password, it sends the password reset email (`INVITED`). For an account with a password, it sends the "you are registered" email with a link to the tournament (`NOTIFIED`). It skips players that were contacted already. An `email` that differs from the email of the account returns `FAILED`, and the account email stays.
  - `POST /tournaments/:id/player-accounts/resend`: the same, also for players that were contacted.
  - `POST /tournaments/:id/player-accounts/revoke`: removes the link between the slot and the account in this tournament. The account and its EMA id stay.
  - `PUT /tournaments/:id/player-accounts/emails`: saves emails without sending anything. A player with no account gets an account without a password. The answer has one result per item: `CREATED`, `UNCHANGED`, `KEPT_ACCOUNT_EMAIL` (the account has another email and keeps it) or `FAILED`.
  - `GET /tournaments/:id/player-import/export`: writes the players as an Excel file with the columns `id`, `ema id`, `email` and `team`. The email is the email of the linked account.
  - `POST /tournaments/:id/player-import/preview`: reads a CSV or Excel file (`fileName`, `dataBase64`) and returns what an import would do. It changes nothing.
  - `POST /tournaments/:id/player-import/apply`: reads the same file again and runs one step of the import. It needs `replaceExisting: true` to replace a player that is in a slot already.
    - The app runs the steps in order, so that no request lasts long: `phase: "CLEAR"` once (empties the slots whose player moves), `phase: "ASSIGN"` with up to 25 `rows` (line numbers) for each batch, and `phase: "FINISH"` once (creates the accounts of the new emails and renames the teams).
    - The first line of the file names the columns. Required: `id` (the slot number) and `ema id`. Optional: `email` and `team`.
    - The delimiter of a CSV file can be a comma, a semicolon or a tab. Excel files use the first sheet.
    - A row has an error when the slot or the EMA number is unknown, when a slot or an EMA number appears twice, or when the email or the team name is wrong. The import skips these rows and applies the others.
    - Players can move between slots in one file. The server clears the slots that give up a player first, so an EMA number is never in two slots.
    - **Emails.** The email of the account always wins. If the account of the player has another email, the import does not save the new email and the preview warns (`EMAIL_KEPT_ACCOUNT`). If another account owns the email, or an earlier row of the file uses it, the import does not save it and the preview warns (`EMAIL_IN_USE`). In both cases the rest of the row applies. A new email for a player with no account creates the account. The import sends no message.
    - The team name renames the team of the slot. Team members stay in their fixed slots.
- The slot keeps `accountUid` and the time of the last email (`notifiedAt`, `contactedAt` in the players list). `invitationStatus` in the players list is computed: `NONE` (no account, or an account with no password that got no email), `INVITED` (no password yet and an email went out) or `ACCEPTED` (the account has a password). The editor app uses it to filter "not invited yet", "invited, waiting" and "contacted".
- Linking registers the tournament for the account (`users/{uid}/playerTournaments/{tournamentId}`) and reserves the EMA id (`emaIdIndex/{emaId}`). One account owns one EMA id.
- A new account gets the full EMA name of the player (first name and last name) as its alias. The user can change the alias later with `PUT /auth/me`.
- Changing the EMA id of a slot ends its account link.
- The registration email goes out through `nodemailer` and the SMTP mailbox of the project (`mailService.ts`). The password reset email goes out through the Google password API (`firebaseAuthRest.ts`). The secret `MTS_SMTP_URI` holds the SMTP address. `MAIL_FROM` and `PLAYER_APP_URL` are in `firebase/functions/.env`. If an email fails, the account and the link stay. The result of that player is `FAILED` and the editor presses "Resend".
- A player sees only the tournaments where the account is registered. `GET /tournaments` and `GET /ema-player-registry` return only that scope to a player-only account. The players list of a tournament hides accounts and emails for players.
- A slot holds a registered EMA player or nothing. There are no non-member players and no provisional EMA ids.
- **Cleanup of old data.** `npm run cleanup:emails-and-guests` previews, and `npm run cleanup:emails-and-guests:apply` removes the retired data: `nonMember` (the slot becomes an open slot), `invitedEmail`, `invitationStatus` and `invitedAt` of slots, and every `playerInvitations` document.


## Firestore data model

- `users/{uid}`
- `emaPlayerRegistry/{emaId}` (shared EMA registry, populated by the EMA sync)
- `countries/{countryCode}` with `{ code, name }`, for example `ES` and `Spain`
- `tournaments/{tournamentId}`
- `tournaments/{tournamentId}/members/{uid}`
- `tournaments/{tournamentId}/players/{playerId}` with `accountUid` and `notifiedAt` after the player is linked to an account
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
