# iOS app

The iOS app is the same app as the Android app: the player part and the admin part of the suite in one app, with the
same version (`admin-versionName` and `admin-versionCode` in `gradle/libs.versions.toml`, see docs/versioning.md).

## How it is built

- `suite` (Kotlin Multiplatform, Android and iOS) holds `SuiteRoot`, which both apps show. Its `iosMain` is the
  `Shared` framework that Xcode links: `MainViewController(url:)` is the app, and `IosSuite` takes the Firebase SDKs
  and the links that open the app.
- Every shared module (`domain`, `data`, `designsystem`, `ui`, `playerApp`, `adminApp`) has the targets `iosArm64` and
  `iosSimulatorArm64`, and an `iosMain` with the platform parts:
  - `data`: Ktor Darwin engine, the session in Application Support, the cache in Caches, the backend from
    `MTSBackendUrl` in the Info.plist (`IosPlatform`).
  - `ui`: photo picker (PHPicker, HEIC turned into JPEG), crop with Skia, CSV/Excel picker and "Save to Files"
    (UIDocumentPicker), share sheet, theme and language in UserDefaults, dates with `NSDateFormatter` templates.
    `IosViewControllers` shows the system controllers over the app.
  - `adminApp`: the timer, the rankings and the table QR open as a sheet over the app (on Android they are windows of
    their own), and keep the screen on. Exports go through the share sheet.
  - `playerApp`: the Green Book opens in Safari inside the app; "My results" uses the share sheet.
- The Kotlin code of the platform parts compiles on Linux too (`./gradlew :suite:compileKotlinIosSimulatorArm64`).
  Linking the framework and building the app need a Mac with Xcode.

## The Xcode project (`iosApp`)

- `iosApp/iosApp.xcodeproj`: one target, `iosApp`, with a synchronized folder: a file added to `iosApp/iosApp` is in
  the app without touching the project. The build phase "Compile Kotlin Framework" runs
  `./gradlew :suite:embedAndSignAppleFrameworkForXcode`, and "Set version" writes the version of the catalog into the
  Info.plist.
- Three configurations, each with its `.xcconfig` in `iosApp/Configuration`:

  | Configuration | Bundle ID | Name | Icon | Backend |
  |---|---|---|---|---|
  | Debug | `com.etologic.mahjongtournamentsuite` | MTS | green | production, App Check debug provider |
  | Release | `com.etologic.mahjongtournamentsuite` | MTS | green | production |
  | Staging | `com.etologic.mahjongtournamentsuite.staging` | MTS Staging | orange | `mts---staging` |

  Schemes: `iosApp` (Debug to run, Release to archive) and `iosApp Staging`.
- `TEAM_ID` in `Configuration/Config.xcconfig` is the Team ID of the Apple Developer account (`PG2F9TPVFV`, an
  individual account). The CI builds for the simulator without signing.
- Minimum iOS 16, iPhone and iPad.
- `Info.plist`: the 8 languages of the app, no export compliance questions (`ITSAppUsesNonExemptEncryption` false,
  only HTTPS), Analytics off until the user accepts, the launch screen (logo without background on the background of
  the theme).
- `PrivacyInfo.xcprivacy`: no tracking; UserDefaults and file timestamps; the collected data of the Play Console
  declaration.
- `iosApp.entitlements`: universal links for `mts.mahjongmadrid.com`.

Open it on the Mac with `open iosApp/iosApp.xcodeproj`, choose an iPhone simulator and press Run.

## Universal links

The site serves `/.well-known/apple-app-site-association` (`apple-app-site-association.json` in `playerApp/src/wasmJsMain/resources`, rewrite and content
type in `firebase.json`). Every address of the site opens the app, like the App Links of Android, except the pages
that are not the app (privacy policy, Green Book, API, files). `IosSuite.openUrl` turns the address into the start of
the app with the same rules as Android (`suiteStartOf`). The app ID in the file is `<Team ID>.<bundle ID>`.

## Firebase

`FirebaseSetup.swift` turns on App Check (App Attest, DeviceCheck on old devices, the debug provider in Debug) and
Analytics, and hands them to the shared code. It does nothing until the project has:

1. The iOS apps in the Firebase consoles of `mahjong-tournament-suite` and `mts---staging`, with the two bundle IDs.
   The production `GoogleService-Info.plist` goes to `iosApp/iosApp`. The staging one needs a build phase that copies it
   in for the Staging configuration (to do). They are not secret, like `google-services.json`.
2. The Swift packages of `firebase-ios-sdk` in the project: `FirebaseAppCheck` and the Analytics product without the
   advertising ID.
3. App Attest registered in the Firebase console (App Check) with the Team ID, and the App Attest capability in Xcode.

The backend stays in App Check `monitor` mode (docs/backend.md). The client tag of the app is `ios/<version>`.

## CI

`.github/workflows/ios.yml` (Build · iOS) builds the app for the simulator on a macOS runner, opens it and keeps
screenshots as an artifact. It checks nothing. The upload to TestFlight comes with the App Store Connect API key.

## App Store

The plan, the Apple Developer account and the store listing: `/mnt/project-files/ios/plan-ios-app-store.md` in the
project files.
