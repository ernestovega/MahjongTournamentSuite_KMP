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
  // A late round stays open until the Play, but only during its planned match time.
  assert.equal(seatingIsOpen(tournament, 1, planned + 60 * MIN), true);
  assert.equal(seatingIsOpen(tournament, 1, planned + 119 * MIN), true);
  assert.equal(seatingIsOpen(tournament, 1, planned + 120 * MIN), false);
  // With FINISH_HAND the extra time counts too.
  const finishHand = { ...tournament, matchEndMode: "FINISH_HAND", matchExtraMinutes: 15 };
  assert.equal(seatingIsOpen(finishHand, 1, planned + 134 * MIN), true);
  assert.equal(seatingIsOpen(finishHand, 1, planned + 135 * MIN), false);
  // A gong after the planned time opens the seats again for the whole match.
  const late = { ...tournament, roundClocks: { 1: clockAt(planned + 150 * MIN) } };
  assert.equal(seatingIsOpen(late, 1, planned + 200 * MIN), true);
  // A round that started early is open at once.
  const early = { ...tournament, roundClocks: { 1: clockAt(planned - 60 * MIN) } };
  assert.equal(seatingIsOpen(early, 1, planned - 59 * MIN), true);
  assert.equal(seatingIsOpen(early, 1, planned + 61 * MIN), false);
  // Without a planned time the seats are open until a later round starts.
  assert.equal(seatingIsOpen({ ...tournament, timeZone: null }, 1, 0), true);
  const twoRounds = { ...tournament, numRounds: 2, timeZone: null, roundClocks: { 2: clockAt(planned) } };
  assert.equal(seatingIsOpen(twoRounds, 1, planned + MIN), false);
  // A late round pushes the next one: its time counts from the end of the round before.
  const pushed = {
    ...tournament,
    numRounds: 2,
    roundSchedules: [{ roundId: 1, date: "2026-10-10", startTime: "10:00" }, { roundId: 2, date: "2026-10-10", startTime: "10:00" }],
    roundClocks: { 1: clockAt(planned) },
  };
  assert.equal(seatingIsOpen(pushed, 2, planned + 60 * MIN), true);
  assert.equal(seatingIsOpen(pushed, 2, planned + 200 * MIN), true);
  assert.equal(seatingIsOpen(pushed, 2, planned + 240 * MIN), false);
  // While the round before is still played, the next one never closes.
  const running = { ...pushed, roundClocks: { 1: clockAt(planned, { pausedAtMs: planned + 30 * MIN }) } };
  assert.equal(seatingIsOpen(running, 2, planned + 600 * MIN), true);
  // Rounds of a past day that nobody started are all over.
  const pastDay = { ...pushed, roundClocks: {} };
  assert.equal(seatingIsOpen(pastDay, 1, planned + 24 * 60 * MIN), false);
  assert.equal(seatingIsOpen(pastDay, 2, planned + 24 * 60 * MIN), false);
  assert.equal(seatingIsOpen(pastDay, 2, planned + 130 * MIN), true);
  // The window follows the changes of the organizers: a new planned time, a longer match, time added, a pause.
  const moved = { ...tournament, roundSchedules: [{ roundId: 1, date: "2026-10-10", startTime: "11:00" }] };
  assert.equal(seatingIsOpen(tournament, 1, planned + 125 * MIN), false);
  assert.equal(seatingIsOpen(moved, 1, planned + 125 * MIN), true);
  assert.equal(seatingIsOpen(moved, 1, planned + 180 * MIN), false);
  assert.equal(seatingIsOpen({ ...tournament, matchDurationMinutes: 180 }, 1, planned + 125 * MIN), true);
  const added = { ...tournament, roundClocks: { 1: clockAt(planned, { adjustMs: 30 * MIN }) } };
  assert.equal(seatingIsOpen(added, 1, planned + 145 * MIN), true);
  assert.equal(seatingIsOpen(added, 1, planned + 150 * MIN), false);
  const pausedClock = { ...tournament, roundClocks: { 1: clockAt(planned, { pausedAtMs: planned + 30 * MIN }) } };
  assert.equal(seatingIsOpen(pausedClock, 1, planned + 600 * MIN), true);
  // Another time zone moves the planned instant: 10:00 in Madrid (UTC+2) is 08:00 UTC, so it ends at 10:00 UTC.
  const madrid = { ...tournament, timeZone: "Europe/Madrid" };
  assert.equal(seatingIsOpen(madrid, 1, planned - 1 * MIN), true);
  assert.equal(seatingIsOpen(madrid, 1, planned), false);
  // Without a time zone the planned time is no instant: the end of the round before does not close the next one.
  const noZone = { ...pushed, timeZone: null };
  assert.equal(seatingIsOpen(noZone, 2, planned + 600 * MIN), true);
  // A completed tournament takes no seats.
  assert.equal(seatingIsOpen({ ...tournament, isCompleted: true }, 1, planned), false);
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
