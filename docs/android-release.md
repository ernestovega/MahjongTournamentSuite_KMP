# Android release

Both Android apps (`androidApp`, the admin app, and `playerAndroidApp`, the player app) are published on Google Play
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

Build the bundles for Google Play:

```bash
./gradlew :androidApp:bundleRelease :playerAndroidApp:bundleRelease
```

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
