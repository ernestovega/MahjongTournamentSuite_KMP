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

The current client sends the email as `email`. During deployment, the function also accepts the old
`identifier` field so older clients and newly deployed functions can coexist.

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

Admins manage all tournaments and the EMA registry. Editors manage content and accounts for assigned tournaments.
Only admins can create, delete, rename, or configure tournaments. Disabling an account keeps its profile and history.

Management routes use `requireEditor`. It accepts the `admin` and `editor` claims and refuses player-only accounts.
Read routes of a tournament use `requireTournamentReader`. It accepts admins, assigned editors and players who are registered in the tournament.
`GET /admin/whoami` and `GET /auth/me` return `roles`. `whoami` also returns `role`, the highest capability.

## Player accounts

- Assigning a player sends no email. If an account already owns the EMA id, the assignment links the slot to that account at once (`accountUid`, `invitationStatus: ACCEPTED`). The player then sees the tournament, but nobody told the player.
- An admin can set the EMA id of an existing account (`PUT /admin/users/:uid` with `emaId`, an empty text clears it). `POST /admin/users` also accepts `emaId`. Both routes check the EMA player and the owner first. An EMA id that belongs to another account returns `409`, and nothing changes. Every tournament that has this EMA id in a slot links to the account at once. No email is sent.
- A send to the email of an existing account links that account at once and sends the registration email. It needs no invitation and no acceptance.
- An editor starts every email by hand. The actions work for one player or for many. The body lists the players: `{ players: [{ playerId, email? }] }`. The answer has one result per player (`INVITED`, `NOTIFIED`, `REVOKED`, `SKIPPED`, `FAILED`).
  - `POST /tournaments/:id/player-accounts/send`: invites a new player (uses the given or the saved email), or sends the "you are registered" email with a link to the tournament to a linked player. It skips players that were already contacted.
  - `POST /tournaments/:id/player-accounts/resend`: creates a new invitation link (the old link stops working), or sends the registration email again.
  - `POST /tournaments/:id/player-accounts/revoke`: cancels a pending invitation, or removes the account link.
  - `PUT /tournaments/:id/player-accounts/emails`: saves the email of players with no account, for a later send.
- The slot keeps `invitationStatus`, `invitedEmail` and the time of the last email (`contactedAt` in the players list). The editor app uses them to filter "not invited yet", "invited, waiting" and "contacted".
- The email has one link. The token is `{tournamentId}.{invitationId}.{secret}`. Only a hash of the secret is stored. The link works once and expires after 14 days.
- `POST /player/invitations/accept` is public. A new account sends `{ token, password }` and the server creates the account. An existing account signs in and sends `{ token }`.
- Accepting links the slot (`accountUid`, `invitationStatus`), registers the tournament for the account and reserves the EMA id (`emaIdIndex/{emaId}`). One account owns one EMA id.
- Changing the EMA id of a slot ends its invitation and account link.
- The email goes out through `nodemailer` and the SMTP mailbox of the project (`mailService.ts`). The secret `MTS_SMTP_URI` holds the SMTP address. `MAIL_FROM` and `PLAYER_APP_URL` are in `firebase/functions/.env`. If the SMTP server fails, the invitation stays. The result of that player is `FAILED` and the editor presses "Resend".
- A player sees only the tournaments where the account is registered. `GET /tournaments` and `GET /ema-player-registry` return only that scope to a player-only account. The players list of a tournament hides accounts and emails for players.

## Hand entry by a player

One player who sits at the table writes the hands down. There is no start step and no confirmation by the other players.
The table must be in hands mode, not completed, and all four seats must be assigned. Hands mode is the default for new tables.

**Seats.** A player of the table assigns the four seats: `POST /player/tournaments/:id/tables/:round/:table/seats` with
`playerEastId`, `playerSouthId`, `playerWestId`, `playerNorthId` and `expectedVersion`. The four ids must be the players of the table.
It works until the first hand is saved, and not when an editor entered totals.

1. `POST /player/tournaments/:id/tables/:round/:table/hands/preview` with `{ playerWinnerId, playerLooserId, handScore, isChickenHand }`.
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

Tournament documents store `eventStartDate` and `eventEndDate` as `YYYY-MM-DD`. Both dates are inclusive. Legacy documents with only `eventDate` use that value for both dates. Older documents without dates use their creation date as a fallback.

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

## Bootstrap admin

A one-time endpoint exists to grant the first `admin` custom claim.

Set an environment variable `BOOTSTRAP_KEY` in Functions and call:

`POST /admin/bootstrapAdmin` with header `X-Bootstrap-Key: <value>`.

Send an existing Firebase Authentication `uid` in the request body. The endpoint rejects the request when an enabled admin already exists.

For the full role migration order, deployment commands, and permission checks, see [Firebase role rollout](firebase-role-rollout.md).

## Required env vars (Functions)

- `FIREBASE_API_KEY` (Web API key from Firebase project settings)
- `BOOTSTRAP_KEY` (a random secret string)
