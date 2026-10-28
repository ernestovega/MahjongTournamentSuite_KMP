import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest, conflict, notFound } from "../api/httpError";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";
import { sendPasswordResetEmail } from "./firebaseAuthRest";
import { assertEmailAvailable, createUserProfile, grantPlayerCapability, revokePlayerCapability } from "./usersService";

/** The data of an account that the tournament screens need. The email lives only in the account. */
export type AccountInfo = {
  uid: string;
  email: string | null;
  /** False until the person chooses a password, for example with the password reset email. */
  hasPassword: boolean;
};

const AUTH_BATCH_SIZE = 100;

/** An account made without a password has no sign-in provider. The password adds the "password" provider. */
export function accountInfoOf(user: { uid: string; email?: string; providerData: Array<{ providerId: string }> }): AccountInfo {
  return {
    uid: user.uid,
    email: user.email?.toLowerCase() ?? null,
    hasPassword: user.providerData.some((provider) => provider.providerId === "password"),
  };
}

/** Reads accounts by uid. An account that does not exist is missing from the result. */
export async function loadAccounts(uids: string[]): Promise<Map<string, AccountInfo>> {
  const unique = [...new Set(uids.filter((uid) => uid !== ""))];
  const accounts = new Map<string, AccountInfo>();
  for (let start = 0; start < unique.length; start += AUTH_BATCH_SIZE) {
    const result = await auth.getUsers(unique.slice(start, start + AUTH_BATCH_SIZE).map((uid) => ({ uid })));
    result.users.forEach((user) => accounts.set(user.uid, accountInfoOf(user)));
  }
  return accounts;
}

/** Finds the accounts that own an email. The key is the email in lower case. */
export async function loadAccountsByEmail(emails: string[]): Promise<Map<string, AccountInfo & { emaId: string | null }>> {
  const unique = [...new Set(emails.map((email) => email.trim().toLowerCase()).filter((email) => email !== ""))];
  const found = new Map<string, AccountInfo>();
  for (let start = 0; start < unique.length; start += AUTH_BATCH_SIZE) {
    const result = await auth.getUsers(unique.slice(start, start + AUTH_BATCH_SIZE).map((email) => ({ email })));
    result.users.forEach((user) => {
      const info = accountInfoOf(user);
      if (info.email != null) found.set(info.email, info);
    });
  }
  const owners = new Map<string, AccountInfo & { emaId: string | null }>();
  const profiles = found.size === 0 ? [] : await db.getAll(...[...found.values()].map((info) => db.doc(`users/${info.uid}`)));
  const emaIdOfUid = new Map(profiles.map((profile) => {
    const emaId = profile.get("emaId");
    return [profile.id, typeof emaId === "string" && emaId.length > 0 ? emaId : null] as const;
  }));
  found.forEach((info, email) => owners.set(email, { ...info, emaId: emaIdOfUid.get(info.uid) ?? null }));
  return owners;
}

/** Finds the account that owns each EMA id. An EMA id without an account is missing from the result. */
export async function loadAccountsByEmaId(emaIds: string[]): Promise<Map<string, AccountInfo>> {
  const unique = [...new Set(emaIds.filter((emaId) => emaId !== ""))];
  const uidOfEma = new Map<string, string>();
  for (let start = 0; start < unique.length; start += 300) {
    const snapshots = await db.getAll(...unique.slice(start, start + 300).map((emaId) => db.doc(`emaIdIndex/${emaId}`)));
    snapshots.forEach((snapshot) => {
      if (snapshot.exists) uidOfEma.set(snapshot.id, String(snapshot.get("uid")));
    });
  }
  const accounts = await loadAccounts([...uidOfEma.values()]);
  const result = new Map<string, AccountInfo>();
  uidOfEma.forEach((uid, emaId) => {
    const account = accounts.get(uid);
    if (account != null) result.set(emaId, account);
  });
  return result;
}

/** Reads the EMA id that an account owns, or null when it owns none. */
async function emaIdOfAccount(uid: string): Promise<string | null> {
  const emaId = (await db.doc(`users/${uid}`).get()).get("emaId");
  return typeof emaId === "string" && emaId.length > 0 ? emaId : null;
}

