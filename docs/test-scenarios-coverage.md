# Test coverage

*Audit of 24 November 2026, on `main` at `63f4427` (web 1.0.401, backend 1.0.62).*

This page says which kinds of tests exist, how much code they cover, which features and screen flows have tests, and
what is still untested, in order of priority. The last part lists the test tournaments of the seed script.

## 1. Kinds of tests

| Kind | Where | How to run | When it runs |
| --- | --- | --- | --- |
| Kotlin unit tests | `domain`, `data`, `ui`, `designsystem`, `adminApp`, `playerApp` (`commonTest`, `jvmTest`) | `./gradlew :domain:jvmTest :data:jvmTest :ui:jvmTest :adminApp:jvmTest :playerApp:jvmTest` | By hand, before a push |
| Compose UI tests on the JVM | `*UiTest` in `ui`, `designsystem`, `adminApp`, `playerApp` | Same as above | Same |
| Screenshot tests (Roborazzi) | `*ScreenshotTest`, references in `*/screenshots/` | `docs/screenshot-tests.md` | By hand and Actions > Check · Screenshots |
| Backend unit tests | `firebase/functions/src/**/*.test.ts` | `npm test` | By hand and on the pull request to `main` |
| Backend integration tests (Auth and Firestore emulators) | `firebase/functions/src/**/*.integration.test.ts` | `npm run test:integration` | Same |
| Android instrumented E2E | `androidApp/src/androidTest` (36 classes, journeys included) | `scripts/run-e2e-parallel.py` on the Mac | By hand |
| Game day (bots on staging) | `scripts/rehearsal`, `.github/workflows/scenario.yml`, `rehearsal.yml` | Actions > Test · Game day | By hand |
| Tests of the Game day scripts | `scripts/rehearsal/test/*.test.mjs` | `node --test scripts/rehearsal/test/*.test.mjs` | By hand |
| Project rules | `scoringCases` (server, admin, player share `testdata/scoring-cases.json`), `clientContract.test` (app calls match API routes), `CommonCodeIsMultiplatformTest` (no JVM-only APIs in `commonMain`), `AnalyticsCoverageTest`, `LocalizationParityTest`, `DesignTokenRatchetTest`, `ButtonConventionTest`, `ScrollIndicatorsConventionTest` | With the Kotlin and backend tests | Same |
| Smoke test of a deploy | `scripts/smoke/smoke.mjs`, its tests in `scripts/smoke/test/` | `node scripts/smoke/smoke.mjs` (`MTS_SITE_URL` for staging) | At the end of every deploy to production and to staging |
| Performance | `benchmarks/`, `.github/workflows/benchmark.yml` | Actions | Every morning against production |
| Tooling scripts | `scripts/versions_test.py`, `scripts/recovery.test.mjs` | `python3 scripts/versions_test.py`, `node --test` | By hand |

There are no tests of: the web (Wasm) apps in a real browser, the Firestore and Storage rules, load beyond Game day,
or accessibility.

## 2. Measured coverage

### Kotlin (Kover, JVM tests only)

`./gradlew koverHtmlReportCoverage koverXmlReportCoverage` (all tests green, 9 min 40 s in the cloud).

| Module | Lines covered |
| --- | ---: |
| domain | 94.9 % |
| designsystem | 93.1 % |
| ui | 87.8 % |
| playerApp | 80.1 % |
| adminApp | 80.0 % |
| data | 65.6 % |
| **Total** | **81.4 % of lines, 56.2 % of branches** |

Kover counts only the JVM build. `androidMain` (~1,000 lines), `wasmJsMain` (~1,000 lines) and `androidApp/src/main`
(~360 lines) are not measured; the Android E2E tests run part of `androidMain`, nothing runs `wasmJsMain`.

Files with the most untested lines:

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
| `ui/.../ImageCropDialog.kt` | 0 % | 134 | Photo crop when uploading a photo |
| `data/.../DefaultAuthRepository.kt` | 40 % | 81 | Sign in, sign out, password change, delete own account |
| `playerApp/.../TeamStatistics.kt` | 4 % | 67 | Team statistics in the Player |
| `adminApp/.../CreateTournamentPresenter.kt` | 63 % | 52 | Error paths of the create flow and the registrations variant |
| `adminApp` and `playerApp` `RoutePersistence.kt` | 0 % | 87 | Restore of the last screen after a reload |
| `adminApp/.../HandRowNavigation.kt` | 42 % | 39 | Keyboard navigation between hand rows |
| `ui/.../AppErrorUi.kt` | 42 % | 51 | Messages of each error kind |

### Backend (`npm run test:coverage`, unit and integration tests on the emulators)

1,390 tests, all green. **94.4 % of lines, 85.5 % of branches, 94.5 % of functions.**

Lowest files:

| File | Lines | Branches | What is missing |
| --- | ---: | ---: | --- |
| `api/routes/support.routes.ts` | 65 % | | `POST /support/message` (Contact form) has no route test |
| `services/featureFlagsService.ts` | 52 % | 40 % | Flags read from Firestore and their cache |
| `services/firebaseAuthRest.ts` | 57 % | | Password sign in and refresh through the Auth REST API |
| `services/versionsPage.ts` | 57 % | | The versions page |
| `api/routes/auth.routes.ts` | 72 % | 64 % | Own photo upload and delete, `DELETE /me` (delete own account) |
| `services/registrationHourRelay.ts` | 79 % | | The hourly relay from Belgium to Madrid |
| `api/routes/tournaments.routes.ts` | 86 % | 69 % | Error branches (404, 409, invalid bodies) of many routes |
| `api/middleware/requirePermission.ts` | 86 % | 60 % | Refusals for some permission kinds |

