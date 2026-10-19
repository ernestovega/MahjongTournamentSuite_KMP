"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const handScoring_1 = require("./handScoring");
const table = {
    playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
    isCompleted: false, useTotalsOnly: false,
};
const hand = (patch) => ({
    playerWinnerId: "", playerLooserId: "", handScore: "", isChickenHand: false, isDone: true,
    playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", ...patch,
});
(0, node_test_1.default)("seats need four different ids", () => {
    strict_1.default.deepEqual((0, handScoring_1.seatIdsOf)(table), ["1", "2", "3", "4"]);
    strict_1.default.equal((0, handScoring_1.seatIdsOf)({ ...table, playerNorthId: "3" }), null);
    strict_1.default.equal((0, handScoring_1.seatIdsOf)({ ...table, playerNorthId: "" }), null);
});
(0, node_test_1.default)("a discarded hand gives the winner the score plus 8 from each loser share", () => {
    // Winner east with 10, loser south: east 10 + 24, south -(10 + 8), others -8.
    strict_1.default.deepEqual((0, handScoring_1.handSeatScores)(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "10" }), ["1", "2", "3", "4"]), [34, -18, -8, -8]);
});
(0, node_test_1.default)("a self drawn hand takes score plus 8 from every other seat", () => {
    strict_1.default.deepEqual((0, handScoring_1.handSeatScores)(hand({ playerWinnerId: "3", handScore: "9" }), ["1", "2", "3", "4"]), [-17, -17, 51, -17]);
});
(0, node_test_1.default)("penalties add to the seats and several rows are summed", () => {
    strict_1.default.deepEqual((0, handScoring_1.handSeatScores)(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "8", playerNorthPenalty: "-10;-5" }), ["1", "2", "3", "4"]), [32, -16, -8, -23]);
});
(0, node_test_1.default)("a hand below the minimum does not count", () => {
    strict_1.default.equal((0, handScoring_1.isResultSelectionInvalid)(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "7" })), true);
    strict_1.default.equal((0, handScoring_1.handSeatScores)(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "7" }), ["1", "2", "3", "4"]), null);
});
(0, node_test_1.default)("a chicken hand cannot score more than 12", () => {
    strict_1.default.equal((0, handScoring_1.isResultSelectionInvalid)(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "13", isChickenHand: true })), true);
    strict_1.default.equal((0, handScoring_1.isResultSelectionInvalid)(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "12", isChickenHand: true })), false);
});
(0, node_test_1.default)("only done hands count unless the table is completed", () => {
    const hands = [
        hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "8" }),
        hand({ playerWinnerId: "3", playerLooserId: "4", handScore: "8", isDone: false }),
    ];
    strict_1.default.deepEqual((0, handScoring_1.handScoreTotals)(table, hands), [32, -16, -8, -8]);
    strict_1.default.deepEqual((0, handScoring_1.handScoreTotals)({ ...table, isCompleted: true }, hands), [24, -24, 24, -24]);
});
(0, node_test_1.default)("points follow the place and share places on ties", () => {
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["30", "10", "0", "-40"]), ["4", "2", "1", "0"]);
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["20", "20", "0", "-40"]), ["3", "3", "1", "0"]);
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["10", "10", "10", "10"]), ["1,75", "1,75", "1,75", "1,75"]);
    strict_1.default.equal((0, handScoring_1.pointsFromScores)(["1", "", "2", "3"]), null);
});
(0, node_test_1.default)("a substitute with forced 0 points leaves 4, 2 and 1 to the other three seats", () => {
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["50", "30", "10", "-90"], 1), ["4", "0", "2", "1"]);
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["50", "30", "10", "-90"], 0), ["0", "4", "2", "1"]);
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["20", "20", "0", "-40"], 3), ["3", "3", "1", "0"]);
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["20", "0", "0", "-40"], 3), ["4", "1,5", "1,5", "0"]);
    strict_1.default.deepEqual((0, handScoring_1.pointsFromScores)(["5", "5", "5", "-15"], 3), ["2,33", "2,33", "2,33", "0"]);
    strict_1.default.equal((0, handScoring_1.pointsFromScores)(["1", "", "2", "3"], 0), null);
});
(0, node_test_1.default)("the table finds the forced seat by the slot id", () => {
    const forced = {
        playerEastId: "5", playerSouthId: "6", playerWestId: "7", playerNorthId: "8", forcedZeroPlayerId: "6",
        playerEastScore: "30", playerSouthScore: "90", playerWestScore: "-10", playerNorthScore: "-110",
    };
    strict_1.default.deepEqual((0, handScoring_1.tablePointsOf)(forced), {
        playerEastPoints: "4", playerSouthPoints: "0", playerWestPoints: "2", playerNorthPoints: "1",
    });
    strict_1.default.equal((0, handScoring_1.tablePointsOf)({ ...forced, forcedZeroPlayerId: "99" }).playerSouthPoints, "4");
});
(0, node_test_1.default)("the table patch has scores and points of the counted hands", () => {
    const patch = (0, handScoring_1.tableTotalsPatch)(table, [hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "8" })]);
    strict_1.default.deepEqual(patch, {
        playerEastScore: "32", playerSouthScore: "-16", playerWestScore: "-8", playerNorthScore: "-8",
        playerEastPoints: "4", playerSouthPoints: "0", playerWestPoints: "1,5", playerNorthPoints: "1,5",
    });
});
//# sourceMappingURL=handScoring.test.js.map