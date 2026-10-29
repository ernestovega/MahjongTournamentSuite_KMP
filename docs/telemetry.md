# Telemetry: Analytics

Both apps send usage data and errors to Firebase Analytics (GA4). Errors are events, so every platform works the same way. This page lists what the apps send and what to set up in Firebase.

## What each platform sends

| App | Platform | Analytics | Errors |
| --- | --- | --- | --- |
| Admin and player | Android | Firebase SDK | Event `app_crash` and event `app_error` |
| Admin and player | Web | Firebase JS SDK (`telemetry.js`) | Event `app_crash` and event `app_error` |
| Admin | Desktop | GA4 Measurement Protocol | Event `app_crash` and event `app_error` |

Crashlytics is not used, because it has no web or desktop version. Read the errors in the events report of GA4. They have the error type but no stack trace.

Nothing is sent until the settings of the platform are filled in. The apps build and run without them.

## Rules for the data

- The apps send no email, no name, no EMA id and no tournament id.
- A parameter holds a fixed word or a count.
- The user properties are `app`, `platform`, `env`, `role` and `language`.
- `env` is `prod`, `dev` or `debug`. Filter the reports on `env = prod`.

## Events

| Event | When | Parameters |
| --- | --- | --- |
| `screen_view` | The user opens a screen | `firebase_screen`: `Tournaments`, `Tournament`, `Tables`, `Players`, `Statistics`, `Rankings` ... |
| `login`, `logout` | Sign in worked, sign out | none |
| `password_reset_requested` | A reset email went out | `kind=bulk`, `players` for the mass reset |
| `tournament_created` | A tournament is created | `players`, `rounds`, `is_teams` |
| `tournament_deleted` | A tournament is deleted | none |
| `table_saved` | An editor saves a table | `hands` |
| `players_imported`, `players_exported` | Import or export of players | `format`: `csv` or `xlsx` |
| `accounts_sent` | Invite or reminder to players | `players` |
| `statistics_viewed` | Statistics load | `scope`: `tournament`, `overview`, `person`, `me` |
| `hand_previewed`, `hand_recorded`, `penalty_added` | A player enters a hand (player app) | `hands` for penalties |
| `action_failed` | Any request fails | `action`, `reason` |
| `app_error`, `app_crash` | A bug or a crash | `action`, `kind`, `message` |

`reason` of `action_failed` is `network`, `timeout`, `auth`, `conflict`, `client_error`, `server_error` or `unexpected`.
Only `server_error` and `unexpected` also send `app_error`. The others are normal events, such as a user with no network.

## Questions the data answers

- Which screens and actions do the users use most and least? `screen_view` and the events above.
- Who uses the apps? Split by the user properties `app`, `role`, `platform` and `language`.
- Where do users stop? Build funnels in Explore: `screen_view Tournaments` > `tournament_created`, and `hand_previewed` > `hand_recorded`.
- Which actions fail? `action_failed` by `action` and `reason`.
- How many editing conflicts happen? `action_failed` with `reason = conflict`.

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
4. Build and run the app. Check the events in DebugView (step 6).

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

### 5. Custom definitions in GA4

GA4 shows a custom parameter in reports only after you register it.
Open Admin > Custom definitions > Create custom dimension. Use scope "Event" for the first group.

| Dimension name | Scope | Parameter or property |
| --- | --- | --- |
| Action | Event | `action` |
| Reason | Event | `reason` |
| Statistics scope | Event | `scope` |
| Import format | Event | `format` |
| App | User | `app` |
| Role | User | `role` |
| Platform | User | `platform` |
| Environment | User | `env` |
| Language | User | `language` |

Create custom metrics (scope "Event", unit "Standard") for `players`, `rounds` and `hands`.

### 6. Check the data

1. Android: run `adb shell setprop debug.firebase.analytics.app <applicationId>`. Open Admin > DebugView in GA4.
2. Web: use the Chrome extension "Google Analytics Debugger". Events show in DebugView.
3. Wait up to 24 hours for the standard reports. DebugView and Realtime show events at once.
4. Optional: link BigQuery in Project settings > Integrations. It keeps every event for your own queries.

## Privacy

The data has no direct personal data. Analytics on the web still stores an identifier in the browser.
The EU rules for cookies can need the consent of the user. Decide this before you turn on the web settings.
The `Telemetry` interface is the one place to add a consent switch.

## Add a new event

1. Add the name to `TelemetryEvent` in the `domain` module.
2. Call `result.track(telemetry, TelemetryEvent.X)` in the presenter, or `telemetry.event(...)`.
3. Add the event to the table on this page.
4. Keep the parameters to fixed words and counts. Register a new parameter in GA4 if a report needs it.
