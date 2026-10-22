import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { getStorage } from "firebase-admin/storage";

const storageBucketName = process.env.EMA_FIREBASE_STORAGE_BUCKET?.trim()
  || "mahjong-tournament-suite.firebasestorage.app";
export const firebaseProjectId = process.env.EMA_FIREBASE_PROJECT?.trim()
  || process.env.GCLOUD_PROJECT?.trim()
  || process.env.GOOGLE_CLOUD_PROJECT?.trim()
  || "mahjong-tournament-suite";

const adminApp = initializeApp({
  projectId: firebaseProjectId,
  storageBucket: storageBucketName,
  // Scripts run outside Google Cloud and need it to enqueue tasks. In Cloud Functions the SDK finds it alone.
  serviceAccountId: process.env.EMA_FIREBASE_SERVICE_ACCOUNT?.trim() || undefined,
});

export const auth = getAuth(adminApp);
export const db = getFirestore(adminApp);
export const storage = getStorage(adminApp).bucket(storageBucketName);

db.settings({
  ignoreUndefinedProperties: true,
});
