"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.createTournament = createTournament;
exports.listAllTournaments = listAllTournaments;
exports.listTournamentsForUser = listTournamentsForUser;
exports.deleteTournament = deleteTournament;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
function toIsoString(value) {
    if (value instanceof firestore_1.Timestamp)
        return value.toDate().toISOString();
    return null;
}
function mapTournamentDoc(d) {
    return {
        id: d.id,
        name: d.get("name") ?? "",
        isTeams: d.get("isTeams") ?? false,
        numPlayers: d.get("numPlayers") ?? 0,
        numRounds: d.get("numRounds") ?? 0,
        numTries: d.get("numTries") ?? 0,
        isCompleted: d.get("isCompleted") ?? false,
        createdByUid: d.get("createdByUid") ?? null,
        createdAt: toIsoString(d.get("createdAt")),
        updatedAt: toIsoString(d.get("updatedAt")),
    };
}
async function createTournament(params) {
    const numTablesPerRound = params.numPlayers / 4;
    const expectedTables = params.numRounds * numTablesPerRound;
    if (params.players.length !== params.numPlayers) {
        throw (0, httpError_1.badRequest)("Invalid players payload", { expected: params.numPlayers, received: params.players.length });
    }
    if (params.tables.length !== expectedTables) {
        throw (0, httpError_1.badRequest)("Invalid tables payload", { expected: expectedTables, received: params.tables.length });
    }
    const ref = firebase_1.db.collection("tournaments").doc();
    const tournamentDoc = {
        name: params.name,
        isTeams: params.isTeams,
        numPlayers: params.numPlayers,
        numRounds: params.numRounds,
        numTries: params.numTries,
        isCompleted: false,
        createdByUid: params.createdByUid,
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    };
    await firebase_1.db.runTransaction(async (tx) => {
        tx.set(ref, tournamentDoc);
        const memberRef = ref.collection("members").doc(params.createdByUid);
        tx.set(memberRef, {
            uid: params.createdByUid,
            role: "ADMIN",
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    });
    // Persist the client-generated schedule payload (players/rounds/tables).
    // Hands are created lazily when a table is first opened to keep write volume manageable.
    const batchCommits = [];
    let batch = firebase_1.db.batch();
    let opsInBatch = 0;
    const flushBatch = async () => {
        if (opsInBatch === 0)
            return;
        batchCommits.push(batch.commit());
        batch = firebase_1.db.batch();
        opsInBatch = 0;
    };
    const addSet = (docRef, data) => {
        batch.set(docRef, data);
        opsInBatch++;
        if (opsInBatch >= 450) {
            // Keep a margin under 500 for safety if we add more writes later.
            return flushBatch();
        }
        return Promise.resolve();
    };
    for (const player of params.players) {
        const playerRef = ref.collection("players").doc(String(player.id));
        // eslint-disable-next-line no-await-in-loop
        await addSet(playerRef, {
            id: player.id,
            name: player.name ?? `Player ${player.id}`,
            team: player.team,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    for (let roundId = 1; roundId <= params.numRounds; roundId++) {
        const roundRef = ref.collection("rounds").doc(String(roundId));
        // eslint-disable-next-line no-await-in-loop
        await addSet(roundRef, {
            roundId,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    for (const table of params.tables) {
        const tableRef = ref.collection("tables").doc(`${table.roundId}_${table.tableId}`);
        // eslint-disable-next-line no-await-in-loop
        await addSet(tableRef, {
            roundId: table.roundId,
            tableId: table.tableId,
            playerIds: table.playerIds,
            playerEastId: "",
            playerSouthId: "",
            playerWestId: "",
            playerNorthId: "",
            playerEastScore: "",
            playerSouthScore: "",
            playerWestScore: "",
            playerNorthScore: "",
            playerEastPoints: "",
            playerSouthPoints: "",
            playerWestPoints: "",
            playerNorthPoints: "",
            manualPlayerEastScore: "",
            manualPlayerSouthScore: "",
            manualPlayerWestScore: "",
            manualPlayerNorthScore: "",
            manualPlayerEastPoints: "",
            manualPlayerSouthPoints: "",
            manualPlayerWestPoints: "",
            manualPlayerNorthPoints: "",
            isCompleted: Boolean(table.isCompleted ?? false),
            useTotalsOnly: Boolean(table.useTotalsOnly ?? false),
            usePointsCalculation: true,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    await flushBatch();
    await Promise.all(batchCommits);
    const snap = await ref.get();
    return mapTournamentDoc(snap);
}
async function listAllTournaments() {
    const snap = await firebase_1.db.collection("tournaments").orderBy("updatedAt", "desc").get();
    return snap.docs.map((d) => mapTournamentDoc(d));
}
function isDocRef(value) {
    return value != null;
}
async function listTournamentsForUser(uid) {
    const memberSnaps = await firebase_1.db.collectionGroup("members").where("uid", "==", uid).get();
    const tournamentRefs = memberSnaps.docs
        .map((m) => m.ref.parent.parent)
        .filter(isDocRef);
    if (tournamentRefs.length === 0)
        return [];
    const tournamentSnaps = await firebase_1.db.getAll(...tournamentRefs);
    return tournamentSnaps
        .filter((t) => t.exists)
        .map((d) => mapTournamentDoc(d));
}
async function deleteTournament(tournamentId) {
    const ref = firebase_1.db.collection("tournaments").doc(tournamentId);
    const snap = await ref.get();
    if (!snap.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const recursiveDelete = firebase_1.db
        .recursiveDelete;
    if (typeof recursiveDelete === "function") {
        await recursiveDelete(ref);
        return;
    }
    // Fallback: delete the parent document (subcollections will remain).
    // This should be extremely rare; most firebase-admin builds expose recursiveDelete.
    await ref.delete();
}
//# sourceMappingURL=tournamentsService.js.map