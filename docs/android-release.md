# Android release

## One app for the whole suite

`androidApp` (`com.etologic.mahjongtournamentsuite`, name **MTS**) is the one Android app of the suite. It holds both
applications, like the web site:

- It opens the player part (`playerApp`), which everyone uses, with or without an account.
- Admins and editors see the **Management** button in the top bar, and the chooser after the sign-in. It switches to
  the admin part (`adminApp`) inside the same app, with the same session. **Player view**, or Back on the first screen
  of the admin part, returns. Each part keeps its screens while the other one is open.
- The admin part has no sign-in of its own: without a session, or after a sign-out, it goes to the player part, as on
  the web (`leaveAdminForPlayerApp`, `SuiteAppHost`).
- The code is `androidApp/src/main/.../SuiteRoot.kt`. Both parts show the version of the app, which is the admin version
  (see [versioning.md](versioning.md)).

`playerAndroidApp` (`com.etologic.mahjongtournamentsuite.player`, **MTS Player**) is the old player app. Its last version
shows a notice at each start: the app has moved, with a button to the page of MTS on Google Play (`MovedAppNotice`).

### Moving the testers to the one app

1. Run the workflow **Tests and deploy** on `main` with the admin and player versions bumped. It uploads MTS (the
   merged app) and the last MTS Player (with the notice) to internal testing.
2. In Play Console, update the store listing of MTS (`com.etologic.mahjongtournamentsuite`): name, short and full
   description for players and organizers, and screenshots of both parts.
3. Tell the testers of MTS Player to install MTS. Add them to the internal testing list of MTS if they are not there.
4. After a few days, unpublish MTS Player in Play Console (**Release > Setup > Advanced settings > App availability >
   Unpublish**). Unpublishing keeps the package name reserved; it can never be used by another app.
5. Firebase: the Android app `...player` can stay in the project for the old installs. App Check with Play Integrity is
   needed only for MTS. Remove the player app and its SHA-1 from the Android API key when nobody uses it any more.

The steps below still apply to both apps while MTS Player exists.

## Play App Signing

Both Android apps (`androidApp` and `playerAndroidApp`) are published on Google Play
with **Play App Signing**: Google keeps the app signing key, and we sign every upload with our own **upload key**.
Each app has its own upload keystore.

## Upload keystores

The keystores and their passwords are never in the repository, which is public. Keep them in a folder outside the
repository (for example `~/keystores/`) and keep a copy of each file and its passwords in a password manager.
Losing an upload key is recoverable (Play Console lets you reset it), but it takes days.

Create one keystore per app:

```bash
mkdir -p ~/keystores
keytool -genkeypair -v -keystore ~/keystores/mts-admin-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
keytool -genkeypair -v -keystore ~/keystores/mts-player-upload.jks -alias upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

## Code shrinking

The release build runs R8 (`isMinifyEnabled` and `isShrinkResources`). It removes unused code and resources but keeps the class names (`-dontobfuscate` in `proguard-rules.pro`): the app reads route and exception names at run time, and crash reports stay readable. The UI tests run on the debug build, so check a release build by hand after changing the rules or adding a library that uses reflection.

## Signing the release build

The release build reads the keystore from `local.properties` (or from environment variables, for CI):

```properties
mts.signing.admin.storeFile=/Users/<you>/keystores/mts-admin-upload.jks
mts.signing.admin.storePassword=...
mts.signing.admin.keyAlias=upload
mts.signing.admin.keyPassword=...

