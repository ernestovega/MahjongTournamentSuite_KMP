import assert from "node:assert/strict";
import test from "node:test";

import { planTournamentBackfill } from "./tournamentBackfill";
import { defaultHand, handsFromData, tableStateFromData } from "./tableManagerService";

const empty = { id: "1_1", data: { isCompleted: false }, hands: [defaultHand(1)] };

test("a tournament without data needs no counter value", () => {
  assert.deepEqual(planTournamentBackfill([{ ...empty, hands: fullHands }]), { tablesWithProgress: 0, missingHands: [] });
});

test("tables with typed data or hand data count as started", () => {
  const plan = planTournamentBackfill([
    { id: "1_1", data: { playerEastId: "3" }, hands: [defaultHand(1)] },
    { id: "1_2", data: {}, hands: [{ ...defaultHand(1), handScore: "8" }] },
    { id: "1_3", data: { isCompleted: true }, hands: [defaultHand(1)] },
    empty,
  ]);

  assert.equal(plan.tablesWithProgress, 3);
});

const fullHands = Array.from({ length: 16 }, (_, index) => defaultHand(index + 1));

test("a table with all 16 hands needs nothing", () => {
  assert.deepEqual(planTournamentBackfill([{ id: "1_1", data: {}, hands: fullHands }]).missingHands, []);
});

test("a table without hands needs all 16", () => {
  const plan = planTournamentBackfill([empty, { id: "2_1", data: {}, hands: [] }]);

  assert.deepEqual(plan.missingHands.map((entry) => entry.tableId), ["1_1", "2_1"]);
  assert.deepEqual(plan.missingHands[1].handIds, Array.from({ length: 16 }, (_, index) => index + 1));
});

test("a table with only some hands needs the others", () => {
  const some = [1, 2, 5, 16].map(defaultHand);

  const plan = planTournamentBackfill([{ id: "3_4", data: {}, hands: some }]);

  assert.deepEqual(plan.missingHands, [{ tableId: "3_4", handIds: [3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15] }]);
});

test("table state reads stored fields with safe defaults", () => {
  const table = tableStateFromData({ roundId: 2, tableId: 3, playerIds: ["1", 2, 3, 4], version: 5 });

  assert.deepEqual(table.playerIds, [1, 2, 3, 4]);
  assert.equal(table.version, 5);
  assert.equal(table.playerEastId, "");
  assert.equal(table.useTotalsOnly, true);
  assert.equal(table.usePointsCalculation, true);
  assert.equal(table.isCompleted, false);
});

test("hands are sorted by id", () => {
  const hands = handsFromData([{ handId: 3 }, { handId: 1 }, { handId: 2, handScore: 8 }]);

  assert.deepEqual(hands.map((hand) => hand.handId), [1, 2, 3]);
  assert.equal(hands[1].handScore, "8");
});
