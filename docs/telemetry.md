# Telemetry: Analytics and error log

Both apps send usage data to Firebase Analytics (GA4). They send errors with their stack trace to our own error log in Firestore.
Both are anonymous. The user must accept them before the app opens.

## What each platform sends

| App | Platform | Analytics | Error log |
| --- | --- | --- | --- |
| Admin and player | Android | Firebase SDK | Handled errors at once. A crash at the next start. |
| Admin and player | Web | Firebase JS SDK (`telemetry.js`) | Handled errors at once. A crash at once, from `telemetry.js`. |
| Admin | Desktop | GA4 Measurement Protocol | Handled errors at once. A crash at the next start. |

Crashlytics is not used, because it has no web or desktop version.
Nothing is sent until the settings of the platform are filled in. The apps build and run without them.
A platform without Analytics settings also sends no error log and shows no consent screen.

## Consent

- On the first start the app shows the screen "Anonymous analytics". It explains what the app records.
- The user can only enter after "Accept and continue". "Decline" keeps the user on the screen with a note.
- The answer is saved on the device. Android: `SharedPreferences` file `mts_telemetry`. Web: `localStorage` key `mtsAnalyticsConsent`. Desktop: Java preferences.
- The user can withdraw the consent in the profile dialog. The app then stops all data and shows the screen again.
- On Android, `firebase_analytics_collection_enabled` is `false` in the manifest. Collection starts only after the user accepts.
- The Android UI tests save the consent before they start the app, so builds with Firebase settings still run them.

## Rules for the data

- The apps send no email, no name, no EMA id and no tournament id.
- A parameter holds a fixed word or a count.
- The user properties are `app`, `platform`, `env`, `role` and `language`.
- `env` is `prod`, `dev` or `debug`. Filter the reports on `env = prod`.
- The server removes emails, long numbers and tokens from the text of an error report before it saves it.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `screen_view` | The user opens a screen | `firebase_screen`: the name of the route, for example `Tournaments`, `Tournament`, `Tables`, `Players`, `Statistics`, `Timer` |
| `dialog_view` | A dialog opens | `dialog`, `screen` |
| `tab_view` | A tab is selected | `tab`, `screen` |
| `view_time` | A screen, dialog or tab closes | `kind` (`screen`, `dialog` or `tab`), `view`, `screen`, `seconds` |
| `login`, `logout` | Sign in worked, sign out | none |
| `password_reset_requested` | A reset email went out | `kind=bulk`, `players` for the mass reset |
| `tournament_created` | A tournament is created | `players`, `rounds`, `is_teams` |
| `tournament_deleted` | A tournament is deleted | none |
| `table_saved` | An editor saves a table | `hands` |
| `players_imported`, `players_exported` | Import or export of players | `format`: `csv` or `xlsx` |
| `accounts_sent` | Invite or reminder to players | `players` |
| `statistics_viewed` | Statistics load | `scope`: `tournament`, `overview`, `person`, `me` |
| `admin_action` | An administrator or editor tries to change or export something (admin app). One event for each try | `action`, one word of the list below. `outcome`: `ok` or `failed` |
| `hand_previewed`, `hand_recorded`, `penalty_added` | A player enters a hand (player app) | `hands` for penalties |
| `action_failed` | Any request fails | `action`, `reason` |
| `app_error` | The app caught a bug | `report_id`, `action`, `kind`, `reason` |
| `app_crash` | The app stopped | `report_id`, `kind` |

`outcome` of `admin_action` is `failed` when the request failed. The same failure also sends `action_failed` with the `reason`. To see why an action failed, look at `action_failed` for the same minute, or use the BigQuery export.

`action` of `admin_action` is one of:

| Group | Words |
| --- | --- |
| Tournament | `tournament_renamed`, `tournament_settings_updated` |
| Players and teams | `player_assigned`, `player_unassigned`, `player_substituted`, `substitution_cleared`, `team_updated`, `emails_saved` |
| Exports | `id_cards_exported`, `id_list_exported`, `id_card_proof_created`, `ema_report_exported` |
| Accounts | `account_created`, `account_updated`, `account_disabled`, `account_enabled`, `account_photo_updated`, `account_photo_removed` |
| EMA registry | `registry_player_created`, `registry_player_updated`, `registry_photo_updated` |

Other admin work has its own event: `tournament_created`, `tournament_deleted`, `table_saved`, `players_imported`, `players_exported`, `accounts_sent`, `password_reset_requested`.
The events carry no name and no account id. Split them with the user property `role` to compare administrators and editors.

