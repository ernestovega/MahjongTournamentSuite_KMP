# Automatic Firebase deploy

## Branches

- Work happens on branches that start from `develop`. Merge them into `develop` without a pull request. Nothing runs on `develop`.
- To release, open a pull request from `develop` to `main`. It runs every test, including the UI tests.
- After the merge into `main`, the workflow deploys Firebase.

## Workflow

The workflow `.github/workflows/firebase.yml` has two parts:

- **Build and test** runs on every pull request to `main`. Six checks run at the same time:
  - **Functions tests**: the unit tests of the functions.
  - **Functions integration tests**: the integration tests on the Firebase Auth and Firestore emulators.
  - **Kotlin tests**: ktlint and the tests of all modules.
  - **Scripts and diagnostics tests**: the Node tests of `scripts/` and the start diagnostics in Chrome.
  - **Build web apps**: both Wasm web apps.
  - **UI tests**: the UI tests of both Android apps on emulators of the GitHub runner. See [Instrumented UI tests](instrumented-ui-tests.md).

  The last job, named **Build and test**, fails when any check fails. The ruleset of `main` requires only this job. Nothing is deployed.
- **Deploy to Firebase** runs on every push to `main`. It can also be started by hand from the Actions tab. It runs the functions tests and then deploys hosting (both sites), functions, Firestore rules and indexes, and Storage rules. The `predeploy` steps in `firebase.json` build the web apps and the functions.

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

Function secrets (`MTS_ID_TOOLKIT_API_KEY`, `MTS_SMTP_URI`, `PLACES_API_KEY`) stay in Secret Manager. The workflow does not change them.

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
- **Pending, no date:** a release pipeline for the Android apps: build, signing, Play Store. It also turns on R8 (code shrinking and obfuscation) and resource shrinking. Both are off now (`isMinifyEnabled = false`).
