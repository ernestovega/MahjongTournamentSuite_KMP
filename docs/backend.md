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

## Auth

### Sign up

`POST /auth/signUp`

Body:

- `email` (required)
- `password` (required)

Creates a Firebase Auth email/password user and writes:

- `users/{uid}` profile

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

- Global: `superadmin` (Firebase custom claim)
- Per tournament membership: `ADMIN | EDITOR | READER`

Superadmins manage accounts and all tournament assignments. Tournament Admins manage users in their tournament.
Only a superadmin can grant Tournament Admin. Disabling a user keeps the profile and audit history.

## User management

- `GET /admin/users` lists Firebase Auth users and their profiles.
- `POST /admin/users` creates an account and sends a password-reset email.
- `PUT /admin/users/:uid` changes email, global role, and tournament assignments.
- `PUT /admin/users/:uid/disabled` disables or enables an account.

Users cannot disable themselves or change their own role. The last enabled superadmin cannot be demoted or disabled.

- `GET /tournaments/:tournamentId/users/lookup?email=...` finds a user for a Tournament Admin.
- Tournament Admins can assign Reader or Editor, change roles, and remove users.
- Only superadmins can grant the Tournament Admin role.

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

## Bootstrap superadmin

A one-time endpoint exists to grant the `superadmin` custom claim.

Set an environment variable `BOOTSTRAP_KEY` in Functions and call:

`POST /admin/bootstrapSuperadmin` with header `X-Bootstrap-Key: <value>`.

## Required env vars (Functions)

- `FIREBASE_API_KEY` (Web API key from Firebase project settings)
- `BOOTSTRAP_KEY` (a random secret string)
