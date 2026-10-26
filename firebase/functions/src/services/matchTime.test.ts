import assert from "node:assert/strict";
import test from "node:test";

import { isSelectableTimeZone, matchPhaseAt, normalizeMatchExtraMinutes, normalizeTimeZone, zonedEpochMs } from "./matchTime";
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

test("the phase follows the start, the duration and the extra time", () => {
  const schedule = { date: "2026-10-10", startTime: "10:00" };
  const base = { timeZone: "Europe/Madrid", durationMinutes: 115, extraMinutes: 15 };
  const at = (time: string) => zonedEpochMs("2026-10-10", time, "Europe/Madrid") as number;

  assert.equal(matchPhaseAt(schedule, { ...base, endMode: "STOP" }, at("09:59")), "NOT_STARTED");
  assert.equal(matchPhaseAt(schedule, { ...base, endMode: "STOP" }, at("11:54")), "PLAYING");
  assert.equal(matchPhaseAt(schedule, { ...base, endMode: "STOP" }, at("11:55")), "OVER");
  assert.equal(matchPhaseAt(schedule, { ...base, endMode: "FINISH_HAND" }, at("11:55")), "EXTRA_TIME");
  assert.equal(matchPhaseAt(schedule, { ...base, endMode: "FINISH_HAND" }, at("12:09")), "EXTRA_TIME");
  assert.equal(matchPhaseAt(schedule, { ...base, endMode: "FINISH_HAND" }, at("12:10")), "OVER");
});

test("the phase is unknown without time zone or date", () => {
  const rules = { timeZone: null, durationMinutes: 120, extraMinutes: 15, endMode: "STOP" as const };
  assert.equal(matchPhaseAt({ date: "2026-10-10", startTime: "10:00" }, rules, 0), null);
  assert.equal(matchPhaseAt({ date: null, startTime: null }, { ...rules, timeZone: "UTC" }, 0), null);
});

test("one close task is planned for each round whose end is in the future", () => {
  const tournament = {
    numRounds: 3,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: "FINISH_HAND",
    matchExtraMinutes: 15,
    roundSchedules: [
      { roundId: 1, date: "2026-10-10", startTime: "10:00" },
      { roundId: 2, date: "2026-10-10", startTime: "14:00" },
    ],
  };
  const now = Date.UTC(2026, 9, 10, 12, 30);

  const tasks = roundCloseTasks("t1", tournament, now);

  // Round 1 ends at 12:15, which is past. Round 2 ends at 16:15. Round 3 has no date.
  assert.deepEqual(tasks.map((task) => [task.roundId, task.at.getTime()]), [[2, Date.UTC(2026, 9, 10, 16, 15)]]);
  assert.equal(tasks[0].id, `t1_r2_${Date.UTC(2026, 9, 10, 16, 15)}`);
  assert.deepEqual(roundCloseTasks("t1", { ...tournament, timeZone: null }, now), []);
});

test("a round that ends more than 29 days ahead gets no task yet", () => {
  const tournament = {
    numRounds: 1,
    timeZone: "UTC",
    matchDurationMinutes: 120,
    matchEndMode: "STOP",
    roundSchedules: [{ roundId: 1, date: "2026-12-12", startTime: "10:00" }],
  };

  assert.deepEqual(roundCloseTasks("t1", tournament, Date.UTC(2026, 9, 23)), []);
  assert.equal(roundCloseTasks("t1", tournament, Date.UTC(2026, 10, 20)).length, 1);
});

test("only region zones and UTC are offered", () => {
  assert.equal(isSelectableTimeZone("Europe/Madrid"), true);
  assert.equal(isSelectableTimeZone("America/Argentina/Buenos_Aires"), true);
  assert.equal(isSelectableTimeZone("UTC"), true);
  assert.equal(isSelectableTimeZone("Etc/UTC"), false);
  assert.equal(isSelectableTimeZone("Madrid"), false);
  assert.equal(isSelectableTimeZone("EST"), false);
});
