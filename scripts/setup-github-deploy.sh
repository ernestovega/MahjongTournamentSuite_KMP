#!/usr/bin/env bash
# Lets the GitHub Actions workflows firebase.yml (deploy) and ui-tests.yml (Firebase Test Lab)
# sign in to Google Cloud without a stored key (Workload Identity Federation). Run it once in Google Cloud Shell
# (https://shell.cloud.google.com) signed in as a project owner. It is safe to run again.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-mahjong-tournament-suite}"
REPOSITORY="ernestovega/MahjongTournamentSuite_KMP"
REPOSITORY_ID="924300180"
POOL="github"
PROVIDER="github-actions"
SA_NAME="github-deployer"  # Also runs the UI tests in Test Lab.
SA_EMAIL="${SA_NAME}@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$PROJECT_ID" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

echo "Enabling the APIs..."
gcloud services enable \
  iam.googleapis.com iamcredentials.googleapis.com sts.googleapis.com \
  cloudresourcemanager.googleapis.com serviceusage.googleapis.com \
  cloudfunctions.googleapis.com cloudbuild.googleapis.com run.googleapis.com \
  artifactregistry.googleapis.com eventarc.googleapis.com pubsub.googleapis.com \
  cloudscheduler.googleapis.com cloudtasks.googleapis.com secretmanager.googleapis.com \
  firebase.googleapis.com firebasehosting.googleapis.com firebaserules.googleapis.com \
  firestore.googleapis.com firebasestorage.googleapis.com \
  testing.googleapis.com toolresults.googleapis.com storage.googleapis.com

echo "Creating the deploy service account..."
gcloud iam service-accounts describe "$SA_EMAIL" >/dev/null 2>&1 \
  || gcloud iam service-accounts create "$SA_NAME" --display-name="GitHub Actions deploy"

echo "Granting the deploy roles..."
for role in \
  roles/firebase.admin \
  roles/cloudfunctions.admin \
  roles/run.admin \
  roles/iam.serviceAccountUser \
  roles/artifactregistry.admin \
  roles/cloudbuild.builds.editor \
  roles/cloudscheduler.admin \
  roles/cloudtasks.admin \
  roles/secretmanager.admin \
  roles/datastore.indexAdmin \
  roles/serviceusage.serviceUsageConsumer \
  roles/cloudtestservice.testAdmin \
  roles/firebase.analyticsViewer \
  roles/storage.admin; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member="serviceAccount:${SA_EMAIL}" --role="$role" --condition=None >/dev/null
done

echo "Creating the GitHub identity pool and provider..."
gcloud iam workload-identity-pools describe "$POOL" --location=global >/dev/null 2>&1 \
  || gcloud iam workload-identity-pools create "$POOL" --location=global --display-name="GitHub"

# Only workflows of this repository that run on main can sign in.
CONDITION="assertion.repository_id == '${REPOSITORY_ID}' && assertion.ref == 'refs/heads/main'"
if gcloud iam workload-identity-pools providers describe "$PROVIDER" \
  --location=global --workload-identity-pool="$POOL" >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER" \
    --location=global --workload-identity-pool="$POOL" \
    --attribute-condition="$CONDITION"
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" \
    --location=global --workload-identity-pool="$POOL" \
    --display-name="GitHub Actions" \
    --issuer-uri="https://token.actions.githubusercontent.com" \
    --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_id=assertion.repository_id,attribute.ref=assertion.ref" \
    --attribute-condition="$CONDITION"
fi

POOL_ID="projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}"
gcloud iam service-accounts add-iam-policy-binding "$SA_EMAIL" \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/${POOL_ID}/attribute.repository_id/${REPOSITORY_ID}" >/dev/null

cat <<EOF

Done. Add these two variables in GitHub
(Settings > Secrets and variables > Actions > Variables > New repository variable):

  GCP_WORKLOAD_IDENTITY_PROVIDER = ${POOL_ID}/providers/${PROVIDER}
  GCP_DEPLOY_SERVICE_ACCOUNT     = ${SA_EMAIL}

They are not secrets. Only the ${REPOSITORY} workflows on main can use them.
EOF