`services/handSynthesis.ts` and `services/googlePlayTester.ts` only serve the seed scripts and are not measured.

## 3. Features and screen flows against their tests

U = Kotlin unit or JVM UI test, S = screenshot, B = backend unit or integration test, E = Android E2E, G = Game day.
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
| 4.6 Tournament QR `/<slug>/my-table` | ✅ | | ✅ | | | No E2E of the link opening the table (App Links) |
| 4.7 Statistics with filters (player, tournament, team) | ✅ | ✅ | ✅ | ✅ | | Team statistics 4 % |
| 4.8 Combinations, Penalties | ✅ | ✅ | | | | |
| 4.8 Green Book (download on Android) | ⚠️ | | | | | Only the link is tested; the Android download and the offline copy are not |
| 4.8 Contact form | | ✅ | ⚠️ | | | `POST /support/message` untested |
| 4.9 Sign in, password recovery | ✅ | ✅ | ✅ | ✅ | | Choosing the new password from the email (see «Gaps without an emulator test») |
| 4.9 Profile: photo, alias, theme, language, delete account, withdraw consent | ⚠️ | ✅ | ⚠️ | ⚠️ | | Photo crop 0 %, `DELETE /me` untested, only the theme is in E2E |
| 4.9 8 languages, AI translation notice | ✅ | ✅ | | | | |
| 4.9 Refresh rhythms | ✅ | | | | ✅ | |

### Admin

| Feature | U | S | B | E | G | Gap |
| --- | :-: | :-: | :-: | :-: | :-: | --- |
| 5.1 Tournament list by role | ✅ | ✅ | ✅ | ✅ | | No E2E as Club Admin |
| 5.1 Create tournament (with or without registrations), settings | ✅ | ✅ | ✅ | ✅ | ✅ | No E2E of «Open registrations» |
| 5.1 Organizations, club venue (Places) | ✅ | ✅ | ✅ | | | No E2E |
| 5.2 Draw (both methods, blocks), «How does the draw work?» | ✅ | ✅ | ✅ | ✅ | ✅ | |
| 5.3 Registrations: settings, list, chips, import, export CSV, draw | ✅ | ✅ | ✅ | | ✅ | Per-person edits 62 %; no E2E |
| 5.3 Automatic emails and jobs (close, weekly report, team reminder) | | | ✅ | | | Hourly relay 79 % |
| 5.4 Tournament players, substitutions, tables per round | ⚠️ | ✅ | ✅ | ✅ | ✅ | `PlayersScreen` 54 % |
| 5.4 Invitations | ✅ | ✅ | ✅ | ✅ | | |
| 5.4 Change the number of players (teams step included) | ✅ | ✅ | ✅ | | ✅ | No E2E |
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
| Restore the last screen after a reload | | `RoutePersistence` 0 % in both apps |
| App Links on Android | `SitePagesLinkTest` (which pages go to the browser) | No instrumented test of the intent filters |
| Keyboard support on desktop | `EntryFocusAndScrollUiTest`, `KeyboardFocus` 78 % | |
| Analytics and error log | `AnalyticsCoverageTest`, `ErrorReporterTest`, backend `errorReports` | |
| Desktop: saved password in the keychain | | `PlatformCredentialStore.jvm.kt` 4 % |

## 4. What to test next, in order of priority

The date to have it ready is 23 December 2026. Priority 1 is what can break a tournament day; priority 3 is nice to have.

**Priority 1**

1. **Player web E2E in a browser.** Most players will use the web on their phone, and no test opens the Wasm app in a
   browser. A few Playwright flows against the local test backend: sign in, open the tournament, set seats, enter a
   hand, see it in the ranking, open `/<slug>/my-table`.
2. ~~**Hand entry of the Player**~~: done 25 November. `HandEntryPanelUiTest` (win from a discard and self-draw,
   chicken hand, draw, penalties, review and edit, conflicts and network errors), `HandDetailsTableUiTest`, and the
   screenshots `dialogs/entry-*` of the Player. They found that the notice of a changed table opened under the entry
   dialog, so a tap on it closed the entry and lost the hand (fixed).
3. **Registrations end to end**: E2E (or web E2E) of registering without an account with the email link, the waiting
   list, cancellation, and the admin per-person edits (`RegistrationsScreen` 62 %).
4. **Change the number of players** has unit, backend and Game day tests but no E2E through the screens.
5. ~~**Production smoke check after each deploy**~~: done, `scripts/smoke/smoke.mjs` (25 November).

**Priority 2**

6. **Admin Players and Teams screens** (54 % and 49 %): substitution, clear slot, team edit, drag of members.
7. **`DefaultTournamentRepository` and `FunctionsBackendApi`** (48 % and 62 %): request and response of the calls
   without tests, with the existing `TestBackendApi`.
8. **Account self-service**: `DELETE /me`, own photo upload and crop, withdraw consent (backend and UI).
9. **Contact form** `POST /support/message` (limits and mail).
10. **Club Admin and custom roles in E2E**: what the list shows and what is refused.
11. **Load test** of hand saves and ranking polls for 60 players beyond Game day, to know the margin.

**Priority 3**

12. Restore the last screen after a reload (`RoutePersistence`), team statistics, keyboard navigation of hand rows.
13. Error branches of `tournaments.routes.ts`, `requirePermission`, feature flags, versions page, Auth REST.
14. Accessibility checks (contrast, touch size, TalkBack labels) and a guard that the Firestore and Storage rules stay
    «deny everything».
15. Green Book download on Android, EMA report and ID cards files in E2E.

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
