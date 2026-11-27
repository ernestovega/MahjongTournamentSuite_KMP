import assert from "node:assert/strict";
import test from "node:test";

import { buildTableDigest } from "./statisticsDigest";

const table = (extra: Record<string, unknown> = {}) => ({
  roundId: 1, tableId: 1, isCompleted: true, useTotalsOnly: false,
  playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
  playerEastScore: "0", playerSouthScore: "0", playerWestScore: "0", playerNorthScore: "0",
  playerEastPoints: "0", playerSouthPoints: "0", playerWestPoints: "0", playerNorthPoints: "0",
  ...extra,
});

const hand = (handId: number, winner: string, loser: string, score: string, extra: Record<string, unknown> = {}) => ({
  handId, playerWinnerId: winner, playerLooserId: loser, handScore: score, isChickenHand: false, isDone: true,
  playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "", ...extra,
});

const emaBySlot = new Map<number, string>([[1, "EMA1"], [2, "EMA2"]]);

/** The digest of one table. The tests read the results and the table totals from it. */
function digestOf(tableData: Record<string, unknown>, hands: Record<string, unknown>[]) {
  const digest = buildTableDigest({ tournamentId: "t1", table: tableData, hands, emaBySlot });
  return { results: digest.results, tables: [digest.summary] };
}

test("digest counts wins, deal-ins, self-draws and draws like the domain use case", () => {
  const digest = digestOf(table(), [
        hand(1, "1", "2", "10"),
        hand(2, "1", "", "8", { isChickenHand: true }),
        hand(3, "", "", "0"),
        hand(4, "3", "-", "20"),
      ]);

  const first = digest.results.find((result) => result.slotId === 1)!;
  const second = digest.results.find((result) => result.slotId === 2)!;
  const third = digest.results.find((result) => result.slotId === 3)!;
  assert.equal(first.emaId, "EMA1");
  assert.equal(third.emaId, "");
  assert.equal(first.handsPlayed, 4);
  assert.equal(first.handsWon, 2);
  assert.equal(first.chickenHands, 1);
  assert.equal(second.dealIns, 1);
  assert.equal(second.dealInScoreSum, 10);
  assert.equal(third.selfDraws, 1);
  // Hand 1: seat 1 wins on seat 2. Seats 3 and 4 only pay the base value.
  assert.equal(third.otherWins, 1);
  assert.equal(second.lostToSelfDraws, 2);
  assert.equal(digest.tables[0].drawnHands, 1);
  assert.equal(digest.tables[0].bestHand, 20);
  assert.equal(digest.tables[0].winningHands, 3);
});

test("a table with only totals gives results without hand values", () => {
  const digest = digestOf(table({
        useTotalsOnly: true,
        playerEastScore: "20", playerSouthScore: "20", playerWestScore: "0", playerNorthScore: "-40",
        playerEastPoints: "3", playerSouthPoints: "3", playerWestPoints: "1", playerNorthPoints: "0",
      }), []);

  assert.deepEqual(digest.results.map((result) => result.position), [1, 1, 3, 4]);
  assert.equal(digest.results[0].points, 3);
  assert.equal(digest.results[0].hasHands, false);
  assert.equal(digest.tables[0].hasHands, false);
});

test("an open table gives a table entry and no results", () => {
  const digest = digestOf(table({ isCompleted: false }), [hand(1, "1", "2", "10")]);

  assert.equal(digest.results.length, 0);
  assert.equal(digest.tables.length, 1);
  assert.equal(digest.tables[0].isCompleted, false);
});

test("digest keeps rival tallies and hand runs like the domain use case", () => {
  const digest = digestOf(table({ playerEastScore: "30", playerSouthScore: "-10", playerWestScore: "0", playerNorthScore: "-20" }), [
    hand(1, "1", "2", "10"),
    hand(2, "1", "-", "8"),
    hand(3, "3", "1", "10"),
    hand(4, "1", "2", "8"),
  ]);

  const first = digest.results.find((result) => result.slotId === 1)!;
  const second = digest.results.find((result) => result.slotId === 2)!;
  assert.deepEqual(first.winRun, { best: 2, start: 2, end: 1 });
  assert.deepEqual(second.noDealInRun, { best: 2, start: 0, end: 0 });
  assert.equal(second.rivals.length, 3);
  assert.deepEqual(second.rivals.find((rival) => rival.opponent === "EMA1"), {
    opponent: "EMA1", tablesBehind: 1, discardLosses: 2, selfDrawLosses: 1, othersDiscardWins: 0,
  });
  // Player 3 won a hand on a discard of player 1. For player 2 this is a win on another player's discard.
  assert.equal(second.rivals.find((rival) => rival.opponent === "t1#3")?.othersDiscardWins, 1);
  // A slot without an EMA number is named by the tournament and the slot.
  assert.ok(second.rivals.some((rival) => rival.opponent === "t1#3"));
});

test("digest keeps the biggest single penalty of a seat", () => {
  const digest = digestOf(table({ playerEastScore: "30", playerSouthScore: "-10", playerWestScore: "0", playerNorthScore: "-20" }), [
    hand(1, "1", "2", "10", { playerSouthPenalty: "-10;-20" }),
    hand(2, "1", "2", "8", { playerSouthPenalty: "-5", playerWestPenalty: "3" }),
  ]);

  const second = digest.results.find((result) => result.slotId === 2)!;
  const third = digest.results.find((result) => result.slotId === 3)!;
  assert.equal(second.maxPenalty, 20);
  assert.equal(second.penaltyPoints, 35);
  // A positive value is not a penalty.
  assert.equal(third.maxPenalty, 0);
});