/**
 * Returns the slot of the player account in a tournament, or null when the account is not registered there.
 * The slot is the one that holds the EMA id of the account, so no stored link can go wrong.
 */
export async function getPlayerRegistration(
  uid: string,
  tournamentId: string,
): Promise<{ playerId: number; emaId: string } | null> {
  const emaId = await emaIdOfAccount(uid);
  if (emaId == null) return null;
  const assignment = await db.doc(`tournaments/${tournamentId}/emaPlayerAssignments/${emaId}`).get();
  if (!assignment.exists) return null;
  return { playerId: Number(assignment.get("playerId")), emaId };
}

/** The ids of the tournaments that hold an EMA id in a slot. */
async function tournamentIdsOfEmaId(emaId: string): Promise<string[]> {
  const slots = await db.collectionGroup("players").where("assignedEmaId", "==", emaId).get();
  return slots.docs.flatMap((slot) => (slot.ref.parent.parent == null ? [] : [slot.ref.parent.parent.id]));
}

export async function listPlayerTournamentIds(uid: string): Promise<string[]> {
  const emaId = await emaIdOfAccount(uid);
  return emaId == null ? [] : tournamentIdsOfEmaId(emaId);
}

/** Tells the clients that the accounts of the slots with this EMA id changed. */
async function bumpTournamentsOfEmaId(emaId: string): Promise<void> {
  for (const tournamentId of await tournamentIdsOfEmaId(emaId)) await bumpTournamentDataVersion(tournamentId, "players");
}

/**
 * Makes the account the owner of an EMA id. One account owns one EMA id, and one EMA id has one account.
 * It does not touch any tournament.
 */