`reason` of `action_failed` is `network`, `timeout`, `auth`, `conflict`, `client_error`, `server_error` or `unexpected`.
Only `server_error` and `unexpected` also create an `app_error` and an error report. The others are normal events, such as a user with no network.

## Time on screen and user journeys

- **Time:** `view_time` gives the seconds of each screen, dialog and tab. It works the same on every platform. A view counts at most 30 minutes, because an app can wait in the background. The web does not send the last view when the user closes the tab.
- **Journeys:** GA4 builds them from the order of `screen_view`, `dialog_view` and `tab_view`. Open Explore > Path exploration and start from `screen_view`.
- **Funnels:** in Explore > Funnel exploration use for example `screen_view Tournaments` > `tournament_created`, or `hand_previewed` > `hand_recorded`.

## The error log

`POST /telemetry/errors` receives a report. The route is public, because a crash can happen before sign-in. App Check and a limit of 30 reports per minute for each address protect it.

- Equal errors share one group in the collection `errorGroups`. The group id comes from the error type, the area and the first three lines of the stack.
- A group holds a count, the first and last time, a sample message and stack trace, and the count for each platform and app.
- The newest reports of a group are in `errorGroups/{id}/reports`. Firestore deletes reports after 30 days and groups after 90 days without a new report. The rule is the TTL on the field `expireAt` in `firebase/firestore.indexes.json`.
- Each report has a `reportId`. The same value is the parameter `report_id` of the event `app_error` or `app_crash` in Analytics.

### Read the error log

- Admin app: administrators open the button "App errors" in the top bar of the Tournaments screen. The screen lists the problems, newest first.
  - Filters: search text, period, app, platform, type (crash or handled) and production only. The sort is by last time or by count.
  - A click on a row opens the stack trace, the counts for each platform and app, and the newest reports with their `reportId`. A button copies the stack trace.
  - The period filter goes to the server. The screen reads every page of that period (200 problems for each request), so no problem is left out. The other filters work on the loaded problems.
  - The reports in the detail load 50 at a time. A button loads the next 50.
- Admin API: `GET /admin/error-groups` lists the groups, newest first, in pages of up to 200 (`limit`). `since` (milliseconds) keeps the groups seen at that time or later. While the answer has a `nextCursor`, send its `at` and `id` as `cursorAt` and `cursorId` to read the next page. `GET /admin/error-groups/{id}` gives one group with its stack trace and a page of its reports (`limit` up to 200, same cursor with `nextReportCursor`). Only admins can call them.
- Firebase console: open Firestore and the collection `errorGroups`.

### Cross the log with Analytics

1. In GA4, a user with `report_id` in `app_error` shows which screen, role, platform and version had the error.
2. In the error log, the report with the same `reportId` has the stack trace.
3. With the BigQuery export of GA4 you can join both in SQL: `event_params.report_id` with the field `reportId` of an export of `errorGroups`.

## Setup in the Firebase console

Do these steps one time. The Firebase project is `mahjong-tournament-suite`.

### 1. Turn on Google Analytics

1. Open Project settings > Integrations > Google Analytics.
2. Link a GA4 property. Create one if the project has none.
3. In the GA4 property, open Admin > Data collection and modification > Data retention. Set event data retention to 14 months.

### 2. Android (both apps)

1. In Project settings > General, add two Android apps:
   - `com.etologic.mahjongtournamentsuite` (admin)
   - `com.etologic.mahjongtournamentsuite.player` (player)
2. Download `google-services.json` of each app.
3. Save the files as `androidApp/google-services.json` and `playerAndroidApp/google-services.json`.
   The file holds no secret. You can commit it, so CI builds and the UI tests also send data.
4. Build and run the app. Accept the consent screen. Check the events in DebugView (step 6).

If you already filled the App Check values in the manifest, keep them. App Check uses its own named Firebase app.

### 3. Web (both apps)

1. In Project settings > General, add two web apps: admin and player. You do not need to select Firebase Hosting for them.
2. Copy `apiKey`, `appId` and `measurementId` of each web app.
3. Fill them in at the top of:
   - `adminApp/src/webMain/resources/telemetry.js`
   - `playerApp/src/wasmJsMain/resources/telemetry.js`
4. Deploy the two hosting sites.

### 4. Desktop

