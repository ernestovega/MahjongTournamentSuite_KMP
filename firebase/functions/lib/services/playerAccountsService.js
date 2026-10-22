"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.INVITATION_TTL_DAYS = void 0;
exports.hashToken = hashToken;
exports.buildToken = buildToken;
exports.parseToken = parseToken;
exports.getPlayerRegistration = getPlayerRegistration;
exports.listPlayerTournamentIds = listPlayerTournamentIds;
exports.linkKnownAccountToSlot = linkKnownAccountToSlot;
exports.assertEmaIdAvailable = assertEmaIdAvailable;
exports.setAccountEmaId = setAccountEmaId;
exports.runAccountAction = runAccountAction;
exports.saveContactEmails = saveContactEmails;
exports.acceptInvitation = acceptInvitation;
exports.listInvitations = listInvitations;
const node_crypto_1 = require("node:crypto");
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const dataVersionsService_1 = require("./dataVersionsService");
const mailService_1 = require("./mailService");
const usersService_1 = require("./usersService");
exports.INVITATION_TTL_DAYS = 14;
/** Where a player opens the invitation link. Set PLAYER_APP_URL to the player web site. */
function playerAppUrl() {
    return (process.env.PLAYER_APP_URL ?? "https://mtsplayer.mahjongmadrid.com").replace(/\/$/, "");
}
function hashToken(secret) {
    return (0, node_crypto_1.createHash)("sha256").update(secret).digest("hex");
}
/** A token holds the document path parts, so accepting needs no query. The secret part is stored only as a hash. */
function buildToken(tournamentId, invitationId, secret) {
    return `${tournamentId}.${invitationId}.${secret}`;
}
function parseToken(token) {
    const parts = token.split(".");
    if (parts.length !== 3 || parts.some((part) => part.length === 0))
        return null;
    return { tournamentId: parts[0], invitationId: parts[1], secret: parts[2] };
}
function invitationsOf(tournamentId) {
    return firebase_1.db.collection(`tournaments/${tournamentId}/playerInvitations`);
}
function playerTournamentRef(uid, tournamentId) {
    return firebase_1.db.doc(`users/${uid}/playerTournaments/${tournamentId}`);
}
/** Returns the slot of the player account in a tournament, or null when the account is not registered there. */
async function getPlayerRegistration(uid, tournamentId) {
    const snap = await playerTournamentRef(uid, tournamentId).get();
    if (!snap.exists)
        return null;
    return { playerId: Number(snap.get("playerId")), emaId: String(snap.get("emaId") ?? "") };
}
async function listPlayerTournamentIds(uid) {
    const snapshot = await firebase_1.db.collection(`users/${uid}/playerTournaments`).get();
    return snapshot.docs.map((doc) => doc.id);
}
async function createInvitationDoc(params) {
    const tournamentRef = firebase_1.db.doc(`tournaments/${params.tournamentId}`);
    const playerRef = firebase_1.db.doc(`tournaments/${params.tournamentId}/players/${params.playerId}`);
    const secret = (0, node_crypto_1.randomBytes)(24).toString("base64url");
    const invitationRef = params.replaceInvitationId
        ? invitationsOf(params.tournamentId).doc(params.replaceInvitationId)
        : invitationsOf(params.tournamentId).doc();
    let tournamentName = "";
    await firebase_1.db.runTransaction(async (transaction) => {
        const [tournament, player, pending] = await Promise.all([
            transaction.get(tournamentRef),
            transaction.get(playerRef),
            transaction.get(invitationsOf(params.tournamentId)
                .where("playerId", "==", params.playerId).where("status", "==", "PENDING")),
        ]);
        if (!tournament.exists)
            throw (0, httpError_1.notFound)("Tournament not found");
        if (!player.exists)
            throw (0, httpError_1.notFound)("Player not found");
        const emaId = player.get("assignedEmaId");
        if (typeof emaId !== "string" || emaId.length === 0) {
            throw (0, httpError_1.badRequest)("Only a player with an EMA id can be invited");
        }
        if (player.get("accountUid"))
            throw (0, httpError_1.conflict)("This player already has an account");
        tournamentName = String(tournament.get("name") ?? "");
        const other = await firebase_1.auth.getUserByEmail(params.email).catch(() => null);
        pending.docs.forEach((doc) => {
            if (doc.id !== invitationRef.id)
                transaction.update(doc.ref, { status: "REVOKED", updatedAt: firestore_1.FieldValue.serverTimestamp() });
        });
        transaction.set(invitationRef, {
            playerId: params.playerId,
            emaId,
            email: params.email,
            existingUid: other?.uid ?? null,
            tokenHash: hashToken(secret),
            status: "PENDING",
            invitedBy: params.invitedBy,
            expiresAt: firestore_1.Timestamp.fromMillis(Date.now() + exports.INVITATION_TTL_DAYS * 24 * 60 * 60 * 1000),
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        transaction.update(playerRef, {
            invitationStatus: "INVITED",
            invitedEmail: params.email,
            invitedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    });
    await (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "players");
    return { invitationId: invitationRef.id, token: buildToken(params.tournamentId, invitationRef.id, secret), tournamentName };
}
const APP_DESCRIPTION = "This is the player app of the tournament. "
    + "After you sign in, you can see where you play in each round, the ranking after every round, "
    + "and your own results. You can also see upcoming and past tournaments, your statistics, and more.";
async function sendInvitationEmail(email, tournamentName, token) {
    const link = `${playerAppUrl()}/invite?token=${encodeURIComponent(token)}`;
    const subject = `Invitation to ${tournamentName}`;
    const text = `You are invited to play in ${tournamentName}.\n\n${APP_DESCRIPTION}\n\n`
        + `Open this link to accept: ${link}\nThe link works once and expires in ${exports.INVITATION_TTL_DAYS} days.`;
    const html = `<p>You are invited to play in <strong>${escapeHtml(tournamentName)}</strong>.</p>`
        + `<p>${APP_DESCRIPTION}</p>`
        + `<p><a href="${link}">Accept the invitation</a></p>`
        + `<p>The link works once and expires in ${exports.INVITATION_TTL_DAYS} days.</p>`;
    await (0, mailService_1.sendEmail)({ to: email, subject, text, html });
}
function escapeHtml(value) {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}
/** Links a slot to the account that already owns the EMA id of the slot. Sends no email. */
async function linkKnownAccountToSlot(tournamentId, playerId) {
    const slotRef = firebase_1.db.doc(`tournaments/${tournamentId}/players/${playerId}`);
    const slotSnap = await slotRef.get();
    const emaId = slotSnap.get("assignedEmaId");
    if (!slotSnap.exists || typeof emaId !== "string" || emaId.length === 0)
        return null;
    if (typeof slotSnap.get("accountUid") === "string")
        return String(slotSnap.get("accountUid"));
    const indexSnap = await firebase_1.db.doc(`emaIdIndex/${emaId}`).get();
    const uid = indexSnap.exists ? String(indexSnap.get("uid")) : null;
    if (!uid)
        return null;
    const account = await firebase_1.auth.getUser(uid).catch(() => null);
    if (!account || account.disabled)
        return null;
    const pending = await invitationsOf(tournamentId)
        .where("playerId", "==", playerId).where("status", "==", "PENDING").get();
    const linked = await firebase_1.db.runTransaction(async (transaction) => {
        const slot = await transaction.get(slotRef);
        if (slot.get("assignedEmaId") !== emaId || typeof slot.get("accountUid") === "string")
            return false;
        pending.docs.forEach((doc) => transaction.update(doc.ref, { status: "REVOKED", updatedAt: firestore_1.FieldValue.serverTimestamp() }));
        transaction.update(slotRef, {
            accountUid: uid,
            invitationStatus: "ACCEPTED",
            invitedEmail: firestore_1.FieldValue.delete(),
            invitedAt: firestore_1.FieldValue.delete(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        transaction.set(playerTournamentRef(uid, tournamentId), {
            playerId, emaId, tournamentId, createdAt: firestore_1.FieldValue.serverTimestamp(),
        });
        return true;
    });
    if (linked)
        await (0, dataVersionsService_1.bumpTournamentDataVersion)(tournamentId, "players");
    return linked ? uid : null;
}
/**
 * Makes the account the owner of an EMA id. One account owns one EMA id, and one EMA id has one account.
 * It does not touch any tournament.
 */
async function claimEmaId(uid, emaId) {
    await firebase_1.db.runTransaction(async (transaction) => {
        const indexRef = firebase_1.db.doc(`emaIdIndex/${emaId}`);
        const [index, profile] = await Promise.all([transaction.get(indexRef), transaction.get(firebase_1.db.doc(`users/${uid}`))]);
        if (index.exists && index.get("uid") !== uid)
            throw (0, httpError_1.conflict)("This EMA id belongs to another account");
        const current = profile.get("emaId");
        if (typeof current === "string" && current.length > 0 && current !== emaId) {
            throw (0, httpError_1.conflict)("This account belongs to another EMA player");
        }
        transaction.set(indexRef, { uid, updatedAt: firestore_1.FieldValue.serverTimestamp() });
        transaction.set(firebase_1.db.doc(`users/${uid}`), { emaId, updatedAt: firestore_1.FieldValue.serverTimestamp() }, { merge: true });
    });
}
/** Removes the account link of every slot that points to the account, and releases its EMA id. */
async function releaseEmaId(uid) {
    const registrations = await firebase_1.db.collection(`users/${uid}/playerTournaments`).get();
    for (const registration of registrations.docs) {
        const playerId = Number(registration.get("playerId"));
        const slotRef = firebase_1.db.doc(`tournaments/${registration.id}/players/${playerId}`);
        const related = await invitationsOf(registration.id).where("playerId", "==", playerId).get();
        const batch = firebase_1.db.batch();
        related.docs.forEach((doc) => {
            if (doc.get("status") === "PENDING" || doc.get("status") === "ACCEPTED") {
                batch.update(doc.ref, { status: "REVOKED", updatedAt: firestore_1.FieldValue.serverTimestamp() });
            }
        });
        if ((await slotRef.get()).exists) {
            batch.update(slotRef, {
                invitationStatus: "NONE",
                accountUid: firestore_1.FieldValue.delete(),
                invitedEmail: firestore_1.FieldValue.delete(),
                invitedAt: firestore_1.FieldValue.delete(),
                notifiedAt: firestore_1.FieldValue.delete(),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
        batch.delete(registration.ref);
        await batch.commit();
        await (0, dataVersionsService_1.bumpTournamentDataVersion)(registration.id, "players");
    }
    const profile = await firebase_1.db.doc(`users/${uid}`).get();
    const emaId = profile.get("emaId");
    if (typeof emaId === "string" && emaId.length > 0) {
        await firebase_1.db.doc(`emaIdIndex/${emaId}`).delete();
        await firebase_1.db.doc(`users/${uid}`).update({ emaId: firestore_1.FieldValue.delete(), updatedAt: firestore_1.FieldValue.serverTimestamp() });
    }
}
/**
 * Checks that the EMA player exists and that no other account owns the EMA id.
 * [ownUid] is the account that wants the EMA id. A new account has none.
 */
async function assertEmaIdAvailable(emaId, ownUid) {
    const registered = await firebase_1.db.doc(`emaPlayerRegistry/${emaId}`).get();
    if (!registered.exists)
        throw (0, httpError_1.notFound)("EMA player not found");
    const index = await firebase_1.db.doc(`emaIdIndex/${emaId}`).get();
    if (index.exists && index.get("uid") !== ownUid)
        throw (0, httpError_1.conflict)("This EMA id belongs to another account");
}
/**
 * Sets the EMA id of an existing account, or clears it with null.
 * Every tournament that has this EMA id in a slot links to the account at once. No email is sent.
 */
async function setAccountEmaId(params) {
    const profile = await firebase_1.db.doc(`users/${params.uid}`).get();
    const current = typeof profile.get("emaId") === "string" ? String(profile.get("emaId")) : null;
    if (current === params.emaId)
        return;
    if (params.emaId != null)
        await assertEmaIdAvailable(params.emaId, params.uid);
    if (current != null)
        await releaseEmaId(params.uid);
    if (params.emaId == null)
        return;
    await claimEmaId(params.uid, params.emaId);
    await (0, usersService_1.grantPlayerCapability)(params.uid);
    await linkAccountToAllSlots(params.emaId);
}
/** Links every tournament slot that holds the EMA id to the account that owns the id. Sends no email. */
async function linkAccountToAllSlots(emaId) {
    const tournaments = await firebase_1.db.collection("tournaments").get();
    for (const tournament of tournaments.docs) {
        const assignment = await firebase_1.db.doc(`tournaments/${tournament.id}/emaPlayerAssignments/${emaId}`).get();
        if (assignment.exists)
            await linkKnownAccountToSlot(tournament.id, Number(assignment.get("playerId")));
    }
}
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const MAX_BULK_PLAYERS = 200;
const BULK_CONCURRENCY = 5;
async function notifyRegistration(params) {
    const [tournament, account] = await Promise.all([
        firebase_1.db.doc(`tournaments/${params.tournamentId}`).get(),
        firebase_1.auth.getUser(params.uid),
    ]);
    if (!account.email)
        throw (0, httpError_1.badRequest)("The account of this player has no email");
    const name = String(tournament.get("name") ?? "");
    const link = `${playerAppUrl()}/?tournament=${encodeURIComponent(params.tournamentId)}`;
    await (0, mailService_1.sendEmail)({
        to: account.email,
        subject: `You are registered in ${name}`,
        text: `You have been added to the tournament "${name}".\n\n${APP_DESCRIPTION}\n\nOpen the tournament: ${link}`,
        html: `<p>You have been added to the tournament <strong>${escapeHtml(name)}</strong>.</p>`
            + `<p>${APP_DESCRIPTION}</p>`
            + `<p><a href="${link}">Open the tournament</a></p>`,
    });
    await firebase_1.db.doc(`tournaments/${params.tournamentId}/players/${params.playerId}`)
        .update({ notifiedAt: firestore_1.FieldValue.serverTimestamp() });
    await (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "players");
}
async function sendInvitation(params) {
    const created = await createInvitationDoc(params);
    await sendInvitationEmail(params.email, created.tournamentName, created.token);
}
async function runOne(tournamentId, action, item, invitedBy) {
    const { playerId } = item;
    const slotRef = firebase_1.db.doc(`tournaments/${tournamentId}/players/${playerId}`);
    const slot = await slotRef.get();
    if (!slot.exists)
        return { playerId, outcome: "FAILED", message: "Player not found" };
    const emaId = slot.get("assignedEmaId");
    const hasEma = typeof emaId === "string" && emaId.length > 0;
    const email = (item.email ?? "").trim().toLowerCase();
    if (email && !EMAIL_PATTERN.test(email))
        return { playerId, outcome: "FAILED", message: "Invalid email" };
    if (action === "revoke") {
        const accountUid = typeof slot.get("accountUid") === "string" ? String(slot.get("accountUid")) : null;
        const status = String(slot.get("invitationStatus") ?? "NONE");
        if (accountUid == null && status !== "INVITED")
            return { playerId, outcome: "SKIPPED", message: "Nothing to revoke" };
        const related = await invitationsOf(tournamentId).where("playerId", "==", playerId).get();
        await firebase_1.db.runTransaction(async (transaction) => {
            related.docs.forEach((doc) => {
                if (doc.get("status") === "PENDING" || doc.get("status") === "ACCEPTED") {
                    transaction.update(doc.ref, { status: "REVOKED", updatedAt: firestore_1.FieldValue.serverTimestamp() });
                }
            });
            transaction.update(slotRef, {
                invitationStatus: "NONE",
                invitedEmail: firestore_1.FieldValue.delete(),
                invitedAt: firestore_1.FieldValue.delete(),
                accountUid: firestore_1.FieldValue.delete(),
                notifiedAt: firestore_1.FieldValue.delete(),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
            if (accountUid != null)
                transaction.delete(playerTournamentRef(accountUid, tournamentId));
        });
        await (0, dataVersionsService_1.bumpTournamentDataVersion)(tournamentId, "players");
        return { playerId, outcome: "REVOKED" };
    }
    if (!hasEma)
        return { playerId, outcome: "FAILED", message: "The player has no EMA id" };
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
            if (status === "INVITED")
                return { playerId, outcome: "SKIPPED", message: "Already invited" };
            const target = email || String(slot.get("invitedEmail") ?? "");
            if (!target)
                return { playerId, outcome: "FAILED", message: "No email for this player" };
            // The email belongs to an account already: no invitation is needed. The account links at once.
            const existing = await firebase_1.auth.getUserByEmail(target).catch(() => null);
            if (existing && !existing.disabled) {
                await claimEmaId(existing.uid, String(emaId));
                await (0, usersService_1.grantPlayerCapability)(existing.uid);
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
        if (status !== "INVITED")
            return { playerId, outcome: "SKIPPED", message: "Nothing to resend" };
        const pending = await invitationsOf(tournamentId)
            .where("playerId", "==", playerId).where("status", "==", "PENDING").get();
        const current = pending.docs[0];
        const target = email || String(current?.get("email") ?? slot.get("invitedEmail") ?? "");
        if (!target)
            return { playerId, outcome: "FAILED", message: "No email for this player" };
        await sendInvitation({ tournamentId, playerId, email: target, invitedBy, replaceInvitationId: current?.id });
        return { playerId, outcome: "INVITED" };
    }
    catch (error) {
        if (error.code === "email_failed") {
            return { playerId, outcome: "FAILED", message: "The email could not be sent. Use Resend." };
        }
        if (error && typeof error === "object" && "status" in error && "message" in error) {
            return { playerId, outcome: "FAILED", message: String(error.message) };
        }
        throw error;
    }
}
/**
 * Runs one action for one player or for many. Every player gets its own result.
 * Editors start these actions by hand. Assigning a player never sends an email.
 */
async function runAccountAction(params) {
    if (params.items.length === 0)
        throw (0, httpError_1.badRequest)("Choose at least one player");
    if (params.items.length > MAX_BULK_PLAYERS)
        throw (0, httpError_1.badRequest)(`At most ${MAX_BULK_PLAYERS} players per request`);
    const results = new Array(params.items.length);
    let next = 0;
    const worker = async () => {
        while (next < params.items.length) {
            const index = next++;
            const item = params.items[index];
            try {
                results[index] = await runOne(params.tournamentId, params.action, item, params.invitedBy);
            }
            catch (error) {
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
async function saveContactEmails(params) {
    const batch = firebase_1.db.batch();
    for (const item of params.items) {
        const email = item.email.trim().toLowerCase();
        if (!EMAIL_PATTERN.test(email))
            throw (0, httpError_1.badRequest)(`Invalid email for player ${item.playerId}`);
        batch.update(firebase_1.db.doc(`tournaments/${params.tournamentId}/players/${item.playerId}`), {
            invitedEmail: email,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    await batch.commit();
    await (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "players");
}
/**
 * Accepts an invitation.
 * A new account is created here and gets its password. An existing account must be signed in as the invited email.
 */
async function acceptInvitation(params) {
    const parsed = parseToken(params.token);
    if (!parsed)
        throw (0, httpError_1.badRequest)("Invalid invitation");
    const ref = invitationsOf(parsed.tournamentId).doc(parsed.invitationId);
    const snap = await ref.get();
    if (!snap.exists || snap.get("tokenHash") !== hashToken(parsed.secret))
        throw (0, httpError_1.notFound)("Invitation not found");
    if (snap.get("status") !== "PENDING")
        throw (0, httpError_1.conflict)("The invitation is no longer valid");
    const expiresAt = snap.get("expiresAt");
    if (expiresAt.toMillis() < Date.now())
        throw (0, httpError_1.conflict)("The invitation has expired");
    const email = String(snap.get("email"));
    const emaId = String(snap.get("emaId"));
    const playerId = Number(snap.get("playerId"));
    const existingUid = snap.get("existingUid") ?? null;
    let uid;
    if (existingUid) {
        if (params.actorUid !== existingUid)
            throw (0, httpError_1.forbidden)("Sign in with the invited account to accept");
        uid = existingUid;
    }
    else {
        const password = params.password ?? "";
        if (password.length < 8)
            throw (0, httpError_1.badRequest)("The password needs at least 8 characters");
        try {
            uid = (await firebase_1.auth.createUser({ email, password, emailVerified: true })).uid;
        }
        catch (error) {
            if (error.code === "auth/email-already-exists") {
                throw (0, httpError_1.conflict)("An account with this email already exists. Ask for a new invitation.");
            }
            throw error;
        }
        await firebase_1.db.doc(`users/${uid}`).set({
            uid, email, alias: "", contactEmail: email, createdAt: firestore_1.FieldValue.serverTimestamp(), updatedAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
    }
    try {
        await linkAccountToSlot({ ref, uid, emaId, playerId, tournamentId: parsed.tournamentId });
    }
    catch (error) {
        if (!existingUid)
            await firebase_1.auth.deleteUser(uid).catch(() => undefined);
        throw error;
    }
    await (0, usersService_1.grantPlayerCapability)(uid);
    await (0, dataVersionsService_1.bumpTournamentDataVersion)(parsed.tournamentId, "players");
    // The account owns the EMA id now, so the player sees every other tournament with this id too.
    await linkAccountToAllSlots(emaId);
    return { tournamentId: parsed.tournamentId, email };
}
async function linkAccountToSlot(params) {
    const { ref, uid, emaId, playerId } = params;
    const tournamentId = params.tournamentId;
    await firebase_1.db.runTransaction(async (transaction) => {
        const emaIndexRef = firebase_1.db.doc(`emaIdIndex/${emaId}`);
        const [invitation, profile, emaIndex, slot] = await Promise.all([
            transaction.get(ref),
            transaction.get(firebase_1.db.doc(`users/${uid}`)),
            transaction.get(emaIndexRef),
            transaction.get(firebase_1.db.doc(`tournaments/${tournamentId}/players/${playerId}`)),
        ]);
        if (invitation.get("status") !== "PENDING")
            throw (0, httpError_1.conflict)("The invitation is no longer valid");
        if (slot.get("assignedEmaId") !== emaId)
            throw (0, httpError_1.conflict)("The player slot changed");
        const profileEma = profile.get("emaId");
        if (typeof profileEma === "string" && profileEma.length > 0 && profileEma !== emaId) {
            throw (0, httpError_1.conflict)("This account belongs to another EMA player");
        }
        if (emaIndex.exists && emaIndex.get("uid") !== uid)
            throw (0, httpError_1.conflict)("This EMA id belongs to another account");
        transaction.set(emaIndexRef, { uid, updatedAt: firestore_1.FieldValue.serverTimestamp() });
        transaction.set(firebase_1.db.doc(`users/${uid}`), { emaId, updatedAt: firestore_1.FieldValue.serverTimestamp() }, { merge: true });
        transaction.set(playerTournamentRef(uid, tournamentId), {
            playerId, emaId, tournamentId, createdAt: firestore_1.FieldValue.serverTimestamp(),
        });
        transaction.update(ref, { status: "ACCEPTED", acceptedUid: uid, updatedAt: firestore_1.FieldValue.serverTimestamp() });
        transaction.update(slot.ref, { accountUid: uid, invitationStatus: "ACCEPTED", updatedAt: firestore_1.FieldValue.serverTimestamp() });
    });
}
async function listInvitations(tournamentId) {
    const snapshot = await invitationsOf(tournamentId).get();
    return snapshot.docs
        .filter((doc) => doc.get("status") === "PENDING" || doc.get("status") === "ACCEPTED")
        .map((doc) => ({
        invitationId: doc.id,
        playerId: Number(doc.get("playerId")),
        email: String(doc.get("email") ?? ""),
        status: String(doc.get("status")),
        expiresAt: doc.get("expiresAt") instanceof firestore_1.Timestamp ? doc.get("expiresAt").toDate().toISOString() : null,
    }));
}
//# sourceMappingURL=playerAccountsService.js.map