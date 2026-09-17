# EMA player registry and sync

This document describes the shared EMA player registry. Tournament player slots remain separate records.

## Purpose

The registry stores one record for each EMA number. Tournament result pages provide the source data.

Player names use Unicode NFC normalization and uppercase letters. This rule preserves accents and other special characters.

The sync merges duplicate source entries by EMA number. It never creates two registry records for one EMA number.

## Firestore and Storage

The sync uses these locations:

- `emaPlayerRegistry/{emaId}`: shared player records.
- `emaPlayerRegistrySyncReports/{reportId}`: sync results and backup subcollections.
- `emaPlayerRegistrySyncState/current`: last successful sync and report ID.
- `emaRankingTournamentResultIndex/{resultPage}`: processed EMA result pages.
- `playerPhotos/{emaId}.{extension}`: Firebase Storage photo copies.

Tournament records use `tournaments/{tournamentId}/players/{playerId}`. The tournament `playerId` is local to that tournament. Its `assignedEmaId` points to the registry.

## Access control

Firestore and Storage client rules deny every direct read and write. Clients must use the authenticated HTTPS API.

| Action | Required access |
| --- | --- |
| Read the player registry | Signed-in user |
| Add or edit a base player | `admin` or `superadmin` custom claim |
| Replace a base player photo | `admin` or `superadmin` custom claim |
| Assign a base player in a tournament | Tournament `EDITOR`, `ADMIN`, or `superadmin` |
| Manage tournament members | Tournament `ADMIN` or `superadmin` |
| Create or delete a tournament | `superadmin` |

There is no player-delete API. The sync never deletes registry records. This protects tournament history.

Custom claims are checked on every protected API request. After changing a claim, the user must sign out and sign in again to receive a new token.

The current registry read endpoint requires sign-in. This is safer than anonymous public access. Change that rule only if the player data should be public outside the app.

## Initial seed

The initial seed crawls all EMA tournament result pages and their player detail pages. It imports EMA number, full name, country, source URLs, and available photos.

The local CLI reads live EMA pages from your computer. It does not use a mirror or the Firebase server for scraping.

From the Functions directory, run the initial seed:

```bash
npm run build
npm run seed:players
```

Create `firebase/functions/local-player-sync.env` first. Add your Firebase project and bucket:

```text
EMA_FIREBASE_PROJECT=mahjong-tournament-suite
EMA_FIREBASE_STORAGE_BUCKET=mahjong-tournament-suite.firebasestorage.app
```

The package scripts load this file automatically. Keep it out of source control. Do not put local sync settings in `.env`, because Firebase deploys that file as function configuration.

For later local runs, use the incremental sync:

```bash
npm run sync:players
```

Both commands read only tournament result pages. The seed reads all known pages. Later runs read only pages that are not in `emaRankingTournamentResultIndex`.

To normalize names already stored in the registry, inspect the changes first:

```bash
npm run normalize:player-names
```

Apply the reported changes with a backup:

```bash
npm run normalize:player-names -- --apply
```

Each applied migration stores the prior documents under `emaPlayerRegistryNameMigrations/{migrationId}/backupPlayers`.

The scripts read the tournament index pages on each run. They do this only to discover new result links. They do not re-read result pages already indexed.

For a one-time repair of records created from an old downloaded copy, set `EMA_LOCAL_SOURCE_DIR` to that copy and run the seed. This mode is explicit and is not used by normal syncs:

```bash
EMA_LOCAL_SOURCE_DIR="$HOME/ema-mirror" npm run seed:players
```

The reader supports both a direct `ranking/` folder and the host folder created by `wget --mirror`. Remove the variable after this repair.

The command writes production Firestore and Storage data. Check the Firebase project before running it.

## Incremental sync

The local incremental sync reads the EMA tournament indexes and processes result pages not in `emaRankingTournamentResultIndex`.

New result pages are marked as processed only after the player update completes. A failed run retries those pages.

The sync does not delete players. It reports no deletions because tournament history is the source of truth.

## Photo behavior

The initial seed downloads each available photo once. Later runs send a conditional request with the saved ETag. An unchanged photo returns `304 Not Modified` and is not downloaded again.

Admins can replace a photo in the app. The upload accepts JPEG, PNG, WebP, and GIF files up to 5 MB.

Changed photos replace the same Storage path. The registry keeps a Firebase Storage download URL and the source ETag.

The app should show EMA attribution and the applicable CC BY-NC-SA 4.0 notice. Check each source page for different terms.

## Backups and restore

Before changing registry records, each run copies the complete current registry into its report document under `backupPlayers`.

To restore, select the report made immediately before the unwanted run. Copy every document from:

```text
emaPlayerRegistrySyncReports/{reportId}/backupPlayers/{emaId}
```

back to:

```text
emaPlayerRegistry/{emaId}
```

Use a script or a Firestore transaction for a large restore. Do not delete the report or backup first.

## Deployment

Build and deploy the Functions:

```bash
cd firebase/functions
npm run build
cd ../..
firebase deploy --only functions:api,firestore:rules,storage --project mahjong-tournament-suite
```

The Firebase project must have Firestore and Storage enabled.

Functions use Node.js 22. This avoids the Node.js 20 decommission date.

Photo sync also needs `EMA_FIREBASE_STORAGE_BUCKET`. Find the bucket after enabling Storage:

```bash
gcloud auth login
gcloud storage buckets list --project=mahjong-tournament-suite --format='value(name)'
```

Set the returned bucket name before a local seed:

```bash
export EMA_FIREBASE_STORAGE_BUCKET=your-project.firebasestorage.app
npm run seed:players
```

## Troubleshooting

`fetch failed` or `ConnectTimeoutError` means the local computer could not reach EMA. Check the browser connection and run the command again.

`ERR_TLS_CERT_ALTNAME_INVALID` means EMA's HTTPS certificate does not match its hostname. For a local run only, you can use:

```bash
EMA_ALLOW_INSECURE_TLS=1 npm run seed:players
```

This option is not used by Firebase services.

`Unable to detect a Project Id` means local Application Default Credentials lack a project. Run:

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project mahjong-tournament-suite
```

The seed prints progress while it reads pages and stores records. It uses limited concurrency to reduce EMA traffic.
