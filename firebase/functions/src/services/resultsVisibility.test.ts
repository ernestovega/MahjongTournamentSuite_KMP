import assert from "node:assert/strict";
import test from "node:test";

import {
  hiddenFromRound,
  isHiddenRound,
  maskTable,
  maskTablesWithHands,
  maskTableSummaries,
  parseHideResultsFromRound,
  readStoredHideResultsFromRound,
  withoutHiddenRounds,
} from "./resultsVisibility";
import { tableStateFromData } from "./tableManagerService";

const table = (roundId: number) => tableStateFromData({
  roundId, tableId: 1, playerIds: [1, 2, 3, 4], version: 7, isCompleted: true,
  playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
  playerEastScore: "40", playerSouthScore: "-10", playerWestScore: "-10", playerNorthScore: "-20",
  manualPlayerEastScore: "1", useTotalsOnly: true, forcedZeroPlayerId: "2",
});
const hand = { handId: 1, playerWinnerId: "1", playerLooserId: "2", handScore: "8", isChickenHand: false, isDone: true,
  playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", doneAtMs: 5 };

test("the results are hidden from the round of the setting until the reveal", () => {
  assert.equal(hiddenFromRound({ hideResultsFromRound: 4 }), 4);
  assert.equal(hiddenFromRound({ hideResultsFromRound: 4, resultsRevealed: true }), null);
  assert.equal(hiddenFromRound({ hideResultsFromRound: null }), null);
  assert.equal(hiddenFromRound({}), null);
  assert.equal(hiddenFromRound({ hideResultsFromRound: 0 }), null);
  assert.equal(hiddenFromRound({ hideResultsFromRound: 2.5 }), null);
  assert.equal(isHiddenRound(3, 4), false);
  assert.equal(isHiddenRound(4, 4), true);
  assert.equal(isHiddenRound(9, null), false);
});

test("the stored setting and the setting of a request", () => {
  assert.equal(readStoredHideResultsFromRound(undefined), null);
  assert.equal(readStoredHideResultsFromRound(null), null);
  assert.equal(readStoredHideResultsFromRound("x"), null);
  assert.equal(readStoredHideResultsFromRound(3), 3);
  assert.equal(parseHideResultsFromRound(null, 5), null);
  assert.equal(parseHideResultsFromRound(1, 5), 1);
  assert.equal(parseHideResultsFromRound(5, 5), 5);
  assert.equal(parseHideResultsFromRound(1, 0), 1);
  assert.equal(parseHideResultsFromRound(0, 5), null);
  for (const wrong of [-1, 6, 2.5, "3", true, undefined]) {
    assert.throws(() => parseHideResultsFromRound(wrong, 5), /hideResultsFromRound/, String(wrong));
  }
});

test("a masked table keeps the seats and the version but no results", () => {
  const masked = maskTable(table(4));
  assert.deepEqual(masked.table.playerIds, [1, 2, 3, 4]);
  assert.equal(masked.table.playerEastId, "1");
  assert.equal(masked.table.version, 7);
  assert.equal(masked.table.isCompleted, true);
  for (const field of ["playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore", "playerEastPoints",
    "playerSouthPoints", "playerWestPoints", "playerNorthPoints", "manualPlayerEastScore", "forcedZeroPlayerId"] as const) {
    assert.equal(masked.table[field], "", field);
  }
  assert.equal(masked.table.useTotalsOnly, false);
  assert.equal(masked.table.resultsHidden, true);
  assert.equal(masked.hands.length, 16);
  assert.ok(masked.hands.every((h) => !h.isDone && h.handScore === "" && h.playerWinnerId === ""));
});

test("only the tables of the hidden rounds are masked", () => {
  const tables = [{ table: table(1), hands: [hand] }, { table: table(2), hands: [hand] }];
  assert.equal(maskTablesWithHands(tables, null), tables);
  const masked = maskTablesWithHands(tables, 2);
  assert.equal(masked[0], tables[0]);
  assert.equal(masked[1].table.playerEastScore, "");
  assert.equal(masked[1].hands[0].isDone, false);

  const summary = (roundId: number) => ({ version: 1, roundId, tableId: 1, playerIds: [1, 2, 3, 4], isCompleted: true,
    useTotalsOnly: true, hasProgress: true, hasValidManualTotals: true, completionStatus: "complete", bestHandScores: [88],
    chickenHandCount: 2 });
  const summaries = [summary(1), summary(2)];
  assert.equal(maskTableSummaries(summaries, null), summaries);
  const maskedSummaries = maskTableSummaries(summaries, 2);
  assert.equal(maskedSummaries[0], summaries[0]);
  assert.deepEqual(maskedSummaries[1], { ...summary(2), bestHandScores: [], chickenHandCount: 0, hasValidManualTotals: false, useTotalsOnly: false });
});

test("the statistics of a person lose the hidden rounds", () => {
  const statistics = {
    emaId: "1",
    tournaments: [
      { tournamentId: "a", results: [{ roundId: 1 }, { roundId: 2 }] },
      { tournamentId: "b", results: [{ roundId: 1 }] },
      { tournamentId: "c", results: [{ roundId: 3 }] },
    ],
  };
  assert.equal(withoutHiddenRounds(statistics, new Map()), statistics);
  assert.deepEqual(withoutHiddenRounds(statistics, new Map([["a", 2], ["c", 1]])), {
    emaId: "1",
    tournaments: [
      { tournamentId: "a", results: [{ roundId: 1 }] },
      { tournamentId: "b", results: [{ roundId: 1 }] },
    ],
  });
});
