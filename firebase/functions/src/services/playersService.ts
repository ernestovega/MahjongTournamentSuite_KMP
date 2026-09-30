import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";

import { db, storage } from "../firebase";
import { badRequest, notFound } from "../api/httpError";
import { normalizePlayerNamePart } from "./playerName";
import { bumpGlobalDataVersion } from "./dataVersionsService";

/** Firestore collection for EMA registry records. Never use this for tournament player slots. */
export const EMA_PLAYER_REGISTRY_COLLECTION = "emaPlayerRegistry";

export type Player = {
  emaId: string;
  firstName: string;
  lastName: string;
  country: string;
  photoUrl: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function timestampToIso(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

function toPlayer(emaId: string, data: FirebaseFirestore.DocumentData): Player {
  const storedCountry = String(data.country ?? "").trim().toUpperCase();
  return {
    emaId,
    firstName: normalizePlayerNamePart(String(data.firstName ?? "")),
    lastName: normalizePlayerNamePart(String(data.lastName ?? "")),
    country: storedCountry === "EU" ? "" : storedCountry,
    photoUrl: typeof data.photoUrl === "string" ? data.photoUrl : null,
    createdAt: timestampToIso(data.createdAt),
    updatedAt: timestampToIso(data.updatedAt),
  };
}

export function validateEmaId(value: unknown): string {
  const emaId = String(value ?? "").trim();
  if (!/^\d+$/.test(emaId)) {
    throw badRequest("EMA number must contain only digits");
  }
  if (emaId.length > 8) {
    throw badRequest("EMA number must contain at most 8 digits");
  }
  return emaId.padStart(8, "0");
}

export async function listPlayers(): Promise<Player[]> {
  const snapshot = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).get();
  return snapshot.docs
    .map((document) => toPlayer(document.id, document.data()))
    .sort((left, right) => left.lastName.localeCompare(right.lastName)
      || left.firstName.localeCompare(right.firstName)
      || left.emaId.localeCompare(right.emaId));
}

export async function createPlayer(params: { emaId: string; firstName: string; lastName: string; country: string }): Promise<Player> {
  const ref = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId);
  const existing = await ref.get();
  if (existing.exists) {
    throw badRequest("A player with this EMA number already exists");
  }
  await ref.create({
    firstName: normalizePlayerNamePart(params.firstName),
    lastName: normalizePlayerNamePart(params.lastName),
    country: params.country,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  const created = await ref.get();
  await bumpGlobalDataVersion("emaPlayers");
  return toPlayer(params.emaId, created.data() ?? {});
}

export async function updatePlayer(params: {
  previousEmaId: string;
  emaId: string;
  firstName: string;
  lastName: string;
  country: string;
}): Promise<void> {
  const previousRef = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(params.previousEmaId);
  const previousSnapshot = await previousRef.get();
  if (!previousSnapshot.exists) {
    throw notFound("Player not found");
  }
  const targetRef = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId);
  if (params.previousEmaId !== params.emaId && (await targetRef.get()).exists) {
    throw badRequest("A player with this EMA number already exists");
  }
  const update = {
    firstName: normalizePlayerNamePart(params.firstName),
    lastName: normalizePlayerNamePart(params.lastName),
    country: params.country,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (params.previousEmaId === params.emaId) {
    await previousRef.update({ ...update, name: FieldValue.delete() });
    await bumpGlobalDataVersion("emaPlayers");
    return;
  }

  const references = await db.collectionGroup("players")
    .where("assignedEmaId", "==", params.previousEmaId)
    .get();
  const batch = db.batch();
  const previousData = previousSnapshot.data() ?? {};
  const { name: _legacyName, ...retainedData } = previousData;
  batch.create(targetRef, { ...retainedData, ...update });
  batch.delete(previousRef);
  references.docs.forEach((reference) => batch.update(reference.ref, { assignedEmaId: params.emaId, updatedAt: FieldValue.serverTimestamp() }));
  await batch.commit();
  await bumpGlobalDataVersion("emaPlayers");
}

const photoExtensions: Record<string, string> = {
  "image/gif": "gif",
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

function hasValidImageSignature(bytes: Buffer, contentType: string): boolean {
  if (contentType === "image/jpeg") {
    return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  }
  if (contentType === "image/png") {
    return bytes.length >= 8 && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
  }
  if (contentType === "image/gif") {
    const signature = bytes.subarray(0, 6).toString("ascii");
    return signature === "GIF87a" || signature === "GIF89a";
  }
  if (contentType === "image/webp") {
    return bytes.length >= 12
      && bytes.subarray(0, 4).toString("ascii") === "RIFF"
      && bytes.subarray(8, 12).toString("ascii") === "WEBP";
  }
  return false;
}

export async function updatePlayerPhoto(params: {
  emaId: string;
  contentType: string;
  dataBase64: string;
}): Promise<Player> {
  const extension = photoExtensions[params.contentType];
  if (!extension) throw badRequest("Photo must be a JPEG, PNG, WebP, or GIF image");

  const ref = db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId);
  if (!(await ref.get()).exists) throw notFound("Player not found");

  const bytes = Buffer.from(params.dataBase64, "base64");
  if (bytes.length === 0) throw badRequest("Photo is empty");
  if (bytes.length > 5 * 1024 * 1024) throw badRequest("Photo must be 5 MB or smaller");
  if (!hasValidImageSignature(bytes, params.contentType)) throw badRequest("Photo data does not match its image type");

  const file = storage.file(`playerPhotos/${params.emaId}.${extension}`);
  const token = randomUUID();
  await file.save(bytes, {
    contentType: params.contentType,
    metadata: {
      cacheControl: "public, max-age=604800",
      metadata: { firebaseStorageDownloadTokens: token },
    },
  });
  const objectName = encodeURIComponent(file.name);
  const photoUrl = `https://firebasestorage.googleapis.com/v0/b/${storage.name}/o/${objectName}?alt=media&token=${token}`;
  await ref.update({ photoUrl, updatedAt: FieldValue.serverTimestamp() });
  const updated = await ref.get();
  await bumpGlobalDataVersion("emaPlayers");
  return toPlayer(params.emaId, updated.data() ?? {});
}

export async function playerExists(emaId: string): Promise<boolean> {
  return (await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()).exists;
}
