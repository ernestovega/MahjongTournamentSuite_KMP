# Test coverage

*Audit of 24 November 2026, on `main` at `63f4427` (web 1.0.401, backend 1.0.62).*

This page says how many tests each part of the code has and how much of it they run (part 0, kept up to date by
`scripts/test-coverage.py`), which kinds of tests exist, which features and screen flows have tests, and what is still
untested, in order of priority. The last part lists the test tournaments of the seed script.

## 0. Tests per part of the code

<!-- test-table:start -->
<!-- Written by scripts/test-coverage.py. Do not edit by hand. -->

| Part | Code lines | Unit | UI (JVM) | Screenshot | Integration | Android E2E | Web E2E | Lines run | Branches run |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `domain`: business rules, use cases | 5,777 | 240 | – | – | – | – | – | 100 % | 95 % |
| `data`: repositories, network, telemetry | 7,951 | 468 | – | – | – | – | – | 99 % | 90 % |
| `designsystem`: theme, base components | 1,928 | – | 34 | 4 | – | – | – | 94 % | 61 % |
| `ui`: shared Compose components | 17,922 | 93 | 322 | 24 | – | – | – | 97 % | 72 % |
| `adminApp`: admin screens | 32,673 | 322 | 620 | 91 | – | 175 | 5 | 93 % | 72 % |
| `playerApp`: player screens | 12,072 | 200 | 183 | 56 | – | 72 | 8 | 95 % | 69 % |
| `androidApp`: Android entry point | 184 | – | – | – | – | – | – | – | – |
| Backend: API routes and middleware | 4,013 | 60 | – | – | 322 | – | – | 93 % | 86 % |
| Backend: services | 16,159 | 376 | – | – | 195 | – | – | 96 % | 88 % |
| Backend: models, clients, setup | 323 | 29 | – | – | – | – | – | 96 % | 83 % |
| Backend: maintenance and seed scripts | 4,380 | 2 | – | – | – | – | – | 53 % | 63 % |
| Firestore and Storage rules (deny all) | 16 | 3 | – | – | – | – | – | – | – |
| Repository scripts (Game day, smoke, versions) | 7,377 | 124 | – | – | – | – | – | – | – |
| **Total** | **110,775** | **1,917** | **1,159** | **175** | **517** | **247** | **13** | **95 %** | **76 %** |

- **Counts** are the tests in the sources of each kind. A screenshot test can record several images, and an
  Android or Web E2E test of the admin or player part counts in `adminApp` or `playerApp`.
- **Kinds:** Unit = Kotlin `commonTest`/`jvmTest` without Compose, and Node or Python tests. UI (JVM) = Compose UI
  tests on the JVM. Screenshot = Roborazzi. Integration = backend tests on the Auth and Firestore emulators.
  Android E2E = `androidApp/src/androidTest` on emulators. Web E2E = Playwright on the Wasm apps.
- **Lines run and branches run** come from Kover (JVM tests of each Kotlin module) and from the coverage of Node
  (backend unit and integration tests, on the compiled JavaScript). They do not count the Android and Web E2E tests,
  `androidMain`, `wasmJsMain` or `androidApp`. A dash means not measured.
- **The total of lines and branches run** leaves out the maintenance and seed scripts of the backend: they run
  by hand, once or on test data, and their row shows how much of them the tests run.
- Coverage measured on 2026-11-26 at `5815f73c1` with `python3 scripts/test-coverage.py --measure`.
<!-- test-table:end -->

## 1. Kinds of tests

