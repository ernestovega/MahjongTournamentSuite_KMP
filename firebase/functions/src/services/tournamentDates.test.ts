import assert from "node:assert/strict";
import test from "node:test";

import { inclusiveDayCount, isValidIsoDate, isValidIsoDateRange } from "./tournamentDates";

test("accepts valid single-day and multi-day periods", () => {
  assert.equal(isValidIsoDateRange("2026-09-23", "2026-09-23"), true);
  assert.equal(isValidIsoDateRange("2026-09-23", "2026-09-26"), true);
});

test("rejects invalid dates and reversed periods", () => {
  assert.equal(isValidIsoDate("2026-02-29"), false);
  assert.equal(isValidIsoDate("2028-02-29"), true);
  assert.equal(isValidIsoDateRange("2026-09-26", "2026-09-23"), false);
});

test("counts both dates in a valid period", () => {
  assert.equal(inclusiveDayCount("2025-02-01", "2025-02-02"), 2);
});
