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
Analytics, and hands them to the shared code. The project has:

1. The Swift package `firebase-ios-sdk` (13.x): `FirebaseCore`, `FirebaseAppCheck` and `FirebaseAnalyticsCore` (Analytics
   without `GoogleAppMeasurementIdentitySupport` and the ads conversion framework, so no advertising ID; the plain
   `FirebaseAnalytics` product brings them in). The Swift code still imports `FirebaseAnalytics`.
2. `iosApp/Firebase/GoogleService-Info.plist` (iOS app of `mahjong-tournament-suite`, for Debug and Release) and
   `iosApp/Firebase/Staging/GoogleService-Info.plist` (iOS app of `mts---staging`). The build phase "Set version and
   Firebase" copies the one of the configuration into the app; without it the app runs without Firebase. They are not
   secret, like `google-services.json`.
3. The App Attest entitlement (`development` in Debug, `production` in Release and Staging) and the App IDs with
   Associated Domains and App Attest in the Apple Developer account. App Attest also has to be registered in the
   Firebase console (App Check) with the Team ID.

The backend stays in App Check `monitor` mode (docs/backend.md). The client tag of the app is `ios/<version>`.

## CI

`.github/workflows/ios.yml` (Build · iOS) builds the app for the simulator on a macOS runner, opens it and keeps
screenshots as an artifact. It checks nothing. Its other job runs `./gradlew iosSimulatorArm64Test`: every
`commonTest`, the iOS platform tests (`src/iosTest`: files, session, cache, theme and language storage, dates,
universal links) and the shared UI tests on the iOS simulator.

## Tests

- `src/iosTest` of `data`, `ui` and `suite` test the iOS `actual`s on the simulator.
- `src/uiTest` of `ui`, `adminApp`, `playerApp` and `designsystem` holds the Compose UI tests that run on the desktop
  (`jvmTest`) and on the iOS simulator (`iosTest`) alike: the same files, compiled for each. A UI test goes there when
  it uses only common APIs (`runComposeUiTest`, no `java.*`, `String.format`, `runDesktopComposeUiTest` or JUnit
  rules); the others stay in `src/jvmTest`. The screenshot tests stay on the desktop.

`.github/workflows/testflight.yml` (Publish · TestFlight), only by hand: archives the Release configuration and uploads
it to App Store Connect (app "Mahjong Tournament Suite", SKU `mahjongtournamentsuite`). It signs automatically with
the App Store Connect API key (Admin access; Key ID and Issuer ID in the workflow, the `.p8` in the secret
`APP_STORE_CONNECT_API_KEY`). App Store Connect refuses a build number it already has, so bump the versions first.

## App Store

The texts of the listing are in [app-store/listing.md](app-store/listing.md) (the description is the one of the Google
Play listing), the screenshots in `docs/app-store/<iphone|ipad>/<language>/` (iPhone 6.9" and iPad 13", made by
`StoreScreenshotsTest` with `MTS_STORE_FORMS=iphone,ipad`).

Actions > Publish · App Store listing (`app-store.yml`, by hand) uploads them with fastlane `deliver`, with the
categories, the age rating (4+) and the review notes, and makes the App Review account in production
([google-play-tester.md](google-play-tester.md)). Its box "submit" also sends the version for review, with the build
of the same version that Publish · TestFlight uploaded.

Only App Store Connect can do these, once: the price (free) and the countries, the App Privacy answers, and the
contact of App Review Information (name, phone, email: not in this public repository).
