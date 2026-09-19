import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { conflict, notFound } from "../api/httpError";

export type UserProfile = {
  uid: string;
  email: string;
  emaId: string;
  contactEmail: string;
};

export async function getUserProfile(uid: string): Promise<UserProfile> {
  const snap = await db.doc(`users/${uid}`).get();
  if (!snap.exists) {
    throw notFound("User profile not found");
  }

  const email = snap.get("email") as string;
  const emaId = snap.get("emaId") as string;
  const contactEmail = (snap.get("contactEmail") as string | undefined) ?? email;

  return { uid, email, emaId, contactEmail };
}

export async function getUserProfileByEmaId(emaId: string): Promise<UserProfile> {
  const matches = await db.collection("users").where("emaId", "==", emaId).limit(1).get();
  if (matches.empty) {
    throw notFound("Unknown emaId");
  }

  return getUserProfile(matches.docs[0].id);
}

export async function assertEmaIdAvailable(emaId: string): Promise<void> {
  const matches = await db.collection("users").where("emaId", "==", emaId).limit(1).get();
  if (!matches.empty) {
    throw conflict("emaId already in use");
  }
}

export async function createUserProfile(uid: string, email: string, emaId: string): Promise<void> {
  await db.doc(`users/${uid}`).set(
    {
      uid,
      email,
      emaId,
      contactEmail: email,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true },
  );
}
