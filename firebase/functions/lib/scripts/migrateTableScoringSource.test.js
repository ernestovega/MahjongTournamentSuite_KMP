"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const migrateTableScoringSource_1 = require("./migrateTableScoringSource");
(0, node_test_1.default)("report conversion keeps hands outside the table patch", () => {
    const result = (0, migrateTableScoringSource_1.convertForcedPointsTable)({
        usePointsCalculation: false,
        playerEastScore: "32000",
        playerSouthScore: "28000",
        playerWestScore: "22000",
        playerNorthScore: "18000",
        hands: [{ handId: 1, handScore: "8" }],
    });
    strict_1.default.deepEqual(result.patch, {
        useTotalsOnly: true,
        usePointsCalculation: true,
        manualPlayerEastScore: "32000",
        playerEastScore: "32000",
        manualPlayerEastPoints: "4",
        playerEastPoints: "4",
        manualPlayerSouthScore: "28000",
        playerSouthScore: "28000",
        manualPlayerSouthPoints: "2",
        playerSouthPoints: "2",
        manualPlayerWestScore: "22000",
        playerWestScore: "22000",
        manualPlayerWestPoints: "1",
        playerWestPoints: "1",
        manualPlayerNorthScore: "18000",
        playerNorthScore: "18000",
        manualPlayerNorthPoints: "0",
        playerNorthPoints: "0",
    });
});
(0, node_test_1.default)("report conversion skips forced points without four integer scores", () => {
    const result = (0, migrateTableScoringSource_1.convertForcedPointsTable)({
        usePointsCalculation: false,
        playerEastPoints: "4",
        playerSouthPoints: "2",
        playerWestPoints: "1",
        playerNorthPoints: "0",
    });
    strict_1.default.equal(result.patch, null);
    strict_1.default.match(result.reason ?? "", /four integer scores/);
});
(0, node_test_1.default)("already calculated tables are unchanged", () => {
    strict_1.default.deepEqual((0, migrateTableScoringSource_1.convertForcedPointsTable)({ usePointsCalculation: true }), { patch: null });
});
//# sourceMappingURL=migrateTableScoringSource.test.js.map