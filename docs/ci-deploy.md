# Automatic Firebase deploy

The workflow `.github/workflows/firebase.yml` has two jobs:

- **Build and test** runs on every pull request to `main`. It runs the functions tests and builds both Wasm web apps. Nothing is deployed.
- **Deploy to Firebase** runs on every push to `main`, which is every merged pull request. It can also be started by hand from the Actions tab. It runs the functions tests and then deploys hosting (both sites), functions, Firestore rules and indexes, and Storage rules. The `predeploy` steps in `firebase.json` build the web apps and the functions.

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

The script creates the service account `github-deployer` with these roles: Firebase Admin, Cloud Functions Admin, Cloud Run Admin, Service Account User, Artifact Registry Admin, Cloud Build Editor, Cloud Scheduler Admin, Cloud Tasks Admin, Secret Manager Admin, Cloud Datastore Index Admin and Service Usage Consumer.

Function secrets (`MTS_ID_TOOLKIT_API_KEY`, `MTS_SMTP_URI`, `PLACES_API_KEY`) stay in Secret Manager. The workflow does not change them.

## Protect main

In GitHub, **Settings > Rules > Rulesets > New ruleset > New branch ruleset**:

- Name `main`, enforcement **Active**.
- Target branches: **Include default branch**.
- Turn on **Restrict deletions**, **Block force pushes** and **Require a pull request before merging** with 0 required approvals.
- Turn on **Require status checks to pass** and add **Build and test**.
- Leave the bypass list empty so nobody can push to `main` directly.