| Kind | Where | How to run | When it runs |
| --- | --- | --- | --- |
| Kotlin unit tests | `domain`, `data`, `ui`, `designsystem`, `adminApp`, `playerApp` (`commonTest`, `jvmTest`) | `./gradlew :domain:jvmTest :data:jvmTest :ui:jvmTest :adminApp:jvmTest :playerApp:jvmTest` | By hand, before a push |
| Compose UI tests on the JVM | `*UiTest` in `ui`, `designsystem`, `adminApp`, `playerApp` | Same as above | Same |
| Screenshot tests (Roborazzi) | `*ScreenshotTest`, references in `*/screenshots/` | `docs/screenshot-tests.md` | By hand and Actions > Check · Screenshots |
| Backend unit tests | `firebase/functions/src/**/*.test.ts` | `npm test` | By hand and on the pull request to `main` |
| Backend integration tests (Auth and Firestore emulators) | `firebase/functions/src/**/*.integration.test.ts` | `npm run test:integration` | Same |
| Android instrumented E2E | `androidApp/src/androidTest` (36 classes, journeys included) | `scripts/run-e2e-parallel.py` on the Mac | By hand, and every night at 03:00 UTC (Actions > Test · UI) |
| Game day (bots on staging) | `scripts/rehearsal`, `.github/workflows/scenario.yml`, `rehearsal.yml` | Actions > Test · Game day | By hand |
| Tests of the Game day scripts | `scripts/rehearsal/test/*.test.mjs` | `node --test scripts/rehearsal/test/*.test.mjs` | By hand |
| Project rules | `scoringCases` (server, admin, player share `testdata/scoring-cases.json`), `clientContract.test` (app calls match API routes), `CommonCodeIsMultiplatformTest` (no JVM-only APIs in `commonMain`), `AnalyticsCoverageTest`, `LocalizationParityTest`, `DesignTokenRatchetTest`, `ButtonConventionTest`, `ScrollIndicatorsConventionTest` | With the Kotlin and backend tests | Same |
| Web E2E (Playwright, Chromium) | `e2e/web/tests/*.test.mjs`: the Wasm Player against the local test backend | `e2e/web/README.md` | Every night at 03:30 UTC (Actions > Test · Web E2E) and by hand |
| Smoke test of a deploy | `scripts/smoke/smoke.mjs`, its tests in `scripts/smoke/test/` | `node scripts/smoke/smoke.mjs` (`MTS_SITE_URL` for staging) | At the end of every deploy to production and to staging |
| Performance | `benchmarks/`, `.github/workflows/benchmark.yml` | Actions | Every morning against production |
| Tooling scripts | `scripts/versions_test.py`, `scripts/recovery.test.mjs` | `python3 scripts/versions_test.py`, `node --test` | CI: Scripts and diagnostics tests (and nightly) |

There are no tests of load beyond Game day, or of accessibility. The Firestore and Storage rules refuse everything, and
`securityRules.test.ts` checks that they stay so.

## 2. Measured coverage

### Kotlin (Kover, JVM tests only)

The lines and branches of each module are in the table of part 0 (merged Kover report of all JVM tests). The HTML
report shows each file: `./gradlew koverHtmlReportCoverage` (`build/reports/kover/htmlCoverage`).

Kover counts only the JVM build. `androidMain` (~1,000 lines), `wasmJsMain` (~1,000 lines) and `androidApp/src/main`
(~360 lines) are not measured; the Android E2E tests run part of `androidMain`, nothing runs `wasmJsMain`.

The measure leaves out the code the compiler writes, not us (filters in the root `build.gradle.kts`, since 26 November):

| Left out | Why |
| --- | --- |
| `*.generated.resources` | Compose resources accessors |
| `*.backend.dto` | Data classes of the backend answers and requests, without logic: their branches are the serializers and default arguments that kotlinx.serialization writes (~3,000 branches) |
| `@Preview` functions | Previews of the screens for the IDE; the screenshot tests draw the screens themselves |
| `*ComposableSingletons*`, `*_Factory`, `*MainKt` | Compose lambdas holders, generated factories and the entry points |
| `*$DefaultImpls` | Static copies of the interface methods with a body, for Java callers. The tests run the methods themselves |

**Goal (26 November):** 100 % of the lines in every part, and 100 % of the branches in the logic: `domain`, `data` and
the backend. In the Compose parts (`designsystem`, `ui`, `adminApp`, `playerApp`) the branches are measured but are
not a goal, because the Compose compiler adds branches for every parameter (its default value and whether it changed).