1. In GA4, open Admin > Data streams and select the stream of the admin web app.
2. Copy the measurement id (`G-...`).
3. Open "Measurement Protocol API secrets" and create a secret.
4. Fill both values in `DesktopTelemetryConfig` in
   `data/src/jvmMain/kotlin/com/etologic/mahjongtournamentsuite/data/telemetry/PlatformTelemetry.jvm.kt`.
5. The desktop events appear in the stream of the admin web app. The user property `platform = desktop` tells them apart.

### 5. Server

1. Deploy the functions: the route `/telemetry/errors` and the admin routes `/admin/error-groups`.
2. Deploy the Firestore settings: `firebase deploy --only firestore:indexes`. This creates the TTL rules for `errorGroups` and `reports`.
3. Check that the TTL policies are active in Firestore > Time-to-live.

### 6. Custom definitions in GA4

GA4 shows a custom parameter in reports only after you register it.
Open Admin > Custom definitions > Create custom dimension.

| Dimension name | Scope | Parameter or property |
| --- | --- | --- |
| Action | Event | `action` |
| Reason | Event | `reason` |
| Outcome | Event | `outcome` |
| Report id | Event | `report_id` |
| View | Event | `view` |
| View kind | Event | `kind` |
| Screen | Event | `screen` |
| Dialog | Event | `dialog` |
| Tab | Event | `tab` |
| Statistics scope | Event | `scope` |
| Import format | Event | `format` |
| App | User | `app` |
| Role | User | `role` |
| Platform | User | `platform` |
| Environment | User | `env` |
| Language | User | `language` |

Create custom metrics (scope "Event", unit "Standard") for `players`, `rounds`, `hands` and `seconds`. Use "Seconds" as the unit of `seconds`.

### 7. Check the data

1. Android: run `adb shell setprop debug.firebase.analytics.app <applicationId>`. Open Admin > DebugView in GA4.
2. Web: use the Chrome extension "Google Analytics Debugger". Events show in DebugView.
3. Wait up to 24 hours for the standard reports. DebugView and Realtime show events at once.
4. Optional: link BigQuery in Project settings > Integrations. It keeps every event for your own queries.

## Privacy

- The consent screen is the legal basis. The text is in `consent_*` of the `ui` module, in all languages.
- The data has no direct personal data. Analytics still stores an identifier of the device or the browser.
- Ask a lawyer to check the text of the consent screen before you publish the apps.

## Tests

| Test | What it checks | How to run |
| --- | --- | --- |
| `Ga4RulesTest` (domain) | Names of events, parameters and user properties follow the rules of GA4. The lists `all` hold every constant. | `./gradlew :domain:jvmTest` |
| `AdminActionTrackingTest` (adminApp) | Every write of the admin app sends one `admin_action`, with `ok` or `failed`. | `./gradlew :adminApp:jvmTest` |
| `telemetryJs.test.ts` (firebase/functions) | Both copies of `telemetry.js` run in a fake page with a fake SDK: consent, events, crash reports, limits. The crash body passes `parseErrorReport` of the server. | `cd firebase/functions && npm test` |
| `DesktopTelemetryTest` (data) | The desktop app posts the right body to a local server, only after consent. | `./gradlew :data:jvmTest` |
| `Ga4ValidationSmokeTest` (data) | Google accepts every event of the app. It uses the validation address of the Measurement Protocol, which stores nothing. | See below. It does nothing without the variables. |

Run the smoke test after you add or change an event:

```
MTS_GA4_MEASUREMENT_ID=G-... MTS_GA4_API_SECRET=... ./gradlew :data:jvmTest --tests '*Ga4ValidationSmokeTest*'
```

A pass means that the format is valid. It does not mean that GA4 counted the event. Only DebugView (step 7) or the BigQuery export shows that.

The tests of `telemetry.js` fill in the empty settings and replace the loader of the SDK with `window.mtsSdkLoader`. The page never sets that loader.

## Add a new event

1. Add the name to `TelemetryEvent` in the `domain` module.
2. Call `result.track(telemetry, TelemetryEvent.X)` in the presenter, or `telemetry.event(...)`.
3. For a new dialog, wrap it in `TrackedDialog("Name")`. For a new tab, call `TrackedTab("Name")` while it is selected. A new route is tracked by `screen<T>`.
4. Add the name to the list `all` of its object. `Ga4RulesTest` fails when a constant is missing from the list or breaks a rule of GA4.
5. Add the event to the table on this page.
6. Keep the parameters to fixed words and counts. Register a new parameter in GA4 if a report needs it.
