"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listTournamentPlayers = listTournamentPlayers;
exports.assignTournamentPlayer = assignTournamentPlayer;
exports.listTournamentRounds = listTournamentRounds;
exports.listTournamentTables = listTournamentTables;
const firestore_1 = require("firebase-admin/firestore");
const firebase_1 = require("../firebase");
function timestampToIso(value) {
    return value instanceof firestore_1.Timestamp ? value.toDate().toISOString() : null;
}
function hasValidTotals(values, expectedTotal, allowDecimal = false) {
    const pattern = allowDecimal ? /^-?\d+(?:[,.]\d+)?$/ : /^-?\d+$/;
    if (values.length !== 4 || !values.every((value) => pattern.test(String(value ?? "").trim()))) {
        return false;
    }
    const total = values.reduce((sum, value) => sum + Number(String(value).replace(",", ".")), 0);
    return Math.abs(total - expectedTotal) < 0.011;
}
async function listTournamentPlayers(tournamentId) {
    const snap = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("players").get();
    return snap.docs
        .map((d) => ({
        id: Number(d.get("id")),
        name: String(d.get("name") ?? ""),
        team: Number(d.get("team") ?? 0),
        country: String(d.get("country") ?? ""),
        assignedEmaId: typeof d.get("assignedEmaId") === "string" ? d.get("assignedEmaId") : null,
        createdAt: timestampToIso(d.get("createdAt")),
        updatedAt: timestampToIso(d.get("updatedAt")),
    }))
        .sort((a, b) => a.id - b.id);
}
async function assignTournamentPlayer(params) {
    const playerRef = firebase_1.db.collection("tournaments").doc(params.tournamentId)
        .collection("players").doc(String(params.playerId));
    const player = await playerRef.get();
    if (!player.exists) {
        throw new Error("Player not found");
    }
    await playerRef.update({
        assignedEmaId: params.emaId,
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
}
async function listTournamentRounds(tournamentId) {
    const snap = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("rounds").get();
    return snap.docs
        .map((d) => ({
        roundId: Number(d.get("roundId")),
    }))
        .sort((a, b) => a.roundId - b.roundId);
}
async function listTournamentTables(tournamentId, roundId) {
    const collection = firebase_1.db.collection("tournaments").doc(tournamentId).collection("tables");
    const snap = roundId == null
        ? await collection.get()
        : await collection.where("roundId", "==", roundId).get();
    const tables = await Promise.all(snap.docs.map(async (d) => {
        const hands = await d.ref.collection("hands").get();
        const hasHandProgress = hands.docs.some((hand) => {
            const value = hand.data();
            return [
                value.playerWinnerId,
                value.playerLooserId,
                value.handScore,
                value.playerEastPenalty,
                value.playerSouthPenalty,
                value.playerWestPenalty,
                value.playerNorthPenalty,
            ].some((field) => String(field ?? "").trim().length > 0)
                || Boolean(value.isChickenHand)
                || Boolean(value.isDone);
        });
        const hasTableProgress = [
            d.get("playerEastId"),
            d.get("playerSouthId"),
            d.get("playerWestId"),
            d.get("playerNorthId"),
            d.get("playerEastScore"),
            d.get("playerSouthScore"),
            d.get("playerWestScore"),
            d.get("playerNorthScore"),
            d.get("playerEastPoints"),
            d.get("playerSouthPoints"),
            d.get("playerWestPoints"),
            d.get("playerNorthPoints"),
        ].some((field) => String(field ?? "").trim().length > 0);
        const hasValidManualScores = hasValidTotals([
            d.get("manualPlayerEastScore") || d.get("playerEastScore"),
            d.get("manualPlayerSouthScore") || d.get("playerSouthScore"),
            d.get("manualPlayerWestScore") || d.get("playerWestScore"),
            d.get("manualPlayerNorthScore") || d.get("playerNorthScore"),
        ], 0);
        const hasValidManualPoints = hasValidTotals([
            d.get("manualPlayerEastPoints") || d.get("playerEastPoints"),
            d.get("manualPlayerSouthPoints") || d.get("playerSouthPoints"),
            d.get("manualPlayerWestPoints") || d.get("playerWestPoints"),
            d.get("manualPlayerNorthPoints") || d.get("playerNorthPoints"),
        ], 7, true);
        const useTotalsOnly = Boolean(d.get("useTotalsOnly") ?? true);
        const usePointsCalculation = Boolean(d.get("usePointsCalculation") ?? true);
        return {
            roundId: Number(d.get("roundId")),
            tableId: Number(d.get("tableId")),
            playerIds: (d.get("playerIds") ?? []).map((x) => Number(x)),
            isCompleted: Boolean(d.get("isCompleted") ?? false),
            useTotalsOnly,
            usePointsCalculation,
            hasProgress: hasTableProgress || hasHandProgress || Boolean(d.get("isCompleted") ?? false),
            hasValidManualTotals: useTotalsOnly ? hasValidManualScores : !usePointsCalculation && hasValidManualPoints,
        };
    }));
    return tables
        .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
//# sourceMappingURL=tournamentContentService.js.map