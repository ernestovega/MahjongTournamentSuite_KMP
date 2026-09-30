"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getGlobalDataVersions = getGlobalDataVersions;
exports.getTournamentDataVersions = getTournamentDataVersions;
exports.globalVersionUpdate = globalVersionUpdate;
exports.tournamentVersionUpdate = tournamentVersionUpdate;
exports.bumpGlobalDataVersion = bumpGlobalDataVersion;
exports.bumpTournamentDataVersion = bumpTournamentDataVersion;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
function toIsoString(value) {
    return value instanceof firestore_1.Timestamp ? value.toDate().toISOString() : null;
}
function readVersion(value) {
    const data = value != null && typeof value === "object" ? value : {};
    return {
        revision: Number.isSafeInteger(data.revision) ? Number(data.revision) : 0,
        changedAt: toIsoString(data.changedAt),
    };
}
async function getGlobalDataVersions() {
    const snapshot = await firebase_1.db.collection("_meta").doc("dataVersions").get();
    const resources = snapshot.get("resources") ?? {};
    return {
        tournaments: readVersion(resources.tournaments),
        emaPlayers: readVersion(resources.emaPlayers),
        countries: readVersion(resources.countries),
        users: readVersion(resources.users),
    };
}
async function getTournamentDataVersions(tournamentId) {
    const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId).get();
    const resources = snapshot.get("dataVersions") ?? {};
    return {
        members: readVersion(resources.members),
        players: readVersion(resources.players),
        teams: readVersion(resources.teams),
        rounds: readVersion(resources.rounds),
        tables: readVersion(resources.tables),
    };
}
function globalVersionUpdate(resource) {
    return {
        resources: {
            [resource]: {
                revision: firestore_1.FieldValue.increment(1),
                changedAt: firestore_1.FieldValue.serverTimestamp(),
            },
        },
    };
}
function tournamentVersionUpdate(resource) {
    return {
        [`dataVersions.${resource}.revision`]: firestore_1.FieldValue.increment(1),
        [`dataVersions.${resource}.changedAt`]: firestore_1.FieldValue.serverTimestamp(),
    };
}
async function bumpGlobalDataVersion(resource) {
    await firebase_1.db.collection("_meta").doc("dataVersions").set(globalVersionUpdate(resource), { merge: true });
}
async function bumpTournamentDataVersion(tournamentId, resource) {
    await firebase_1.db.collection("tournaments").doc(tournamentId).update(tournamentVersionUpdate(resource));
}
//# sourceMappingURL=dataVersionsService.js.map