The total of the table also leaves out the maintenance and seed scripts of the backend (`firebase/functions/src/scripts`,
since 26 November). They run by hand, once or on test data, so their row is shown apart and does not count toward 100 %.

Files with the most untested lines (audit of 24 November; some are covered since):

| File | Lines | Untested lines | What is missing |
| --- | ---: | ---: | --- |
| `data/.../DefaultTournamentRepository.kt` | 48 % | 419 | Many repository calls (writes of tables, players, teams, exports) only run in E2E |
| `adminApp/.../PlayersScreen.kt` | 54 % | 519 | Substitution, clear slot, EMA edits, import from the screen |
| `adminApp/.../GlobalUsersScreen.kt` | 64 % | 351 | Create and edit accounts, permissions per person |
| `adminApp/.../RegistrationsScreen.kt` | 62 % | 322 | Per-person edits (email, team, food, paid, give a place, waiting list, remove) |
| `playerApp/.../HandEntryPanel.kt` | 60 % | 260 | Win, discard, self-draw and penalty steps; corrections and deletes |
| `adminApp/.../TeamsScreen.kt` | 49 % | 247 | Team edit dialog and drag of members |
| `playerApp/.../HandDetails.kt` | 14 % | 162 | Hand details table of a completed table (`HandDetailsTable`, `resultChange`) |
| `data/.../FunctionsBackendApi.kt` | 62 % | 157 | Calls with no test of their request or response |
| `data/.../DefaultAdminRepository.kt` | 43 % | 142 | Accounts, roles, EMA registry writes |
| `ui/.../ImageCropDialog.kt` | covered 25 Nov | – | `PhotoCropAndConsentUiTest` |
| `data/.../DefaultAuthRepository.kt` | 40 % | 81 | Sign in, sign out, password change, delete own account |
| `playerApp/.../TeamStatistics.kt` | covered 25 Nov | – | `TeamStatisticsTabUiTest` |
| `adminApp/.../CreateTournamentPresenter.kt` | 63 % | 52 | Error paths of the create flow and the registrations variant |
| `adminApp` and `playerApp` `RoutePersistence.kt` | covered 25 Nov | – | `RoutePersistenceUiTest` in both apps |
| `adminApp/.../HandRowNavigation.kt` | 42 % | 39 | Keyboard navigation between hand rows |
| `ui/.../AppErrorUi.kt` | 42 % | 51 | Messages of each error kind |

### Backend (`npm run test:coverage`, unit and integration tests on the emulators)

The lines and branches of each part are in the table of part 0. At the audit of 24 November: 1,390 tests, all
green, 94.4 % of lines, 85.5 % of branches, 94.5 % of functions.

Lowest files:

| File | Lines | Branches | What is missing |
| --- | ---: | ---: | --- |
| `api/routes/support.routes.ts` | 65 % | | `POST /support/message` tested since 25 November (`support.integration.test.ts`) |
| `services/featureFlagsService.ts` | covered 25 Nov | | `featureFlagsService.test.ts` |
| `services/firebaseAuthRest.ts` | covered 25 Nov | | `firebaseAuthRest.test.ts` |
| `services/versionsPage.ts` | 57 % | | The versions page |
| `api/routes/auth.routes.ts` | 72 % | 64 % | Own photo upload and delete, `DELETE /me` (delete own account) |
| `services/registrationHourRelay.ts` | 79 % | | The hourly relay from Belgium to Madrid |
| `api/routes/tournaments.routes.ts` | 86 % | 69 % | Error branches (404, 409, invalid bodies) of many routes |
| `api/middleware/requirePermission.ts` | covered 25 Nov | | `requirePermission.test.ts` |

`services/handSynthesis.ts` and `services/googlePlayTester.ts` only serve the seed scripts and are not measured.

## 3. Features and screen flows against their tests

U = Kotlin unit or JVM UI test, S = screenshot, B = backend unit or integration test, E = Android E2E, G = Game day.
The web E2E tests (`e2e/web`) are in the notes.
"Gap" says what has no test.

