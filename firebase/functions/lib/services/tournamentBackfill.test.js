"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tournamentBackfill_1 = require("./tournamentBackfill");
const tableManagerService_1 = require("./tableManagerService");
const empty = { id: "1_1", data: { isCompleted: false }, hands: [(0, tableManagerService_1.defaultHand)(1)] };
(0, node_test_1.default)("a tournament without data needs no counter value", () => {
    strict_1.default.deepEqual((0, tournamentBackfill_1.planTournamentBackfill)([{ ...empty, hands: fullHands }]), { tablesWithProgress: 0, missingHands: [] });
});
(0, node_test_1.default)("tables with typed data or hand data count as started", () => {
    const plan = (0, tournamentBackfill_1.planTournamentBackfill)([
        { id: "1_1", data: { playerEastId: "3" }, hands: [(0, tableManagerService_1.defaultHand)(1)] },
        { id: "1_2", data: {}, hands: [{ ...(0, tableManagerService_1.defaultHand)(1), handScore: "8" }] },
        { id: "1_3", data: { isCompleted: true }, hands: [(0, tableManagerService_1.defaultHand)(1)] },
        empty,
    ]);
    strict_1.default.equal(plan.tablesWithProgress, 3);
});
const fullHands = Array.from({ length: 16 }, (_, index) => (0, tableManagerService_1.defaultHand)(index + 1));
(0, node_test_1.default)("a table with all 16 hands needs nothing", () => {
    strict_1.default.deepEqual((0, tournamentBackfill_1.planTournamentBackfill)([{ id: "1_1", data: {}, hands: fullHands }]).missingHands, []);
});
(0, node_test_1.default)("a table without hands needs all 16", () => {
    const plan = (0, tournamentBackfill_1.planTournamentBackfill)([empty, { id: "2_1", data: {}, hands: [] }]);
    strict_1.default.deepEqual(plan.missingHands.map((entry) => entry.tableId), ["1_1", "2_1"]);
    strict_1.default.deepEqual(plan.missingHands[1].handIds, Array.from({ length: 16 }, (_, index) => index + 1));
});
(0, node_test_1.default)("a table with only some hands needs the others", () => {
    const some = [1, 2, 5, 16].map(tableManagerService_1.defaultHand);
    const plan = (0, tournamentBackfill_1.planTournamentBackfill)([{ id: "3_4", data: {}, hands: some }]);
    strict_1.default.deepEqual(plan.missingHands, [{ tableId: "3_4", handIds: [3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15] }]);
});
(0, node_test_1.default)("table state reads stored fields with safe defaults", () => {
    const table = (0, tableManagerService_1.tableStateFromData)({ roundId: 2, tableId: 3, playerIds: ["1", 2, 3, 4], version: 5 });
    strict_1.default.deepEqual(table.playerIds, [1, 2, 3, 4]);
    strict_1.default.equal(table.version, 5);
    strict_1.default.equal(table.playerEastId, "");
    strict_1.default.equal(table.useTotalsOnly, false);
    strict_1.default.equal(table.usePointsCalculation, true);
    strict_1.default.equal(table.isCompleted, false);
});
(0, node_test_1.default)("hands are sorted by id", () => {
    const hands = (0, tableManagerService_1.handsFromData)([{ handId: 3 }, { handId: 1 }, { handId: 2, handScore: 8 }]);
    strict_1.default.deepEqual(hands.map((hand) => hand.handId), [1, 2, 3]);
    strict_1.default.equal(hands[1].handScore, "8");
});
//# sourceMappingURL=tournamentBackfill.test.js.map