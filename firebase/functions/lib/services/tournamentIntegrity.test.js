"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentIntegrity_1 = require("./tournamentIntegrity");
const hand = (handId, extra = {}) => ({
    id: String(handId),
    data: {
        handId, playerWinnerId: "", playerLooserId: "", handScore: "", isChickenHand: false, isDone: false,
        playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", ...extra,
    },
});
const hands = (overrides = []) => {
    const list = Array.from({ length: 16 }, (_, index) => hand(index + 1));
    overrides.forEach((item) => { list[Number(item.id) - 1] = item; });
    return list;
};
function valid() {
    return {
        tournament: {
            name: "Test", shortName: "T", numPlayers: 4, numRounds: 1, eventStartDate: "2026-10-01", eventEndDate: "2026-10-02",
            hostCity: "Madrid", hostCountry: "ES", venueName: "Hall", venueAddress: "Street 1",
            roundSchedules: [{ roundId: 1, date: "2026-10-01", startTime: "09:00" }], agendaItems: [],
            isTeams: false, isCompleted: false, tablesWithProgress: 0,
            countBestHands: true, countChickenHands: true,
        },
        players: [1, 2, 3, 4].map((id) => ({ id: String(id), data: { id, name: `P${id}`, team: 1, assignedEmaId: `0000000${id}` } })),
        teams: [],
        rounds: [{ id: "1", data: { roundId: 1 } }],
        tables: [{
                id: "1_1",
                data: {
                    roundId: 1, tableId: 1, playerIds: [1, 2, 3, 4], version: 0, isCompleted: false, useTotalsOnly: false,
                    playerEastId: "", playerSouthId: "", playerWestId: "", playerNorthId: "",
                    playerEastScore: "", playerSouthScore: "", playerWestScore: "", playerNorthScore: "",
                    playerEastPoints: "", playerSouthPoints: "", playerWestPoints: "", playerNorthPoints: "",
                    manualPlayerEastScore: "", manualPlayerSouthScore: "", manualPlayerWestScore: "", manualPlayerNorthScore: "",
                    completionStatus: "empty", hasProgress: false, chickenHandCount: 0, bestHandScores: [],
                },
                hands: hands(),
            }],
        registry: new Map([1, 2, 3, 4].map((id) => [`0000000${id}`, { country: "ES" }])),
    };
}
const codes = (input) => (0, tournamentIntegrity_1.checkTournamentIntegrity)("t", input).findings.map((finding) => finding.code);
(0, node_test_1.default)("a clean tournament has no errors or warnings", () => {
    const report = (0, tournamentIntegrity_1.checkTournamentIntegrity)("t", valid());
    strict_1.default.equal(report.counts.errors, 0);
    strict_1.default.equal(report.counts.warnings, 0);
});
(0, node_test_1.default)("an assigned player that is not in the registry is an error", () => {
    const input = valid();
    input.registry.set("00000002", null);
    strict_1.default.ok(codes(input).includes("players.notInRegistry"));
});
(0, node_test_1.default)("a duplicate EMA assignment and an unassigned slot are errors", () => {
    const input = valid();
    input.players[1].data.assignedEmaId = "00000001";
    input.players[2].data.assignedEmaId = "";
    const found = codes(input);
    strict_1.default.ok(found.includes("players.duplicateEma"));
    strict_1.default.ok(found.includes("players.unassigned"));
});
(0, node_test_1.default)("saved scores that differ from the hands are an error", () => {
    const input = valid();
    const table = input.tables[0];
    Object.assign(table.data, {
        playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
        playerEastScore: "10", playerSouthScore: "-10", playerWestScore: "0", playerNorthScore: "0",
        isCompleted: true, completionStatus: "completed", hasProgress: true,
    });
    table.hands = hands([hand(1, { playerWinnerId: "1", playerLooserId: "2", handScore: "10", isDone: true })]);
    input.tournament.isCompleted = true;
    input.tournament.tablesWithProgress = 1;
    strict_1.default.ok(codes(input).includes("tables.totalsMismatch"));
});
(0, node_test_1.default)("a player at two tables in one round is an error", () => {
    const input = valid();
    input.tables.push({ ...input.tables[0], id: "1_2", data: { ...input.tables[0].data, tableId: 2 } });
    input.tournament.numPlayers = 8;
    const found = codes(input);
    strict_1.default.ok(found.includes("tables.playerTwice"));
});
(0, node_test_1.default)("a wrong tournament completion flag is an error", () => {
    const input = valid();
    input.tournament.isCompleted = true;
    strict_1.default.ok(codes(input).includes("counters.isCompleted"));
});
//# sourceMappingURL=tournamentIntegrity.test.js.map