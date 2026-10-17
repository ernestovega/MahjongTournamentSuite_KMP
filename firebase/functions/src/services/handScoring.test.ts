import assert from "node:assert/strict";
import test from "node:test";

import { handScoreTotals, handSeatScores, isResultSelectionInvalid, pointsFromScores, seatIdsOf, tableTotalsPatch } from "./handScoring";

const table = {
  playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
  isCompleted: false, useTotalsOnly: false,
};
const hand = (patch: Record<string, unknown>) => ({
  playerWinnerId: "", playerLooserId: "", handScore: "", isChickenHand: false, isDone: true,
  playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", ...patch,
});

test("seats need four different ids", () => {
  assert.deepEqual(seatIdsOf(table), ["1", "2", "3", "4"]);
  assert.equal(seatIdsOf({ ...table, playerNorthId: "3" }), null);
  assert.equal(seatIdsOf({ ...table, playerNorthId: "" }), null);
});

test("a discarded hand gives the winner the score plus 8 from each loser share", () => {
  // Winner east with 10, loser south: east 10 + 24, south -(10 + 8), others -8.
  assert.deepEqual(
    handSeatScores(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "10" }), ["1", "2", "3", "4"]),
    [34, -18, -8, -8],
  );
});

test("a self drawn hand takes score plus 8 from every other seat", () => {
  assert.deepEqual(
    handSeatScores(hand({ playerWinnerId: "3", handScore: "9" }), ["1", "2", "3", "4"]),
    [-17, -17, 51, -17],
  );
});

test("penalties add to the seats and several rows are summed", () => {
  assert.deepEqual(
    handSeatScores(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "8", playerNorthPenalty: "-10;-5" }), ["1", "2", "3", "4"]),
    [32, -16, -8, -23],
  );
});

test("a hand below the minimum does not count", () => {
  assert.equal(isResultSelectionInvalid(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "7" })), true);
  assert.equal(handSeatScores(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "7" }), ["1", "2", "3", "4"]), null);
});

test("a chicken hand cannot score more than 12", () => {
  assert.equal(isResultSelectionInvalid(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "13", isChickenHand: true })), true);
  assert.equal(isResultSelectionInvalid(hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "12", isChickenHand: true })), false);
});

test("only done hands count unless the table is completed", () => {
  const hands = [
    hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "8" }),
    hand({ playerWinnerId: "3", playerLooserId: "4", handScore: "8", isDone: false }),
  ];
  assert.deepEqual(handScoreTotals(table, hands), [32, -16, -8, -8]);
  assert.deepEqual(handScoreTotals({ ...table, isCompleted: true }, hands), [24, -24, 24, -24]);
});

test("points follow the place and share places on ties", () => {
  assert.deepEqual(pointsFromScores(["30", "10", "0", "-40"]), ["4", "2", "1", "0"]);
  assert.deepEqual(pointsFromScores(["20", "20", "0", "-40"]), ["3", "3", "1", "0"]);
  assert.deepEqual(pointsFromScores(["10", "10", "10", "10"]), ["1,75", "1,75", "1,75", "1,75"]);
  assert.equal(pointsFromScores(["1", "", "2", "3"]), null);
});

test("the table patch has scores and points of the counted hands", () => {
  const patch = tableTotalsPatch(table, [hand({ playerWinnerId: "1", playerLooserId: "2", handScore: "8" })]);
  assert.deepEqual(patch, {
    playerEastScore: "32", playerSouthScore: "-16", playerWestScore: "-8", playerNorthScore: "-8",
    playerEastPoints: "4", playerSouthPoints: "0", playerWestPoints: "1,5", playerNorthPoints: "1,5",
  });
});
