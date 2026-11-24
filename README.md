# Mahjong Tournament Suite

**Mahjong tournaments (MCR rules) from start to finish: registrations, draw, live tables, rankings and statistics.**

One codebase (Kotlin Multiplatform + Compose) that ships an Android app, a web app and a desktop app, in two parts:

- **Player**: for players and the public. Everything is public, and nobody needs an account to look.
- **Admin (Management)**: for organizers, Club Admins and table editors.

Web: https://mts.mahjongmadrid.com · Management: `/admin/` · Android: the «MTS» app (Player and Management in one app).

📘 **Detailed feature catalogue:** [docs/features.md](docs/features.md) · Google Play listing texts: [docs/play-store/listing.md](docs/play-store/listing.md)

---

## Player

- 🏆 **Tournaments**: tabs In play · All · Statistics, with filters (status, country, year) and stamps «Playing / Played / Registered / Waiting list».
- 📝 **Registrations**: register with or without an account, places and waiting list, teams, food restrictions and cancellation. A short link per tournament (for example `/6thmmc`).
- 🀄 **Live tables**: players set their seats and enter their own hands and penalties from the phone. Everyone sees them at once, with animations for a new or corrected hand.
- ⏰ **My table**: a «Table N · Round R» reminder when the round comes, a floating «Go to my table» button, the time left in the round, and a tournament QR code that opens your table.
- 📊 **Rankings** of players and teams, chicken hands and best hand, with photos, now or after any past round, shareable as an image.
- 👤 **Player page**: Tables · My results (a table of every round with a chart of the place after each round, shareable as an image) · Statistics.
- 📈 **Public statistics** with filters (player, rival, tournament, countries, years): skills, places, records, rivals and charts per round.
- 📖 **Rules at hand**: the 81 MCR combinations with tile pictures, penalties and the Green Book (EN/ES/RU).
- 🌍 **8 languages** (EN, ES, FR, IT, DE, SV, NL, RU), light and dark theme, and a profile with photo.

## Admin

- 🗂️ **Tournaments**: create (individual or teams), configure, make public, organizations that own them with logo and color.
- 🎲 **Draw** of the pairings with no repeated rivals, explained with a diagram anyone can follow.
- 📝 **Registrations**: places, waiting list, paid, import of the Google Form (it replaces the list and marks the rows with problems to fix), CSV export, emails and «Make the draw».
- 👥 **Players and teams**: EMA registry, import, email invitations, substitutions, and changing the number of players before the start.
- ✍️ **Round and table editor**: 16 hands per table, manual totals, penalties, and several editors at once without overwriting each other.
- ⏱️ **Round clock** that opens and closes the tables by itself, plus a round control to close forgotten tables.
- 📺 **For the venue**: a big-screen ranking that pages by itself, and a tournament QR code to project or print on cards for each table (1 to 8 per sheet, with the organizer's logo).
- 🖨️ **Printing and export**: ID cards in PDF (with substitutes and staff) and the results report for the EMA.
- 🔐 **Roles**: Super Admin, Club Admin, Editor, and custom roles with permissions one by one.
- 🕵️ **Oversight**: activity log (who changed what), app errors, and the same statistics as the Player.

---

## Technologies

### Apps (client)

| Part | Technology |
|---|---|
| Language | Kotlin 2.4 (Kotlin Multiplatform) |
| UI | Compose Multiplatform 1.12 + Material 3, own design system (`:designsystem`) |
| Platforms | Android (minSdk 24, targetSdk 36), web with Kotlin/Wasm, JVM desktop |
| Dependency injection | Koin 4 |
| Networking | Ktor client 3 (OkHttp on Android, CIO on desktop, JS on web) + kotlinx.serialization |
| Concurrency and dates | Kotlin Coroutines, kotlinx-datetime |
| Navigation | Navigation Compose (JetBrains) |
| Images and QR | Coil 3, QRose |
| Logging | Kermit |
| Firebase on Android | App Check (Play Integrity), Analytics (GA4) |
| Build | Gradle 9 + AGP 9, version catalog (`gradle/libs.versions.toml`) |

Modules: `domain` (models and rules, no platform dependencies), `data` (API client, DTOs, caches), `ui` (shared components and screens), `designsystem`, `playerApp`, `adminApp` and `androidApp` (the Android app that holds both parts).

### Backend

| Part | Technology |
|---|---|
| API | One HTTP Cloud Function `api` (2nd gen) in `europe-southwest1` (Madrid): Node 22 + Express 5 + TypeScript |
| Jobs | Cloud Tasks (closes rounds when their time ends) and Cloud Scheduler (hourly registrations job), in `europe-west1` |
| Database | Cloud Firestore (`eur3`). Rules deny everything: only the function reads and writes |
| Files | Cloud Storage (player and account photos, organization logos) |
| Authentication | Firebase Auth with custom claims for roles, plus named permissions |
| Security | App Check, rate limits, secrets in Secret Manager |
| Hosting | Firebase Hosting: both web apps, `/api-docs/`, and `/api/**` forwarded to the function |
| PDF, Excel and email | PDFKit (ID cards and QR cards), ExcelJS (Google Form import), Nodemailer (SMTP), sharp (images) |
| External | Google Places API (venues), EMA registry and rankings |

API reference (OpenAPI / Swagger UI): https://mts.mahjongmadrid.com/api-docs/ (source [docs/api/openapi.json](docs/api/openapi.json), written by `python3 scripts/generate-openapi.py`).

### Quality and delivery

- **CI/CD with GitHub Actions**: Firebase and web deploys, Google Play uploads, staging, screenshot gallery and rehearsals.
- **Environments**: production and staging (`mts---staging.web.app`).
- **Versions**: separate for web, the Android app and the backend (`python3 scripts/versions.py status`).
- **Tests**: unit and multiplatform tests (kotlin.test, Koin verify), backend tests with `node --test` and the Firebase emulators, E2E tests on Android emulators, Roborazzi screenshots (Git LFS) of every screen in all 8 languages, ktlint and Kover. A source check warns when common code calls a JVM-only API that breaks the web build (`CommonCodeIsMultiplatformTest`).
- **Game day**: bots that play whole tournaments against staging to test load and edge cases.

More in [docs/architecture/](docs/architecture/README.md): system diagrams, data model, deploys and versions, and the screen map.

## Build and run

```bash
# Android
./gradlew :androidApp:assembleDebug

# Desktop (management)
./gradlew :adminApp:run

# Web (Wasm)
./gradlew :adminApp:wasmJsBrowserDevelopmentRun
./gradlew :playerApp:wasmJsBrowserDevelopmentRun
```

The web apps use the production backend by default. For a local backend with test data, see [docs/test-environment.md](docs/test-environment.md).

## Tests

```bash
# Kotlin
./gradlew :domain:jvmTest :data:jvmTest :ui:jvmTest :adminApp:jvmTest :playerApp:jvmTest

# Backend
cd firebase/functions && npm test                 # unit tests
cd firebase/functions && npm run test:integration # on the Firebase emulators
```

Screenshots: [docs/screenshot-tests.md](docs/screenshot-tests.md) · UI tests: [docs/instrumented-ui-tests.md](docs/instrumented-ui-tests.md).

## License

Apache 2.0. See [LICENSE](LICENSE).
