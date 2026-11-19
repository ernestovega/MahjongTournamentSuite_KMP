import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { addPerson, joinRuns, summaryOf, tournamentsOf } from "./statisticsGroups";
import type { DigestResult, DigestTable } from "./statisticsDigest";

const run = (best: number, start: number, end: number) => ({ best, start, end });

function result(emaId: string, roundId: number, position: number, extra: Partial<DigestResult> = {}): DigestResult {
  return {
    emaId, slotId: 1, roundId, tableId: 1, seat: 0, position, score: 10 * (5 - position), points: 4 - position,
    hasHands: true, handsPlayed: 16, handsWon: 4, selfDraws: 1, dealIns: 2, drawnHands: 1, winningScoreSum: 40,
    bestHand: 12, chickenHands: 0, dealInScoreSum: 20, lostToSelfDraws: 1, otherWins: 8, penaltyCount: 0,
    penaltyPoints: 0, maxPenalty: 0, rivals: [], winRun: run(2, 0, 1), noDealInRun: run(5, 1, 3), ...extra,
  };
}

const table = (roundId: number, results: DigestResult[]): { roundId: number; tableId: number; summary: DigestTable; results: DigestResult[] } => ({
  roundId, tableId: 1, results,
  summary: { roundId, tableId: 1, isCompleted: true, hasHands: true, handsPlayed: 16, drawnHands: 1, winningHands: 15, selfDraws: 3, winningScoreSum: 200, bestHand: 24 + roundId, chickenHands: 1 },
});

describe("statistics groups", () => {
  it("joins the runs of tables: a full table continues the run before it", () => {
    assert.equal(joinRuns([{ run: run(2, 0, 2), hands: 4 }, { run: run(4, 4, 4), hands: 4 }, { run: run(1, 1, 0), hands: 4 }]), 7);
    assert.equal(joinRuns([{ run: run(3, 0, 0), hands: 4 }]), 3);
    assert.equal(joinRuns([]), 0);
  });

  it("sums the tables and every person of a tournament, and leaves out seats without an EMA number", () => {
    const summary = summaryOf([
      table(2, [result("A", 2, 1, { score: 50 }), result("", 2, 2)]),
      table(1, [result("A", 1, 1, { handsWon: 6, bestHand: 30 }), result("B", 1, 4)]),
    ]);

    assert.equal(summary.tables.tablesCompleted, 2);
    assert.equal(summary.tables.handsPlayed, 32);
    assert.equal(summary.tables.highestHand, 26);
    assert.deepEqual(summary.persons.map((person) => person.emaId).sort(), ["A", "B"]);
    const a = summary.persons.find((person) => person.emaId === "A")!;
    assert.equal(a.tournaments, 1);
    assert.equal(a.tables, 2);
    assert.equal(a.place1, 2);
    assert.equal(a.tableWinRun, 2);
    assert.equal(a.handsWon, 10);
    assert.equal(a.bestHand, 30);
    assert.equal(a.bestTableScore, 50);
    assert.equal(a.mostHandsWonAtATable, 6);
  });

  it("adds the numbers of two tournaments and keeps the best values", () => {
    const [first] = summaryOf([table(1, [result("A", 1, 1)])]).persons;
    const [second] = summaryOf([table(1, [result("A", 1, 3, { bestHand: 40 })])]).persons;
    addPerson(first, second);

    assert.equal(first.tournaments, 2);
    assert.equal(first.tables, 2);
    assert.equal(first.points, 4);
    assert.equal(first.bestHand, 40);
    assert.equal(first.tableWinRun, 1);
  });

  it("keeps the tournaments of a country, of some ids and between two dates", () => {
    const all = [
      { id: "t1", country: "ES", startDate: "2025-03-01" },
      { id: "t2", country: "ES", startDate: "2026-05-01" },
      { id: "t3", country: "SE", startDate: "2026-06-01" },
      { id: "t4", country: "ES", startDate: null },
    ];
    const ids = (filter: Parameters<typeof tournamentsOf>[1]) => tournamentsOf(all, filter).map((tournament) => tournament.id);

    assert.deepEqual(ids({}), ["t1", "t2", "t3", "t4"]);
    assert.deepEqual(ids({ tournamentCountry: "ES" }), ["t1", "t2", "t4"]);
    assert.deepEqual(ids({ tournamentCountry: "ES", from: "2026-01-01" }), ["t2"]);
    assert.deepEqual(ids({ to: "2025-12-31" }), ["t1"]);
    assert.deepEqual(ids({ tournamentIds: ["t3", "t4"] }), ["t3", "t4"]);
  });
});
