// The HTTP client of the rehearsal bots (api.mjs). Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";

import { Api, Metrics, parseArgs, parseRange, percentile, rehearsalEmail } from "../api.mjs";
import { withFetch } from "./support.mjs";

test("the summary of the metrics takes the max of a long list without a spread", () => {
  // The medium scenario makes hundreds of thousands of calls: Math.max(...list) overflows the stack there.
  const metrics = new Metrics();
  for (let index = 0; index < 300_000; index += 1) metrics.record("GET manifest", 200, index % 1_000);
  metrics.record("GET manifest", 200, 4_321);
  const entry = metrics.summary().labels["GET manifest"];
  assert.equal(entry.count, 300_001);
  assert.equal(entry.max, 4_321);
  assert.equal(entry.p50, 500);
});

test("the metrics keep the refusals with their message, by status and label", () => {
  const metrics = new Metrics();
  metrics.record("POST hand", 409, 10, "The table changed");
  metrics.record("POST hand", 409, 10, "The table changed");
  metrics.record("POST hand", 200, 10, "ignored");
  assert.deepEqual(metrics.summary().refusals, { "409 POST hand: The table changed": 2 });
  assert.deepEqual(metrics.summary().labels["POST hand"].statuses, { 200: 1, 409: 2 });
});

test("a percentile rounds up to the next value and is null for no values", () => {
  assert.equal(percentile([], 95), null);
  assert.equal(percentile([5, 1, 3, 2, 4], 50), 3);
  assert.equal(percentile(Array.from({ length: 100 }, (_, index) => index + 1), 95), 95);
});

test("a read that fails on the network is tried once more, and a write is not", async () => {
  let calls = 0;
  const metrics = new Metrics();
  const api = new Api("https://staging.test/api", metrics);
  await withFetch(() => {
    calls += 1;
    if (calls === 1) throw new TypeError("fetch failed");
    return [200, { ok: true }];
  }, async () => {
    const reply = await api.call("GET", "/public/tournaments", { label: "GET tournaments" });
    assert.equal(reply.status, 200);
  });
  assert.equal(calls, 2);
  assert.equal(metrics.events[0].kind, "network-retry");
  // Only the answer counts in the metrics, not the failed try.
  assert.deepEqual(metrics.summary().labels["GET tournaments"].statuses, { 200: 1 });

  calls = 0;
  await withFetch(() => {
    calls += 1;
    throw new TypeError("fetch failed");
  }, async () => {
    const reply = await api.call("POST", "/player/x", { body: {}, label: "POST x" });
    assert.equal(reply.status, 0);
  });
  assert.equal(calls, 1);
});

test("the email of a rehearsal account, the ranges of slots and the arguments", () => {
  assert.equal(rehearsalEmail(" Admin+old@Example.test ", "j07"), "admin+mts-j07@example.test");
  assert.throws(() => rehearsalEmail("not-an-email", "j01"));
  assert.deepEqual(parseRange("2,3,5-7"), [2, 3, 5, 6, 7]);
  assert.deepEqual(parseRange(""), []);
  assert.deepEqual(parseArgs(["--scenario=small", "--verbose", "plain"]), { scenario: "small", verbose: true });
});
