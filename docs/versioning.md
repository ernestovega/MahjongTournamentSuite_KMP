# Versions

There are three products, and each one has its own version:

| Product | What it is | Where it is shown |
|---|---|---|
| **Web** | Both web apps (admin and player) and everything else that is deployed to Firebase: functions, rules and indexes. | Profile dialog and sign-in screen of both web apps |
| **Admin app** | The admin app on Android (`androidApp`) and on desktop. | Profile dialog and sign-in screen of the admin app |
| **Player app** | The player app on Android (`playerAndroidApp`). | Profile dialog and sign-in screen of the player app |

The versions are in `gradle/libs.versions.toml`:

```toml
web-versionName = "1.0.1"
admin-versionName = "1.0.1"
admin-versionCode = "2"
player-versionName = "1.0.1"
player-versionCode = "2"
```

The names are `MAJOR.MINOR.PATCH`. The apps also have a version code for Google Play, which goes up by one with every new version. The web has no version code. The build generates the object `AppVersion` in `domain` from these values, and the apps show the right one through `LocalAppVersion`.

## Bump a version

```bash
python3 scripts/versions.py bump web      # 1.0.1 -> 1.0.2
python3 scripts/versions.py bump admin    # 1.0.1 -> 1.0.2, code + 1
python3 scripts/versions.py show
```

The script only raises the patch (the third number). MAJOR and MINOR are changed by hand in `gradle/libs.versions.toml` when the owner decides, setting the patch back to 0 and raising the version code of the apps by one. The check accepts that too.

## The check on pull requests

The **Build and test** job runs `python3 scripts/versions.py check` on every pull request to `main`. It looks at the files that the pull request changes and fails when a product that those files go into does not have a higher version than on `main`. The error message says which command to run.

Which files go into which product:

- `domain`, `data`, `ui`: all three. Their `wasmJsMain`/`webMain` sources only go into the web, `androidMain` into both apps, and `jvmMain` into the admin app (desktop).
- `adminApp`: web and admin app. `playerApp`: web and player app. The same source set rules apply.
- `androidApp`: admin app. `playerAndroidApp`: player app.
- `firebase/`, `firebase.json`, `public/`, `kotlin-js-store/`: web.
- The Gradle build (`build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/`): all three. A change in `libs.versions.toml` that only touches the version lines needs no bump.
- Tests, Markdown files, `docs/`, `scripts/` and `.github/`: none.

A pull request can also bump a product that it did not change. The check only fails when a version goes down, or when a name and its code do not go up together.
