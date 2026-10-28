import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest, conflict, notFound } from "../api/httpError";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";
import { sendPasswordResetEmail } from "./firebaseAuthRest";
import { sendEmail } from "./mailService";
import { buildRegistrationEmail, DEFAULT_LANGUAGE, type Language } from "./i18n";
import { EMA_PLAYER_REGISTRY_COLLECTION, toPlayer } from "./playersService";
import { assertEmailAvailable, createUserProfile, grantPlayerCapability, revokePlayerCapability } from "./usersService";

/** Where a player opens the tournament after the registration email. Set PLAYER_APP_URL to the player web site. */
function playerAppUrl(): string {
  return (process.env.PLAYER_APP_URL ?? "https://mtsplayer.mahjongmadrid.com").replace(/\/$/, "");
}

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

/** The full EMA name of a player. A new account uses it as its first alias. Returns "" when the player is unknown. */
async function emaFullName(emaId: string): Promise<string> {
  const snap = await db.doc(`${EMA_PLAYER_REGISTRY_COLLECTION}/${emaId}`).get();
  if (!snap.exists) return "";
  const player = toPlayer(emaId, snap.data() ?? {});
  return `${player.firstName} ${player.lastName}`.trim();
}

/**
 * Creates the account of an EMA player from an email. The account has no password and the server sends no email.
 * The person chooses a password later, with the password reset email that "Invite" sends.
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
    await createUserProfile(user.uid, email, await emaFullName(params.emaId));
    await setAccountEmaId({ uid: user.uid, emaId: params.emaId });
  } catch (error) {
    await setAccountEmaId({ uid: user.uid, emaId: null }).catch(() => undefined);
    await Promise.allSettled([auth.deleteUser(user.uid), db.doc(`users/${user.uid}`).delete()]);
    throw error;
  }
  await bumpGlobalDataVersion("users");
  return user.uid;
}

export type AccountAction = "send" | "resend" | "revoke";
export type AccountOutcome = "INVITED" | "NOTIFIED" | "REVOKED" | "SKIPPED" | "FAILED";
export type AccountActionItem = { playerId: number; email?: string };
export type AccountActionResult = { playerId: number; outcome: AccountOutcome; message?: string };

const MAX_BULK_PLAYERS = 200;
const BULK_CONCURRENCY = 5;

async function markNotified(tournamentId: string, playerId: number): Promise<void> {
  await db.doc(`tournaments/${tournamentId}/players/${playerId}`)
    .update({ notifiedAt: FieldValue.serverTimestamp() });
  await bumpTournamentDataVersion(tournamentId, "players");
}

/** Tells a player who has a password that the registration exists. */
async function notifyRegistration(params: { tournamentId: string; playerId: number; email: string; language: Language }): Promise<void> {
  const tournament = await db.doc(`tournaments/${params.tournamentId}`).get();
  const name = String(tournament.get("name") ?? "");
  const link = `${playerAppUrl()}/?tournament=${encodeURIComponent(params.tournamentId)}`;
  await sendEmail({ to: params.email, ...buildRegistrationEmail(params.language, { tournamentName: name, link }) });
  await markNotified(params.tournamentId, params.playerId);
}

/** The reasons why an email cannot be saved for a player. The text goes to the editor. */
function emailRefusal(error: unknown): string | null {
  if (error && typeof error === "object" && "status" in error && "message" in error) {
    return String((error as { message: unknown }).message);
  }
  return null;
}

