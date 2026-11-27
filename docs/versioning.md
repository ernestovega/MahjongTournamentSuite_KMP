# Versions

There are three products, and each one has its own version:

| Product | What it is | Where it is shown |
|---|---|---|
| **Web** | Both web apps (admin and player), served by Firebase Hosting. | Profile dialog and sign-in screen of both web apps, and `/version.json` on the site |
| **Admin app** | The Android app (`androidApp`), which holds the admin part and the player part, the iOS app and the desktop app (`:suite`). | Profile dialog of both parts on Android, and of the desktop app |
| **Backend** | Everything under `firebase/`: the functions (the API), the Firestore and Storage rules, and the indexes. | `GET /api/health` answers `{ "ok": true, "version": "…" }` |

Until November 2026 the backend counted as part of the web. It got its own version, starting at 1.0.0, so that a change in the API no longer raises the web version and the other way round.

There was a third product, the player app on Android (`playerAndroidApp`, `com.etologic.mahjongtournamentsuite.player`). It was removed in November 2026, when the Android app took the player part. See [android-release.md](android-release.md).

The versions are in `gradle/libs.versions.toml`:

```toml
web-versionName = "1.0.2"
admin-versionName = "1.0.1"
admin-versionCode = "1"
backend-versionName = "1.0.0"
```

The names are `MAJOR.MINOR.PATCH`. The patch counts the pull requests merged to `main` that changed the product, counted from PR #1: PR #2 set the web to 1.0.1 and PR #15 set the web to 1.0.2 and both apps to 1.0.1. The Android app also has a version code for Google Play, which goes up by one with every new version. The web and the backend have no version code. The build generates the object `AppVersion` in `domain` from these values, and the apps show the right one through `LocalAppVersion`. The functions build (`npm run build`) writes the backend version into `lib/backendVersion.json`, and `scripts/assemble-web-hosting.sh` writes the web version into `/version.json` of the site.

## See the published versions

The quickest way: open **https://mts.mahjongmadrid.com/version**. The backend answers it with a table of the three products: the latest version (on `main` when the backend was deployed) next to the published one, with ✓ when they match. The web comes from `/version.json`, the backend is the one that answers, and the Android app is read from Google Play (cached for 5 minutes). Google Play needs the service account of the functions in Play Console (Users and permissions, "View app information"); until then the page says which account to invite. The code is `firebase/functions/src/services/versionsPage.ts`.

The same table from a terminal or GitHub Actions:

```bash
python3 scripts/versions.py status
```

It prints, for each product, the version on `main` (the latest) next to the published one:

- **Web**: what `https://mts.mahjongmadrid.com/version.json` answers.
- **Backend**: what `https://mts.mahjongmadrid.com/api/health` answers.
- **Android app**: the release on each Google Play track (internal testing, closed, open, production).

Google Play needs credentials. The easy way, also from a phone: **Actions > Check · Published versions > Run workflow** on `main`, and read the table in the summary of the run. It only reads. The runs of **Deploy · Production** end with the same table. On a computer, Google Play is shown after `gcloud auth application-default login` with an account that has access to the app in Play Console, and `pip install google-auth google-api-python-client`; without them the command still shows the web and the backend. `MTS_SITE_URL` changes the site that it asks.

Every push to `main` deploys the web and the backend together (`firebase deploy`), so both are usually up to date a few minutes after a push. The Android app only goes to Google Play when the workflow is started by hand (see [android-release.md](android-release.md)), so its published version is usually behind.

## Bump a version

```bash
python3 scripts/versions.py bump web      # 1.0.2 -> 1.0.3
python3 scripts/versions.py bump admin    # 1.0.1 -> 1.0.2, code + 1
python3 scripts/versions.py bump backend  # 1.0.0 -> 1.0.1
python3 scripts/versions.py show
```

The script only raises the patch (the third number). MAJOR and MINOR are changed by hand in `gradle/libs.versions.toml` when the owner decides, setting the patch back to 0 and raising the version code of the apps by one. The check accepts that too.

## The check on pull requests

The **Build and test** job runs `python3 scripts/versions.py check` on every pull request to `main`. It looks at the files that the pull request changes and fails when a product that those files go into does not have a higher version than on `main`. The error message says which command to run.

Which files go into which product:

- `domain`, `data`, `ui`: both. Their `wasmJsMain`/`webMain` sources only go into the web, and `androidMain` and `jvmMain` into the admin app.
- `adminApp`: web and admin app. `playerApp`: web and admin app (the Android app holds the player part). The same source set rules apply.
- `androidApp`: admin app.
- `firebase/`: backend (its tests: none).
- `firebase.json`, `.firebaserc`: web and backend, because they configure both.
- `public/`, `kotlin-js-store/`: web.
- The Gradle build (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`): web and admin app, not the backend. A change in `libs.versions.toml` that only touches the version lines or comments needs no bump.
- Tests, Markdown files, `docs/`, `scripts/` and `.github/`: none.

A pull request can also bump a product that it did not change. The check only fails when a version goes down, or when a name and its code do not go up together.

`scripts/versions_test.py` tests these rules and the check itself on a small git repository. The job "Repository scripts, translations and web start tests" runs it: `python3 -m unittest discover -s scripts -p "*_test.py"`. Change the tests together with the rules above.
