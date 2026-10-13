"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.StaleTableVersion = exports.HANDS_PER_TABLE = void 0;
exports.tableStateFromData = tableStateFromData;
exports.handsFromData = handsFromData;
exports.defaultHand = defaultHand;
exports.getTableWithHands = getTableWithHands;
exports.getRoundTablesWithHands = getRoundTablesWithHands;
exports.getAllTablesWithHands = getAllTablesWithHands;
exports.calculateTableSummary = calculateTableSummary;
exports.isTournamentCompleteAfterTableSave = isTournamentCompleteAfterTableSave;
exports.saveTableStateInTransaction = saveTableStateInTransaction;
exports.saveTableState = saveTableState;
exports.resetTable = resetTable;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const httpError_2 = require("../api/httpError");
const tournamentContentRules_1 = require("./tournamentContentRules");
const dataVersionsService_1 = require("./dataVersionsService");
const changeLog_1 = require("./changeLog");
/** Reads a table from its stored fields. The stored document and a merged document in memory use the same code. */
function tableStateFromData(data) {
    const text = (field) => String(data[field] ?? "");
    return {
        roundId: Number(data.roundId),
        tableId: Number(data.tableId),
        playerIds: (data.playerIds ?? []).map((x) => Number(x)),
        playerEastId: text("playerEastId"),
        playerSouthId: text("playerSouthId"),
        playerWestId: text("playerWestId"),
        playerNorthId: text("playerNorthId"),
        playerEastScore: text("playerEastScore"),
        playerSouthScore: text("playerSouthScore"),
        playerWestScore: text("playerWestScore"),
        playerNorthScore: text("playerNorthScore"),
        playerEastPoints: text("playerEastPoints"),
        playerSouthPoints: text("playerSouthPoints"),
        playerWestPoints: text("playerWestPoints"),
        playerNorthPoints: text("playerNorthPoints"),
        manualPlayerEastScore: text("manualPlayerEastScore"),
        manualPlayerSouthScore: text("manualPlayerSouthScore"),
        manualPlayerWestScore: text("manualPlayerWestScore"),
        manualPlayerNorthScore: text("manualPlayerNorthScore"),
        manualPlayerEastPoints: text("manualPlayerEastPoints"),
        manualPlayerSouthPoints: text("manualPlayerSouthPoints"),
        manualPlayerWestPoints: text("manualPlayerWestPoints"),
        manualPlayerNorthPoints: text("manualPlayerNorthPoints"),
        isCompleted: Boolean(data.isCompleted ?? false),
        useTotalsOnly: Boolean(data.useTotalsOnly ?? false),
        usePointsCalculation: Boolean(data.usePointsCalculation ?? true),
        version: Number(data.version ?? 0),
    };
}
function handsFromData(list) {
    return list
        .map((d) => ({
        handId: Number(d.handId),
        playerWinnerId: String(d.playerWinnerId ?? ""),
        playerLooserId: String(d.playerLooserId ?? ""),
        handScore: String(d.handScore ?? ""),
        isChickenHand: Boolean(d.isChickenHand ?? false),
        isDone: Boolean(d.isDone ?? false),
        playerEastPenalty: String(d.playerEastPenalty ?? ""),
        playerSouthPenalty: String(d.playerSouthPenalty ?? ""),
        playerWestPenalty: String(d.playerWestPenalty ?? ""),
        playerNorthPenalty: String(d.playerNorthPenalty ?? ""),
    }))
        .sort((a, b) => a.handId - b.handId);
}
exports.HANDS_PER_TABLE = 16;
function defaultHand(handId) {
    return {
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
    };
}
async function getTableWithHands(params) {
    const tableDocId = `${params.roundId}_${params.tableId}`;
    const tableRef = firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
    // The table and its hands are independent reads, so run them together.
    const [tableSnap, handsSnap] = await Promise.all([tableRef.get(), tableRef.collection("hands").get()]);
    if (!tableSnap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    return {
        table: tableStateFromData(tableSnap.data() ?? {}),
        hands: handsFromData(handsSnap.docs.map((doc) => doc.data())),
    };
}
/** Reads every table of a round with its hands in one call, so the client needs one request instead of one per table. */
async function getRoundTablesWithHands(params) {
    const tablesSnap = await firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables")
        .where("roundId", "==", params.roundId)
        .get();
    return readTablesWithHands(tablesSnap.docs);
}
/** Reads every table of the tournament with its hands in one call. The ranking needs all of them. */
async function getAllTablesWithHands(params) {
    const tablesSnap = await firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables").get();
    return readTablesWithHands(tablesSnap.docs);
}
async function readTablesWithHands(tableDocs) {
    const tables = await Promise.all(tableDocs.map(async (tableSnap) => {
        const handsSnap = await tableSnap.ref.collection("hands").get();
        return {
            table: tableStateFromData(tableSnap.data()),
            hands: handsFromData(handsSnap.docs.map((doc) => doc.data())),
        };
    }));
    return tables.sort((a, b) => a.table.roundId - b.table.roundId || a.table.tableId - b.table.tableId);
}
function calculateTableSummary(table, hands) {
    const hasTableProgress = [
        "playerEastId", "playerSouthId", "playerWestId", "playerNorthId",
        "playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
        "playerEastPoints", "playerSouthPoints", "playerWestPoints", "playerNorthPoints",
    ].some((field) => String(table[field] ?? "").trim().length > 0);
    const hasHandProgress = hands.some((hand) => [
        "playerWinnerId", "playerLooserId", "handScore", "playerEastPenalty",
        "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty",
    ].some((field) => String(hand[field] ?? "").trim().length > 0)
        || Boolean(hand.isChickenHand) || Boolean(hand.isDone));
    const useTotalsOnly = Boolean(table.useTotalsOnly ?? false);
    const usePointsCalculation = Boolean(table.usePointsCalculation ?? true);
    const validScores = (0, tournamentContentRules_1.hasFourValidScores)([
        table.manualPlayerEastScore || table.playerEastScore,
        table.manualPlayerSouthScore || table.playerSouthScore,
        table.manualPlayerWestScore || table.playerWestScore,
        table.manualPlayerNorthScore || table.playerNorthScore,
    ]);
    const validPoints = (0, tournamentContentRules_1.hasValidTablePoints)([
        table.manualPlayerEastPoints || table.playerEastPoints,
        table.manualPlayerSouthPoints || table.playerSouthPoints,
        table.manualPlayerWestPoints || table.playerWestPoints,
        table.manualPlayerNorthPoints || table.playerNorthPoints,
    ]);
    const hasValidManualTotals = useTotalsOnly ? validScores : !usePointsCalculation && validPoints;
    return {
        hasProgress: hasTableProgress || hasHandProgress || Boolean(table.isCompleted),
        hasValidManualTotals,
        completionStatus: (0, tournamentContentRules_1.calculateTableCompletionStatus)({
            hasData: hasTableProgress || hasHandProgress,
            seatIds: [table.playerEastId, table.playerSouthId, table.playerWestId, table.playerNorthId],
            useTotalsOnly,
            hasValidTotals: useTotalsOnly ? validScores : (0, tournamentContentRules_1.hasFourValidScores)([
                table.playerEastScore, table.playerSouthScore, table.playerWestScore, table.playerNorthScore,
            ]),
        }),
        ...(0, tournamentContentRules_1.calculateHandSummary)(hands),
    };
}
/**
 * Returns the tournament completion state after the current table changes.
 * The query contains every table that was incomplete when the transaction read it.
 */
function isTournamentCompleteAfterTableSave(params) {
    if (!params.currentTableIsComplete)
        return false;
    return params.incompleteTablePaths.every((path) => path === params.currentTablePath);
}
function handRefsOf(tableRef) {
    return Array.from({ length: exports.HANDS_PER_TABLE }, (_, index) => tableRef.collection("hands").doc(String(index + 1)));
}
class StaleTableVersion extends Error {
    constructor(expectedVersion, currentVersion) {
        super("Stale table version");
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }
}
exports.StaleTableVersion = StaleTableVersion;
/** A table counts as started when it has any data. The tournament keeps the number of started tables. */
function progressDelta(before, after) {
    return Number(after) - Number(before);
}
/**
 * Runs a save inside a transaction that the caller owns. The caller must finish its own reads before it calls this,
 * because this function writes. It throws [StaleTableVersion] when the expected version is old.
 */
async function saveTableStateInTransaction(transaction, params) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const tableRef = tournamentRef.collection("tables").doc((0, dataVersionsService_1.tableVersionKey)(params.roundId, params.tableId));
    const tableSnapshot = await transaction.get(tableRef);
    if (!tableSnapshot.exists)
        throw (0, httpError_1.notFound)("Table not found");
    const currentVersion = Number(tableSnapshot.get("version") ?? 0);
    if (params.expectedVersion !== currentVersion) {
        throw new StaleTableVersion(params.expectedVersion, currentVersion);
    }
    // The hands are read by document, not by query. A query locks a whole range, and then editors of
    // different tables would wait for each other.
    const handSnapshots = await transaction.getAll(...handRefsOf(tableRef));
    if (handSnapshots.some((snapshot) => !snapshot.exists))
        throw (0, httpError_1.notFound)("Hands not found");
    const handsBefore = new Map(handSnapshots.map((snapshot) => [snapshot.id, snapshot.data() ?? {}]));
    if (params.handPatches.some((item) => !handsBefore.has(String(item.handId)))) {
        throw (0, httpError_1.notFound)("Hand not found");
    }
    const tableBefore = tableSnapshot.data() ?? {};
    const mergedTable = { ...tableBefore, ...params.tablePatch };
    const handsAfter = new Map(handsBefore);
    params.handPatches.forEach((item) => {
        const key = String(item.handId);
        handsAfter.set(key, { ...(handsBefore.get(key) ?? {}), ...item.patch, handId: item.handId });
    });
    const previousSummary = calculateTableSummary(tableBefore, [...handsBefore.values()]);
    const summary = calculateTableSummary(mergedTable, [...handsAfter.values()]);
    const wasComplete = Boolean(tableBefore.isCompleted);
    const isComplete = Boolean(mergedTable.isCompleted);
    // Only a table that becomes complete can finish the tournament. Other saves skip this query,
    // so editors of different tables do not lock each other.
    let tournamentIsComplete = false;
    if (isComplete) {
        const incompleteTables = await transaction.get(tournamentRef.collection("tables").where("isCompleted", "==", false).limit(2));
        tournamentIsComplete = isTournamentCompleteAfterTableSave({
            currentTablePath: tableRef.path,
            currentTableIsComplete: true,
            incompleteTablePaths: incompleteTables.docs.map((document) => document.ref.path),
        });
    }
    const nextVersion = currentVersion + 1;
    const editedBy = params.actor == null
        ? {}
        : { lastEditedByUid: params.actor.uid, lastEditedByName: params.actor.name, lastEditedAt: firestore_1.FieldValue.serverTimestamp() };
    transaction.update(tableRef, {
        ...params.tablePatch,
        ...summary,
        ...editedBy,
        version: nextVersion,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    params.handPatches.forEach((item) => {
        transaction.update(tableRef.collection("hands").doc(String(item.handId)), {
            ...item.patch,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    });
    const startedChange = progressDelta(previousSummary.hasProgress, summary.hasProgress);
    transaction.update(tournamentRef, {
        isCompleted: tournamentIsComplete,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        ...(startedChange === 0 ? {} : { tablesWithProgress: firestore_1.FieldValue.increment(startedChange) }),
        ...(0, dataVersionsService_1.tableVersionUpdate)(params.roundId, params.tableId, nextVersion),
    });
    // The tournament list shows only whether the tournament is complete. That can change only when a table changes.
    if (wasComplete !== isComplete) {
        transaction.set(firebase_1.db.collection("_meta").doc("dataVersions"), (0, dataVersionsService_1.globalVersionUpdate)("tournaments"), { merge: true });
    }
    transaction.set(tournamentRef.collection("changes").doc(), {
        ...(0, changeLog_1.buildSaveChangeEntry)({
            roundId: params.roundId,
            tableId: params.tableId,
            version: nextVersion,
            actor: params.actor ?? null,
            tableBefore,
            tablePatch: params.tablePatch,
            handsBefore,
            handPatches: params.handPatches,
        }),
        at: firestore_1.FieldValue.serverTimestamp(),
    });
    // The answer comes from the data that the transaction wrote, so the client needs no second read.
    return {
        table: tableStateFromData({ ...mergedTable, ...summary, version: nextVersion }),
        hands: handsFromData([...handsAfter.values()]),
    };
}
async function saveTableState(params) {
    try {
        return await firebase_1.db.runTransaction((transaction) => saveTableStateInTransaction(transaction, params));
    }
    catch (error) {
        if (error instanceof StaleTableVersion) {
            throw (0, httpError_2.conflict)("Table data changed on the server", {
                expectedVersion: error.expectedVersion,
                currentVersion: error.currentVersion,
                current: await getTableWithHands(params),
            });
        }
        throw error;
    }
}
const EMPTY_TABLE_FIELDS = {
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
    isCompleted: false,
    useTotalsOnly: false,
    usePointsCalculation: true,
    hasProgress: false,
    hasValidManualTotals: false,
    completionStatus: "empty",
    bestHandScores: [],
    chickenHandCount: 0,
};
async function resetTable(params) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const tableRef = tournamentRef.collection("tables").doc((0, dataVersionsService_1.tableVersionKey)(params.roundId, params.tableId));
    // A transaction makes the reset atomic with saves. A save based on the old version fails after it.
    await firebase_1.db.runTransaction(async (transaction) => {
        const tableSnap = await transaction.get(tableRef);
        if (!tableSnap.exists)
            throw (0, httpError_1.notFound)("Table not found");
        const handSnapshots = await transaction.getAll(...handRefsOf(tableRef));
        if (handSnapshots.some((snapshot) => !snapshot.exists))
            throw (0, httpError_1.notFound)("Hands not found");
        const tableBefore = tableSnap.data() ?? {};
        const nextVersion = Number(tableBefore.version ?? 0) + 1;
        const previous = calculateTableSummary(tableBefore, handSnapshots.map((hand) => hand.data() ?? {}));
        transaction.update(tableRef, {
            ...EMPTY_TABLE_FIELDS,
            version: nextVersion,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
        handSnapshots.forEach((hand) => {
            transaction.update(hand.ref, {
                ...defaultHand(Number(hand.get("handId") ?? hand.id)),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        });
        const startedChange = progressDelta(previous.hasProgress, false);
        transaction.update(tournamentRef, {
            isCompleted: false,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
            ...(startedChange === 0 ? {} : { tablesWithProgress: firestore_1.FieldValue.increment(startedChange) }),
            ...(0, dataVersionsService_1.tableVersionUpdate)(params.roundId, params.tableId, nextVersion),
        });
        if (Boolean(tableBefore.isCompleted)) {
            transaction.set(firebase_1.db.collection("_meta").doc("dataVersions"), (0, dataVersionsService_1.globalVersionUpdate)("tournaments"), { merge: true });
        }
        transaction.set(tournamentRef.collection("changes").doc(), {
            ...(0, changeLog_1.buildResetChangeEntry)({
                roundId: params.roundId,
                tableId: params.tableId,
                version: nextVersion,
                actor: params.actor ?? null,
            }),
            at: firestore_1.FieldValue.serverTimestamp(),
        });
    });
}
//# sourceMappingURL=tableManagerService.js.map