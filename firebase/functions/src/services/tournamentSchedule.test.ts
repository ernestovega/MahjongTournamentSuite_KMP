import assert from "node:assert/strict";
import test from "node:test";

import { normalizeAgendaItems, normalizeRoundSchedules } from "./tournamentSchedule";

test("normalizes partial round schedules and keeps one row per round", () => {
  const schedules = normalizeRoundSchedules(
    [{ roundId: 2, date: "2026-10-03", startTime: "09:30" }],
    3,
    "2026-10-02",
    "2026-10-04",
  );

  assert.deepEqual(schedules, [
    { roundId: 1, date: null, startTime: null },
    { roundId: 2, date: "2026-10-03", startTime: "09:30" },
    { roundId: 3, date: null, startTime: null },
  ]);
});

test("rejects invalid round dates and times", () => {
  assert.throws(
    () => normalizeRoundSchedules(
      [{ roundId: 1, date: "2026-10-05", startTime: "25:00" }],
      1,
      "2026-10-02",
      "2026-10-04",
    ),
    /inside the tournament date range/,
  );
  assert.throws(
    () => normalizeRoundSchedules(
      [{ roundId: 1, date: "2026-10-02", startTime: "25:00" }],
      1,
      "2026-10-02",
      "2026-10-04",
    ),
    /HH:mm/,
  );
});

test("keeps partial agenda items and omits rows without a title", () => {
  const agenda = normalizeAgendaItems(
    [
      { title: " Registration ", date: "2026-10-02", startTime: "08:30", endTime: "09:00" },
      { title: "Awards" },
      { title: "", date: "2026-10-02" },
    ],
    "2026-10-02",
    "2026-10-04",
  );

  assert.deepEqual(agenda, [
    { title: "Registration", date: "2026-10-02", startTime: "08:30", endTime: "09:00" },
    { title: "Awards", date: null, startTime: null, endTime: null },
  ]);
});

test("rejects agenda items that end before they start", () => {
  assert.throws(
    () => normalizeAgendaItems(
      [{ title: "Lunch", startTime: "14:00", endTime: "13:00" }],
      "2026-10-02",
      "2026-10-04",
    ),
    /must not be before/,
  );
});
