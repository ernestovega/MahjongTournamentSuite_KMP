"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.runTournamentIntegrityCheck = runTournamentIntegrityCheck;
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const playersService_1 = require("./playersService");
const tournamentIntegrity_1 = require("./tournamentIntegrity");
/** Loads one tournament and runs every integrity check. It only reads data. */
async function runTournamentIntegrityCheck(tournamentId) {
    const ref = firebase_1.db.collection("tournaments").doc(tournamentId);
    const [tournament, players, teams, rounds, tables] = await Promise.all([
        ref.get(), ref.collection("players").get(), ref.collection("teams").get(),
        ref.collection("rounds").get(), ref.collection("tables").get(),
    ]);
    if (!tournament.exists)
        throw (0, httpError_1.notFound)("Tournament not found");
    const tableEntries = [];
    const queue = [...tables.docs];
    // A few reads at a time keep a 550 table tournament fast without flooding the database.
    await Promise.all(Array.from({ length: 12 }, async () => {
        while (queue.length > 0) {
            const table = queue.shift();
            // eslint-disable-next-line no-await-in-loop
            const hands = await table.ref.collection("hands").get();
            tableEntries.push({
                id: table.id,
                data: table.data(),
                hands: hands.docs.map((hand) => ({ id: hand.id, data: hand.data() })),
            });
        }
    }));
    tableEntries.sort((a, b) => a.id.localeCompare(b.id, undefined, { numeric: true }));
    const emaIds = [...new Set(players.docs.map((slot) => String(slot.get("assignedEmaId") ?? "").trim()).filter((id) => id !== ""))];
    const registry = new Map();
    if (emaIds.length > 0) {
        const documents = await firebase_1.db.getAll(...emaIds.map((id) => firebase_1.db.collection(playersService_1.EMA_PLAYER_REGISTRY_COLLECTION).doc(id)));
        documents.forEach((document) => {
            registry.set(document.id, document.exists ? { country: String(document.get("country") ?? "").trim().toUpperCase() } : null);
        });
    }
    return (0, tournamentIntegrity_1.checkTournamentIntegrity)(tournamentId, {
        tournament: tournament.data() ?? {},
        players: players.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
        teams: teams.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
        rounds: rounds.docs.map((doc) => ({ id: doc.id, data: doc.data() })),
        tables: tableEntries,
        registry,
    });
}
//# sourceMappingURL=tournamentIntegrityService.js.map