### Player

| Feature (`docs/features.md`) | U | S | B | E | G | Gap |
| --- | :-: | :-: | :-: | :-: | :-: | --- |
| 4.1 Tournament list, tabs In play · All · Statistics, filters, stamps, places | ✅ | ✅ | ✅ | ✅ | | E2E predates the tabs and the all-public rule (`playerDoesNotSeeOtherTournaments`) |
| 4.2 Registrations: register with account, without account (email link), waiting list | ✅ | ✅ | ✅ | | ✅ | No E2E; register, EMA search and «I have no EMA number» dialogs have no screenshot |
| 4.2 My registration: food, team, cancellation; team captain | ✅ | ✅ | ✅ | | | No E2E, no screenshot of the dialogs |
| 4.3 Tournament tabs: Tables, Ranking (by round, Share PNG), Players, Teams, Statistics, Info | ✅ | ✅ | ✅ | ✅ | | Share PNG only screenshot of the preview |
| 4.4 Player page and Results, Share PNG | ✅ | ✅ | ✅ | | | No E2E |
| 4.5 Seats | ✅ | ✅ | ✅ | ✅ | ✅ | |
| 4.5 Enter hands and penalties | ✅ | ✅ | ✅ | ✅ | ✅ | |
| 4.5 Live animation, differences, time left | ✅ | ✅ | ✅ | ✅ | | |
| 4.5 Completed table, hand details | ✅ | ✅ | | ✅ | | |
| 4.6 «Go to my table» reminder and floating button | ✅ | ✅ | | ✅ | | |
| 4.6 Tournament QR `/<slug>/my-table` | ✅ | | ✅ | | | Web E2E opens the table from `/t/<id>/my-table`; no Android E2E of App Links |
| 4.7 Statistics with filters (player, tournament, team) | ✅ | ✅ | ✅ | ✅ | | Team statistics 4 % |
| 4.8 Combinations, Penalties | ✅ | ✅ | | | | |
| 4.8 Green Book (download on Android) | ⚠️ | | | | | Only the link is tested; the Android download and the offline copy are not |
| 4.8 Contact form | | ✅ | ✅ | | | |
| 4.9 Sign in, password recovery | ✅ | ✅ | ✅ | ✅ | | Choosing the new password from the email (see «Gaps without an emulator test») |
| 4.9 Profile: photo, alias, theme, language, delete account, withdraw consent | ⚠️ | ✅ | ⚠️ | ⚠️ | | Crop, remove photo and withdraw consent have UI tests (25 Nov); only the theme is in E2E; `DELETE /me` has a backend test |
| 4.9 8 languages, AI translation notice | ✅ | ✅ | | | | |
| 4.9 Refresh rhythms | ✅ | | | | ✅ | |

### Admin

