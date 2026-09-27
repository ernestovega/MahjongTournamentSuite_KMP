# Firebase role rollout

This rollout replaces the deployed legacy role API with the `EDITOR` and `ADMIN` model.

## Current state

- The active project is `mahjong-tournament-suite`.
- The deployed `api` revision is `api-00029-yar`.
- The function uses Node.js 22 in `europe-west1`.
- The management web application is live at `https://mahjong-tournament-suite.web.app`.
- Firebase Hosting forwards `/api/**` to the deployed `api` function.
- Both required secrets exist.
- Firestore and Storage deny all direct client access.
- A Functions deployment dry run passes.
- The deployed `whoami` response uses the `EDITOR` and `ADMIN` format.
- The access reset left one enabled admin and no tournament assignments.
- The `members.uid` collection-group index is deployed and ready.
- The live role permission verification passes.

The new clients require `whoami.role`. The deployed backend now provides this field.

The Android and desktop clients build locally. They are not distributed by Firebase Hosting.

## Required order

1. Deploy the `api` function.
2. Check `/health` and `/version`.
3. Run the access-reset script in dry-run mode.
4. Review the selected admin email and affected counts.
5. Run the access reset in apply mode.
6. Use password reset if the selected admin has no password.
7. Sign in as `ADMIN` and create one `EDITOR` account.
8. Assign the editor to one test tournament.
9. Run the role checks below.

Do not run the access reset against an older backend. Older revisions do not return the response required by the new client.

## Deployment commands

Run the validation command from the repository root:

```bash
npx -y firebase-tools@latest deploy \
  --only functions:api \
  --dry-run \
  --project mahjong-tournament-suite \
  --non-interactive
```

Deploy only after the dry run succeeds:

```bash
npx -y firebase-tools@latest deploy \
  --only functions:api \
  --project mahjong-tournament-suite
```

The Firebase CLI currently reports that `firebase-functions` is outdated. The current version builds and passes deployment validation. Update it in a separate change because the latest version can contain breaking changes.

## Web deployment

Build the production WebAssembly application from the repository root:

```bash
./gradlew :composeApp:wasmJsBrowserDistribution
```

Firebase Hosting reads the generated files from `composeApp/build/dist/wasmJs/productionExecutable`.

Deploy a temporary channel before the live site:

```bash
npx -y firebase-tools@latest hosting:channel:deploy roles-ready \
  --expires 1d \
  --project mahjong-tournament-suite
```

Check the application page, `/api/health`, and `/api/version` on the temporary URL. Then deploy the live site:

```bash
npx -y firebase-tools@latest deploy \
  --only hosting \
  --project mahjong-tournament-suite
```

The live management application URL is `https://mahjong-tournament-suite.web.app`.

## Admin checks

Confirm these results with an `ADMIN` account:

- all tournaments are visible;
- create, rename, configure, and delete actions are visible;
- global account management is available;
- admin accounts can be created and edited;
- accounts can be enabled and disabled;
- the shared EMA player registry is editable;
- admin rows show global access in a tournament;
- an admin cannot be removed from a tournament;
- the last enabled admin cannot be disabled or demoted.

## Editor checks

Confirm these results with an `EDITOR` account:

- only assigned tournaments are visible;
- tournament content is editable;
- tournament creation, configuration, rename, and deletion are unavailable;
- editor accounts can be created and edited;
- editors can be assigned only to accessible tournaments;
- admin accounts cannot be edited;
- account enable and disable actions are unavailable;
- the shared EMA player registry is read-only;
- other editors can be assigned and removed from an accessible tournament.

The `npm run verify:roles` script performs these backend checks with one temporary admin and two temporary editor accounts. It always attempts to remove the temporary accounts, profiles, and assignments.

## Direct database security

Firestore and Storage reject every direct client read and write. All application access goes through the authenticated `api` function.

This prevents client code from bypassing backend role checks. Keep these rules closed unless a future client requires direct Firebase SDK access.
