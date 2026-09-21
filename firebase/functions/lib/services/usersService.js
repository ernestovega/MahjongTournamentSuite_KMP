"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getGlobalUserRole = getGlobalUserRole;
exports.getUserProfile = getUserProfile;
exports.createUserProfile = createUserProfile;
exports.listManagedUsers = listManagedUsers;
exports.getManagedUser = getManagedUser;
exports.setGlobalUserRole = setGlobalUserRole;
exports.updateManagedUser = updateManagedUser;
exports.listUserTournamentAssignments = listUserTournamentAssignments;
exports.syncUserTournamentAssignments = syncUserTournamentAssignments;
exports.setManagedUserDisabled = setManagedUserDisabled;
exports.countEnabledSuperadmins = countEnabledSuperadmins;
exports.assertEmailAvailable = assertEmailAvailable;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
function getGlobalUserRole(user) {
    if (user.customClaims?.superadmin === true)
        return "SUPERADMIN";
    return "REGULAR";
}
async function getUserProfile(uid) {
    const snap = await firebase_1.db.doc(`users/${uid}`).get();
    if (!snap.exists) {
        throw (0, httpError_1.notFound)("User profile not found");
    }
    const email = snap.get("email");
    const alias = String(snap.get("alias") ?? "").trim();
    return { uid, email, alias };
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
async function listManagedUsers() {
    const profiles = await firebase_1.db.collection("users").get();
    const profilesByUid = new Map(profiles.docs.map((doc) => [doc.id, doc.data()]));
    const assignmentsByUid = await listTournamentAssignmentsByUid();
    const users = [];
    let pageToken;
    do {
        const page = await firebase_1.auth.listUsers(1000, pageToken);
        page.users.forEach((user) => {
            const profile = profilesByUid.get(user.uid);
            const email = user.email ?? String(profile?.email ?? "");
            users.push({
                uid: user.uid,
                email,
                alias: String(profile?.alias ?? "").trim(),
                role: getGlobalUserRole(user),
                disabled: user.disabled,
                tournamentAssignments: assignmentsByUid.get(user.uid) ?? [],
            });
        });
        pageToken = page.pageToken;
    } while (pageToken);
    return users.sort((left, right) => left.email.localeCompare(right.email));
}
async function getManagedUser(uid) {
    const user = await firebase_1.auth.getUser(uid);
    const profile = await firebase_1.db.doc(`users/${uid}`).get();
    const email = user.email ?? String(profile.get("email") ?? "");
    const alias = String(profile.get("alias") ?? "").trim();
    return {
        uid,
        email,
        alias,
        role: getGlobalUserRole(user),
        disabled: user.disabled,
        tournamentAssignments: await listUserTournamentAssignments(uid),
    };
}
async function setGlobalUserRole(uid, role) {
    const user = await firebase_1.auth.getUser(uid);
    const claims = { ...(user.customClaims ?? {}) };
    delete claims.admin;
    delete claims.superadmin;
    if (role === "SUPERADMIN")
        claims.superadmin = true;
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
    await syncUserTournamentAssignments(params.uid, params.tournamentAssignments);
}
async function listTournamentAssignmentsByUid() {
    const result = new Map();
    const tournaments = await firebase_1.db.collection("tournaments").get();
    await Promise.all(tournaments.docs.map(async (tournament) => {
        const members = await tournament.ref.collection("members").get();
        members.docs.forEach((member) => {
            const assignment = {
                tournamentId: tournament.id,
                tournamentName: String(tournament.get("name") ?? ""),
                role: member.get("role"),
            };
            result.set(member.id, [...(result.get(member.id) ?? []), assignment]);
        });
    }));
    result.forEach((assignments) => assignments.sort((left, right) => left.tournamentName.localeCompare(right.tournamentName)));
    return result;
}
async function listUserTournamentAssignments(uid) {
    const memberships = await firebase_1.db.collectionGroup("members").where("uid", "==", uid).get();
    const assignments = await Promise.all(memberships.docs.map(async (membership) => {
        const tournamentRef = membership.ref.parent.parent;
        if (!tournamentRef)
            return null;
        const tournament = await tournamentRef.get();
        if (!tournament.exists)
            return null;
        return {
            tournamentId: tournament.id,
            tournamentName: String(tournament.get("name") ?? ""),
            role: membership.get("role"),
        };
    }));
    return assignments
        .filter((assignment) => assignment !== null)
        .sort((left, right) => left.tournamentName.localeCompare(right.tournamentName));
}
async function syncUserTournamentAssignments(uid, assignments) {
    const desired = new Map(assignments.map((assignment) => [assignment.tournamentId, assignment]));
    const tournamentIds = [...desired.keys()];
    if (tournamentIds.length !== assignments.length)
        throw (0, httpError_1.conflict)("Duplicate tournament assignment");
    const tournamentRefs = tournamentIds.map((id) => firebase_1.db.doc(`tournaments/${id}`));
    const tournamentDocs = tournamentRefs.length > 0 ? await firebase_1.db.getAll(...tournamentRefs) : [];
    if (tournamentDocs.some((document) => !document.exists))
        throw (0, httpError_1.notFound)("Tournament not found");
    const current = await firebase_1.db.collectionGroup("members").where("uid", "==", uid).get();
    const batch = firebase_1.db.batch();
    let operationCount = 0;
    current.docs.forEach((membership) => {
        const tournamentId = membership.ref.parent.parent?.id;
        if (tournamentId && !desired.has(tournamentId)) {
            batch.delete(membership.ref);
            operationCount++;
        }
    });
    assignments.forEach((assignment) => {
        batch.set(firebase_1.db.doc(`tournaments/${assignment.tournamentId}/members/${uid}`), {
            uid,
            role: assignment.role,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
            createdAt: firestore_1.FieldValue.serverTimestamp(),
        }, { merge: true });
        operationCount++;
    });
    if (operationCount > 0)
        await batch.commit();
}
async function setManagedUserDisabled(uid, disabled) {
    await firebase_1.auth.updateUser(uid, { disabled });
    if (disabled)
        await firebase_1.auth.revokeRefreshTokens(uid);
    await firebase_1.db.doc(`users/${uid}`).set({
        disabled,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
}
async function countEnabledSuperadmins() {
    let count = 0;
    let pageToken;
    do {
        const page = await firebase_1.auth.listUsers(1000, pageToken);
        count += page.users.filter((user) => !user.disabled && getGlobalUserRole(user) === "SUPERADMIN").length;
        pageToken = page.pageToken;
    } while (pageToken);
    return count;
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