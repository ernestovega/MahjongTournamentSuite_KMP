"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listTournamentMembers = listTournamentMembers;
exports.upsertTournamentMember = upsertTournamentMember;
exports.removeTournamentMember = removeTournamentMember;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const globalRole_1 = require("../models/globalRole");
const usersService_1 = require("./usersService");
const dataVersionsService_1 = require("./dataVersionsService");
async function listTournamentMembers(tournamentId) {
    const [membersSnapshot, admins] = await Promise.all([
        firebase_1.db.collection(`tournaments/${tournamentId}/members`).get(),
        (0, usersService_1.listEnabledAdmins)(),
    ]);
    const adminIds = new Set(admins.map((admin) => admin.uid));
    const editors = await Promise.all(membersSnapshot.docs.map(async (document) => {
        if (adminIds.has(document.id))
            return null;
        const user = await firebase_1.auth.getUser(document.id).catch(() => null);
        if (!user || (0, globalRole_1.getGlobalUserRole)(user) !== "EDITOR")
            return null;
        return {
            uid: document.id,
            email: user.email ?? "",
            role: "EDITOR",
        };
    }));
    const assignedEditors = editors.filter((member) => member !== null);
    const result = [
        ...admins.map((admin) => ({ ...admin, role: "ADMIN" })),
        ...assignedEditors,
    ];
    return result.sort((left, right) => left.email.localeCompare(right.email));
}
async function upsertTournamentMember(params) {
    const user = await firebase_1.auth.getUser(params.uid);
    if (user.disabled)
        throw (0, httpError_1.badRequest)("A disabled account cannot be assigned");
    if ((0, globalRole_1.getGlobalUserRole)(user) === "ADMIN") {
        throw (0, httpError_1.badRequest)("Admins already have access to every tournament");
    }
    const ref = firebase_1.db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`);
    await ref.set({
        uid: params.uid,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    });
    await Promise.all([
        (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "members"),
        (0, dataVersionsService_1.bumpGlobalDataVersion)("users"),
    ]);
}
async function removeTournamentMember(params) {
    const user = await firebase_1.auth.getUser(params.uid);
    if ((0, globalRole_1.getGlobalUserRole)(user) === "ADMIN") {
        throw (0, httpError_1.badRequest)("Admins cannot be removed from a tournament");
    }
    await firebase_1.db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`).delete();
    await Promise.all([
        (0, dataVersionsService_1.bumpTournamentDataVersion)(params.tournamentId, "members"),
        (0, dataVersionsService_1.bumpGlobalDataVersion)("users"),
    ]);
}
//# sourceMappingURL=membersService.js.map