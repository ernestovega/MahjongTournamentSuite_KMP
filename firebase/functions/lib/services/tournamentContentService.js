"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.listTournamentPlayers = listTournamentPlayers;
exports.listTournamentRounds = listTournamentRounds;
exports.listTournamentTables = listTournamentTables;
const firebase_1 = require("../firebase");
async function listTournamentPlayers(tournamentId) {
    const snap = await firebase_1.db.collection("tournaments").doc(tournamentId).collection("players").get();
    return snap.docs
        .map((d) => ({
        id: Number(d.get("id")),
        name: String(d.get("name") ?? ""),
        team: Number(d.get("team") ?? 0),
    }))
        .sort((a, b) => a.id - b.id);
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
    return snap.docs
        .map((d) => ({
        roundId: Number(d.get("roundId")),
        tableId: Number(d.get("tableId")),
        playerIds: (d.get("playerIds") ?? []).map((x) => Number(x)),
        isCompleted: Boolean(d.get("isCompleted") ?? false),
        useTotalsOnly: Boolean(d.get("useTotalsOnly") ?? false),
    }))
        .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
//# sourceMappingURL=tournamentContentService.js.map