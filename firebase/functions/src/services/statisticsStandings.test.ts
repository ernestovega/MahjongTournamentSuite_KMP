import assert from "node:assert/strict";
import test from "node:test";

import { standingOf } from "./statisticsStandings";

/** Four slots. Team 7 has the slots 1 and 3. Team 8 has the slots 2 and 4. */
const data = {
  totals: { 1: { points: 10, score: 100 }, 2: { points: 5, score: 50 }, 3: { points: 2, score: 20 }, 4: { points: -17, score: -170 } },
  teams: { 1: 7, 2: 8, 3: 7, 4: 8 },
  chicken: { 2: { "1_1": 1, "2_1": 1 }, 3: { "1_1": 1 } },
  best: { 1: { "1_1": 40 }, 2: { "1_1": 64, "2_1": 30 }, 3: { "2_1": 50 } },
};

test("standing gives the place, the place of the team, the chicken hand place and the best hand place", () => {
  // Team 7 has 12 points. Team 8 has -12 points.
  assert.deepEqual(standingOf("t", 1, data), {
    tournamentId: "t", rank: 1, of: 4, points: 10, score: 100, teamRank: 1, teamOf: 2, chickenRank: null, bestHandRank: 3,
  });
  assert.deepEqual(standingOf("t", 2, data), {
    tournamentId: "t", rank: 2, of: 4, points: 5, score: 50, teamRank: 2, teamOf: 2, chickenRank: 1, bestHandRank: 1,
  });
});

test("chicken hand places break a tie of hands with the points and the score", () => {
  const tied = { ...data, chicken: { 2: { "1_1": 1 }, 3: { "1_1": 1 }, 4: { "1_1": 1 } } };
  assert.equal(standingOf("t", 2, tied)?.chickenRank, 1);
  assert.equal(standingOf("t", 3, tied)?.chickenRank, 2);
  assert.equal(standingOf("t", 4, tied)?.chickenRank, 3);
});

test("standing has no team place without teams and no hand places without hands", () => {
  const plain = { totals: data.totals };
  assert.deepEqual(standingOf("t", 2, plain), {
    tournamentId: "t", rank: 2, of: 4, points: 5, score: 50, teamRank: null, teamOf: null, chickenRank: null, bestHandRank: null,
  });
});

test("equal best hands share a place and a slot without a result has no standing", () => {
  const equal = { ...data, best: { 1: { "1_1": 50 }, 2: { "1_1": 50 }, 3: { "1_1": 20 } } };
  assert.equal(standingOf("t", 1, equal)?.bestHandRank, 1);
  assert.equal(standingOf("t", 2, equal)?.bestHandRank, 1);
  assert.equal(standingOf("t", 3, equal)?.bestHandRank, 3);
  assert.equal(standingOf("t", 9, data), null);
  assert.equal(standingOf("t", 1, {}), null);
});
