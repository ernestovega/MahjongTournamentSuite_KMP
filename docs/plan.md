# MahjongTournamentSuite — KMP migration plan

_Last updated: 2026-04-11_

## Goals

- Replace the Windows Forms app with a **Kotlin Multiplatform** app targeting **Android + Desktop (JVM) + Web (Wasm)**.
- Use **Compose Multiplatform** for UI, **Koin** for DI, **Navigation Compose** for navigation, **Ktor client** + **kotlinx.serialization** for networking.
- **Sync across devices**.
- Implement **custom login + role system** (email + password + emaId).
- Provide **Excel export**.

## Not in scope (for now)

- Data migration from the WinForms/EF6 local DB.
- Reader app + public access flows.
- 4-player “confirm hand write” workflow.
- Background polling; refresh happens only on screen open, after writes, or via a refresh button.

## Architecture (chosen)

### Client (KMP)

- Clean architecture modules:
  - `domain`: models + repository contracts (no platform/framework dependencies)
  - `data`: Ktor backend client + repository implementations
  - `composeApp`: UI + navigation + DI bootstrap
  - `androidApp`: Android entrypoint only
- **No Firebase SDK usage in the client**.
- All backend calls go through a single Ktor-based client implementation.

### Backend (Firebase)

- **Firestore rules: deny all**.
- All reads/writes go through **HTTPS Firebase Cloud Functions** (Node/TypeScript).
- Functions use `firebase-admin` to access Auth/Firestore.

## Auth + roles (chosen)

- **Sign-up**: `email + password + emaId`
- **Sign-in**: accepts a single `identifier` that can be **email OR emaId** (server resolves).
- Roles:
  - Global: `superadmin` (Firebase custom claim)
  - Per tournament membership: `ADMIN | EDITOR | READER`

## Firestore structure (current draft)

- Identity:
  - `users/{uid}`
  - `emaIdUsers/{emaId}` → `{ uid, email }`
- Global player pool (future scraper output):
  - `emaPlayers/{emaId}`
- Tournament:
  - `tournaments/{tournamentId}`
  - `tournaments/{tournamentId}/members/{uid}`
  - `tournaments/{tournamentId}/players/{playerId}`
  - `tournaments/{tournamentId}/teams/{teamId}`
  - `tournaments/{tournamentId}/tables/{tableKey}`
  - `tournaments/{tournamentId}/tables/{tableKey}/hands/{handId}`

Notes:
- Keep “hands” as individual documents (avoid large arrays; document size limit).
- Expect growth: many tournaments + many hands => design reads/writes to be role-scoped.

---

# Current status

## Backend

Implemented (Firebase Functions):

- Auth:
  - `POST /auth/signUp`
  - `POST /auth/signIn`
  - `POST /auth/refresh`
  - `GET /auth/me`
- Admin:
  - `POST /admin/bootstrapSuperadmin` (one-time; uses `X-Bootstrap-Key`)
  - `GET /admin/whoami`
  - `GET /admin/users/lookup?identifier=...` (email or emaId)
- Tournaments:
  - `GET /tournaments` (superadmin: all; others: membership derived)
  - `POST /tournaments` (superadmin only)
  - `GET /tournaments/:tournamentId/members`
  - `PUT /tournaments/:tournamentId/members/:uid`
  - `DELETE /tournaments/:tournamentId/members/:uid`
  - `GET /tournaments/:tournamentId` is still a placeholder.

Rules + docs:
- `firebase/firestore.rules`: deny all
- `docs/backend.md`: backend overview + env vars

## KMP shared modules

- `data`:
  - Ktor client + DTOs + `FunctionsBackendApi`
  - `AuthRepository`, `AdminRepository`, `TournamentRepository` implementations
  - Automatic **refresh-token retry** once on `401`
  - Session storage is **in-memory only** (no persistence yet)
- `domain`:
  - Models (`AuthSession`, `UserProfile`, `Tournament*`, `AdminStatus`)
  - Repository contracts
- Koin wiring in `data/di` is in place.

## UI

- Admin UI screens are **not implemented yet**.
- `composeApp` still shows the starter “greeting” sample.

---

# Next steps (recommended order)

## 1) App configuration

- Decide how to set `ApiConfiguration.baseUrl`:
  - simplest: hardcode a dev URL for now
  - better: add a minimal “Settings” screen to edit base URL (persistence can come later)

## 2) Admin MVP UI (Compose)

Build the admin-side app flow (no reader app yet):

- Auth:
  - Sign Up (email, emaId, password)
  - Sign In (identifier: email or emaId, password)
  - Sign Out
- Tournaments:
  - List tournaments (with refresh)
  - Create tournament (superadmin only)
- Members:
  - View tournament members
  - Lookup user by email/emaId (superadmin only)
  - Assign member role (ADMIN/EDITOR/READER)

## 3) Persist auth session

- Store `AuthSession` in a simple local store:
  - Phase 1: file-based JSON in `data` (per platform implementation)
  - Phase 2: introduce a proper local DB/cache if needed

## 4) Excel export (must-have)

Preferred approach (cross-platform friendly):

- Add backend endpoint: `GET /tournaments/:tournamentId/export.xlsx`
  - Server queries Firestore and generates `.xlsx` (Node library like `exceljs`)
  - Returns as streamed download or uploads to Storage and returns a short-lived URL
- Client just downloads the file and triggers “save/share” via platform APIs.

## 5) Scraper (defer scheduling, keep manual)

- Add a backend endpoint to fetch + parse the website HTML and update `emaPlayers/{emaId}`.
- Trigger it from a button in the admin UI.
- Later: schedule it via Firebase scheduled functions / Cloud Scheduler.

---

# Phase plan (high-level)

## Phase A — Admin MVP (auth + tournaments)

Deliverables:
- Working sign-in/sign-up
- Superadmin bootstrap
- Tournament list/create
- Member management

## Phase B — Tournament operations

Deliverables:
- Tournament details screen
- Players/teams CRUD
- Tables + hands entry (initial cut)

## Phase C — Exports + reporting

Deliverables:
- Excel export for tournaments (hands + standings as agreed)
- Optional: cached computed rankings for fast export

## Phase D — Parity with WinForms logic

Deliverables:
- Port scoring + ranking + schedule algorithms into `domain` with unit tests
- Replace any remaining “manual” calculations with deterministic domain logic

---

# Open questions / decisions to confirm

- Excel export format: exactly which sheets/columns should match the WinForms output?
- Countries partitioning: should `tournaments` be filtered by `countryCode` as a first-class field?
- Tournament ownership model: is creator always an ADMIN member automatically? (currently planned/implemented)