async function claimEmaId(uid: string, emaId: string): Promise<void> {
  await db.runTransaction(async (transaction) => {
    const indexRef = db.doc(`emaIdIndex/${emaId}`);
    const [index, profile] = await Promise.all([transaction.get(indexRef), transaction.get(db.doc(`users/${uid}`))]);
    if (index.exists && index.get("uid") !== uid) throw conflict("This EMA id belongs to another account");
    const current = profile.get("emaId");
    if (typeof current === "string" && current.length > 0 && current !== emaId) {
      throw conflict("This account belongs to another EMA player");
    }
    transaction.set(indexRef, { uid, updatedAt: FieldValue.serverTimestamp() });
    transaction.set(db.doc(`users/${uid}`), { emaId, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  });
}

/** Releases the EMA id of the account. The slots that hold the id stay, and they have no account any more. */
async function releaseEmaId(uid: string): Promise<void> {
  const emaId = await emaIdOfAccount(uid);
  if (emaId == null) return;
  await db.doc(`emaIdIndex/${emaId}`).delete();
  await db.doc(`users/${uid}`).update({ emaId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
  await bumpTournamentsOfEmaId(emaId);
}

/**
 * Checks that the EMA player exists and that no other account owns the EMA id.
 * [ownUid] is the account that wants the EMA id. A new account has none.
 */
export async function assertEmaIdAvailable(emaId: string, ownUid?: string): Promise<void> {
  const registered = await db.doc(`emaPlayerRegistry/${emaId}`).get();
  if (!registered.exists) throw notFound("EMA player not found");
  const index = await db.doc(`emaIdIndex/${emaId}`).get();
  if (index.exists && index.get("uid") !== ownUid) throw conflict("This EMA id belongs to another account");
}

/**
 * Sets the EMA id of an existing account, or clears it with null. An EMA id gives the player capability, and clearing
 * the EMA id takes it away.
 * Every slot that holds this EMA id shows the account at once, because the link is the EMA id. No email is sent.
 */
export async function setAccountEmaId(params: { uid: string; emaId: string | null }): Promise<void> {
  const profile = await db.doc(`users/${params.uid}`).get();
  const current = typeof profile.get("emaId") === "string" ? String(profile.get("emaId")) : null;
  if (current === params.emaId) return;
  if (params.emaId != null) await assertEmaIdAvailable(params.emaId, params.uid);
  if (current != null) await releaseEmaId(params.uid);
  // The player capability exists only with an EMA id.
  if (params.emaId == null) {
    await revokePlayerCapability(params.uid);
    return;
  }
  await claimEmaId(params.uid, params.emaId);
  await grantPlayerCapability(params.uid);
  await bumpTournamentsOfEmaId(params.emaId);
}

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * Creates the account of an EMA player from an email. The account has no password and no alias, and this function
 * sends no email. The person chooses a password later, with the password reset email that "Invite" sends.
 * The account links to every tournament that holds the EMA id. The email never links an existing account to an EMA id:
 * an email that an account owns already is refused, because several people can share one email.
 */
export async function createPlayerAccount(params: { email: string; emaId: string }): Promise<string> {
  const email = params.email.trim().toLowerCase();
  if (!EMAIL_PATTERN.test(email)) throw badRequest("Invalid email");
  await assertEmaIdAvailable(params.emaId);
  await assertEmailAvailable(email);
  const user = await auth.createUser({ email, disabled: false });
  try {
    await createUserProfile(user.uid, email);
    await setAccountEmaId({ uid: user.uid, emaId: params.emaId });
  } catch (error) {
    await setAccountEmaId({ uid: user.uid, emaId: null }).catch(() => undefined);
    await Promise.allSettled([auth.deleteUser(user.uid), db.doc(`users/${user.uid}`).delete()]);
    throw error;
  }
  await bumpGlobalDataVersion("users");
  return user.uid;
}

/** Why an action failed for a player. The client turns the reason into a text for the editor. */
export type AccountFailure = "PLAYER_NOT_FOUND" | "NO_EMA_ID" | "NO_EMAIL" | "EMAIL_IN_USE" | "EMAIL_FAILED" | "UNEXPECTED";
/** INVITED: the server made the account and sent the password reset email. RESET_SENT: the account existed already. */
export type AccountOutcome = "INVITED" | "RESET_SENT" | "FAILED";
export type AccountActionItem = { playerId: number };
export type AccountActionResult = { playerId: number; outcome: AccountOutcome; reason?: AccountFailure };

const MAX_BULK_PLAYERS = 200;
const BULK_CONCURRENCY = 5;

const failed = (playerId: number, reason: AccountFailure): AccountActionResult => ({ playerId, outcome: "FAILED", reason });

/** Sends the password reset email. A failed email gives the reason, and the account stays for a new try. */
async function sendReset(playerId: number, email: string, outcome: AccountOutcome): Promise<AccountActionResult> {
  try {
    await sendPasswordResetEmail(email);
    return { playerId, outcome };
  } catch (error) {
    console.error("Password reset email failed", { playerId, error });
    return failed(playerId, "EMAIL_FAILED");
  }
}

/**
 * Handles one player by the state of the slot. A slot with no account gets one, made from the pending email of the
 * import, and the password reset email. A slot with an account gets the password reset email.
 */
async function runOne(tournamentId: string, playerId: number): Promise<AccountActionResult> {
  const slot = await db.doc(`tournaments/${tournamentId}/players/${playerId}`).get();
  if (!slot.exists) return failed(playerId, "PLAYER_NOT_FOUND");
  const emaId = slot.get("assignedEmaId");
  if (typeof emaId !== "string" || emaId.length === 0) return failed(playerId, "NO_EMA_ID");
  const account = (await loadAccountsByEmaId([emaId])).get(emaId);
  if (account != null) {
    return account.email == null ? failed(playerId, "NO_EMAIL") : sendReset(playerId, account.email, "RESET_SENT");
  }
  const pending = slot.get("email");
  if (typeof pending !== "string" || pending === "") return failed(playerId, "NO_EMAIL");
  try {
    await createPlayerAccount({ email: pending, emaId });
  } catch (error) {
    if ((error as { status?: number }).status === 409) return failed(playerId, "EMAIL_IN_USE");
    throw error;
  }
  return sendReset(playerId, pending.trim().toLowerCase(), "INVITED");
}

/**
 * Sends the action that fits each player: invite when the slot has no account, password reset when it has one.
 * Every player gets its own result. Editors start this by hand. Importing or assigning a player never sends an email.
 */
export async function runAccountAction(params: {
  tournamentId: string;
  items: AccountActionItem[];
}): Promise<AccountActionResult[]> {
  if (params.items.length === 0) throw badRequest("Choose at least one player");
  if (params.items.length > MAX_BULK_PLAYERS) throw badRequest(`At most ${MAX_BULK_PLAYERS} players per request`);
  const results: AccountActionResult[] = new Array(params.items.length);
  let next = 0;
  const worker = async () => {
    while (next < params.items.length) {
      const index = next++;
      const item = params.items[index];
      try {
        results[index] = await runOne(params.tournamentId, item.playerId);
      } catch (error) {
        console.error("Account action failed", { playerId: item.playerId, error });
        results[index] = failed(item.playerId, "UNEXPECTED");
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(BULK_CONCURRENCY, params.items.length) }, worker));
  return results;
}

/** Why a pending email was not saved. */
export type EmailFailure = "PLAYER_NOT_FOUND" | "NO_EMA_ID" | "HAS_ACCOUNT" | "INVALID_EMAIL" | "EMAIL_IN_USE";
export type EmailItem = { playerId: number; email: string };
export type EmailResult = { playerId: number; outcome: "SAVED" | "CLEARED" | "FAILED"; reason?: EmailFailure };

/**
 * Sets the pending email of slots, for the "Invite" action. An empty email clears it.
 * A slot with an account keeps the email of the account, so it refuses a change. The email must be valid and unique:
 * no account of another EMA id and no other slot of the tournament may hold it. It changes nothing for a refused slot.
 */
export async function savePendingEmails(params: { tournamentId: string; items: EmailItem[] }): Promise<EmailResult[]> {
  if (params.items.length === 0) throw badRequest("Choose at least one player");
  if (params.items.length > MAX_BULK_PLAYERS) throw badRequest(`At most ${MAX_BULK_PLAYERS} players per request`);
  const slots = await db.collection(`tournaments/${params.tournamentId}/players`).get();
  const slotsById = new Map(slots.docs.map((slot) => [Number(slot.get("id") ?? slot.id), slot]));
  // The email of every slot now. Saving an item updates this map, so two items of one request cannot clash.
  const pendingOf = new Map<number, string>();
  slots.docs.forEach((slot) => {
    const email = slot.get("email");
    if (typeof email === "string" && email !== "") pendingOf.set(Number(slot.get("id") ?? slot.id), email.toLowerCase());
  });
  const emaIds = slots.docs.flatMap((slot) => (typeof slot.get("assignedEmaId") === "string" ? [String(slot.get("assignedEmaId"))] : []));
  const accountsByEma = await loadAccountsByEmaId(emaIds);
  const wanted = params.items.map((item) => item.email.trim().toLowerCase()).filter((email) => EMAIL_PATTERN.test(email));
  const owners = await loadAccountsByEmail(wanted);

  const results: EmailResult[] = [];
  const writes: Array<() => Promise<unknown>> = [];
  for (const item of params.items) {
    const fail = (reason: EmailFailure): void => { results.push({ playerId: item.playerId, outcome: "FAILED", reason }); };
    const slot = slotsById.get(item.playerId);
    if (slot == null) { fail("PLAYER_NOT_FOUND"); continue; }
    const emaId = slot.get("assignedEmaId");
    if (typeof emaId !== "string" || emaId === "") { fail("NO_EMA_ID"); continue; }
    if (accountsByEma.has(emaId)) { fail("HAS_ACCOUNT"); continue; }
    const email = item.email.trim().toLowerCase();
    if (email === "") {
      pendingOf.delete(item.playerId);
      writes.push(() => slot.ref.update({ email: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() }));
      results.push({ playerId: item.playerId, outcome: "CLEARED" });
      continue;
    }
    if (!EMAIL_PATTERN.test(email)) { fail("INVALID_EMAIL"); continue; }
    const owner = owners.get(email);
    const usedBySlot = [...pendingOf.entries()].some(([playerId, other]) => playerId !== item.playerId && other === email);
    if (owner != null || usedBySlot) { fail("EMAIL_IN_USE"); continue; }
    pendingOf.set(item.playerId, email);
    writes.push(() => slot.ref.update({ email, updatedAt: FieldValue.serverTimestamp() }));
    results.push({ playerId: item.playerId, outcome: "SAVED" });
  }
  await Promise.all(writes.map((write) => write()));
  if (writes.length > 0) await bumpTournamentDataVersion(params.tournamentId, "players");
  return results;
}
