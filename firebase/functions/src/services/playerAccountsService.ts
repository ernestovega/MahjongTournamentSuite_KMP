import { createHash, randomBytes } from "node:crypto";

import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest, conflict, forbidden, notFound } from "../api/httpError";
import { bumpTournamentDataVersion } from "./dataVersionsService";
import { sendEmail } from "./mailService";
import { grantPlayerCapability } from "./usersService";

export type InvitationStatus = "NONE" | "INVITED" | "ACCEPTED";

export const INVITATION_TTL_DAYS = 14;

/** Where a player opens the invitation link. Set PLAYER_APP_URL to the player web site. */
function playerAppUrl(): string {
  return (process.env.PLAYER_APP_URL ?? "https://mtsplayer.mahjongmadrid.com").replace(/\/$/, "");
}

export function hashToken(secret: string): string {
  return createHash("sha256").update(secret).digest("hex");
}

/** A token holds the document path parts, so accepting needs no query. The secret part is stored only as a hash. */
export function buildToken(tournamentId: string, invitationId: string, secret: string): string {
  return `${tournamentId}.${invitationId}.${secret}`;
}

export function parseToken(token: string): { tournamentId: string; invitationId: string; secret: string } | null {
  const parts = token.split(".");
  if (parts.length !== 3 || parts.some((part) => part.length === 0 || part.includes("/") || part.length > 200)) return null;
  return { tournamentId: parts[0], invitationId: parts[1], secret: parts[2] };
}

function invitationsOf(tournamentId: string) {
  return db.collection(`tournaments/${tournamentId}/playerInvitations`);
}

function playerTournamentRef(uid: string, tournamentId: string) {
  return db.doc(`users/${uid}/playerTournaments/${tournamentId}`);
}

/** Returns the slot of the player account in a tournament, or null when the account is not registered there. */
export async function getPlayerRegistration(
  uid: string,
  tournamentId: string,
): Promise<{ playerId: number; emaId: string } | null> {
  const snap = await playerTournamentRef(uid, tournamentId).get();
  if (!snap.exists) return null;
  return { playerId: Number(snap.get("playerId")), emaId: String(snap.get("emaId") ?? "") };
}

export async function listPlayerTournamentIds(uid: string): Promise<string[]> {
  const snapshot = await db.collection(`users/${uid}/playerTournaments`).get();
  return snapshot.docs.map((doc) => doc.id);
}

