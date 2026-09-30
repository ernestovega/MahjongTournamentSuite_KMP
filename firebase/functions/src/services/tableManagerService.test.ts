import assert from "node:assert/strict";
import test from "node:test";

import { calculateTableSummary } from "./tableManagerService";

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