| Feature | U | S | B | E | G | Gap |
| --- | :-: | :-: | :-: | :-: | :-: | --- |
| 5.1 Tournament list by role | ✅ | ✅ | ✅ | ✅ | | Web E2E as Club Admin (`admin-roles.test.mjs`, 25 Nov) |
| 5.1 Create tournament (with or without registrations), settings | ✅ | ✅ | ✅ | ✅ | ✅ | No E2E of «Open registrations» |
| 5.1 Organizations, club venue (Places) | ✅ | ✅ | ✅ | | | No E2E |
| 5.2 Draw (both methods, blocks), «How does the draw work?» | ✅ | ✅ | ✅ | ✅ | ✅ | |
| 5.3 Registrations: settings, list, chips, import, export CSV, draw | ✅ | ✅ | ✅ | | ✅ | Per-person edits 62 %; no E2E |
| 5.3 Automatic emails and jobs (close, weekly report, team reminder) | | | ✅ | | | Hourly relay 79 % |
| 5.4 Tournament players, substitutions, tables per round | ⚠️ | ✅ | ✅ | ✅ | ✅ | `PlayersScreen` 54 % |
| 5.4 Invitations | ✅ | ✅ | ✅ | ✅ | | |
| 5.4 Change the number of players (teams step included) | ✅ | ✅ | ✅ | | ✅ | Web E2E removes a whole team; no E2E of the Teams step |
| 5.4 Teams | ⚠️ | ✅ | ✅ | ✅ | | `TeamsScreen` 49 %, edit only by E2E |
| 5.4 EMA registry (photo, hide, create, delete) | ✅ | ✅ | ✅ | ✅ | | Photo upload, see 4.9 |
| 5.4 Accounts and per-person permissions | ⚠️ | ✅ | ✅ | ✅ | | `GlobalUsersScreen` 64 %; create or edit an account is not in E2E |
| 5.4 Roles and custom roles | ✅ | ✅ | ✅ | | | No E2E |
| 5.5 Round editor, hands, totals, conflicts between editors | ✅ | ✅ | ✅ | ✅ | ✅ | Keyboard navigation of hand rows 42 % |
| 5.5 Round clock, independent timer | ✅ | ✅ | ✅ | ✅ | ✅ | |
| 5.5 Round control and open tables of finished rounds | ✅ | ✅ | ✅ | ✅ | ✅ | |
| 5.5 Big-screen ranking | ✅ | ✅ | | ✅ | | |
| 5.5 QR projection and table cards PDF | ✅ | ✅ | ✅ | | | No check of the PDF content beyond the service test |
| 5.5 Statistics | ✅ | ✅ | ✅ | ✅ | | |
| 5.6 ID cards PDF (extras, back side) | ✅ | ✅ | ✅ | ⚠️ | | E2E only opens the dialog |
| 5.6 EMA report CSV | ✅ | | ✅ | | | No E2E (needs a share window) |
| 5.7 Activity, App errors, DEV menu | ✅ | ✅ | ✅ | ⚠️ | | E2E only opens the empty errors log |

### Shared

| Feature | Covered by | Gap |
| --- | --- | --- |
| Browser back and forward | `BrowserHistoryTest`, `BrowserHistoryUiTest` | Not in a real browser |
| Restore the last screen after a reload | ✅ | `RoutePersistenceUiTest` in both apps (25 Nov) |
| App Links on Android | `SitePagesLinkTest` (which pages go to the browser) | No instrumented test of the intent filters |
| Keyboard support on desktop | `EntryFocusAndScrollUiTest`, `KeyboardFocus` 78 % | |
| Analytics and error log | `AnalyticsCoverageTest`, `ErrorReporterTest`, backend `errorReports` | |
| Desktop: saved password in the keychain | | `PlatformCredentialStore.jvm.kt` 4 % |

## 4. What to test next, in order of priority

The date to have it ready is 23 December 2026. Priority 1 is what can break a tournament day; priority 3 is nice to have.

**Priority 1**

1. ~~**Player web E2E in a browser**~~: done 25 November, `e2e/web` (Playwright in Chromium against the local test
   backend, every night): a guest opens a tournament from the list and from its link, a player signs in, goes to the
   table and saves a hand, and the QR link `/t/<id>/my-table` opens the table of the player. They found that the QR
   link opened My tables but never the table: the start screen closed before the table was found (fixed). Next flows:
   set the seats, a win, the ranking after a hand, the registrations (item 3).
2. ~~**Hand entry of the Player**~~: done 25 November. `HandEntryPanelUiTest` (win from a discard and self-draw,
   chicken hand, draw, penalties, review and edit, conflicts and network errors), `HandDetailsTableUiTest`, and the
   screenshots `dialogs/entry-*` of the Player. They found that the notice of a changed table opened under the entry
   dialog, so a tap on it closed the entry and lost the hand (fixed).
3. **Registrations end to end**: the web E2E covers since 25 November registering without an account from the
   public link, the email link, the waiting list, cancelling and a place given by hand (`registrations.test.mjs`).
   Still missing: registering with an account, food and team in My registration, and the admin per-person edits
   through the screens (`RegistrationsScreen` 62 %).