async function runOne(
  tournamentId: string,
  action: AccountAction,
  item: AccountActionItem,
  language: Language,
): Promise<AccountActionResult> {
  const { playerId } = item;
  const slotRef = db.doc(`tournaments/${tournamentId}/players/${playerId}`);
  const slot = await slotRef.get();
  if (!slot.exists) return { playerId, outcome: "FAILED", message: "Player not found" };
  const emaId = slot.get("assignedEmaId");
  const hasEma = typeof emaId === "string" && emaId.length > 0;
  const email = (item.email ?? "").trim().toLowerCase();
  if (email && !EMAIL_PATTERN.test(email)) return { playerId, outcome: "FAILED", message: "Invalid email" };
  const ownedAccount = hasEma ? (await loadAccountsByEmaId([emaId])).get(emaId) : undefined;

  if (action === "revoke") {
    // The account belongs to the EMA id, so the revoke only forgets that the player got an email.
    if (slot.get("notifiedAt") == null) return { playerId, outcome: "SKIPPED", message: "Nothing to revoke" };
    await slotRef.update({ notifiedAt: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
    await bumpTournamentDataVersion(tournamentId, "players");
    return { playerId, outcome: "REVOKED" };
  }

  if (!hasEma) return { playerId, outcome: "FAILED", message: "The player has no EMA id" };
  try {
    let accountUid = ownedAccount?.uid ?? null;
    if (accountUid == null) {
      if (!email) return { playerId, outcome: "FAILED", message: "No email for this player" };
      accountUid = await createPlayerAccount({ email, emaId: String(emaId) });
    }
    const account = ownedAccount ?? (await loadAccounts([accountUid])).get(accountUid);
    if (account?.email == null) return { playerId, outcome: "FAILED", message: "The account of this player has no email" };
    // The email of the account always wins. A different email does not change the account.
    if (email && email !== account.email) {
      return { playerId, outcome: "FAILED", message: `The account of this player has the email ${account.email}. The account email stays.` };
    }
    if (action === "send" && slot.get("notifiedAt") != null) return { playerId, outcome: "SKIPPED", message: "Already notified" };
    if (!account.hasPassword) {
      // The account has no password yet. The password reset email lets the person choose one.
      await sendPasswordResetEmail(account.email);
      await markNotified(tournamentId, playerId);
      return { playerId, outcome: "INVITED" };
    }
    await notifyRegistration({ tournamentId, playerId, email: account.email, language });
    return { playerId, outcome: "NOTIFIED" };
  } catch (error) {
    if ((error as { code?: string }).code === "email_failed") {
      return { playerId, outcome: "FAILED", message: "The email could not be sent. Use Resend." };
    }
    const message = emailRefusal(error);
    if (message != null) return { playerId, outcome: "FAILED", message };
    throw error;
  }
}

/**
 * Runs one action for one player or for many. Every player gets its own result.
 * Editors start these actions by hand. Assigning a player never sends an email.
 */
export async function runAccountAction(params: {
  tournamentId: string;
  action: AccountAction;
  items: AccountActionItem[];
  /** The language of the emails. The default is English. */
  language?: Language;
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
        results[index] = await runOne(params.tournamentId, params.action, item, params.language ?? DEFAULT_LANGUAGE);
      } catch (error) {
        results[index] = {
          playerId: item.playerId,
          outcome: "FAILED",
          message: error instanceof Error ? error.message : "Unexpected error",
        };
      }
    }
  };
  await Promise.all(Array.from({ length: Math.min(BULK_CONCURRENCY, params.items.length) }, worker));
  return results;
}

export type EmailOutcome = "CREATED" | "UNCHANGED" | "KEPT_ACCOUNT_EMAIL" | "FAILED";
export type EmailResult = { playerId: number; outcome: EmailOutcome; message?: string };

/**
 * Saves the email of players in their accounts. A player with no account gets a new account without a password.
 * An account that has an email keeps it: a different email is refused. No email is sent.
 */
export async function saveAccountEmails(params: {
  tournamentId: string;
  items: Array<{ playerId: number; email: string }>;
}): Promise<EmailResult[]> {
  const results: EmailResult[] = [];
  for (const item of params.items) {
    const email = item.email.trim().toLowerCase();
    try {
      if (!EMAIL_PATTERN.test(email)) throw badRequest(`Invalid email for player ${item.playerId}`);
      const slot = await db.doc(`tournaments/${params.tournamentId}/players/${item.playerId}`).get();
      if (!slot.exists) throw notFound("Player not found");
      const emaId = slot.get("assignedEmaId");
      if (typeof emaId !== "string" || emaId.length === 0) throw badRequest("The player has no EMA id");
      const account = (await loadAccountsByEmaId([emaId])).get(emaId);
      if (account == null) {
        await createPlayerAccount({ email, emaId });
        results.push({ playerId: item.playerId, outcome: "CREATED" });
        continue;
      }
      if (account?.email === email) results.push({ playerId: item.playerId, outcome: "UNCHANGED" });
      else results.push({ playerId: item.playerId, outcome: "KEPT_ACCOUNT_EMAIL", message: account?.email ?? "" });
    } catch (error) {
      const message = emailRefusal(error);
      if (message == null) throw error;
      results.push({ playerId: item.playerId, outcome: "FAILED", message });
    }
  }
  return results;
}
