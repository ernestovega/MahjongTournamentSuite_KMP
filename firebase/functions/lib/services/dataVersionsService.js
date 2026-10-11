"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getGlobalDataVersions = getGlobalDataVersions;
exports.getTournamentDataVersions = getTournamentDataVersions;
exports.tableVersionKey = tableVersionKey;
exports.readTableVersions = readTableVersions;
exports.getTournamentManifest = getTournamentManifest;
exports.globalVersionUpdate = globalVersionUpdate;
exports.tableVersionUpdate = tableVersionUpdate;
exports.tournamentVersionUpdate = tournamentVersionUpdate;
exports.bumpGlobalDataVersion = bumpGlobalDataVersion;
exports.bumpTournamentDataVersion = bumpTournamentDataVersion;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
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
function tableVersionKey(roundId, tableId) {
    return `${roundId}_${tableId}`;
}
function readTableVersions(value) {
    if (value == null || typeof value !== "object")
        return {};
    const versions = {};
    for (const [key, version] of Object.entries(value)) {
        if (Number.isSafeInteger(version) && Number(version) >= 0)
            versions[key] = Number(version);
    }
    return versions;
}
/** One read gives both the coarse revisions and the exact version of every saved table. */
async function getTournamentManifest(tournamentId) {
    const [snapshot, global] = await Promise.all([
        firebase_1.db.collection("tournaments").doc(tournamentId).get(),
        getGlobalDataVersions(),
    ]);
    if (!snapshot.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const versions = snapshot.get("dataVersions") ?? {};
    return {
        globalResources: global,
        resources: {
            members: readVersion(versions.members),
            players: readVersion(versions.players),
            teams: readVersion(versions.teams),
            rounds: readVersion(versions.rounds),
            tables: readVersion(versions.tables),
        },
        tableVersions: readTableVersions(versions.tableVersions),
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
/** Records the new version of one table. Polling clients compare it with the version they hold. */
function tableVersionUpdate(roundId, tableId, version) {
    return {
        ...tournamentVersionUpdate("tables"),
        [`dataVersions.tableVersions.${tableVersionKey(roundId, tableId)}`]: version,
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