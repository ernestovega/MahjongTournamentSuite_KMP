import assert from "node:assert/strict";
import test from "node:test";

import {
  calculateTableSummary,
  isTournamentCompleteAfterTableSave,
} from "./tableManagerService";

test("table summary detects hand progress", () => {
  const summary = calculateTableSummary(
    { useTotalsOnly: false, usePointsCalculation: true, isCompleted: false },
    [{ handScore: "8", isDone: true }],
  );

  assert.equal(summary.hasProgress, true);
});

test("table summary validates manual scores", () => {
  const summary = calculateTableSummary({
    useTotalsOnly: true,
    usePointsCalculation: true,
    manualPlayerEastScore: "10",
    manualPlayerSouthScore: "-5",
    manualPlayerWestScore: "-3",
    manualPlayerNorthScore: "-2",
  }, []);

  assert.equal(summary.hasValidManualTotals, true);
});

test("table summary validates manual points when calculation is disabled", () => {
  const summary = calculateTableSummary({
    useTotalsOnly: false,
    usePointsCalculation: false,
    manualPlayerEastPoints: "4",
    manualPlayerSouthPoints: "2",
    manualPlayerWestPoints: "1",
    manualPlayerNorthPoints: "0",
  }, []);

  assert.equal(summary.hasValidManualTotals, true);
});

test("table summary treats a completed table as having progress", () => {
  const summary = calculateTableSummary({ isCompleted: true }, []);

  assert.equal(summary.hasProgress, true);
});

test("table summary accepts decimal points with a comma separator", () => {
  const summary = calculateTableSummary({
    useTotalsOnly: false,
    usePointsCalculation: false,
    manualPlayerEastPoints: "3,5",
    manualPlayerSouthPoints: "2",
    manualPlayerWestPoints: "1",
    manualPlayerNorthPoints: "0,5",
  }, []);

  assert.equal(summary.hasValidManualTotals, true);
});

test("tournament completes when the current table is the last incomplete table", () => {
  assert.equal(isTournamentCompleteAfterTableSave({
    currentTablePath: "tournaments/t1/tables/2_4",
    currentTableIsComplete: true,
    incompleteTablePaths: ["tournaments/t1/tables/2_4"],
  }), true);
});

test("tournament stays incomplete while another table is incomplete", () => {
  assert.equal(isTournamentCompleteAfterTableSave({
    currentTablePath: "tournaments/t1/tables/2_4",
    currentTableIsComplete: true,
    incompleteTablePaths: [
      "tournaments/t1/tables/1_1",
      "tournaments/t1/tables/2_4",
    ],
  }), false);
});

test("tournament cannot complete when the current table remains incomplete", () => {
  assert.equal(isTournamentCompleteAfterTableSave({
    currentTablePath: "tournaments/t1/tables/2_4",
    currentTableIsComplete: false,
    incompleteTablePaths: ["tournaments/t1/tables/2_4"],
  }), false);
});

test("tournament remains complete when no table is incomplete", () => {
  assert.equal(isTournamentCompleteAfterTableSave({
    currentTablePath: "tournaments/t1/tables/2_4",
    currentTableIsComplete: true,
    incompleteTablePaths: [],
  }), true);
});
