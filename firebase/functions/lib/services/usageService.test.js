"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = __importDefault(require("node:test"));
const usageService_1 = require("./usageService");
const HOUR = 3600000;
const base = Date.UTC(2026, 9, 10, 9, 0, 0);
function change(partial) {
    return {
        tournamentId: "t1", kind: "save", source: "editor", uid: "u1", name: "Ana",
        roundId: 1, tableId: 1, handChangeCount: 2, at: base, ...partial,
    };
}
(0, node_test_1.default)("report counts saves by source, conflicts and resets", () => {
    const report = (0, usageService_1.buildUsageReport)({
        now: new Date(base),
        tournaments: [{ id: "t1", name: "Open" }],
        changes: [
            change({}),
            change({ source: "player", uid: "p1", name: "Player 10", tableId: 2, at: base + 2 * HOUR }),
            change({ kind: "conflict", source: "player", uid: "p1" }),
            change({ kind: "reset" }),
        ],
        tables: [
            { tournamentId: "t1", isCompleted: true, useTotalsOnly: false, hasValidManualTotals: false, hasProgress: true },
            { tournamentId: "t1", isCompleted: true, useTotalsOnly: true, hasValidManualTotals: true, hasProgress: false },
        ],
        slots: [
            { tournamentId: "t1", invitationStatus: "ACCEPTED", hasAccount: true, isAssigned: true },
            { tournamentId: "t1", invitationStatus: "NONE", hasAccount: false, isAssigned: false },
        ],
    });
    strict_1.default.equal(report.totals.saves, 2);
    strict_1.default.equal(report.totals.playerSaves, 1);
    strict_1.default.equal(report.totals.editorSaves, 1);
    strict_1.default.equal(report.totals.conflicts, 1);
    strict_1.default.equal(report.totals.resets, 1);
    strict_1.default.equal(report.totals.playerShare, 0.5);
    strict_1.default.equal(report.totals.conflictRate, 1 / 3);
    const item = report.tournaments[0];
    strict_1.default.equal(item.name, "Open");
    strict_1.default.equal(item.tablesTouched, 2);
    strict_1.default.equal(item.savesPerTouchedTable, 1);
    strict_1.default.equal(item.activeHours, 2);
    strict_1.default.equal(item.handsTables, 1);
    strict_1.default.equal(item.totalsOnlyTables, 1);
    strict_1.default.equal(item.manualTotalsTables, 1);
    strict_1.default.equal(item.slotsWithAccount, 1);
    strict_1.default.equal(item.invitationsAccepted, 1);
    strict_1.default.equal(report.savesByHour[9], 1);
    strict_1.default.equal(report.savesByHour[11], 1);
});
(0, node_test_1.default)("a change without a source counts as an editor save", () => {
    const report = (0, usageService_1.buildUsageReport)({
        now: new Date(base), tournaments: [], tables: [], slots: [],
        changes: [change({ source: null })],
    });
    strict_1.default.equal(report.totals.editorSaves, 1);
    strict_1.default.equal(report.users[0].source, "editor");
});
//# sourceMappingURL=usageService.test.js.map