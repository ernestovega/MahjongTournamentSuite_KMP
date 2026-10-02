import assert from "node:assert/strict";
import test from "node:test";

import { convertForcedPointsTable } from "./migrateTableScoringSource";

test("report conversion keeps hands outside the table patch", () => {
  const result = convertForcedPointsTable({
    usePointsCalculation: false,
    playerEastScore: "32000",
    playerSouthScore: "28000",
    playerWestScore: "22000",
    playerNorthScore: "18000",
    hands: [{ handId: 1, handScore: "8" }],
  });

  assert.deepEqual(result.patch, {
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

test("report conversion skips forced points without four integer scores", () => {
  const result = convertForcedPointsTable({
    usePointsCalculation: false,
    playerEastPoints: "4",
    playerSouthPoints: "2",
    playerWestPoints: "1",
    playerNorthPoints: "0",
  });

  assert.equal(result.patch, null);
  assert.match(result.reason ?? "", /four integer scores/);
});

test("already calculated tables are unchanged", () => {
  assert.deepEqual(convertForcedPointsTable({ usePointsCalculation: true }), { patch: null });
});
