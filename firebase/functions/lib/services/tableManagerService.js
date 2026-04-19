"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.getTableWithHands = getTableWithHands;
exports.updateTable = updateTable;
exports.updateHand = updateHand;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
async function getTableWithHands(params) {
    const tableDocId = `${params.roundId}_${params.tableId}`;
    const tableRef = firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
    const tableSnap = await tableRef.get();
    if (!tableSnap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    const handsCollection = tableRef.collection("hands");
    let handsSnap = await handsCollection.get();
    if (handsSnap.empty) {
        // Hands are created lazily to keep tournament creation write volume manageable.
        const batch = firebase_1.db.batch();
        for (let handId = 1; handId <= 16; handId++) {
            const handRef = handsCollection.doc(String(handId));
            batch.set(handRef, {
                handId,
                playerWinnerId: "",
                playerLooserId: "",
                handScore: "",
                isChickenHand: false,
                playerEastPenalty: "",
                playerSouthPenalty: "",
                playerWestPenalty: "",
                playerNorthPenalty: "",
                createdAt: firestore_1.FieldValue.serverTimestamp(),
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
        }
        await batch.commit();
        handsSnap = await handsCollection.get();
    }
    const table = {
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
        useTotalsOnly: Boolean(tableSnap.get("useTotalsOnly") ?? false),
        usePointsCalculation: Boolean(tableSnap.get("usePointsCalculation") ?? true),
    };
    const hands = handsSnap.docs
        .map((d) => ({
        handId: Number(d.get("handId")),
        playerWinnerId: String(d.get("playerWinnerId") ?? ""),
        playerLooserId: String(d.get("playerLooserId") ?? ""),
        handScore: String(d.get("handScore") ?? ""),
        isChickenHand: Boolean(d.get("isChickenHand") ?? false),
        playerEastPenalty: String(d.get("playerEastPenalty") ?? ""),
        playerSouthPenalty: String(d.get("playerSouthPenalty") ?? ""),
        playerWestPenalty: String(d.get("playerWestPenalty") ?? ""),
        playerNorthPenalty: String(d.get("playerNorthPenalty") ?? ""),
    }))
        .sort((a, b) => a.handId - b.handId);
    return { table, hands };
}
async function updateTable(params) {
    const tableDocId = `${params.roundId}_${params.tableId}`;
    const tableRef = firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
    const snap = await tableRef.get();
    if (!snap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    await tableRef.update({
        ...params.patch,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    if (Object.prototype.hasOwnProperty.call(params.patch, "isCompleted")) {
        const tournamentRef = firebase_1.db.collection("tournaments").doc(params.tournamentId);
        const newIsCompleted = Boolean(params.patch.isCompleted);
        if (!newIsCompleted) {
            await tournamentRef.update({
                isCompleted: false,
                updatedAt: firestore_1.FieldValue.serverTimestamp(),
            });
            return;
        }
        const incomplete = await tournamentRef
            .collection("tables")
            .where("isCompleted", "==", false)
            .limit(1)
            .get();
        await tournamentRef.update({
            isCompleted: incomplete.empty,
            updatedAt: firestore_1.FieldValue.serverTimestamp(),
        });
    }
}
async function updateHand(params) {
    const tableDocId = `${params.roundId}_${params.tableId}`;
    const tableRef = firebase_1.db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
    const tableSnap = await tableRef.get();
    if (!tableSnap.exists)
        throw (0, httpError_1.notFound)("Table not found");
    const handRef = tableRef.collection("hands").doc(String(params.handId));
    const handSnap = await handRef.get();
    if (!handSnap.exists)
        throw (0, httpError_1.notFound)("Hand not found");
    await handRef.update({
        ...params.patch,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
}
//# sourceMappingURL=tableManagerService.js.map