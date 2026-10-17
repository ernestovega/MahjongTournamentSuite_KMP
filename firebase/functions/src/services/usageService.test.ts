import assert from "node:assert/strict";
import test from "node:test";

import { buildUsageReport, type UsageChange } from "./usageService";

const HOUR = 3_600_000;
const base = Date.UTC(2026, 9, 10, 9, 0, 0);

function change(partial: Partial<UsageChange>): UsageChange {
  return {
    tournamentId: "t1", kind: "save", source: "editor", uid: "u1", name: "Ana",
    roundId: 1, tableId: 1, handChangeCount: 2, at: base, ...partial,
  };
}

test("report counts saves by source, conflicts and resets", () => {
  const report = buildUsageReport({
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

  assert.equal(report.totals.saves, 2);
  assert.equal(report.totals.playerSaves, 1);
  assert.equal(report.totals.editorSaves, 1);
  assert.equal(report.totals.conflicts, 1);
  assert.equal(report.totals.resets, 1);
  assert.equal(report.totals.playerShare, 0.5);
  assert.equal(report.totals.conflictRate, 1 / 3);
  const item = report.tournaments[0];
  assert.equal(item.name, "Open");
  assert.equal(item.tablesTouched, 2);
  assert.equal(item.savesPerTouchedTable, 1);
  assert.equal(item.activeHours, 2);
  assert.equal(item.handsTables, 1);
  assert.equal(item.totalsOnlyTables, 1);
  assert.equal(item.manualTotalsTables, 1);
  assert.equal(item.slotsWithAccount, 1);
  assert.equal(item.invitationsAccepted, 1);
  assert.equal(report.savesByHour[9], 1);
  assert.equal(report.savesByHour[11], 1);
});

test("a change without a source counts as an editor save", () => {
  const report = buildUsageReport({
    now: new Date(base), tournaments: [], tables: [], slots: [],
    changes: [change({ source: null })],
  });
  assert.equal(report.totals.editorSaves, 1);
  assert.equal(report.users[0].source, "editor");
});
