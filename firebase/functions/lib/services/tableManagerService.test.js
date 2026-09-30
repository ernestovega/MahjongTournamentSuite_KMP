"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const tableManagerService_1 = require("./tableManagerService");
(0, node_test_1.default)("table summary detects hand progress", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({ useTotalsOnly: false, usePointsCalculation: true, isCompleted: false }, [{ handScore: "8", isDone: true }]);
    strict_1.default.equal(summary.hasProgress, true);
});
(0, node_test_1.default)("table summary validates manual scores", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        useTotalsOnly: true,
        usePointsCalculation: true,
        manualPlayerEastScore: "10",
        manualPlayerSouthScore: "-5",
        manualPlayerWestScore: "-3",
        manualPlayerNorthScore: "-2",
    }, []);
    strict_1.default.equal(summary.hasValidManualTotals, true);
});
(0, node_test_1.default)("table summary validates manual points when calculation is disabled", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        useTotalsOnly: false,
        usePointsCalculation: false,
        manualPlayerEastPoints: "4",
        manualPlayerSouthPoints: "2",
        manualPlayerWestPoints: "1",
        manualPlayerNorthPoints: "0",
    }, []);
    strict_1.default.equal(summary.hasValidManualTotals, true);
});
//# sourceMappingURL=tableManagerService.test.js.map