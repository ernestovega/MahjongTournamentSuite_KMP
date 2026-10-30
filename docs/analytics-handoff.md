# Analytics and error log: status and next steps

Date of this summary: 2026-10-30. Use it to continue the work from any device.
Read [Telemetry](telemetry.md) for the full description of events, consent and setup.

## What is done (merged in `develop`)

- **App errors screen.** It replaces the old "App usage" screen. Only administrators see it.
  - The button is in the top bar of the Tournaments screen.
  - It has search, filters (period, app, platform, type, production only) and two sort orders.
  - A click on a problem opens the stack trace, the counts for each platform and app, and the newest reports with their `reportId`.
  - The period goes to the server. The screen reads every page of that period, so no problem is hidden.
- **Error log API.** `GET /admin/error-groups` and `GET /admin/error-groups/{id}` answer one page at a time with a cursor (`cursorAt`, `cursorId`). `since` keeps the groups seen at that time or later.
- **Removed.** The App usage screen, the route `GET /admin/usage`, `usageService`, its models, texts and tests.
- **New event `admin_action`.** One event for each try of a write in the admin app. It has `action` (one word of `TelemetryAction`) and `outcome` (`ok` or `failed`). A failure also sends `action_failed` with the reason.
- **Fix.** The failure of the ID card proof request is now written to the log, so Analytics counts it.
- **Tests added.**
  - Presenters: `AdminActionTrackingTest`.
  - Data: repository of the error log, ID card proof, and `DesktopTelemetryTest`.
  - Screen: `ErrorsScreenUiTest` and `ErrorLogFiltersTest`.
  - Server: `pageQuery.test.ts`, `errorLog.integration.test.ts` and `telemetryJs.test.ts`.
  - GA4: `Ga4RulesTest` checks the names against the rules of GA4. `Ga4ValidationSmokeTest` asks Google to validate every event (see [Telemetry](telemetry.md#tests)).
- **Code style.** The code passes `./gradlew ktlintCheck`. This is now required.

## What is still open

Nothing is sent to GA4 until the settings are filled in. The apps build and run without them.

### State in the Google console

- The GA4 property `mahjong-tournament-suite` (id `529254409`) is linked to the Firebase project.
- It has one web data stream, "Mahjong Tournament Suite" (id `14349496202`).

### Steps

1. **Retention.** In GA4, open Admin > Data collection and modification > Data retention. Set event data to 14 months.
2. **Web apps.** Use the existing web stream for the admin web app, the player web app and the desktop app. The user properties `app` and `platform` separate them in the reports.
   - In the Firebase console, open Project settings > General > Your apps > the web app > SDK setup > Config.
   - Copy `apiKey`, `appId` and `measurementId` (starts with `G-`).
   - Put them at the top of `adminApp/src/webMain/resources/telemetry.js` and `playerApp/src/wasmJsMain/resources/telemetry.js`. Both files must have the same values. Only the line `const APP` differs.
3. **Desktop.** In GA4, open the data stream and create a secret in "Measurement Protocol API secrets". Put the measurement id and the secret in `DesktopTelemetryConfig`, in `data/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/data/telemetry/PlatformTelemetry.jvm.kt`.
4. **Android.** In Firebase, add two Android apps: `com.etologic.mahjongtournamentsuite` and `com.etologic.mahjongtournamentsuite.player`. Download their `google-services.json` files. Save them as `androidApp/google-services.json` and `playerAndroidApp/google-services.json`.
5. **Custom definitions in GA4.** Register the dimensions and metrics of [Telemetry, step 6](telemetry.md#6-custom-definitions-in-ga4). New: the event dimension `Outcome` (parameter `outcome`).
6. **Deploy.**
   - Deploy the two hosting sites and the functions.
   - Run `firebase deploy --only firestore:indexes`. Check the TTL policies in Firestore.
   - The paging of the error log was tested only on the emulator. If Firestore asks for an index, the error message has a link to create it.
7. **Check the data.** Use DebugView (see [Telemetry, step 7](telemetry.md#7-check-the-data)). Do an admin action and look for `admin_action` with `outcome=ok`.
8. **Run the checks again.** Run the tests below after you fill in the settings.
9. **Consent text.** A lawyer must read the text of the consent screen before the apps go public.

## How to run the checks

| Check | Command |
| --- | --- |
| Kotlin tests | `./gradlew :domain:allTests :data:allTests :adminApp:jvmTest --offline` |
| Web and Android compile | `./gradlew :playerApp:compileKotlinWasmJs :adminApp:compileKotlinWasmJs :androidApp:compileDebugKotlin --offline` |
| Server unit tests (include `telemetry.js`) | `cd firebase/functions && npm test` |
| Server integration tests | `cd firebase/functions && npm run test:integration` (needs Java 21 for the emulators) |
| Code style | `./gradlew ktlintCheck --offline` |
| GA4 format check | `MTS_GA4_MEASUREMENT_ID=G-... MTS_GA4_API_SECRET=... ./gradlew :data:jvmTest --tests '*Ga4ValidationSmokeTest*'` |

The Android instrumented tests (`androidApp/src/androidTest`) use the new "App errors" button. They were updated and compile, but nobody ran them yet.

## Rules for the next changes

- **ktlint must pass.** The rules are in `.editorconfig`. Do not regenerate the baseline to hide new violations.
  - The baseline matches by file, rule, line and column. A change above an old violation makes it appear again. Fix the file.
  - `./gradlew ktlintFormat` ignores the baseline and reformats about 200 files. Run it, then restore every file that the change does not need.
- **New event.** Follow [Telemetry, "Add a new event"](telemetry.md#add-a-new-event). The lists `all` in `TelemetryEvent.kt` and `Ga4RulesTest` guard the names.
- **Buttons.** Import the `Debounced*` or `Focused*` buttons. `ButtonConventionTest` fails on a raw Material button.
- **Git.** `develop` is checked out in the main checkout. A worktree cannot check it out. Move it with `git -C <main> merge --ff-only <branch>`.
