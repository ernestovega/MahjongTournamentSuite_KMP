"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.requireEmulator = requireEmulator;
exports.newTournamentId = newTournamentId;
exports.seedTournament = seedTournament;
exports.readTournament = readTournament;
exports.readTable = readTable;
exports.readHand = readHand;
exports.readChanges = readChanges;
exports.readGlobalTournamentsRevision = readGlobalTournamentsRevision;
exports.countHandDocs = countHandDocs;
const node_crypto_1 = require("node:crypto");
const firebase_1 = require("../firebase");
/** Integration tests need the Firestore emulator. `npm run test:integration` starts it. */
function requireEmulator() {
    if (!process.env.FIRESTORE_EMULATOR_HOST) {
        throw new Error("Run these tests with the Firestore emulator: npm run test:integration");
    }
}
function newTournamentId() {
    return `test-${(0, node_crypto_1.randomUUID)()}`;
}
/** Creates a tournament with tables and 16 empty hands per table, as the app does after creation. */
async function seedTournament(params) {
    const tournamentId = params.tournamentId ?? newTournamentId();
    const tournamentRef = firebase_1.db.collection("tournaments").doc(tournamentId);
    await tournamentRef.set({
        isCompleted: params.isCompleted ?? false,
        tablesWithProgress: 0,
        dataVersions: { tables: { revision: 1, changedAt: null } },
    });
    for (const table of params.tables) {
        const tableRef = tournamentRef.collection("tables").doc(`${table.roundId}_${table.tableId}`);
        await tableRef.set({
            roundId: table.roundId,
            tableId: table.tableId,
            playerIds: [1, 2, 3, 4],
            isCompleted: table.isCompleted ?? false,
            useTotalsOnly: false,
            usePointsCalculation: true,
            version: table.version ?? 0,
        });
        if (params.withHands === false)
            continue;
        const batch = firebase_1.db.batch();
        for (let handId = 1; handId <= 16; handId++) {
            batch.set(tableRef.collection("hands").doc(String(handId)), {
                handId,
                playerWinnerId: "",
                playerLooserId: "",
                handScore: "",
                isChickenHand: false,
                isDone: false,
                playerEastPenalty: "",
                playerSouthPenalty: "",
                playerWestPenalty: "",
                playerNorthPenalty: "",
            });
        }
        await batch.commit();
    }
    return tournamentId;
}
async function readTournament(tournamentId) {
    const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId).get();
    return snapshot.data() ?? {};
}
async function readTable(tournamentId, roundId, tableId) {
    const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId)
        .collection("tables").doc(`${roundId}_${tableId}`).get();
    return snapshot.data() ?? {};
}
async function readHand(tournamentId, roundId, tableId, handId) {
    const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId)
        .collection("tables").doc(`${roundId}_${tableId}`)
        .collection("hands").doc(String(handId)).get();
    return snapshot.data() ?? {};
}
async function readChanges(tournamentId) {
    const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("changes").get();
    return snapshot.docs
        .map((doc) => doc.data())
        .sort((a, b) => Number(a.version) - Number(b.version));
}
async function readGlobalTournamentsRevision() {
    const snapshot = await firebase_1.db.collection("_meta").doc("dataVersions").get();
    return Number(snapshot.get("resources.tournaments.revision") ?? 0);
}
async function countHandDocs(tournamentId, roundId, tableId) {
    const snapshot = await firebase_1.db.collection("tournaments").doc(tournamentId)
        .collection("tables").doc(`${roundId}_${tableId}`).collection("hands").get();
    return snapshot.size;
}
//# sourceMappingURL=firestoreFixtures.js.map