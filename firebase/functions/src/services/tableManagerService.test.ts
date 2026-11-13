import assert from "node:assert/strict";
import test from "node:test";

import {
  calculateTableSummary,
  defaultHand,
  HANDS_COPY_FIELD,
  handsCopyUpdate,
  handsCopyVersionUpdate,
  handsFromCopy,
  HANDS_PER_TABLE,
  isTournamentCompleteAfterTableSave,
  tableStateFromData,
} from "./tableManagerService";

test("table summary detects hand progress", () => {
  const summary = calculateTableSummary(
    { useTotalsOnly: false, isCompleted: false },
    [{ handScore: "8", isDone: true }],
  );

  assert.equal(summary.hasProgress, true);
});

test("table summary validates manual scores", () => {
  const summary = calculateTableSummary({
    useTotalsOnly: true,
    manualPlayerEastScore: "10",
    manualPlayerSouthScore: "-5",
    manualPlayerWestScore: "-3",
    manualPlayerNorthScore: "-2",
  }, []);

  assert.equal(summary.hasValidManualTotals, true);
});

test("table summary ignores manual scores when the table uses hands", () => {
  const summary = calculateTableSummary({
    useTotalsOnly: false,
    manualPlayerEastScore: "10",
    manualPlayerSouthScore: "-5",
    manualPlayerWestScore: "-3",
    manualPlayerNorthScore: "-2",
  }, []);

  assert.equal(summary.hasValidManualTotals, false);
});

test("table summary treats a completed table as having progress", () => {
  const summary = calculateTableSummary({ isCompleted: true }, []);

  assert.equal(summary.hasProgress, true);
});

test("table points come only from the scores and never from stored points", () => {
  const table = tableStateFromData({
    roundId: 1, tableId: 1, useTotalsOnly: true,
    manualPlayerEastScore: "30", manualPlayerSouthScore: "10", manualPlayerWestScore: "-10", manualPlayerNorthScore: "-30",
    playerEastPoints: "7", playerSouthPoints: "7", playerWestPoints: "7", playerNorthPoints: "7",
  });

  assert.deepEqual(
    [table.playerEastPoints, table.playerSouthPoints, table.playerWestPoints, table.playerNorthPoints],
    ["4", "2", "1", "0"],
  );
});

