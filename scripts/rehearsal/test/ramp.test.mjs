import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { margin, ramp, stagePhones, stageVerdict } from "../ramp.mjs";
import { withFetch } from "./support.mjs";

const entry = (statuses, p95) => ({ count: Object.values(statuses).reduce((a, b) => a + b, 0), statuses, p95 });

describe("load ramp", () => {
  it("each stage multiplies the players, the room screen and the spectators", () => {
    assert.deepEqual(stagePhones(60, 1), { players: 60, spectators: 7 });
    assert.deepEqual(stagePhones(60, 4), { players: 240, spectators: 28 });
  });

  it("a stage holds with a fast p95 and less than 1 % of failed calls", () => {
    assert.equal(stageVerdict({ labels: { a: entry({ 200: 995, 429: 5 }, 900) } }, 1500).holds, true);
    assert.equal(stageVerdict({ labels: { a: entry({ 200: 980, 503: 20 }, 900) } }, 1500).holds, false);
    assert.equal(stageVerdict({ labels: { a: entry({ 200: 980, 0: 20 }, 900) } }, 1500).holds, false);
    assert.equal(stageVerdict({ labels: { a: entry({ 200: 980, 403: 20 }, 900) } }, 1500).holds, false, "a refused call fails");
    const slow = stageVerdict({ labels: { a: entry({ 200: 10 }, 200), b: entry({ 200: 10 }, 1600) } }, 1500);
    assert.equal(slow.holds, false);
    assert.equal(slow.worstLabel, "b");
    assert.equal(stageVerdict({ labels: {} }, 1500).holds, false, "a stage without calls measured nothing");
  });

  it("the margin is the last stage that holds with all the stages before it", () => {
    const stage = (multiplier, holds) => ({ multiplier, verdict: { holds } });
    assert.equal(margin([stage(1, true), stage(2, true), stage(4, false)]), 2);
    assert.equal(margin([stage(1, false), stage(2, true)]), 0);
    assert.equal(margin([stage(1, true), stage(2, true), stage(4, true)]), 4);
  });

  it("runs the stages against the round in play and stops at the first that fails", async () => {
    const start = Date.now();
    const report = await withFetch((method, path) => {
      if (path === "/public/tournaments/t1/rounds") return [200, { rounds: [{ roundId: 1 }, { roundId: 2 }] }];
      if (path === "/public/tournaments/t1/rounds/2/tables-with-hands") return [200, { tables: [{ table: { tableId: 1 }, hands: [] }] }];
      if (path === "/public/tournaments/t1/rounds/1/tables-with-hands") {
        return [200, { tables: [{ table: { tableId: 1 }, hands: [{ isDone: true }] }, { table: { tableId: 2 }, hands: [] }] }];
      }
      if (path === "/public/tournaments/t1/sync/manifest") return Date.now() - start > 3_500 ? [503, { message: "busy" }] : [200, { tableVersions: { "1_1": 3 } }];
      if (path.startsWith("/public/tournaments/t1/tables/1/")) return [200, { table: {} }];
      return [404, { message: path }];
    }, () => ramp({ base: "http://local/api", tournamentId: "t1", players: 4, stages: [1, 2], stageSeconds: 3, p95Limit: 1500, log: () => {} }));

    assert.equal(report.roundId, 1, "the last round with a done hand");
    assert.equal(report.tables, 2);
    assert.equal(report.stages[0].verdict.holds, true);
    assert.equal(report.stages[1].verdict.holds, false);
    assert.equal(report.margin, 1);
  });
});
