import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { conflict, notFound } from "../api/httpError";

export type UserProfile = {
  uid: string;
  email: string;
  emaId: string;
  contactEmail: string;
};

export type EmaIdMapping = {
  uid: string;
  email: string;
  emaId: string;
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

export async function getEmaIdMapping(emaId: string): Promise<EmaIdMapping> {
  const snap = await db.doc(`emaIdUsers/${emaId}`).get();
  if (!snap.exists) {
    throw notFound("Unknown emaId");
  }

  return {
    uid: snap.get("uid") as string,
    email: snap.get("email") as string,
    emaId: snap.get("emaId") as string,
  };
}

export async function assertEmaIdAvailable(emaId: string): Promise<void> {
  const snap = await db.doc(`emaIdUsers/${emaId}`).get();
  if (snap.exists) {
    throw conflict("emaId already in use");
  }
}

export async function getEmailForEmaId(emaId: string): Promise<string> {
  const mapping = await getEmaIdMapping(emaId);
  return mapping.email;
}

export async function linkEmaIdToUser(uid: string, email: string, emaId: string): Promise<void> {
  const mappingRef = db.doc(`emaIdUsers/${emaId}`);

  await db.runTransaction(async (tx) => {
    const current = await tx.get(mappingRef);
    if (current.exists) {
      throw conflict("emaId already in use");
    }

    tx.set(mappingRef, {
      uid,
      email,
      emaId,
      createdAt: FieldValue.serverTimestamp(),
    });

    tx.set(
      db.doc(`users/${uid}`),
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
  });
}