test("table points are empty while a score is missing", () => {
  const table = tableStateFromData({ roundId: 1, tableId: 1, useTotalsOnly: true, manualPlayerEastScore: "30" });

  assert.equal(table.playerEastPoints, "");
  assert.equal(table.playerNorthPoints, "");
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

test("table summary reports the best done hand and the chicken hand count", () => {
  const summary = calculateTableSummary({}, [
    { handScore: "12", isDone: true, isChickenHand: true },
    { handScore: "30", isDone: true, isChickenHand: false },
    { handScore: "80", isDone: false, isChickenHand: true },
    { handScore: "abc", isDone: true },
  ]);

  assert.deepEqual(summary.bestHandScores, [30, 12]);
  assert.equal(summary.chickenHandCount, 2);
  assert.deepEqual(calculateTableSummary({}, []).bestHandScores, []);
});

test("table summary keeps up to three best hands and the ties with the third one", () => {
  const hands = [8, 40, 30, 30, 30, 12].map((score) => ({ handScore: String(score), isDone: true }));
  assert.deepEqual(calculateTableSummary({}, hands).bestHandScores, [40, 30, 30, 30]);
  assert.deepEqual(calculateTableSummary({}, hands.slice(0, 2)).bestHandScores, [40, 8]);
});

const seatedTable = {
  playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
};

test("table summary status is empty without data", () => {
  assert.equal(calculateTableSummary({}, []).completionStatus, "empty");
});

test("table summary status ignores the legacy completed flag when there is no data", () => {
  assert.equal(calculateTableSummary({ isCompleted: true }, []).completionStatus, "empty");
});

test("table summary status is incomplete when seats are missing", () => {
  const summary = calculateTableSummary({
    useTotalsOnly: true,
    playerEastId: "1",
    manualPlayerEastScore: "10",
    manualPlayerSouthScore: "-5",
    manualPlayerWestScore: "-3",
    manualPlayerNorthScore: "-2",
  }, []);

  assert.equal(summary.completionStatus, "incomplete");
});

test("table summary status is incomplete with seats but invalid manual scores", () => {
  const summary = calculateTableSummary({ ...seatedTable, useTotalsOnly: true, manualPlayerEastScore: "10" }, []);

  assert.equal(summary.completionStatus, "incomplete");
});

test("table summary status is partial with seats and valid manual scores", () => {
  const summary = calculateTableSummary({
    ...seatedTable,
    useTotalsOnly: true,
    manualPlayerEastScore: "10",
    manualPlayerSouthScore: "-5",
    manualPlayerWestScore: "-3",
    manualPlayerNorthScore: "-2",
  }, []);

  assert.equal(summary.completionStatus, "partial");
});

test("table summary status is completed in hands mode without hands", () => {
  const summary = calculateTableSummary({
    ...seatedTable,
    useTotalsOnly: false,
    playerEastScore: "10",
    playerSouthScore: "-5",
    playerWestScore: "-3",
    playerNorthScore: "-2",
  }, []);

  assert.equal(summary.completionStatus, "completed");
});

test("table summary status is completed in hands mode with some hands not done", () => {
  const summary = calculateTableSummary({
    ...seatedTable,
    useTotalsOnly: false,
    playerEastScore: "8",
    playerSouthScore: "-8",
    playerWestScore: "0",
    playerNorthScore: "0",
  }, [{ handScore: "8", isDone: true }, { handScore: "", isDone: false }]);

  assert.equal(summary.completionStatus, "completed");
});

test("table summary status in hands mode ignores stale manual scores", () => {
  const summary = calculateTableSummary({
    ...seatedTable,
    useTotalsOnly: false,
    manualPlayerEastScore: "10",
    manualPlayerSouthScore: "-5",
    manualPlayerWestScore: "-3",
    manualPlayerNorthScore: "-2",
  }, []);

  assert.equal(summary.completionStatus, "incomplete");
});

const sixteenHands = () => Array.from({ length: HANDS_PER_TABLE }, (_, index) => defaultHand(index + 1));

test("the copy of the hands counts only for the version it was written for", () => {
  const table = { version: 4, ...handsCopyUpdate(4, sixteenHands()) };

  assert.equal(handsFromCopy(table)?.length, HANDS_PER_TABLE);
  assert.equal(handsFromCopy({ ...table, version: 5 }), null);
  assert.equal(handsFromCopy({ version: 4 }), null);
});

test("a copy without all the hands is not used", () => {
  assert.equal(handsFromCopy({ version: 0, ...handsCopyUpdate(0, sixteenHands().slice(1)) }), null);
});

test("the copy keeps only the fields of a hand and sorts the hands", () => {
  const hands = sixteenHands().reverse().map((hand) => ({ ...hand, updatedAt: "now", handScore: String(hand.handId) }));
  const copy = handsCopyUpdate(1, hands)[HANDS_COPY_FIELD] as { hands: Array<Record<string, unknown>> };

  assert.equal(copy.hands[0].handId, 1);
  assert.equal(copy.hands[0].handScore, "1");
  assert.equal("updatedAt" in copy.hands[0], false);
});

test("a new version keeps a valid copy valid and leaves an old copy alone", () => {
  const valid = { version: 2, ...handsCopyUpdate(2, sixteenHands()) };

  assert.deepEqual(handsCopyVersionUpdate(valid, 3), { [`${HANDS_COPY_FIELD}.version`]: 3 });
  assert.deepEqual(handsCopyVersionUpdate({ ...valid, version: 3 }, 4), {});
});
