# Desktop app

The desktop app is the suite, like the Android and iOS apps: it opens the player part, and admins and editors switch to
the admin part with the button of the top bar (`SuiteRoot`). It runs on macOS (Apple silicon), Windows and Linux.

| | |
|---|---|
| Module | `:suite` (`jvmMain/DesktopMain.kt`), packaged with Compose Desktop (jpackage) |
| Version | The admin version (`admin-versionName`), like the Android and iOS apps. See [versioning.md](versioning.md) |
| Backend | Production only |
| Installers | macOS `.dmg` (signed and notarized), Windows `.msi` (per user, no administrator), Linux `.deb` |
| Where | GitHub releases tagged `desktop-v<version>` |
| Updates | At start the app asks GitHub for the newest `desktop-v*` release and offers it in a dialog (`DesktopUpdates.kt`) |

## Run it

```bash
./gradlew :suite:run       # the suite, as installed
./gradlew :adminApp:run    # the admin part alone, with its own sign-in (development)
```

Build an installer on its own system (jpackage cannot build for another one):

```bash
./gradlew :suite:packageDmg   # macOS
./gradlew :suite:packageMsi   # Windows
./gradlew :suite:packageDeb   # Linux
```

The installer lands in `suite/build/compose/binaries/main/<format>/`.

## Publish

1. Bump the admin version if the current one already has a desktop release: `python3 scripts/versions.py bump admin`.
2. Actions > **Publish · Desktop** > Run workflow on `main` (it does nothing on other branches; see docs/ci-deploy.md).
3. The workflow builds the three installers on macOS, Windows and Linux runners, signs and notarizes the DMG, and
   creates the release `desktop-v<version>` with the three files. A version that already has a release stops the run.

### Secrets

| Secret | What |
|---|---|
| `MAC_DEVELOPER_ID_P12` | The *Developer ID Application* certificate with its private key, exported as `.p12`, in base64 |
| `MAC_DEVELOPER_ID_P12_PASSWORD` | The password of that `.p12` |
| `APP_STORE_CONNECT_API_KEY` | The `.p8` key that TestFlight uses too. `notarytool` sends the DMG to Apple with it |
| `MTS_GA4_API_SECRET` | The analytics of the desktop app (`data/build.gradle.kts`) |

Without `MAC_DEVELOPER_ID_P12` the DMG is built unsigned: macOS then blocks it (Open anyway in System Settings >
Privacy & Security).

### The Developer ID certificate

Only the Account Holder of the Apple Developer account can create it. On the Mac:

1. Xcode > Settings > Accounts > the team > Manage Certificates > + > **Developer ID Application**.
2. Keychain Access > My Certificates > right click on *Developer ID Application: …* > Export > `.p12` with a password.
3. `base64 -i DeveloperID.p12 | pbcopy` and paste it in the secret `MAC_DEVELOPER_ID_P12`; the password goes in
   `MAC_DEVELOPER_ID_P12_PASSWORD`.

The certificate lasts five years. Apps signed before it expires keep opening.

## Notes

- Windows finds the installed app by `upgradeUuid` (`suite/build.gradle.kts`) and replaces it with a new version. It
  must never change.
- The Windows installer is not signed: SmartScreen warns the first time (More info > Run anyway). A code signing
  certificate would remove the warning.
- The session, the caches and the settings live in `~/.mahjongtournamentsuite` and the Java preferences, shared with
  `:adminApp:run`.
- The windows that the admin part opens apart (timer, rankings, QR of the tables) are windows of the desktop app.