4. ~~**Change the number of players** through the screens~~: done 25 November for a tournament not started, in the
   web E2E of the admin (`admin-player-count.test.mjs`: 28 to 24, the draw in the browser, tables and emails).
5. ~~**Production smoke check after each deploy**~~: done, `scripts/smoke/smoke.mjs` (25 November).

**Priority 2**

6. **Admin Players and Teams screens** (54 % and 49 %): substitution, clear slot, team edit, drag of members.
7. **`DefaultTournamentRepository` and `FunctionsBackendApi`** (48 % and 62 %): request and response of the calls
   without tests, with the existing `TestBackendApi`. Since 25 November `DefaultTournamentRepositoryWritesTest` covers
   members, teams, player numbers, rename, delete, closing tables, substitutions, the Timer (clocks and controls), the
   own tournaments, the table cards and ID list files, the tournament settings (cleaned values, list read again) and
   refused calls.
8. ~~**Account self-service**~~: done 25 November. `CropSelectedImageTest` (the desktop crop: position, zoom, 512 px,
   PNG, bad file) and `PhotoCropAndConsentUiTest` (crop dialog, remove an own photo, failed remove, withdraw consent).
   The backend of `DELETE /me` and of the sign-in is in `ownAccount.integration.test.ts`. The image picker itself is
   native and stays manual.
9. ~~**Contact form** `POST /support/message`~~: done 25 November (`support.integration.test.ts`: mail, honeypot,
   bad input, no mailbox, limit per address).
10. ~~**Club Admin and custom roles in E2E**~~: done 25 November. `e2e/web/tests/admin-roles.test.mjs` with the new
    test account `club@e2e.test`: the list, refused calls, and a permission taken away from one person.
11. ~~**Load test**~~: done 25 November. `scripts/rehearsal/ramp.mjs` (docs/rehearsal.md) ramps x1 to x16 the phones
    of a 60-player day; locally every stage held (p95 461 ms at x16). A run in staging needs a tournament in play.

**Priority 3**

12. ~~Restore the last screen after a reload (`RoutePersistence`), team statistics, keyboard navigation of hand
    rows~~: done 25 November (`RoutePersistenceUiTest` in both apps, `TeamStatisticsTabUiTest`, `HandRowKeyboardUiTest`).
13. ~~Error branches~~: done 25 November. `routeErrors.integration.test.ts` (22 refused requests of the tournament
    routes, and a refused change changes nothing), `requirePermission.test.ts`, `featureFlagsService.test.ts` and
    `firebaseAuthRest.test.ts`. The versions page keeps its own test.
14. ~~Accessibility checks and the deny-all rules guard~~: done 25 November. `MtsContrastTest` (text 4.5:1 and field
    borders 3:1 in both themes, with the known gaps listed), `AccessibilityChecksUiTest` (every control of the profile
    and the photo menu has a name and 48 dp), and `securityRules.test.ts` (Firestore and Storage refuse everything, and
    the Firebase configs deploy those files). Known gaps: the green and the red as text on cards (2.8 and 2.9:1 light,
    about 4.3:1 dark) and the field borders in dark mode (2.9:1).
15. ~~Green Book, EMA report and ID cards files in E2E~~: done 25 November in the web E2E
    (`e2e/web/tests/documents.test.mjs`): the PDFs open with their pages, the workbooks hold the players and their EMA
    numbers, and the Green Book link of the Player serves the PDF. The download on Android has no automatic test: it
    was checked by hand on the Mac emulator on 16 November.

---

## 5. Test tournaments of the seed script

The script `firebase/functions/src/scripts/seedTestTournaments.ts` writes 13 tournaments with fixed ids that start with `test-`.
This part lists each scenario and the tournament that covers it. A scenario without a tournament is listed as a gap.

### Tournaments

