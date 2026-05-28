import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";

const adminApp = initializeApp();

export const auth = getAuth(adminApp);
export const db = getFirestore(adminApp);

db.settings({
  ignoreUndefinedProperties: true,
});
