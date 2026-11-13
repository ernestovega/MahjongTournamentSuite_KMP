import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import test from "node:test";

import { handSeatScores, pointsFromScores, SeatTexts, tablePointsOf, tableTotalsPatch } from "./handScoring";

/**
 * Checks the shared scoring cases in testdata/scoring-cases.json. The app tests check the same file,
 * so a change in the scoring of one side fails here or there until both agree.
 */
type CaseHand = {
  winner?: string; loser?: string; score?: string; chicken?: boolean; done?: boolean; penalties?: string[];
};
type Cases = {
  seats: [string, string, string, string];
  hands: { name: string; hand: CaseHand; scores: number[] | null; result?: number[] | null }[];
  tables: {
    name: string;
    table: {
      seats: string[]; completed?: boolean; useTotalsOnly?: boolean; forcedZero?: string;
      manualScores?: string[]; savedScores?: string[];
    };
    hands: CaseHand[];
    scores: string[];
    points: string[];
  }[];
  points: { name: string; scores: string[]; forcedZeroSeat?: number; points: string[] | null }[];
};

const cases = JSON.parse(
  readFileSync(resolve(__dirname, "../../../../testdata/scoring-cases.json"), "utf8"),
) as Cases;

const handData = (hand: CaseHand, withPenalties = true) => {
  const penalties = withPenalties ? hand.penalties ?? ["", "", "", ""] : ["", "", "", ""];
  return {
    playerWinnerId: hand.winner ?? "", playerLooserId: hand.loser ?? "", handScore: hand.score ?? "",
    isChickenHand: hand.chicken ?? false, isDone: hand.done ?? true,
    playerEastPenalty: penalties[0], playerSouthPenalty: penalties[1],
    playerWestPenalty: penalties[2], playerNorthPenalty: penalties[3],
  };
};

const SEAT_KEYS = ["East", "South", "West", "North"] as const;

for (const entry of cases.hands) {
  test(`hand: ${entry.name}`, () => {
    assert.deepEqual(handSeatScores(handData(entry.hand), cases.seats), entry.scores);
    const result = entry.result === undefined ? entry.scores : entry.result;
    assert.deepEqual(handSeatScores(handData(entry.hand, false), cases.seats), result);
  });
}

for (const entry of cases.tables) {
  test(`table: ${entry.name}`, () => {
    const table: Record<string, unknown> = {
      isCompleted: entry.table.completed ?? false,
      useTotalsOnly: entry.table.useTotalsOnly ?? false,
      forcedZeroPlayerId: entry.table.forcedZero ?? "",
    };
    SEAT_KEYS.forEach((seat, index) => {
      table[`player${seat}Id`] = entry.table.seats[index];
      table[`manualPlayer${seat}Score`] = entry.table.manualScores?.[index] ?? "";
      table[`player${seat}Score`] = entry.table.savedScores?.[index] ?? "";
    });
    const hands = entry.hands.map((hand) => handData(hand));
    if (entry.table.useTotalsOnly) {
      const points = tablePointsOf(table);
      assert.deepEqual(SEAT_KEYS.map((seat) => points[`player${seat}Points`]), entry.points);
      return;
    }
    const patch = tableTotalsPatch(table, hands);
    assert.deepEqual(SEAT_KEYS.map((seat) => patch[`player${seat}Score`]), entry.scores);
    assert.deepEqual(SEAT_KEYS.map((seat) => patch[`player${seat}Points`]), entry.points);
  });
}

for (const entry of cases.points) {
  test(`points: ${entry.name}`, () => {
    assert.deepEqual(pointsFromScores(entry.scores as SeatTexts, entry.forcedZeroSeat ?? null), entry.points);
  });
}
