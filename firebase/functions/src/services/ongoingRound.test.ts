import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { matchPhaseAt, zonedEpochMs } from "./matchTime";
import { nextRoundWithoutHands, planOngoingRound, zonedSlot } from "./ongoingRound";

const ZONE = "Europe/Madrid";
const TIMES = ["10:00", "12:30", "15:00", "18:00", "20:30", "23:00"];

/** 12 rounds over 2 days, 6 a day, as the ongoing production tournament. */
function tournament(extra: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    numRounds: 12,
    timeZone: ZONE,
    matchDurationMinutes: 120,
    matchEndMode: "FINISH_HAND",
    matchExtraMinutes: 15,
    eventStartDate: "2026-11-12",
    eventEndDate: "2026-11-13",
    roundSchedules: Array.from({ length: 12 }, (_, index) => ({
      roundId: index + 1,
      date: index < 6 ? "2026-11-12" : "2026-11-13",
      startTime: TIMES[index % 6],
    })),
    agendaItems: [{ title: "Welcome", date: "2026-11-12", startTime: "09:15" }, { title: "Party", date: "2026-11-13", startTime: null }],
    roundClocks: {
      1: { startedAtMs: zonedEpochMs("2026-11-12", "10:00", ZONE), pausedAtMs: null, pausedMs: 0, adjustMs: 0 },
      7: { startedAtMs: zonedEpochMs("2026-11-13", "10:00", ZONE), pausedAtMs: null, pausedMs: 0, adjustMs: 0 },
      9: { startedAtMs: zonedEpochMs("2026-11-13", "15:00", ZONE), pausedAtMs: null, pausedMs: 0, adjustMs: 0 },
    },
    ...extra,
  };
}

const rules = { timeZone: ZONE, durationMinutes: 120, endMode: "FINISH_HAND" as const, extraMinutes: 15 };

describe("ongoing round", () => {
  it("is the round after the last round with hands", () => {
    assert.equal(nextRoundWithoutHands([]), 1);
    assert.equal(nextRoundWithoutHands([1, 2, 5, 3]), 6);
  });

  it("formats an instant in the time zone of the tournament", () => {
    assert.deepEqual(zonedSlot(Date.UTC(2026, 10, 13, 23, 30), ZONE), { date: "2026-11-14", startTime: "00:30" });
  });

  it("moves every planned time so the round starts now and the earlier rounds are over", () => {
    const now = (zonedEpochMs("2026-11-14", "13:47", ZONE) as number) + 25_000;
    const plan = planOngoingRound(tournament(), [1, 2, 3, 4, 5, 6, 7, 8], now);

    assert.equal(plan.roundId, 9);
    assert.deepEqual(plan.previousStart, { date: "2026-11-13", startTime: "15:00" });
    assert.deepEqual(plan.roundSchedules[8], { roundId: 9, date: "2026-11-14", startTime: "13:47" });
    // The rounds keep the time between them.
    assert.deepEqual(plan.roundSchedules[7], { roundId: 8, date: "2026-11-14", startTime: "11:17" });
    assert.deepEqual(plan.roundSchedules[11], { roundId: 12, date: "2026-11-14", startTime: "21:47" });
    assert.deepEqual(plan.roundSchedules[0], { roundId: 1, date: "2026-11-13", startTime: "08:47" });
    assert.equal(plan.eventStartDate, "2026-11-13");
    assert.equal(plan.eventEndDate, "2026-11-14");
    assert.deepEqual(plan.agendaItems, [
      { title: "Welcome", date: "2026-11-13", startTime: "08:02" },
      { title: "Party", date: "2026-11-14", startTime: null },
    ]);

    assert.deepEqual([...plan.clocks.keys()], [1, 2, 3, 4, 5, 6, 7, 8]);
    for (const clock of plan.clocks.values()) assert.equal(matchPhaseAt(clock, rules, now), "OVER");
    assert.deepEqual(plan.clearedClocks, [9]);
  });

  it("starts round 1 when no round has hands", () => {
    const now = zonedEpochMs("2026-11-01", "10:00", ZONE) as number;
    const plan = planOngoingRound(tournament({ roundClocks: undefined }), [], now);
    assert.equal(plan.roundId, 1);
    assert.equal(plan.clocks.size, 0);
    assert.deepEqual(plan.clearedClocks, []);
    assert.deepEqual(plan.roundSchedules[0], { roundId: 1, date: "2026-11-01", startTime: "10:00" });
    assert.equal(plan.eventStartDate, "2026-11-01");
  });

  it("refuses a tournament without empty rounds or already completed", () => {
    const now = Date.now();
    assert.throws(() => planOngoingRound(tournament(), Array.from({ length: 12 }, (_, index) => index + 1), now), /Every round/);
    assert.throws(() => planOngoingRound(tournament({ isCompleted: true }), [], now), /completed/);
  });
});
