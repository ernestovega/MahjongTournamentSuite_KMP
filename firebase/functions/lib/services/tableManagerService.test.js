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
(0, node_test_1.default)("table summary treats a completed table as having progress", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({ isCompleted: true }, []);
    strict_1.default.equal(summary.hasProgress, true);
});
(0, node_test_1.default)("table summary accepts decimal points with a comma separator", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        useTotalsOnly: false,
        usePointsCalculation: false,
        manualPlayerEastPoints: "3,5",
        manualPlayerSouthPoints: "2",
        manualPlayerWestPoints: "1",
        manualPlayerNorthPoints: "0,5",
    }, []);
    strict_1.default.equal(summary.hasValidManualTotals, true);
});
(0, node_test_1.default)("tournament completes when the current table is the last incomplete table", () => {
    strict_1.default.equal((0, tableManagerService_1.isTournamentCompleteAfterTableSave)({
        currentTablePath: "tournaments/t1/tables/2_4",
        currentTableIsComplete: true,
        incompleteTablePaths: ["tournaments/t1/tables/2_4"],
    }), true);
});
(0, node_test_1.default)("tournament stays incomplete while another table is incomplete", () => {
    strict_1.default.equal((0, tableManagerService_1.isTournamentCompleteAfterTableSave)({
        currentTablePath: "tournaments/t1/tables/2_4",
        currentTableIsComplete: true,
        incompleteTablePaths: [
            "tournaments/t1/tables/1_1",
            "tournaments/t1/tables/2_4",
        ],
    }), false);
});
(0, node_test_1.default)("tournament cannot complete when the current table remains incomplete", () => {
    strict_1.default.equal((0, tableManagerService_1.isTournamentCompleteAfterTableSave)({
        currentTablePath: "tournaments/t1/tables/2_4",
        currentTableIsComplete: false,
        incompleteTablePaths: ["tournaments/t1/tables/2_4"],
    }), false);
});
(0, node_test_1.default)("tournament remains complete when no table is incomplete", () => {
    strict_1.default.equal((0, tableManagerService_1.isTournamentCompleteAfterTableSave)({
        currentTablePath: "tournaments/t1/tables/2_4",
        currentTableIsComplete: true,
        incompleteTablePaths: [],
    }), true);
});
(0, node_test_1.default)("table summary reports the best done hand and the chicken hand count", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({}, [
        { handScore: "12", isDone: true, isChickenHand: true },
        { handScore: "30", isDone: true, isChickenHand: false },
        { handScore: "80", isDone: false, isChickenHand: true },
        { handScore: "abc", isDone: true },
    ]);
    strict_1.default.equal(summary.bestHandScore, 30);
    strict_1.default.equal(summary.chickenHandCount, 2);
    strict_1.default.equal((0, tableManagerService_1.calculateTableSummary)({}, []).bestHandScore, null);
});
const seatedTable = {
    playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
};
(0, node_test_1.default)("table summary status is empty without data", () => {
    strict_1.default.equal((0, tableManagerService_1.calculateTableSummary)({}, []).completionStatus, "empty");
});
(0, node_test_1.default)("table summary status ignores the legacy completed flag when there is no data", () => {
    strict_1.default.equal((0, tableManagerService_1.calculateTableSummary)({ isCompleted: true }, []).completionStatus, "empty");
});
(0, node_test_1.default)("table summary status is incomplete when seats are missing", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        useTotalsOnly: true,
        playerEastId: "1",
        manualPlayerEastScore: "10",
        manualPlayerSouthScore: "-5",
        manualPlayerWestScore: "-3",
        manualPlayerNorthScore: "-2",
    }, []);
    strict_1.default.equal(summary.completionStatus, "incomplete");
});
(0, node_test_1.default)("table summary status is incomplete with seats but invalid manual scores", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({ ...seatedTable, useTotalsOnly: true, manualPlayerEastScore: "10" }, []);
    strict_1.default.equal(summary.completionStatus, "incomplete");
});
(0, node_test_1.default)("table summary status is partial with seats and valid manual scores", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        ...seatedTable,
        useTotalsOnly: true,
        manualPlayerEastScore: "10",
        manualPlayerSouthScore: "-5",
        manualPlayerWestScore: "-3",
        manualPlayerNorthScore: "-2",
    }, []);
    strict_1.default.equal(summary.completionStatus, "partial");
});
(0, node_test_1.default)("table summary status is completed in hands mode without hands", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        ...seatedTable,
        useTotalsOnly: false,
        playerEastScore: "10",
        playerSouthScore: "-5",
        playerWestScore: "-3",
        playerNorthScore: "-2",
    }, []);
    strict_1.default.equal(summary.completionStatus, "completed");
});
(0, node_test_1.default)("table summary status is completed in hands mode with some hands not done", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        ...seatedTable,
        useTotalsOnly: false,
        playerEastScore: "8",
        playerSouthScore: "-8",
        playerWestScore: "0",
        playerNorthScore: "0",
    }, [{ handScore: "8", isDone: true }, { handScore: "", isDone: false }]);
    strict_1.default.equal(summary.completionStatus, "completed");
});
(0, node_test_1.default)("table summary status in hands mode ignores stale manual scores", () => {
    const summary = (0, tableManagerService_1.calculateTableSummary)({
        ...seatedTable,
        useTotalsOnly: false,
        manualPlayerEastScore: "10",
        manualPlayerSouthScore: "-5",
        manualPlayerWestScore: "-3",
        manualPlayerNorthScore: "-2",
    }, []);
    strict_1.default.equal(summary.completionStatus, "incomplete");
});
//# sourceMappingURL=tableManagerService.test.js.map