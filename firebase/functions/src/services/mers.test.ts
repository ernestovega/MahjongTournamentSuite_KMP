import assert from "node:assert/strict";
import test from "node:test";

import { calculateMers, inclusiveDayCount } from "./mers";

test("inclusiveDayCount counts both event dates", () => {
  assert.equal(inclusiveDayCount("2025-02-01", "2025-02-02"), 2);
});

test("calculateMers adds player and country bonuses", () => {
  assert.equal(calculateMers({
    startDate: "2025-02-01",
    endDate: "2025-02-02",
    participantCount: 100,
    representedCountries: ["ESP", "FRA", "ITA", "NED", "SUI", "GER"],
  }), 3.5);
});
