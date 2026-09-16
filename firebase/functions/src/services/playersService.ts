import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { db } from "../firebase";
import { badRequest, notFound } from "../api/httpError";

export type Player = {
  emaId: string;
  name: string;
  country: string;
  createdAt: string | null;
  updatedAt: string | null;
};

function timestampToIso(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

function toPlayer(emaId: string, data: FirebaseFirestore.DocumentData): Player {
  return {
    emaId,
    name: String(data.name ?? ""),
    country: String(data.country ?? ""),
    createdAt: timestampToIso(data.createdAt),
    updatedAt: timestampToIso(data.updatedAt),
  };
}

export function validateEmaId(value: unknown): string {
  const emaId = String(value ?? "").trim();
  if (!/^\d+$/.test(emaId)) {
    throw badRequest("EMA number must contain only digits");
  }
  return emaId;
}

export async function listPlayers(): Promise<Player[]> {
  const snapshot = await db.collection("players").get();
  return snapshot.docs
    .map((document) => toPlayer(document.id, document.data()))
    .sort((left, right) => left.name.localeCompare(right.name) || left.emaId.localeCompare(right.emaId));
}

export async function createPlayer(params: { emaId: string; name: string; country: string }): Promise<Player> {
  const ref = db.collection("players").doc(params.emaId);
  const existing = await ref.get();
  if (existing.exists) {
    throw badRequest("A player with this EMA number already exists");
  }
  await ref.create({
    name: params.name,
    country: params.country,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });
  const created = await ref.get();
  return toPlayer(params.emaId, created.data() ?? {});
}

export async function updatePlayer(params: { emaId: string; name: string; country: string }): Promise<void> {
  const ref = db.collection("players").doc(params.emaId);
  if (!(await ref.get()).exists) {
    throw notFound("Player not found");
  }
  await ref.update({ name: params.name, country: params.country, updatedAt: FieldValue.serverTimestamp() });
}

export async function playerExists(emaId: string): Promise<boolean> {
  return (await db.collection("players").doc(emaId).get()).exists;
}
