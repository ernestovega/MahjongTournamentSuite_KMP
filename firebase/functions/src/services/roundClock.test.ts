import assert from "node:assert/strict";
import test from "node:test";

import {
  applyRoundClockAction,
  clockEndMs,
  clockRemainingMs,
  parseRoundClockAction,
  readStoredRoundClocks,
  roundClockViews,
  type RoundClock,
} from "./roundClock";

const MIN = 60_000;
const NOW = Date.UTC(2026, 10, 12, 10, 0);
const started = (extra: Partial<RoundClock> = {}): RoundClock => ({ startedAtMs: NOW, pausedAtMs: null, pausedMs: 0, adjustMs: 0, ...extra });

test("Play starts the clock at the time of the server", () => {
  assert.deepEqual(applyRoundClockAction(null, { kind: "start", agoMs: 0 }, 120, NOW), started());
});

test("a second Play changes nothing, so the first Timer wins", () => {
  const clock = started();
  assert.equal(applyRoundClockAction(clock, { kind: "start", agoMs: 0 }, 120, NOW + 5 * MIN), clock);
});

test("\"started N minutes ago\" moves the start back, at most the whole match", () => {
  assert.equal(applyRoundClockAction(null, { kind: "start", agoMs: 7 * MIN }, 120, NOW)?.startedAtMs, NOW - 7 * MIN);
  assert.equal(applyRoundClockAction(null, { kind: "start", agoMs: 500 * MIN }, 120, NOW)?.startedAtMs, NOW - 120 * MIN);
});

test("a pause freezes the remaining time and the resume adds the pause", () => {
  const paused = applyRoundClockAction(started(), { kind: "pause", agoMs: 0 }, 120, NOW + 30 * MIN) as RoundClock;
  assert.equal(paused.pausedAtMs, NOW + 30 * MIN);
  assert.equal(clockRemainingMs(paused, 120, NOW + 50 * MIN), 90 * MIN);
  assert.equal(clockEndMs(paused, 120), null);
  // A second pause keeps the first pause.
  assert.equal(applyRoundClockAction(paused, { kind: "pause", agoMs: 0 }, 120, NOW + 40 * MIN), paused);

  const resumed = applyRoundClockAction(paused, { kind: "resume", agoMs: 0 }, 120, NOW + 45 * MIN) as RoundClock;
  assert.equal(resumed.pausedAtMs, null);
  assert.equal(resumed.pausedMs, 15 * MIN);
  assert.equal(clockEndMs(resumed, 120), NOW + 135 * MIN);
  assert.equal(applyRoundClockAction(resumed, { kind: "resume", agoMs: 0 }, 120, NOW + 46 * MIN), resumed);
});

test("an action that reaches the server late counts from the moment of the press, up to 10 minutes", () => {
  const paused = applyRoundClockAction(started(), { kind: "pause", agoMs: 2 * MIN }, 120, NOW + 30 * MIN) as RoundClock;
  assert.equal(paused.pausedAtMs, NOW + 28 * MIN);
  const capped = applyRoundClockAction(started(), { kind: "pause", agoMs: 60 * MIN }, 120, NOW + 90 * MIN) as RoundClock;
  assert.equal(capped.pausedAtMs, NOW + 80 * MIN);
  // A pause never goes before the start.
  const early = applyRoundClockAction(started(), { kind: "pause", agoMs: 5 * MIN }, 120, NOW + MIN) as RoundClock;
  assert.equal(early.pausedAtMs, NOW);
});

test("Set time changes the remaining time, also during a pause", () => {
  const clock = applyRoundClockAction(started(), { kind: "setRemaining", remainingMs: 10 * MIN }, 120, NOW + 30 * MIN) as RoundClock;
  assert.equal(clockRemainingMs(clock, 120, NOW + 30 * MIN), 10 * MIN);
  assert.equal(clockEndMs(clock, 120), NOW + 40 * MIN);

  const paused = started({ pausedAtMs: NOW + 30 * MIN });
  const longer = applyRoundClockAction(paused, { kind: "setRemaining", remainingMs: 100 * MIN }, 120, NOW + 60 * MIN) as RoundClock;
  assert.equal(clockRemainingMs(longer, 120, NOW + 90 * MIN), 100 * MIN);
});

test("Pause, Resume and Set time need a started round; Reset removes the clock", () => {
  assert.throws(() => applyRoundClockAction(null, { kind: "pause", agoMs: 0 }, 120, NOW));
  assert.throws(() => applyRoundClockAction(null, { kind: "resume", agoMs: 0 }, 120, NOW));
  assert.throws(() => applyRoundClockAction(null, { kind: "setRemaining", remainingMs: 0 }, 120, NOW));
  assert.throws(() => applyRoundClockAction(started(), { kind: "setRemaining", remainingMs: 13 * 60 * MIN }, 120, NOW));
  assert.equal(applyRoundClockAction(started(), { kind: "reset" }, 120, NOW), null);
});

test("the request body names the action", () => {
  assert.deepEqual(parseRoundClockAction({ action: "start" }), { kind: "start", agoMs: 0 });
  assert.deepEqual(parseRoundClockAction({ action: "start", agoSeconds: 90 }), { kind: "start", agoMs: 90_000 });
  assert.deepEqual(parseRoundClockAction({ action: "setRemaining", remainingSeconds: 60 }), { kind: "setRemaining", remainingMs: 60_000 });
  assert.deepEqual(parseRoundClockAction({ action: "reset" }), { kind: "reset" });
  assert.throws(() => parseRoundClockAction({ action: "stop" }));
  assert.throws(() => parseRoundClockAction({ action: "start", agoSeconds: -1 }));
  assert.throws(() => parseRoundClockAction({ action: "setRemaining" }));
});

test("stored clocks are read safely and sorted by round", () => {
  const tournament = {
    numRounds: 3,
    roundClocks: {
      3: { startedAtMs: NOW, pausedAtMs: null, pausedMs: 0, adjustMs: 0 },
      1: { startedAtMs: NOW - MIN, pausedAtMs: NOW - 2 * MIN, pausedMs: -5 },
      2: { startedAtMs: "bad" },
      9: { startedAtMs: NOW },
    },
  };

  assert.deepEqual([...readStoredRoundClocks(tournament).keys()].sort(), [1, 3]);
  assert.deepEqual(roundClockViews(tournament), [
    // A pause before the start and negative pauses are damaged values. They are dropped.
    { roundId: 1, startedAtMs: NOW - MIN, pausedAtMs: null, pausedMs: 0, adjustMs: 0 },
    { roundId: 3, startedAtMs: NOW, pausedAtMs: null, pausedMs: 0, adjustMs: 0 },
  ]);
  assert.deepEqual(roundClockViews({ numRounds: 2 }), []);
});
