import assert from "node:assert/strict";
import test from "node:test";

import {
  isSelectableTimeZone,
  matchPhaseAt,
  normalizeMatchExtraMinutes,
  normalizeTimeZone,
  plannedRoundClocks,
  seatingIsOpen,
  tournamentMatchPhase,
  zonedEpochMs,
} from "./matchTime";
import type { RoundClock } from "./roundClock";
import { roundCloseTasks } from "./matchTimeTaskService";

test("converts a local time to an instant, also near summer time", () => {
  // Madrid is UTC+2 on 2026-10-10 and UTC+1 on 2026-12-10.
  assert.equal(zonedEpochMs("2026-10-10", "10:00", "Europe/Madrid"), Date.UTC(2026, 9, 10, 8, 0));
  assert.equal(zonedEpochMs("2026-12-10", "10:00", "Europe/Madrid"), Date.UTC(2026, 11, 10, 9, 0));
  assert.equal(zonedEpochMs("2026-10-10", "10:00", "UTC"), Date.UTC(2026, 9, 10, 10, 0));
  assert.equal(zonedEpochMs("bad", "10:00", "UTC"), null);
});

test("time zone and extra minutes are validated", () => {
  assert.equal(normalizeTimeZone(""), null);
  assert.equal(normalizeTimeZone(" Europe/Madrid "), "Europe/Madrid");
  assert.throws(() => normalizeTimeZone("Mars/Base"));
  assert.equal(normalizeMatchExtraMinutes(undefined), 15);
  assert.throws(() => normalizeMatchExtraMinutes(0));
});

const MIN = 60_000;
const clockAt = (startedAtMs: number, extra: Partial<RoundClock> = {}): RoundClock => ({
  startedAtMs, pausedAtMs: null, pausedMs: 0, adjustMs: 0, ...extra,
});

test("the phase follows the Play of the Timer, the duration and the extra time", () => {
  const start = Date.UTC(2026, 9, 10, 8, 0);
  const base = { timeZone: "Europe/Madrid", durationMinutes: 115, extraMinutes: 15 };
  const stop = { ...base, endMode: "STOP" as const };
  const finish = { ...base, endMode: "FINISH_HAND" as const };

  assert.equal(matchPhaseAt(null, stop, start + 500 * MIN), "NOT_STARTED");
  assert.equal(matchPhaseAt(clockAt(start), stop, start), "PLAYING");
  assert.equal(matchPhaseAt(clockAt(start), stop, start + 114 * MIN), "PLAYING");
  assert.equal(matchPhaseAt(clockAt(start), stop, start + 115 * MIN), "OVER");
  assert.equal(matchPhaseAt(clockAt(start), finish, start + 115 * MIN), "EXTRA_TIME");
  assert.equal(matchPhaseAt(clockAt(start), finish, start + 129 * MIN), "EXTRA_TIME");
  assert.equal(matchPhaseAt(clockAt(start), finish, start + 130 * MIN), "OVER");
});

test("a pause stops the match time and an adjustment moves the end", () => {
  const start = Date.UTC(2026, 9, 10, 8, 0);
  const rules = { timeZone: "UTC", durationMinutes: 120, extraMinutes: 15, endMode: "STOP" as const };
  const paused = clockAt(start, { pausedAtMs: start + 100 * MIN });

  // A paused clock stays at 100 minutes of play, however long the pause lasts.
  assert.equal(matchPhaseAt(paused, rules, start + 500 * MIN), "PLAYING");
  // 10 minutes of pauses that are over move the end 10 minutes later.
  assert.equal(matchPhaseAt(clockAt(start, { pausedMs: 10 * MIN }), rules, start + 125 * MIN), "PLAYING");
  assert.equal(matchPhaseAt(clockAt(start, { pausedMs: 10 * MIN }), rules, start + 130 * MIN), "OVER");
  assert.equal(matchPhaseAt(clockAt(start, { adjustMs: -30 * MIN }), rules, start + 90 * MIN), "OVER");
});

test("the phase of a tournament reads the clock of the round, not the planned time", () => {
  const start = Date.UTC(2026, 9, 10, 8, 0);
  const tournament = {
    numRounds: 2,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    roundSchedules: [{ roundId: 1, date: "2026-10-10", startTime: "06:00" }, { roundId: 2, date: "2026-10-10", startTime: "06:00" }],
    roundClocks: { 1: clockAt(start) },
  };

  assert.equal(tournamentMatchPhase(tournament, 1, start + 10 * MIN), "PLAYING");
  // The planned time of round 2 is past, but nobody pressed Play.
  assert.equal(tournamentMatchPhase(tournament, 2, start + 10 * MIN), "NOT_STARTED");
});

