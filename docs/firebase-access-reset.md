# Firebase access reset

This script resets management accounts and tournament assignments. It keeps tournament content and the EMA player registry.

## Scope

The script performs these actions:

- keeps or creates one Firebase Authentication account for the first `ADMIN`;
- deletes all other Firebase Authentication accounts;
- deletes all documents in the top-level `users` collection;
- deletes all tournament `members` documents;
- creates one clean profile for the selected `ADMIN`;
- sets only the `admin: true` custom claim on that account;
- verifies the final account, profile, claim, and assignment counts.

The script does not delete tournaments, rounds, tables, players, results, countries, or EMA registry records.

## Authentication

Run the script from a trusted local environment. Firebase CLI login does not replace Application Default Credentials.

Use one of these credential methods:

- set `GOOGLE_APPLICATION_CREDENTIALS` to a service-account file with Firebase Auth and Firestore access;
- use Application Default Credentials from Google Cloud CLI.

Never commit a service-account file.

## Dry run

Run this command from `firebase/functions`:

```bash
npm run reset:access -- \
  --project mahjong-tournament-suite \
  --admin-email admin@example.com \
  --admin-name "Tournament Admin"
```

The dry run lists the affected counts. It does not change remote data.

## Apply the reset

Review the dry-run output first. Then add both confirmation arguments:

```bash
npm run reset:access -- \
  --project mahjong-tournament-suite \
  --admin-email admin@example.com \
  --admin-name "Tournament Admin" \
  --apply \
  --confirm-project=mahjong-tournament-suite
```

The script writes a local JSON inventory before the first remote change. It stores inventories in `firebase/functions/.firebase-reset-backups/` with file mode `0600`.

The inventory contains account metadata, claims, profiles, and assignments. It does not contain passwords or password hashes. It cannot restore sign-in credentials.

If the admin account is new and has no password, use the application password-reset screen before the first sign-in.
