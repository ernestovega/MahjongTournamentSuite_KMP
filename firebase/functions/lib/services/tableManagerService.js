"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getTableWithHands = getTableWithHands;
exports.getRoundTablesWithHands = getRoundTablesWithHands;
exports.calculateTableSummary = calculateTableSummary;
exports.isTournamentCompleteAfterTableSave = isTournamentCompleteAfterTableSave;
exports.saveTableState = saveTableState;
exports.updateTable = updateTable;
exports.updateHand = updateHand;
exports.resetTable = resetTable;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const httpError_2 = require("../api/httpError");
const tournamentContentRules_1 = require("./tournamentContentRules");
const dataVersionsService_1 = require("./dataVersionsService");
function toTableState(tableSnap) {
    return {
        roundId: Number(tableSnap.get("roundId")),
        tableId: Number(tableSnap.get("tableId")),
        playerIds: (tableSnap.get("playerIds") ?? []).map((x) => Number(x)),
        playerEastId: String(tableSnap.get("playerEastId") ?? ""),
        playerSouthId: String(tableSnap.get("playerSouthId") ?? ""),
        playerWestId: String(tableSnap.get("playerWestId") ?? ""),
        playerNorthId: String(tableSnap.get("playerNorthId") ?? ""),
        playerEastScore: String(tableSnap.get("playerEastScore") ?? ""),
        playerSouthScore: String(tableSnap.get("playerSouthScore") ?? ""),
        playerWestScore: String(tableSnap.get("playerWestScore") ?? ""),
        playerNorthScore: String(tableSnap.get("playerNorthScore") ?? ""),
        playerEastPoints: String(tableSnap.get("playerEastPoints") ?? ""),
        playerSouthPoints: String(tableSnap.get("playerSouthPoints") ?? ""),
        playerWestPoints: String(tableSnap.get("playerWestPoints") ?? ""),
        playerNorthPoints: String(tableSnap.get("playerNorthPoints") ?? ""),
        manualPlayerEastScore: String(tableSnap.get("manualPlayerEastScore") ?? ""),
        manualPlayerSouthScore: String(tableSnap.get("manualPlayerSouthScore") ?? ""),
        manualPlayerWestScore: String(tableSnap.get("manualPlayerWestScore") ?? ""),
        manualPlayerNorthScore: String(tableSnap.get("manualPlayerNorthScore") ?? ""),
        manualPlayerEastPoints: String(tableSnap.get("manualPlayerEastPoints") ?? ""),
        manualPlayerSouthPoints: String(tableSnap.get("manualPlayerSouthPoints") ?? ""),
        manualPlayerWestPoints: String(tableSnap.get("manualPlayerWestPoints") ?? ""),
        manualPlayerNorthPoints: String(tableSnap.get("manualPlayerNorthPoints") ?? ""),
        isCompleted: Boolean(tableSnap.get("isCompleted") ?? false),
        useTotalsOnly: Boolean(tableSnap.get("useTotalsOnly") ?? true),
        usePointsCalculation: Boolean(tableSnap.get("usePointsCalculation") ?? true),
        version: Number(tableSnap.get("version") ?? 0),
    };
}
function toTableHands(docs) {
    return docs
        .map((d) => ({
        handId: Number(d.get("handId")),
        playerWinnerId: String(d.get("playerWinnerId") ?? ""),
        playerLooserId: String(d.get("playerLooserId") ?? ""),
        handScore: String(d.get("handScore") ?? ""),
        isChickenHand: Boolean(d.get("isChickenHand") ?? false),
        isDone: Boolean(d.get("isDone") ?? false),
        playerEastPenalty: String(d.get("playerEastPenalty") ?? ""),
        playerSouthPenalty: String(d.get("playerSouthPenalty") ?? ""),
        playerWestPenalty: String(d.get("playerWestPenalty") ?? ""),
        playerNorthPenalty: String(d.get("playerNorthPenalty") ?? ""),
    }))
        .sort((a, b) => a.handId - b.handId);
}
/** Hands are created lazily to keep tournament creation write volume manageable. Returns the new hands without a second read. */
async function createDefaultHands(handsCollection) {
    const batch = firebase_1.db.batch();
    const hands = [];
    for (let handId = 1; handId <= 16; handId++) {
        const hand = {
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
        hands.push(hand);
        batch.set(handsCollection.doc(String(handId)), {
            ...hand,
            createdAt: firestore_1.FieldValue.serverTimestamp(),
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
    await batch.commit();
    return hands;
}
async function readHands(tableRef, handsSnap) {
    return handsSnap.empty
        ? createDefaultHands(tableRef.collection("hands"))
        : toTableHands(handsSnap.docs);
}
async function getTableWithHands(params) {
    const tableDocId = `${params.roundId}_${params.tableId}`;
    const tableRef = firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
    // The table and its hands are independent reads, so run them together.
    const [tableSnap, handsSnap] = await Promise.all([tableRef.get(), tableRef.collection("hands").get()]);
    if (!tableSnap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    return { table: toTableState(tableSnap), hands: await readHands(tableRef, handsSnap) };
}
/** Reads every table of a round with its hands in one call, so the client needs one request instead of one per table. */
async function getRoundTablesWithHands(params) {
    const tablesSnap = await firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables")
        .where("roundId", "==", params.roundId)
        .get();
    const tables = await Promise.all(tablesSnap.docs.map(async (tableSnap) => {
        const handsSnap = await tableSnap.ref.collection("hands").get();
        return { table: toTableState(tableSnap), hands: await readHands(tableSnap.ref, handsSnap) };
    }));
    return tables.sort((a, b) => a.table.tableId - b.table.tableId);
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
    const useTotalsOnly = Boolean(table.useTotalsOnly ?? true);
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
class StaleTableVersion extends Error {
    constructor(expectedVersion, currentVersion) {
        super("Stale table version");
        this.expectedVersion = expectedVersion;
        this.currentVersion = currentVersion;
    }
}
async function saveTableState(params) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const tableRef = tournamentRef.collection("tables").doc(`${params.roundId}_${params.tableId}`);
    const handRefs = params.handPatches.map((item) => tableRef.collection("hands").doc(String(item.handId)));
    try {
        await firebase_1.db.runTransaction(async (transaction) => {
            const tableSnapshot = await transaction.get(tableRef);
            if (!tableSnapshot.exists)
                throw (0, httpError_1.notFound)("Table not found");
            const currentVersion = Number(tableSnapshot.get("version") ?? 0);
            if (params.expectedVersion != null && params.expectedVersion !== currentVersion) {
                throw new StaleTableVersion(params.expectedVersion, currentVersion);
            }
            const currentHands = new Map();
            const allHandsSnapshot = await transaction.get(tableRef.collection("hands"));
            const incompleteTables = await transaction.get(tournamentRef.collection("tables").where("isCompleted", "==", false));
            allHandsSnapshot.docs.forEach((snapshot) => currentHands.set(snapshot.id, snapshot.data()));
            if (params.handPatches.some((item) => !currentHands.has(String(item.handId)))) {
                throw (0, httpError_1.notFound)("Hand not found");
            }
            const mergedTable = { ...(tableSnapshot.data() ?? {}), ...params.tablePatch };
            params.handPatches.forEach((item) => {
                const key = String(item.handId);
                currentHands.set(key, { ...(currentHands.get(key) ?? {}), ...item.patch, handId: item.handId });
            });
            const summary = calculateTableSummary(mergedTable, [...currentHands.values()]);
            const currentTableIsComplete = Boolean(mergedTable.isCompleted);
            const tournamentIsComplete = isTournamentCompleteAfterTableSave({
                currentTablePath: tableRef.path,
                currentTableIsComplete,
                incompleteTablePaths: incompleteTables.docs.map((document) => document.ref.path),
            });
            transaction.update(tableRef, {
                ...params.tablePatch,
                ...summary,
                version: currentVersion + 1,
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
            params.handPatches.forEach((item, index) => {
                transaction.update(handRefs[index], {
                    ...item.patch,
                    updatedAt: firestore_1.FieldValue.serverTimestamp(),
                });
            });
            transaction.update(tournamentRef, {
                isCompleted: tournamentIsComplete,
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
                ...(0, dataVersionsService_1.tournamentVersionUpdate)("tables"),
            });
            transaction.set(firebase_1.db.collection("_meta").doc("dataVersions"), (0, dataVersionsService_1.globalVersionUpdate)("tournaments"), { merge: true });
        });
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
    return getTableWithHands(params);
}
async function updateTable(params) {
    await saveTableState({
        ...params,
        expectedVersion: null,
        tablePatch: params.patch,
        handPatches: [],
    });
}
async function updateHand(params) {
    await saveTableState({
        tournamentId: params.tournamentId,
        roundId: params.roundId,
        tableId: params.tableId,
        expectedVersion: null,
        tablePatch: {},
        handPatches: [{ handId: params.handId, patch: params.patch }],
    });
}
async function resetTable(params) {
    const tournamentRef = firebase_1.db.collection("tournaments").doc(params.tournamentId);
    const tableDocId = `${params.roundId}_${params.tableId}`;
    const tableRef = tournamentRef.collection("tables").doc(tableDocId);
    const [tableSnap, handsSnap] = await Promise.all([
        tableRef.get(),
        tableRef.collection("hands").get(),
    ]);
    if (!tableSnap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    const batch = firebase_1.db.batch();
    batch.update(tableRef, {
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
        useTotalsOnly: true,
        usePointsCalculation: true,
        hasProgress: false,
        hasValidManualTotals: false,
        completionStatus: "empty",
        bestHandScores: [],
        chickenHandCount: 0,
        version: firestore_1.FieldValue.increment(1),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    handsSnap.docs.forEach((hand) => {
        batch.update(hand.ref, {
            playerWinnerId: "",
            playerLooserId: "",
            handScore: "",
            isChickenHand: false,
            isDone: false,
            playerEastPenalty: "",
            playerSouthPenalty: "",
            playerWestPenalty: "",
            playerNorthPenalty: "",
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    });
    batch.update(tournamentRef, {
        isCompleted: false,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
        ...(0, dataVersionsService_1.tournamentVersionUpdate)("tables"),
    });
    batch.set(firebase_1.db.collection("_meta").doc("dataVersions"), (0, dataVersionsService_1.globalVersionUpdate)("tournaments"), { merge: true });
    await batch.commit();
}
//# sourceMappingURL=tableManagerService.js.map