mts.signing.player.storeFile=/Users/<you>/keystores/mts-player-upload.jks
mts.signing.player.storePassword=...
mts.signing.player.keyAlias=upload
mts.signing.player.keyPassword=...
```

| local.properties | Environment variable |
|---|---|
| `mts.signing.admin.storeFile` | `MTS_ADMIN_SIGNING_STORE_FILE` |
| `mts.signing.admin.storePassword` | `MTS_ADMIN_SIGNING_STORE_PASSWORD` |
| `mts.signing.admin.keyAlias` | `MTS_ADMIN_SIGNING_KEY_ALIAS` |
| `mts.signing.admin.keyPassword` | `MTS_ADMIN_SIGNING_KEY_PASSWORD` |

The player app uses the same names with `player` / `PLAYER`. Without these values the release build is not signed,
and the debug build is not affected.

Build the bundles for Google Play by hand (CI does it on every merge to `main`, see below):

```bash
./gradlew :androidApp:bundleRelease :playerAndroidApp:bundleRelease
```

## Automatic upload to Google Play

The job **Publish to Google Play** of the workflow **Tests and deploy** (`.github/workflows/firebase.yml`) runs when
the workflow is started by hand on `main` (Actions > Tests and deploy > Run workflow). Since 2026-11-11 it no longer
runs on every push, so small changes do not reach Google Play. Starting the workflow also deploys Firebase. For each app it asks Google Play which version codes the app
already has, and only builds and uploads the app when its version code in `gradle/libs.versions.toml` is new
(see [versioning.md](versioning.md)). A merge that does not bump an app does not upload it. When the version code is
lower than the highest one on Google Play, the job fails.

The new version goes to the **internal testing** track and is rolled out to the testers at once. To change that, add
repository variables in **Settings > Secrets and variables > Actions > Variables**:

| Variable | Default | Values |
|---|---|---|
| `PLAY_TRACK` | `internal` | `internal`, `alpha` (closed testing), `beta` (open testing), `production` |
| `PLAY_RELEASE_STATUS` | `completed` | `completed` (roll out at once), `draft` (prepare it, roll it out by hand in Play Console) |

The script that talks to Google Play is `scripts/play.py`. The job also runs from **Actions > Tests and deploy > Run
workflow** on `main`, for example to retry a failed upload.

### Setup

Google Play access uses the same service account and Workload Identity Federation as the Firebase deploy
([ci-deploy.md](ci-deploy.md)), so no Google key is stored in GitHub.

1. Enable the Google Play Android Developer API once (Cloud Shell, or run `scripts/setup-github-deploy.sh` again):

   ```bash
   gcloud services enable androidpublisher.googleapis.com --project mahjong-tournament-suite
   ```

2. In Play Console, **Users and permissions > Invite new users**, invite
   `github-deployer@mahjong-tournament-suite.iam.gserviceaccount.com`. Under **App permissions** add both apps with
   **Release to testing tracks** (add **Release to production...** only if `PLAY_TRACK` is `production`).
3. Add these secrets to the environment `production` (**Settings > Environments > production > Environment
   secrets**). From the Mac, with the GitHub CLI signed in:

   ```bash
   base64 -i ~/keystores/mts-admin-upload.jks | gh secret set MTS_ADMIN_UPLOAD_KEYSTORE_BASE64 --env production
   gh secret set MTS_ADMIN_UPLOAD_STORE_PASSWORD --env production   # asks for the value
   gh secret set MTS_ADMIN_UPLOAD_KEY_PASSWORD --env production
   base64 -i ~/keystores/mts-player-upload.jks | gh secret set MTS_PLAYER_UPLOAD_KEYSTORE_BASE64 --env production
   gh secret set MTS_PLAYER_UPLOAD_STORE_PASSWORD --env production
   gh secret set MTS_PLAYER_UPLOAD_KEY_PASSWORD --env production
   ```

   The key alias is `upload`. If a keystore uses another alias, add `MTS_ADMIN_UPLOAD_KEY_ALIAS` or
   `MTS_PLAYER_UPLOAD_KEY_ALIAS`. Without the secrets the job fails with a message that points here.

## Firebase API key

The Android API key in `google-services.json` is a public Firebase client key. In Google Cloud it is restricted to the
two package names and the SHA-1 of each certificate that signs an installed app:

- the debug keystore of each developer machine (`~/.android/debug.keystore`),
- the **app signing key** of each app, from Play Console → Test and release → App integrity
  (installs from Google Play are signed with this key, not with the upload key),
- the upload key, only if you install release builds by hand.

When a certificate is missing, that build cannot reach Firebase (Analytics stops sending data). Add each new SHA-1
in Google Cloud → APIs & Services → Credentials → "Android key (auto created by Firebase)", and also in the Firebase
console under the settings of each Android app.
