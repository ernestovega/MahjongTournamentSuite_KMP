"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const statisticsDigest_1 = require("./statisticsDigest");
const table = (extra = {}) => ({
    roundId: 1, tableId: 1, isCompleted: true, useTotalsOnly: false,
    playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
    playerEastScore: "0", playerSouthScore: "0", playerWestScore: "0", playerNorthScore: "0",
    playerEastPoints: "0", playerSouthPoints: "0", playerWestPoints: "0", playerNorthPoints: "0",
    ...extra,
});
const hand = (handId, winner, loser, score, extra = {}) => ({
    handId, playerWinnerId: winner, playerLooserId: loser, handScore: score, isChickenHand: false, isDone: true,
    playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", ...extra,
});
const emaBySlot = new Map([[1, "EMA1"], [2, "EMA2"]]);
/** The digest of one table. The tests read the results and the table totals from it. */
function digestOf(tableData, hands) {
    const digest = (0, statisticsDigest_1.buildTableDigest)({ tournamentId: "t1", table: tableData, hands, emaBySlot });
    return { results: digest.results, tables: [digest.summary] };
}
(0, node_test_1.default)("digest counts wins, deal-ins, self-draws and draws like the domain use case", () => {
    const digest = digestOf(table(), [
        hand(1, "1", "2", "10"),
        hand(2, "1", "", "8", { isChickenHand: true }),
        hand(3, "", "", "0"),
        hand(4, "3", "-", "20"),
    ]);
    const first = digest.results.find((result) => result.slotId === 1);
    const second = digest.results.find((result) => result.slotId === 2);
    const third = digest.results.find((result) => result.slotId === 3);
    strict_1.default.equal(first.emaId, "EMA1");
    strict_1.default.equal(third.emaId, "");
    strict_1.default.equal(first.handsPlayed, 4);
    strict_1.default.equal(first.handsWon, 2);
    strict_1.default.equal(first.chickenHands, 1);
    strict_1.default.equal(second.dealIns, 1);
    strict_1.default.equal(second.dealInScoreSum, 10);
    strict_1.default.equal(third.selfDraws, 1);
    // Hand 1: seat 1 wins on seat 2. Seats 3 and 4 only pay the base value.
    strict_1.default.equal(third.otherWins, 1);
    strict_1.default.equal(second.lostToSelfDraws, 2);
    strict_1.default.equal(digest.tables[0].drawnHands, 1);
    strict_1.default.equal(digest.tables[0].bestHand, 20);
    strict_1.default.equal(digest.tables[0].winningHands, 3);
});
(0, node_test_1.default)("a table with only totals gives results without hand values", () => {
    const digest = digestOf(table({
        useTotalsOnly: true,
        playerEastScore: "20", playerSouthScore: "20", playerWestScore: "0", playerNorthScore: "-40",
        playerEastPoints: "3", playerSouthPoints: "3", playerWestPoints: "1", playerNorthPoints: "0",
    }), []);
    strict_1.default.deepEqual(digest.results.map((result) => result.position), [1, 1, 3, 4]);
    strict_1.default.equal(digest.results[0].points, 3);
    strict_1.default.equal(digest.results[0].hasHands, false);
    strict_1.default.equal(digest.tables[0].hasHands, false);
});
(0, node_test_1.default)("an open table gives a table entry and no results", () => {
    const digest = digestOf(table({ isCompleted: false }), [hand(1, "1", "2", "10")]);
    strict_1.default.equal(digest.results.length, 0);
    strict_1.default.equal(digest.tables.length, 1);
    strict_1.default.equal(digest.tables[0].isCompleted, false);
});
(0, node_test_1.default)("digest keeps rival tallies and hand runs like the domain use case", () => {
    const digest = digestOf(table({ playerEastScore: "30", playerSouthScore: "-10", playerWestScore: "0", playerNorthScore: "-20" }), [
        hand(1, "1", "2", "10"),
        hand(2, "1", "-", "8"),
        hand(3, "3", "1", "10"),
        hand(4, "1", "2", "8"),
    ]);
    const first = digest.results.find((result) => result.slotId === 1);
    const second = digest.results.find((result) => result.slotId === 2);
    strict_1.default.deepEqual(first.winRun, { best: 2, start: 2, end: 1 });
    strict_1.default.deepEqual(second.noDealInRun, { best: 2, start: 0, end: 0 });
    strict_1.default.equal(second.rivals.length, 3);
    strict_1.default.deepEqual(second.rivals.find((rival) => rival.opponent === "EMA1"), {
        opponent: "EMA1", tablesBehind: 1, discardLosses: 2, selfDrawLosses: 1, othersDiscardWins: 0,
    });
    // Player 3 won a hand on a discard of player 1. For player 2 this is a win on another player's discard.
    strict_1.default.equal(second.rivals.find((rival) => rival.opponent === "t1#3")?.othersDiscardWins, 1);
    // A slot without an EMA number is named by the tournament and the slot.
    strict_1.default.ok(second.rivals.some((rival) => rival.opponent === "t1#3"));
});
(0, node_test_1.default)("digest keeps the biggest single penalty of a seat", () => {
    const digest = digestOf(table({ playerEastScore: "30", playerSouthScore: "-10", playerWestScore: "0", playerNorthScore: "-20" }), [
        hand(1, "1", "2", "10", { playerSouthPenalty: "-10;-20" }),
        hand(2, "1", "2", "8", { playerSouthPenalty: "-5", playerWestPenalty: "3" }),
    ]);
    const second = digest.results.find((result) => result.slotId === 2);
    const third = digest.results.find((result) => result.slotId === 3);
    strict_1.default.equal(second.maxPenalty, 20);
    strict_1.default.equal(second.penaltyPoints, 35);
    // A positive value is not a penalty.
    strict_1.default.equal(third.maxPenalty, 0);
});
//# sourceMappingURL=statisticsDigest.test.js.map