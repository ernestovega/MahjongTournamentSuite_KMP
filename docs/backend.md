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

- Global roles: `EDITOR | ADMIN`
- Firebase custom claim: `admin: true` for administrators
- Per-tournament editor assignment: `tournaments/{tournamentId}/members/{uid}`

Admins manage all tournaments and the EMA registry. Editors manage content and accounts for assigned tournaments.
Only admins can create, delete, rename, or configure tournaments. Disabling an account keeps its profile and history.

## User management

- `GET /admin/users` lists Firebase Auth users and their profiles.
- `POST /admin/users` creates an account and sends a password-reset email.
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
- `tournaments/{tournamentId}/players/{playerId}`
- `tournaments/{tournamentId}/teams/{teamId}`
- `tournaments/{tournamentId}/tables/{tableKey}` (tableKey: `{roundId}_{tableId}`)
- `tournaments/{tournamentId}/tables/{tableKey}/hands/{handId}` with hand fields including `isChickenHand` and `isDone`
- (optional later) `tournaments/{tournamentId}/playerStats/{playerId}` for fast rankings

Tournament documents store `eventStartDate` and `eventEndDate` as `YYYY-MM-DD`. Both dates are inclusive. Legacy documents with only `eventDate` use that value for both dates. Older documents without dates use their creation date as a fallback.

## Seed countries

The country picker reads `countries/{countryCode}` documents. To write the full ISO country list, set Application Default Credentials for the Firebase project, then run:

```bash
cd firebase/functions
npm run seed:countries
```

## Refresh strategy (client)

No background polling for now.

- Load when a screen opens
- Reload after any write
- Manual refresh button

## Bootstrap admin

A one-time endpoint exists to grant the first `admin` custom claim.

Set an environment variable `BOOTSTRAP_KEY` in Functions and call:

`POST /admin/bootstrapAdmin` with header `X-Bootstrap-Key: <value>`.

Send an existing Firebase Authentication `uid` in the request body. The endpoint rejects the request when an enabled admin already exists.

For the full role migration order, deployment commands, and permission checks, see [Firebase role rollout](firebase-role-rollout.md).

## Required env vars (Functions)

- `FIREBASE_API_KEY` (Web API key from Firebase project settings)
- `BOOTSTRAP_KEY` (a random secret string)
