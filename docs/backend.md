# Backend (Firebase Functions + Firestore)

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
- `emaId` (required)

Creates a Firebase Auth email/password user and writes:

- `users/{uid}` profile
- `emaIdUsers/{emaId}` mapping → `{ uid, email }`

### Sign in (email or EMA id)

`POST /auth/signIn`

Body:

- `identifier` (email or emaId)
- `password`

If `identifier` is an emaId, the function resolves it via `emaIdUsers/{emaId}`.

### Refresh

`POST /auth/refresh`

Body:

- `refreshToken`

Clients should refresh tokens when receiving `401 unauthenticated`.

## Roles

- Global: `superadmin` (Firebase custom claim)
- Per tournament membership: `ADMIN | EDITOR | READER`

Membership is stored at:

- `tournaments/{tournamentId}/members/{uid}`

## Firestore data model

- `users/{uid}`
- `emaIdUsers/{emaId}`
- `emaPlayers/{emaId}` (global pool, populated later by a scraper job)
- `tournaments/{tournamentId}`
- `tournaments/{tournamentId}/members/{uid}`
- `tournaments/{tournamentId}/players/{playerId}`
- `tournaments/{tournamentId}/teams/{teamId}`
- `tournaments/{tournamentId}/tables/{tableKey}` (tableKey: `{roundId}_{tableId}`)
- `tournaments/{tournamentId}/tables/{tableKey}/hands/{handId}`
- (optional later) `tournaments/{tournamentId}/playerStats/{playerId}` for fast rankings

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
