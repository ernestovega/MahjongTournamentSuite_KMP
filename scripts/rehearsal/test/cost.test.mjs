// The cost report of the game day (cost.mjs). Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";

import { costReport, DEFAULT_PRICES, markdownCost, METRICS, priceOf, projectUse, readMonitoring, scales, sharesOfScreens, sumOfSeries } from "../cost.mjs";
import { Metrics, parseUsage } from "../api.mjs";

const prices = JSON.parse(readFileSync(DEFAULT_PRICES, "utf8"));

test("the API usage header turns into numbers, and a missing or broken one into null", () => {
  assert.deepEqual(parseUsage("reads=12; writes=3; deletes=0"), { reads: 12, writes: 3, deletes: 0 });
  assert.equal(parseUsage(null), null);
  assert.equal(parseUsage("reads=x"), null);
});

test("the metrics of the bots keep the Firestore operations and the bytes of each label", () => {
  const metrics = new Metrics();
  metrics.record("GET manifest", 200, 10, null, { reads: 2, writes: 0, deletes: 0, bytes: 300 });
  metrics.record("GET manifest", 200, 10, null, { bytes: 100 }); // an old backend without the header
  metrics.record("POST hand", 200, 10, null, { reads: 5, writes: 3, deletes: 0, bytes: 50 });
  const labels = metrics.summary().labels;
  assert.deepEqual(labels["GET manifest"].usage, { measured: 1, reads: 2, writes: 0, deletes: 0, bytes: 400 });
  assert.equal(labels["POST hand"].usage.writes, 3);
});

test("a timeSeries answer sums all its points, ints and doubles", () => {
  assert.equal(sumOfSeries({ timeSeries: [{ points: [{ value: { int64Value: "10" } }, { value: { int64Value: "5" } }] }, { points: [{ value: { doubleValue: 1.5 } }] }] }), 16.5);
  assert.equal(sumOfSeries({}), 0);
});

test("Cloud Monitoring is read metric by metric, and a refusal is kept apart", async () => {
  const asked = [];
  const fetchImpl = async (url) => {
    asked.push(decodeURIComponent(url));
    if (url.includes("storage.googleapis.com")) return { ok: false, status: 403, json: async () => ({ error: { message: "Permission denied" } }) };
    return { ok: true, status: 200, json: async () => ({ timeSeries: [{ points: [{ value: { int64Value: "7" } }] }] }) };
  };
  const { values, errors } = await readMonitoring({ project: "p", since: "2026-11-19T10:00:00Z", until: "2026-11-19T11:00:00Z", token: "t", fetchImpl });
  assert.equal(asked.length, Object.keys(METRICS).length);
  assert.ok(asked[0].includes('metric.type="firestore.googleapis.com/document/read_count"'));
  assert.equal(values.firestoreReads, 7);
  assert.equal(values.storageSentBytes, undefined);
  assert.match(errors.storageSentBytes, /403 Permission denied/);
});

test("the screens share comes from GET calls, the rest from the actions", () => {
  const shares = sharesOfScreens({
    "GET manifest": { count: 90, usage: { reads: 900, bytes: 9000 } },
    "POST hand": { count: 10, usage: { reads: 100, bytes: 1000 } },
  });
  assert.deepEqual(shares, { reads: 0.9, requests: 0.9, bytes: 0.9 });
  assert.deepEqual(sharesOfScreens({}), { reads: null, requests: null, bytes: null });
});

test("the projection scales the screens by players and minutes, and the writes by players and rounds", () => {
  const scale = scales({
    scenario: { players: 64, rounds: 7 },
    target: { players: 32, rounds: 7, roundMinutes: 100, breakMinutes: 20 },
    windowMinutes: 420,
  });
  assert.equal(scale.realMinutes, 840);
  assert.equal(scale.screens, 0.5 * 2);
  assert.equal(scale.actions, 0.5);
  const projected = projectUse({ firestoreReads: 1000, firestoreWrites: 100 }, { reads: 0.75 }, scale);
  assert.equal(projected.firestoreReads, 1000 * (0.75 * 1 + 0.25 * 0.5));
  assert.equal(projected.firestoreWrites, 50);
});

test("the price takes off the free tiers only when asked, Firestore by day", () => {
  const use = { firestoreReads: 150_000, requests: 3_000_000, cpuSeconds: 200_000 };
  const gross = priceOf(use, prices, { days: 2 });
  assert.ok(Math.abs(gross.lines.firestoreReads - 1.5 * prices.firestore.readsPer100k) < 1e-9);
  const free = priceOf(use, prices, { days: 2, free: true });
  // 75 000 a day, 50 000 of them free.
  assert.ok(Math.abs(free.lines.firestoreReads - 0.5 * prices.firestore.readsPer100k) < 1e-9);
  assert.ok(Math.abs(free.lines.requests - prices.cloudRun.perMillionRequests) < 1e-9);
  assert.ok(Math.abs(free.lines.cpu - 20_000 * prices.cloudRun.vcpuSecond) < 1e-9);
  assert.ok(free.total < gross.total);
});

test("the report has the projection, the costs and a markdown table, and says when metrics are missing", () => {
  const report = costReport({
    measured: { firestoreReads: 500_000, firestoreWrites: 5_000, requests: 200_000, cpuSeconds: 3_000, memoryGibSeconds: 1_500, apiSentBytes: 2 * 1024 ** 3 },
    errors: { storageSentBytes: "403 Permission denied" },
    labels: { "GET manifest": { count: 100, usage: { measured: 100, reads: 400, writes: 0, deletes: 0, bytes: 1000 } }, "POST hand": { count: 5, usage: { measured: 5, reads: 50, writes: 15, deletes: 0, bytes: 10 } } },
    scenario: { name: "Game day medium", players: 64, rounds: 7 },
    target: { players: 60, spectators: 20, rounds: 7, roundMinutes: 120, breakMinutes: 15, days: 2, tournamentsPerMonth: 1 },
    windowMinutes: 105,
    prices,
    web: { mb: 15, loadsPerClient: 2 },
  });
  assert.ok(report.tournament.firestoreReads > report.measured.firestoreReads);
  assert.equal(report.readsByLabel[0].label, "GET manifest");
  assert.equal(report.readsByLabel[0].readsPerCall, 4);
  assert.ok(report.cost.month.total >= report.cost.monthWithFreeTier.total);
  const markdown = markdownCost(report, "Cost of Game day medium");
  assert.match(markdown, /### Cost of Game day medium/);
  assert.match(markdown, /roles\/monitoring.viewer/);
  assert.match(markdown, /\| \*\*Total\*\* \|/);
  assert.match(markdown, /\| GET manifest \| 100 \| 400 \| 4\.0 \|/);
});