| Id | Players | Mode | Purpose |
| --- | ---: | --- | --- |
| `test-min-16` | 16 | Individual | The smallest valid tournament. One round, finished. |
| `test-draws-penalties` | 16 | Individual | Draws, all draws and penalties. Finished. |
| `test-ties` | 16 | Individual | Ties inside tables and in the ranking. Finished. |
| `test-special-names` | 16 | Individual | EMA players whose names have accents or other alphabets. |
| `test-teams-partial` | 24 | Teams | Teams with 0 to 3 open slots, long and unicode team names. |
| `test-totals-only` | 16 | Teams | Tables with manual totals only, and a total that does not add up to zero. |
| `test-no-extras` | 20 | Individual | No best hands, no chicken hands, no agenda. Every round has a date and a start time, as every tournament must. |
| `test-incomplete-states` | 16 | Teams | Every incomplete table state, and invalid hands. |
| `test-registration-open` | 24 | Teams | Registered, not started. All tables empty. Future dates. |
| `test-extremes` | 32 | Individual | Extreme scores and penalties, a very long name, long venue and agenda. |
| `test-large` | 64 | Teams | 16 tables per round. Last round mixes finished, running, seated and empty tables. |
| `test-last-table` | 24 | Individual | One table has 15 hands. Writing the last hand finishes the tournament. Broken logo address. Chicken hands off. |
| `test-live-ongoing` | 40 | Teams | Running tournament. Round 4 has every table state. Used for tables, conflicts, players and the player app. |

### Scenario matrix

| Scenario | Tournament |
| --- | --- |
| Individual tournament | `test-min-16` and others |
| Team tournament | `test-live-ongoing`, `test-large` |
| Minimum size (16 players) | `test-min-16` |
| Large size (64 players, 48 tables) | `test-large` |
| One round only | `test-min-16` |
| Many rounds (7) | `test-extremes` |
| Finished tournament | `test-min-16`, `test-draws-penalties`, `test-ties` |
| Running tournament | `test-live-ongoing` |
| Not started tournament | `test-registration-open` |
| One table left to finish | `test-last-table` |
| EMA players and open slots together | `test-teams-partial` |
| Unicode and very long names | `test-special-names`, `test-extremes` |
| Teams with missing players | `test-teams-partial` |
| Draw hands, all draws | `test-draws-penalties` |
| Penalties | `test-draws-penalties`, `test-extremes` |
| Ties in table and ranking | `test-ties` |
| Totals only, valid and invalid sum | `test-totals-only` |
| Empty, seated, partial, pending and invalid hands | `test-incomplete-states` |
| Best hands off and chicken hands off | `test-no-extras` |
| Best hands on and chicken hands off | `test-last-table` |
| No agenda | `test-no-extras` |
| Logo address that does not load | `test-last-table` |
| Editor member of some tournaments | accounts `editor@e2e.test` and `outsider@e2e.test` |
| Player at a running table, seated, waiting, finished | accounts `player@`, `seated@`, `waiting@`, `done@` |
| Disabled account | `disabled@e2e.test` |
| Account that plays nowhere | `stranger@e2e.test` |

### Error cases that show a message

| Case | Message | Covered by |
| --- | --- | --- |
| Wrong password or unknown email | The email or password is incorrect. | `SignInTest` |
| Empty email or password | Enter your email and password. | `SignInTest` |
| Disabled account | This account is disabled. Ask an administrator for help. | `SignInTest` |
| Backend not reachable | Network request failed. | `SignInTest` |
| Reset password without email | Enter your email address first. | `SignInTest` |
| Player opens the account list | You do not have permission to perform this action. | `TournamentListTest` |
| Account with no EMA number opens statistics | Your account is not linked to a player yet. Ask the organizers to link your EMA number. | `PlayerStatisticsTest` |
| Another editor saved the same table | Table updated, with the lost values | `TableEditingTest` |
| A hand is invalid (loser without winner, score below 8, winner without score) | The table editor's warning | `TableEditingTest` |

### Gaps without an emulator test

- Photo upload for EMA players, ID card export and EMA report export. They need a file picker or a share window.
- Choosing a password with the password reset email. The local backend keeps the email in memory and sends no real message.