test("the seats open 10 minutes before the planned start and close when the time is over", () => {
  const planned = Date.UTC(2026, 9, 10, 10, 0);
  const tournament = {
    numRounds: 1,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: "STOP",
    roundSchedules: [{ roundId: 1, date: "2026-10-10", startTime: "10:00" }],
  };

  assert.equal(seatingIsOpen(tournament, 1, planned - 11 * MIN), false);
  assert.equal(seatingIsOpen(tournament, 1, planned - 10 * MIN), true);
  // A late round stays open until the Play.
  assert.equal(seatingIsOpen(tournament, 1, planned + 60 * MIN), true);
  // A round that started early is open at once.
  const early = { ...tournament, roundClocks: { 1: clockAt(planned - 60 * MIN) } };
  assert.equal(seatingIsOpen(early, 1, planned - 59 * MIN), true);
  assert.equal(seatingIsOpen(early, 1, planned + 61 * MIN), false);
  // Without a planned time the seats are always open before the end.
  assert.equal(seatingIsOpen({ ...tournament, timeZone: null }, 1, 0), true);
});

test("one close task is planned for each running round whose end is in the future", () => {
  const tournament = {
    numRounds: 3,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: "FINISH_HAND",
    matchExtraMinutes: 15,
    roundClocks: {
      1: clockAt(Date.UTC(2026, 9, 10, 10, 0)),
      2: clockAt(Date.UTC(2026, 9, 10, 12, 0)),
      3: clockAt(Date.UTC(2026, 9, 10, 12, 0), { pausedAtMs: Date.UTC(2026, 9, 10, 12, 10) }),
    },
  };
  const now = Date.UTC(2026, 9, 10, 12, 30);

  const tasks = roundCloseTasks("t1", tournament, now);

  // Round 1 ends at 12:15, which is past. Round 2 ends at 14:15. Round 3 is paused, so its end is not known.
  assert.deepEqual(tasks.map((task) => [task.roundId, task.at.getTime()]), [[2, Date.UTC(2026, 9, 10, 14, 15)]]);
  assert.equal(tasks[0].id, `t1_r2_${Date.UTC(2026, 9, 10, 14, 15)}`);
  assert.deepEqual(roundCloseTasks("t1", tournament, now, 1), []);
  assert.equal(roundCloseTasks("t1", tournament, now, 2).length, 1);
  assert.deepEqual(roundCloseTasks("t1", { ...tournament, roundClocks: {} }, now), []);
});

test("only region zones and UTC are offered", () => {
  assert.equal(isSelectableTimeZone("Europe/Madrid"), true);
  assert.equal(isSelectableTimeZone("America/Argentina/Buenos_Aires"), true);
  assert.equal(isSelectableTimeZone("UTC"), true);
  assert.equal(isSelectableTimeZone("Etc/UTC"), false);
  assert.equal(isSelectableTimeZone("Madrid"), false);
  assert.equal(isSelectableTimeZone("EST"), false);
});

test("gives a clock at the planned start to each past round without one", () => {
  const tournament = {
    numRounds: 4,
    timeZone: "UTC",
    roundSchedules: [
      { roundId: 1, date: "2026-10-10", startTime: "10:00" },
      { roundId: 2, date: "2026-10-10", startTime: "12:00" },
      { roundId: 3, date: "2026-10-10", startTime: "14:00" },
    ],
    roundClocks: { 2: { startedAtMs: Date.UTC(2026, 9, 10, 12, 7), pausedAtMs: null, pausedMs: 0, adjustMs: 0 } },
  };

  const clocks = plannedRoundClocks(tournament, Date.UTC(2026, 9, 10, 13, 0));

  // Round 2 keeps its own clock, round 3 has not reached its start and round 4 has no date.
  assert.deepEqual([...clocks.entries()], [[1, { startedAtMs: Date.UTC(2026, 9, 10, 10, 0), pausedAtMs: null, pausedMs: 0, adjustMs: 0 }]]);
  assert.equal(plannedRoundClocks({ ...tournament, isCompleted: true }, Date.UTC(2026, 9, 10, 13, 0)).size, 0);
  assert.equal(plannedRoundClocks({ ...tournament, timeZone: "" }, Date.UTC(2026, 9, 10, 13, 0)).size, 0);
});
