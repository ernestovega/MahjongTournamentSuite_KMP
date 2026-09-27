"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getUserProfile = getUserProfile;
exports.createUserProfile = createUserProfile;
exports.listManagedUsers = listManagedUsers;
exports.getManagedUser = getManagedUser;
exports.setGlobalUserRole = setGlobalUserRole;
exports.updateManagedUser = updateManagedUser;
exports.listUserTournamentAssignments = listUserTournamentAssignments;
exports.listAssignedTournamentIds = listAssignedTournamentIds;
exports.syncUserTournamentAssignments = syncUserTournamentAssignments;
exports.setManagedUserDisabled = setManagedUserDisabled;
exports.countEnabledAdmins = countEnabledAdmins;
exports.listEnabledAdmins = listEnabledAdmins;
exports.assertEmailAvailable = assertEmailAvailable;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const globalRole_1 = require("../models/globalRole");
async function getUserProfile(uid) {
    const snap = await firebase_1.db.doc(`users/${uid}`).get();
    if (!snap.exists)
        throw (0, httpError_1.notFound)("User profile not found");
    return {
        uid,
        email: String(snap.get("email") ?? ""),
        alias: String(snap.get("alias") ?? "").trim(),
    };
}
async function createUserProfile(uid, email, alias = "") {
    await firebase_1.db.doc(`users/${uid}`).set({
        uid,
        email,
        alias: alias.trim(),
        contactEmail: email,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
}
async function listManagedUsers(visibleTournamentIds) {
    const profiles = await firebase_1.db.collection("users").get();
    const profilesByUid = new Map(profiles.docs.map((doc) => [doc.id, doc.data()]));
    const assignmentsByUid = await listTournamentAssignmentsByUid(visibleTournamentIds);
    const users = [];
    let pageToken;
    do {
        const page = await firebase_1.auth.listUsers(1000, pageToken);
        page.users.forEach((user) => {
            const profile = profilesByUid.get(user.uid);
            users.push({
                uid: user.uid,
                email: user.email ?? String(profile?.email ?? ""),
                alias: String(profile?.alias ?? "").trim(),
                role: (0, globalRole_1.getGlobalUserRole)(user),
                disabled: user.disabled,
                tournamentAssignments: assignmentsByUid.get(user.uid) ?? [],
            });
        });
        pageToken = page.pageToken;
    } while (pageToken);
    return users.sort((left, right) => left.email.localeCompare(right.email));
}
async function getManagedUser(uid, visibleTournamentIds) {
    const user = await firebase_1.auth.getUser(uid);
    const profile = await firebase_1.db.doc(`users/${uid}`).get();
    return {
        uid,
        email: user.email ?? String(profile.get("email") ?? ""),
        alias: String(profile.get("alias") ?? "").trim(),
        role: (0, globalRole_1.getGlobalUserRole)(user),
        disabled: user.disabled,
        tournamentAssignments: await listUserTournamentAssignments(uid, visibleTournamentIds),
    };
}
async function setGlobalUserRole(uid, role) {
    const user = await firebase_1.auth.getUser(uid);
    const claims = { ...(user.customClaims ?? {}) };
    delete claims.admin;
    delete claims.superadmin;
    if (role === "ADMIN")
        claims.admin = true;
    await firebase_1.auth.setCustomUserClaims(uid, claims);
}
async function updateManagedUser(params) {
    await firebase_1.auth.updateUser(params.uid, { email: params.email });
    await setGlobalUserRole(params.uid, params.role);
    await firebase_1.db.doc(`users/${params.uid}`).set({
        uid: params.uid,
        email: params.email,
        alias: params.alias.trim(),
        contactEmail: params.email,
        emaId: firestore_1.FieldValue.delete(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
    await syncUserTournamentAssignments(params.uid, params.role === "ADMIN" ? [] : params.tournamentAssignments, params.role === "ADMIN" ? undefined : params.assignmentScope);
}
async function listTournamentAssignmentsByUid(visibleTournamentIds) {
    const result = new Map();
    const tournaments = await firebase_1.db.collection("tournaments").get();
    await Promise.all(tournaments.docs.map(async (tournament) => {
        if (visibleTournamentIds && !visibleTournamentIds.has(tournament.id))
            return;
        const members = await tournament.ref.collection("members").get();
        members.docs.forEach((member) => {
            const assignment = {
                tournamentId: tournament.id,
                tournamentName: String(tournament.get("name") ?? ""),
            };
            result.set(member.id, [...(result.get(member.id) ?? []), assignment]);
        });
    }));
    result.forEach((assignments) => assignments.sort((left, right) => left.tournamentName.localeCompare(right.tournamentName)));
    return result;
}
async function listUserTournamentAssignments(uid, visibleTournamentIds) {
    const memberships = await firebase_1.db.collectionGroup("members").where("uid", "==", uid).get();
    const assignments = await Promise.all(memberships.docs.map(async (membership) => {
        const tournamentRef = membership.ref.parent.parent;
        if (!tournamentRef || (visibleTournamentIds && !visibleTournamentIds.has(tournamentRef.id)))
            return null;
        const tournament = await tournamentRef.get();
        if (!tournament.exists)
            return null;
        return {
            tournamentId: tournament.id,
            tournamentName: String(tournament.get("name") ?? ""),
        };
    }));
    return assignments
        .filter((assignment) => assignment !== null)
        .sort((left, right) => left.tournamentName.localeCompare(right.tournamentName));
}
async function listAssignedTournamentIds(uid) {
    return new Set((await listUserTournamentAssignments(uid)).map((assignment) => assignment.tournamentId));
}
async function syncUserTournamentAssignments(uid, assignments, scopeTournamentIds) {
    const desired = new Map(assignments.map((assignment) => [assignment.tournamentId, assignment]));
    const tournamentIds = [...desired.keys()];
    if (tournamentIds.length !== assignments.length)
        throw (0, httpError_1.conflict)("Duplicate tournament assignment");
    if (scopeTournamentIds && tournamentIds.some((id) => !scopeTournamentIds.has(id))) {
        throw (0, httpError_1.forbidden)("Editors can assign accounts only to their own tournaments");
    }
    const tournamentRefs = tournamentIds.map((id) => firebase_1.db.doc(`tournaments/${id}`));
    const tournamentDocs = tournamentRefs.length > 0 ? await firebase_1.db.getAll(...tournamentRefs) : [];
    if (tournamentDocs.some((document) => !document.exists))
        throw (0, httpError_1.notFound)("Tournament not found");
    const current = await firebase_1.db.collectionGroup("members").where("uid", "==", uid).get();
    const batch = firebase_1.db.batch();
    let operationCount = 0;
    current.docs.forEach((membership) => {
        const tournamentId = membership.ref.parent.parent?.id;
        const isInScope = tournamentId && (!scopeTournamentIds || scopeTournamentIds.has(tournamentId));
        if (tournamentId && isInScope && !desired.has(tournamentId)) {
            batch.delete(membership.ref);
            operationCount++;
        }
    });
    assignments.forEach((assignment) => {
        batch.set(firebase_1.db.doc(`tournaments/${assignment.tournamentId}/members/${uid}`), {
            uid,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
            createdAt: firestore_1.FieldValue.serverTimestamp(),
        });
        operationCount++;
    });
    if (operationCount > 0)
        await batch.commit();
}
async function setManagedUserDisabled(uid, disabled) {
    await firebase_1.auth.updateUser(uid, { disabled });
    if (disabled)
        await firebase_1.auth.revokeRefreshTokens(uid);
    await firebase_1.db.doc(`users/${uid}`).set({ disabled, updatedAt: firestore_1.FieldValue.serverTimestamp() }, { merge: true });
}
async function countEnabledAdmins() {
    let count = 0;
    let pageToken;
    do {
        const page = await firebase_1.auth.listUsers(1000, pageToken);
        count += page.users.filter((user) => !user.disabled && (0, globalRole_1.getGlobalUserRole)(user) === "ADMIN").length;
        pageToken = page.pageToken;
    } while (pageToken);
    return count;
}
async function listEnabledAdmins() {
    const admins = [];
    let pageToken;
    do {
        const page = await firebase_1.auth.listUsers(1000, pageToken);
        page.users.forEach((user) => {
            if (!user.disabled && (0, globalRole_1.getGlobalUserRole)(user) === "ADMIN") {
                admins.push({ uid: user.uid, email: user.email ?? "" });
            }
        });
        pageToken = page.pageToken;
    } while (pageToken);
    return admins;
}
async function assertEmailAvailable(email, excludedUid) {
    try {
        const existing = await firebase_1.auth.getUserByEmail(email);
        if (existing.uid !== excludedUid)
            throw (0, httpError_1.conflict)("Email already in use");
    }
    catch (error) {
        const code = error.code;
        if (code !== "auth/user-not-found")
            throw error;
    }
}
//# sourceMappingURL=usersService.js.map