async function createInvitationDoc(params: {
  tournamentId: string;
  playerId: number;
  email: string;
  invitedBy: string;
  replaceInvitationId?: string;
}): Promise<{ invitationId: string; token: string; tournamentName: string }> {
  const tournamentRef = db.doc(`tournaments/${params.tournamentId}`);
  const playerRef = db.doc(`tournaments/${params.tournamentId}/players/${params.playerId}`);
  const secret = randomBytes(24).toString("base64url");
  const invitationRef = params.replaceInvitationId
    ? invitationsOf(params.tournamentId).doc(params.replaceInvitationId)
    : invitationsOf(params.tournamentId).doc();
  let tournamentName = "";

  await db.runTransaction(async (transaction) => {
    const [tournament, player, pending] = await Promise.all([
      transaction.get(tournamentRef),
      transaction.get(playerRef),
      transaction.get(invitationsOf(params.tournamentId)
        .where("playerId", "==", params.playerId).where("status", "==", "PENDING")),
    ]);
    if (!tournament.exists) throw notFound("Tournament not found");
    if (!player.exists) throw notFound("Player not found");
    const emaId = player.get("assignedEmaId");
    if (typeof emaId !== "string" || emaId.length === 0) {
      throw badRequest("Only a player with an EMA id can be invited");
    }
    if (player.get("accountUid")) throw conflict("This player already has an account");
    tournamentName = String(tournament.get("name") ?? "");

    const other = await auth.getUserByEmail(params.email).catch(() => null);
    pending.docs.forEach((doc) => {
      if (doc.id !== invitationRef.id) transaction.update(doc.ref, { status: "REVOKED", updatedAt: FieldValue.serverTimestamp() });
    });
    transaction.set(invitationRef, {
      playerId: params.playerId,
      emaId,
      email: params.email,
      existingUid: other?.uid ?? null,
      tokenHash: hashToken(secret),
      status: "PENDING",
      invitedBy: params.invitedBy,
      expiresAt: Timestamp.fromMillis(Date.now() + INVITATION_TTL_DAYS * 24 * 60 * 60 * 1000),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    transaction.update(playerRef, {
      invitationStatus: "INVITED",
      invitedEmail: params.email,
      invitedAt: FieldValue.serverTimestamp(),
    });
  });
  await bumpTournamentDataVersion(params.tournamentId, "players");
  return { invitationId: invitationRef.id, token: buildToken(params.tournamentId, invitationRef.id, secret), tournamentName };
}

const APP_DESCRIPTION = "This is the player app of the tournament. "
  + "After you sign in, you can see where you play in each round, the ranking after every round, "
  + "and your own results. You can also see upcoming and past tournaments, your statistics, and more.";

async function sendInvitationEmail(email: string, tournamentName: string, token: string): Promise<void> {
  const link = `${playerAppUrl()}/invite?token=${encodeURIComponent(token)}`;
  const subject = `Invitation to ${tournamentName}`;
  const text = `You are invited to play in ${tournamentName}.\n\n${APP_DESCRIPTION}\n\n`
    + `Open this link to accept: ${link}\nThe link works once and expires in ${INVITATION_TTL_DAYS} days.`;
  const html = `<p>You are invited to play in <strong>${escapeHtml(tournamentName)}</strong>.</p>`
    + `<p>${APP_DESCRIPTION}</p>`
    + `<p><a href="${link}">Accept the invitation</a></p>`
    + `<p>The link works once and expires in ${INVITATION_TTL_DAYS} days.</p>`;
  await sendEmail({ to: email, subject, text, html });
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/** Links a slot to the account that already owns the EMA id of the slot. Sends no email. */
export async function linkKnownAccountToSlot(tournamentId: string, playerId: number): Promise<string | null> {
  const slotRef = db.doc(`tournaments/${tournamentId}/players/${playerId}`);
  const slotSnap = await slotRef.get();
  const emaId = slotSnap.get("assignedEmaId");
  if (!slotSnap.exists || typeof emaId !== "string" || emaId.length === 0) return null;
  if (typeof slotSnap.get("accountUid") === "string") return String(slotSnap.get("accountUid"));
  const indexSnap = await db.doc(`emaIdIndex/${emaId}`).get();
  const uid = indexSnap.exists ? String(indexSnap.get("uid")) : null;
  if (!uid) return null;
  const account = await auth.getUser(uid).catch(() => null);
  if (!account || account.disabled) return null;

  const pending = await invitationsOf(tournamentId)
    .where("playerId", "==", playerId).where("status", "==", "PENDING").get();
  const linked = await db.runTransaction(async (transaction) => {
    const slot = await transaction.get(slotRef);
    if (slot.get("assignedEmaId") !== emaId || typeof slot.get("accountUid") === "string") return false;
    pending.docs.forEach((doc) => transaction.update(doc.ref, { status: "REVOKED", updatedAt: FieldValue.serverTimestamp() }));
    transaction.update(slotRef, {
      accountUid: uid,
      invitationStatus: "ACCEPTED",
      invitedEmail: FieldValue.delete(),
      invitedAt: FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    transaction.set(playerTournamentRef(uid, tournamentId), {
      playerId, emaId, tournamentId, createdAt: FieldValue.serverTimestamp(),
    });
    return true;
  });
  if (linked) await bumpTournamentDataVersion(tournamentId, "players");
  return linked ? uid : null;
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

/** Removes the account link of every slot that points to the account, and releases its EMA id. */
async function releaseEmaId(uid: string): Promise<void> {
  const registrations = await db.collection(`users/${uid}/playerTournaments`).get();
  for (const registration of registrations.docs) {
    const playerId = Number(registration.get("playerId"));
    const slotRef = db.doc(`tournaments/${registration.id}/players/${playerId}`);
    const related = await invitationsOf(registration.id).where("playerId", "==", playerId).get();
    const batch = db.batch();
    related.docs.forEach((doc) => {
      if (doc.get("status") === "PENDING" || doc.get("status") === "ACCEPTED") {
        batch.update(doc.ref, { status: "REVOKED", updatedAt: FieldValue.serverTimestamp() });
      }
    });
    if ((await slotRef.get()).exists) {
      batch.update(slotRef, {
        invitationStatus: "NONE",
        accountUid: FieldValue.delete(),
        invitedEmail: FieldValue.delete(),
        invitedAt: FieldValue.delete(),
        notifiedAt: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    batch.delete(registration.ref);
    await batch.commit();
    await bumpTournamentDataVersion(registration.id, "players");
  }
  const profile = await db.doc(`users/${uid}`).get();
  const emaId = profile.get("emaId");
  if (typeof emaId === "string" && emaId.length > 0) {
    await db.doc(`emaIdIndex/${emaId}`).delete();
    await db.doc(`users/${uid}`).update({ emaId: FieldValue.delete(), updatedAt: FieldValue.serverTimestamp() });
  }
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
 * Sets the EMA id of an existing account, or clears it with null.
 * Every tournament that has this EMA id in a slot links to the account at once. No email is sent.
 */
export async function setAccountEmaId(params: { uid: string; emaId: string | null }): Promise<void> {
  const profile = await db.doc(`users/${params.uid}`).get();
  const current = typeof profile.get("emaId") === "string" ? String(profile.get("emaId")) : null;
  if (current === params.emaId) return;
  if (params.emaId != null) await assertEmaIdAvailable(params.emaId, params.uid);
  if (current != null) await releaseEmaId(params.uid);
  if (params.emaId == null) return;
  await claimEmaId(params.uid, params.emaId);
  await grantPlayerCapability(params.uid);
  await linkAccountToAllSlots(params.emaId);
}

/** Links every tournament slot that holds the EMA id to the account that owns the id. Sends no email. */
async function linkAccountToAllSlots(emaId: string): Promise<void> {
  const tournaments = await db.collection("tournaments").get();
  for (const tournament of tournaments.docs) {
    const assignment = await db.doc(`tournaments/${tournament.id}/emaPlayerAssignments/${emaId}`).get();
    if (assignment.exists) await linkKnownAccountToSlot(tournament.id, Number(assignment.get("playerId")));
  }
}

export type AccountAction = "send" | "resend" | "revoke";
export type AccountOutcome = "INVITED" | "NOTIFIED" | "REVOKED" | "SKIPPED" | "FAILED";
export type AccountActionItem = { playerId: number; email?: string };
export type AccountActionResult = { playerId: number; outcome: AccountOutcome; message?: string };

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_BULK_PLAYERS = 200;
const BULK_CONCURRENCY = 5;

async function notifyRegistration(params: { tournamentId: string; playerId: number; uid: string }): Promise<void> {
  const [tournament, account] = await Promise.all([
    db.doc(`tournaments/${params.tournamentId}`).get(),
    auth.getUser(params.uid),
  ]);
  if (!account.email) throw badRequest("The account of this player has no email");
  const name = String(tournament.get("name") ?? "");
  const link = `${playerAppUrl()}/?tournament=${encodeURIComponent(params.tournamentId)}`;
  await sendEmail({
    to: account.email,
    subject: `You are registered in ${name}`,
    text: `You have been added to the tournament "${name}".\n\n${APP_DESCRIPTION}\n\nOpen the tournament: ${link}`,
    html: `<p>You have been added to the tournament <strong>${escapeHtml(name)}</strong>.</p>`
      + `<p>${APP_DESCRIPTION}</p>`
      + `<p><a href="${link}">Open the tournament</a></p>`,
  });
  await db.doc(`tournaments/${params.tournamentId}/players/${params.playerId}`)
    .update({ notifiedAt: FieldValue.serverTimestamp() });
  await bumpTournamentDataVersion(params.tournamentId, "players");
}

async function sendInvitation(params: {
  tournamentId: string;
  playerId: number;
  email: string;
  invitedBy: string;
  replaceInvitationId?: string;
}): Promise<void> {
  const created = await createInvitationDoc(params);
  await sendInvitationEmail(params.email, created.tournamentName, created.token);
}

async function runOne(
  tournamentId: string,
  action: AccountAction,
  item: AccountActionItem,
  invitedBy: string,
): Promise<AccountActionResult> {
  const { playerId } = item;
  const slotRef = db.doc(`tournaments/${tournamentId}/players/${playerId}`);
  const slot = await slotRef.get();
  if (!slot.exists) return { playerId, outcome: "FAILED", message: "Player not found" };
  const emaId = slot.get("assignedEmaId");
  const hasEma = typeof emaId === "string" && emaId.length > 0;
  const email = (item.email ?? "").trim().toLowerCase();
  if (email && !EMAIL_PATTERN.test(email)) return { playerId, outcome: "FAILED", message: "Invalid email" };

  if (action === "revoke") {
    const accountUid = typeof slot.get("accountUid") === "string" ? String(slot.get("accountUid")) : null;
    const status = String(slot.get("invitationStatus") ?? "NONE");
    if (accountUid == null && status !== "INVITED") return { playerId, outcome: "SKIPPED", message: "Nothing to revoke" };
    const related = await invitationsOf(tournamentId).where("playerId", "==", playerId).get();
    await db.runTransaction(async (transaction) => {
      related.docs.forEach((doc) => {
        if (doc.get("status") === "PENDING" || doc.get("status") === "ACCEPTED") {
          transaction.update(doc.ref, { status: "REVOKED", updatedAt: FieldValue.serverTimestamp() });
        }
      });
      transaction.update(slotRef, {
        invitationStatus: "NONE",
        invitedEmail: FieldValue.delete(),
        invitedAt: FieldValue.delete(),
        accountUid: FieldValue.delete(),
        notifiedAt: FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      if (accountUid != null) transaction.delete(playerTournamentRef(accountUid, tournamentId));
    });
    await bumpTournamentDataVersion(tournamentId, "players");
    return { playerId, outcome: "REVOKED" };
  }

  if (!hasEma) return { playerId, outcome: "FAILED", message: "The player has no EMA id" };
  const accountUid = typeof slot.get("accountUid") === "string"
    ? String(slot.get("accountUid"))
    : action === "send" ? await linkKnownAccountToSlot(tournamentId, playerId) : null;

  try {
    if (accountUid != null) {
      if (action === "send" && (slot.get("notifiedAt") != null || slot.get("invitedAt") != null)) {
        return { playerId, outcome: "SKIPPED", message: "Already notified" };
      }
      await notifyRegistration({ tournamentId, playerId, uid: accountUid });
      return { playerId, outcome: "NOTIFIED" };
    }
    const status = String(slot.get("invitationStatus") ?? "NONE");
    if (action === "send") {
      if (status === "INVITED") return { playerId, outcome: "SKIPPED", message: "Already invited" };
      const target = email || String(slot.get("invitedEmail") ?? "");
      if (!target) return { playerId, outcome: "FAILED", message: "No email for this player" };
      // The email belongs to an account already: no invitation is needed. The account links at once.
      const existing = await auth.getUserByEmail(target).catch(() => null);
      if (existing && !existing.disabled) {
        await claimEmaId(existing.uid, String(emaId));
        await grantPlayerCapability(existing.uid);
        const linkedUid = await linkKnownAccountToSlot(tournamentId, playerId);
        if (linkedUid != null) {
          await notifyRegistration({ tournamentId, playerId, uid: linkedUid });
          return { playerId, outcome: "NOTIFIED" };
        }
      }
      await sendInvitation({ tournamentId, playerId, email: target, invitedBy });
      return { playerId, outcome: "INVITED" };
    }
    // Resend: a new link replaces the old one.
    if (status !== "INVITED") return { playerId, outcome: "SKIPPED", message: "Nothing to resend" };
    const pending = await invitationsOf(tournamentId)
      .where("playerId", "==", playerId).where("status", "==", "PENDING").get();
    const current = pending.docs[0];
    const target = email || String(current?.get("email") ?? slot.get("invitedEmail") ?? "");
    if (!target) return { playerId, outcome: "FAILED", message: "No email for this player" };
    await sendInvitation({ tournamentId, playerId, email: target, invitedBy, replaceInvitationId: current?.id });
    return { playerId, outcome: "INVITED" };
  } catch (error) {
    if ((error as { code?: string }).code === "email_failed") {
      return { playerId, outcome: "FAILED", message: "The email could not be sent. Use Resend." };
    }
    if (error && typeof error === "object" && "status" in error && "message" in error) {
      return { playerId, outcome: "FAILED", message: String((error as { message: unknown }).message) };
    }
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
  invitedBy: string;
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
        results[index] = await runOne(params.tournamentId, params.action, item, params.invitedBy);
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

/** Saves the email of players that have no account yet, so that a later bulk send can use it. */
export async function saveContactEmails(params: {
  tournamentId: string;
  items: Array<{ playerId: number; email: string }>;
}): Promise<void> {
  const batch = db.batch();
  for (const item of params.items) {
    const email = item.email.trim().toLowerCase();
    if (!EMAIL_PATTERN.test(email)) throw badRequest(`Invalid email for player ${item.playerId}`);
    batch.update(db.doc(`tournaments/${params.tournamentId}/players/${item.playerId}`), {
      invitedEmail: email,
      updatedAt: FieldValue.serverTimestamp(),
    });
  }
  await batch.commit();
  await bumpTournamentDataVersion(params.tournamentId, "players");
}

export type AcceptResult = { tournamentId: string; email: string };

/**
 * Accepts an invitation.
 * A new account is created here and gets its password. An existing account must be signed in as the invited email.
 */
export async function acceptInvitation(params: {
  token: string;
  password?: string;
  actorUid?: string;
}): Promise<AcceptResult> {
  const parsed = parseToken(params.token);
  if (!parsed) throw badRequest("Invalid invitation");
  const ref = invitationsOf(parsed.tournamentId).doc(parsed.invitationId);
  const snap = await ref.get();
  if (!snap.exists || snap.get("tokenHash") !== hashToken(parsed.secret)) throw notFound("Invitation not found");
  if (snap.get("status") !== "PENDING") throw conflict("The invitation is no longer valid");
  const expiresAt = snap.get("expiresAt") as Timestamp;
  if (expiresAt.toMillis() < Date.now()) throw conflict("The invitation has expired");

  const email = String(snap.get("email"));
  const emaId = String(snap.get("emaId"));
  const playerId = Number(snap.get("playerId"));
  const existingUid = (snap.get("existingUid") as string | null) ?? null;

  let uid: string;
  if (existingUid) {
    if (params.actorUid !== existingUid) throw forbidden("Sign in with the invited account to accept");
    uid = existingUid;
  } else {
    const password = params.password ?? "";
    if (password.length < 8) throw badRequest("The password needs at least 8 characters");
    try {
      uid = (await auth.createUser({ email, password, emailVerified: true })).uid;
    } catch (error) {
      if ((error as { code?: string }).code === "auth/email-already-exists") {
        throw conflict("An account with this email already exists. Ask for a new invitation.");
      }
      throw error;
    }
    await db.doc(`users/${uid}`).set({
      uid, email, alias: "", contactEmail: email, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  }

  try {
    await linkAccountToSlot({ ref, uid, emaId, playerId, tournamentId: parsed.tournamentId });
  } catch (error) {
    if (!existingUid) await auth.deleteUser(uid).catch(() => undefined);
    throw error;
  }
  await grantPlayerCapability(uid);
  await bumpTournamentDataVersion(parsed.tournamentId, "players");
  // The account owns the EMA id now, so the player sees every other tournament with this id too.
  await linkAccountToAllSlots(emaId);
  return { tournamentId: parsed.tournamentId, email };
}

async function linkAccountToSlot(params: {
  ref: FirebaseFirestore.DocumentReference;
  uid: string;
  emaId: string;
  playerId: number;
  tournamentId: string;
}): Promise<void> {
  const { ref, uid, emaId, playerId } = params;
  const tournamentId = params.tournamentId;
  await db.runTransaction(async (transaction) => {
    const emaIndexRef = db.doc(`emaIdIndex/${emaId}`);
    const [invitation, profile, emaIndex, slot] = await Promise.all([
      transaction.get(ref),
      transaction.get(db.doc(`users/${uid}`)),
      transaction.get(emaIndexRef),
      transaction.get(db.doc(`tournaments/${tournamentId}/players/${playerId}`)),
    ]);
    if (invitation.get("status") !== "PENDING") throw conflict("The invitation is no longer valid");
    if (slot.get("assignedEmaId") !== emaId) throw conflict("The player slot changed");
    const profileEma = profile.get("emaId");
    if (typeof profileEma === "string" && profileEma.length > 0 && profileEma !== emaId) {
      throw conflict("This account belongs to another EMA player");
    }
    if (emaIndex.exists && emaIndex.get("uid") !== uid) throw conflict("This EMA id belongs to another account");
    transaction.set(emaIndexRef, { uid, updatedAt: FieldValue.serverTimestamp() });
    transaction.set(db.doc(`users/${uid}`), { emaId, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
    transaction.set(playerTournamentRef(uid, tournamentId), {
      playerId, emaId, tournamentId, createdAt: FieldValue.serverTimestamp(),
    });
    transaction.update(ref, { status: "ACCEPTED", acceptedUid: uid, updatedAt: FieldValue.serverTimestamp() });
    transaction.update(slot.ref, { accountUid: uid, invitationStatus: "ACCEPTED", updatedAt: FieldValue.serverTimestamp() });
  });
}

export type InvitationView = {
  invitationId: string;
  playerId: number;
  email: string;
  status: string;
  expiresAt: string | null;
};

export async function listInvitations(tournamentId: string): Promise<InvitationView[]> {
  const snapshot = await invitationsOf(tournamentId).get();
  return snapshot.docs
    .filter((doc) => doc.get("status") === "PENDING" || doc.get("status") === "ACCEPTED")
    .map((doc) => ({
      invitationId: doc.id,
      playerId: Number(doc.get("playerId")),
      email: String(doc.get("email") ?? ""),
      status: String(doc.get("status")),
      expiresAt: doc.get("expiresAt") instanceof Timestamp ? (doc.get("expiresAt") as Timestamp).toDate().toISOString() : null,
    }));
}
