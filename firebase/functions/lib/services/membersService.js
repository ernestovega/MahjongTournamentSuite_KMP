"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listTournamentMembers = listTournamentMembers;
exports.upsertTournamentMember = upsertTournamentMember;
exports.removeTournamentMember = removeTournamentMember;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
async function listTournamentMembers(tournamentId) {
    const snap = await firebase_1.db.collection(`tournaments/${tournamentId}/members`).get();
    return Promise.all(snap.docs.map(async (d) => ({
        uid: d.id,
        email: await firebase_1.auth.getUser(d.id).then((user) => user.email ?? "").catch(() => ""),
        role: d.get("role"),
    })));
}
async function upsertTournamentMember(params) {
    const ref = firebase_1.db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`);
    await ref.set({
        uid: params.uid,
        role: params.role,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        createdAt: firestore_1.FieldValue.serverTimestamp(),
    }, { merge: true });
}
async function removeTournamentMember(params) {
    await firebase_1.db.doc(`tournaments/${params.tournamentId}/members/${params.uid}`).delete();
}
//# sourceMappingURL=membersService.js.map