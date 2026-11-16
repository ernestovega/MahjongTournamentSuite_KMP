# Staging

Staging is a second Firebase project with the same backend and web apps as production and test data only. Use it to
rehearse a whole tournament before a release, with real devices, emulators and bots. Nothing in staging reaches the
production data.

## What is where

| Part | Staging |
| --- | --- |
| Firebase project | The repository variable `STAGING_FIREBASE_PROJECT` |
| Player web | `https://<project>.web.app` |
| Admin web | `https://<project>.web.app/admin/` |
| API | `https://europe-southwest1-<project>.cloudfunctions.net/api` |
| Android app | Build type `staging`: package `com.etologic.mahjongtournamentsuite.staging`, name "MTS Staging" |

## Set it up once

1. Create the Firebase project, on the Blaze plan. Firestore in `eur3`, Storage in the location of production,
   Authentication with email and password.
2. Add a web app and copy its `apiKey`. Add an Android app with the package
   `com.etologic.mahjongtournamentsuite.staging` and put its `google-services.json` in `androidApp/src/staging/`.
3. In Google Cloud Shell, from a clone of the repository: `PROJECT_ID=<project> bash scripts/setup-github-deploy.sh`.
   It also enables the Cloud Billing API, which the deploy of the functions needs and cannot enable itself.
4. In the Secret Manager of staging: `MTS_SMTP_URI` and `PLACES_API_KEY` (copies of production) and
   `MTS_ID_TOOLKIT_API_KEY` (the `apiKey` of step 2).
5. In GitHub, the variables `STAGING_FIREBASE_PROJECT`, `STAGING_GCP_WORKLOAD_IDENTITY_PROVIDER` and
   `STAGING_GCP_DEPLOY_SERVICE_ACCOUNT` (the two values that step 3 prints), and the secret `STAGING_ADMIN_EMAIL`.
6. Put `mts.staging.project=<project>` in `gradle.properties`.

## Deploy and seed

- Actions > Deploy staging > Run workflow, action `deploy`: publishes main on staging. The functions read
  `.env.<project>`, written by the workflow: invitation links to the staging web and photos in the staging bucket.
  The staging web apps send no Analytics and no App Check token to production.
- The same workflow, action `seed`: countries, the admin account of `STAGING_ADMIN_EMAIL` (a new one gets the
  password reset email) and the EMA players of `firebase/functions/src/scripts/stagingPlayers.ts`
  (the people registered for the next tournament, plus fictitious 9999xxxx numbers). The players marked `byHand` are left for the rehearsal, which creates them in Player base.

## The Android app

`./gradlew :androidApp:assembleStaging` builds the staging app, signed with the debug key. It installs next to the real
app. Without `mts.staging.project` the app calls an address that does not exist, never production.
`-Pmts.testBuildType=staging` runs the instrumented tests on the staging app.
