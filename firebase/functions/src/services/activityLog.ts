import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import type { ChangeActor } from "./changeLog";

/**
 * The activity log keeps one record for each change that is not a table save: tournament settings, players, teams,
 * editors, accounts and the player registry. It is append-only. Table saves have their own log in "changes".
 * Records outlive a deleted tournament. They hold no e-mail addresses and no image data.
 */
export const ACTIVITY_COLLECTION = "activity";

export type ActivityAction =
  | "tournament.create" | "tournament.update" | "tournament.rename" | "tournament.delete"
  | "player.assign" | "player.substitute" | "player.clearSubstitution"
  | "team.update" | "member.add" | "member.remove"
  | "account.send" | "account.resend" | "account.revoke" | "account.saveEmails"
  | "user.create" | "user.update" | "user.disable" | "user.enable" | "user.photo" | "user.photo.remove"
  | "registry.create" | "registry.update" | "registry.photo";

export type ActivityEntry = {
  action: ActivityAction;
  uid: string | null;
  name: string | null;
  /** The tournament that changed, or null for a change outside a tournament. */
  tournamentId: string | null;
  /** What the change touched, for example "player 5" or "user abc". */
  target: string | null;
  details: Record<string, unknown>;
};

const MAX_TEXT = 200;
const SECRET_KEY = /base64|email|password|token/i;

/** Keeps the short values of a request. Long text, image data and e-mail addresses stay out of the log. */
export function sanitizeDetails(details: Record<string, unknown> | undefined | null): Record<string, unknown> {
  return sanitizeObject(details, 0);
}

function sanitizeObject(details: Record<string, unknown> | undefined | null, depth: number): Record<string, unknown> {
  const clean: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(details ?? {})) {
    if (SECRET_KEY.test(key) || value === undefined) continue;
    clean[key] = sanitizeValue(value, depth);
  }
  return clean;
}

function sanitizeValue(value: unknown, depth: number): unknown {
  if (value == null || typeof value === "number" || typeof value === "boolean") return value ?? null;
  if (typeof value === "string") return value.length > MAX_TEXT ? `${value.slice(0, MAX_TEXT)}…` : value;
  if (depth >= 3) return null;
  if (Array.isArray(value)) return value.slice(0, 50).map((item) => sanitizeValue(item, depth + 1));
  if (typeof value === "object") return sanitizeObject(value as Record<string, unknown>, depth + 1);
  return null;
}

export function buildActivityEntry(params: {
  action: ActivityAction;
  actor: ChangeActor | null;
  tournamentId?: string | null;
  target?: string | null;
  details?: Record<string, unknown>;
}): ActivityEntry {
  return {
    action: params.action,
    uid: params.actor?.uid ?? null,
    name: params.actor?.name ?? null,
    tournamentId: params.tournamentId ?? null,
    target: params.target ?? null,
    details: sanitizeDetails(params.details),
  };
}

/** Writes one record. A failed write is reported and never fails the change that was already made. */
export async function recordActivity(params: Parameters<typeof buildActivityEntry>[0]): Promise<void> {
  try {
    await db.collection(ACTIVITY_COLLECTION).add({ ...buildActivityEntry(params), at: FieldValue.serverTimestamp() });
  } catch (error) {
    console.error("Writing the activity log failed", params.action, error);
  }
}

/** The name of a tournament, read before it is deleted. */
export async function tournamentNameOf(tournamentId: string): Promise<string | null> {
  try {
    const snapshot = await db.collection("tournaments").doc(tournamentId).get();
    return snapshot.exists ? String(snapshot.get("name") ?? "") : null;
  } catch {
    return null;
  }
}
