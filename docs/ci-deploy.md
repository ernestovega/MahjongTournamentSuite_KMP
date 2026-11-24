# Automatic Firebase deploy

## Branches

- Work happens on branches that start from `develop`. Merge them into `develop` without a pull request. Nothing runs on `develop`.
- To release, open a pull request from `develop` to `main`. It runs every test except the UI tests, which run by hand until the apps are in production.
- After the merge into `main`, the workflow deploys Firebase.

## Workflow

The workflow `.github/workflows/firebase.yml` (shown in GitHub as **Deploy · Production**) has two parts. Its tests run in GitHub runners, which are free because the repository is public. Only the deploy goes to Firebase.

- **Build and test** runs on every pull request to `main`. It first checks that every changed product has a higher version ([versioning.md](versioning.md)). Five checks run at the same time:
  - **Functions tests**: the unit tests of the functions.
  - **Functions integration tests**: the integration tests on the Firebase Auth and Firestore emulators.
  - **Kotlin tests**: ktlint and the tests of all modules.
  - **Scripts and diagnostics tests**: the Node tests of `scripts/` and the start diagnostics in Chrome.
  - **Build web apps**: both Wasm web apps.

  The UI tests do not run here until the apps are in production. Run them by hand: see [Instrumented UI tests](instrumented-ui-tests.md).

  The last job, named **Build and test**, fails when any check fails. The ruleset of `main` requires only this job. Nothing is deployed.
- **Deploy to Firebase** runs on every push to `main`. It can also be started by hand from the Actions tab. Until production it runs no test (the tests run on the computer before the push). With the gate on (below), it runs only after every test and a deploy to staging. It deploys hosting (one site with both web apps, see [web-hosting.md](web-hosting.md)), functions, Firestore rules and indexes, and Storage rules. The `predeploy` steps in `firebase.json` build the web apps and the functions.

- **Publish to Google Play** runs only when the workflow is started by hand on `main` (Actions > Deploy · Production > Run workflow). The upload on every push is paused since 2026-11-11. It builds and uploads each Android app whose version code is not on Google Play yet. See [android-release.md](android-release.md#automatic-upload-to-google-play).

### Every night

Every test runs on `main` every night, and GitHub emails the owner when one fails (Ernesto, 2026-11-24). Times in UTC
(Madrid is one hour later in winter):

| Time | Workflow | What |
|---|---|---|
| 02:30 | Deploy · Production | The five checks of **Build and test**. Nothing is deployed. |
| 02:45 | Test · Screenshots | Compares every screen with its reference image. |
| 03:00 | Test · UI (emulators) | The instrumented UI tests of both parts on the GitHub emulators. |
| 03:15 | Test · Game day · Auto | Deploys `main` to staging and plays a tournament: `small` on even days of the year, `corners` on odd days. |
| 05:00 | Check · Production benchmark | The times of production (see [benchmark.md](benchmark.md)). |

Two tests guard the lists themselves: `testList.test.ts` fails when a unit test of the functions is not in `npm test`,
or an integration test is outside the folders of `npm run test:integration`. The scripts job runs the tests of
`scripts/rehearsal/test` too.

### The gate before production

The repository variable `PROD_DEPLOY_GATE` turns the gate on (Settings > Secrets and variables > Actions > Variables,
value `on`; planned for 23-dec-2026, Ernesto). Then each push to `main`:

1. runs the five checks of **Build and test** (without the version check: a push has no base branch);
2. deploys `main` to staging (**Deploy to staging first**, the deploy job of `staging.yml`);
3. deploys to production only when all of that passed.

A failed test or a failed staging deploy stops the run, and production keeps the version it had. Remove the variable
(or give it another value) to deploy without tests again. A run started by hand on `main` deploys without the gate.

The deploy uses `--force`, so a function removed from the code is also deleted in Firebase. Two deploys never run at the same time: a newer push waits for the running one.

## Credentials

The deploy signs in to Google Cloud with Workload Identity Federation. No key is stored in GitHub. Only workflows of this repository that run on `main` can sign in.

One-time setup:

1. Open [Google Cloud Shell](https://shell.cloud.google.com) with a project owner account.
2. Run:

   ```bash
   curl -fsSL https://raw.githubusercontent.com/ernestovega/MahjongTournamentSuite_KMP/main/scripts/setup-github-deploy.sh | bash
   ```

3. Add the two values the script prints as repository variables in GitHub: **Settings > Secrets and variables > Actions > Variables**.
   - `GCP_WORKLOAD_IDENTITY_PROVIDER`
   - `GCP_DEPLOY_SERVICE_ACCOUNT`

The script creates the service account `github-deployer` with these roles: Firebase Admin, Cloud Functions Admin, Cloud Run Admin, Service Account User, Artifact Registry Admin, Cloud Build Editor, Cloud Scheduler Admin, Cloud Tasks Admin, Secret Manager Admin, Cloud Datastore Index Admin and Service Usage Consumer. The UI tests workflow (`ui-tests.yml`) uses the same account to run Firebase Test Lab, with Firebase Test Lab Admin, Firebase Analytics Viewer and Storage Admin.

Function secrets (`MTS_ID_TOOLKIT_API_KEY`, `MTS_SMTP_URI`, `PLACES_API_KEY`, `MTS_SUPPORT_EMAIL`) stay in Secret Manager. The workflow does not change them. Each one must exist before it is bound in `firebase/functions/src/index.ts`, or the deploy fails. `MTS_SUPPORT_EMAIL` is the mailbox that gets the messages of the "Need help?" form of the sign-in; the apps never see it.

## Protect main

In GitHub, **Settings > Rules > Rulesets > New ruleset > New branch ruleset**:

- Name `main`, enforcement **Active**.
- Target branches: **Include default branch**.
- Turn on **Restrict deletions**, **Block force pushes** and **Require a pull request before merging** with 0 required approvals.
- Turn on **Require status checks to pass** and add **Build and test**. It includes the UI tests.
- Leave the bypass list empty so nobody can push to `main` directly.

## Dependency updates

`.github/dependabot.yml` makes Dependabot open pull requests every week. It covers Gradle, npm (root and `firebase/functions`) and GitHub Actions. Each pull request runs the normal check **Build and test**. Merge it when the check passes. Dependabot opens at most 3 pull requests at a time for Gradle and for functions.

## Kotlin style (ktlint)

The check **Kotlin tests** runs `./gradlew ktlintCheck`. The rules are in `.editorconfig`. The code that existed before the check has about 10 000 style warnings. They are in `ktlint-baseline.xml` of each module, so only new code fails.

- Check on your computer: `./gradlew ktlintCheck`.
- Fix the style of the files you changed: `./gradlew ktlintFormat`.
- Fix old code in a module and make its baseline smaller: fix the files, then run `./gradlew :<module>:ktlintGenerateBaseline`.

## Dependency updates

`.github/dependabot.yml` makes Dependabot open pull requests to `develop` every week. It covers Gradle, npm (root and `firebase/functions`) and GitHub Actions. Nothing checks them on `develop`. The checks run when you release `develop` to `main`.

## Plan and status

- **Done:** deploy on merge to `main`, the ruleset on `main`, all tests in the pull request to `main`, ktlint and Dependabot.
- **Idea:** UI tests of the web apps with Playwright, in Chromium, Firefox and WebKit. The first tests would check that the app starts and draws the canvas.
- **Pending, no date:** a release pipeline for the Android apps: build, signing, Play Store. R8 code and resource shrinking is on for release builds since 2026-11-13, without obfuscation (`proguard-rules.pro` in each app module).
