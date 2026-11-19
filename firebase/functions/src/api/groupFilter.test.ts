import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { MAX_GROUP_TOURNAMENTS, readGroupFilter } from "./groupFilter";

describe("group filter", () => {
  it("reads every filter", () => {
    assert.deepEqual(
      readGroupFilter({ tournaments: "a, b,a", tournamentCountry: "es", playerCountry: "esp", from: "2025-01-01", to: "2025-12-31" }),
      { tournamentIds: ["a", "b"], tournamentCountry: "ES", playerCountry: "ESP", from: "2025-01-01", to: "2025-12-31" },
    );
  });

  it("keeps everything without filters, and reads none as the players without a country", () => {
    assert.deepEqual(readGroupFilter({}), {});
    assert.deepEqual(readGroupFilter({ playerCountry: "none" }), { playerCountry: "" });
  });

  it("refuses wrong values", () => {
    for (const query of [
      { tournamentCountry: "ESP" }, { playerCountry: "ES" }, { from: "2025-1-1" }, { from: "2026-01-01", to: "2025-01-01" },
      { tournaments: Array.from({ length: MAX_GROUP_TOURNAMENTS + 1 }, (_, index) => `t${index}`).join(",") },
      { playerCountry: ["ESP", "SWE"] },
    ]) {
      assert.throws(() => readGroupFilter(query), /Invalid|Too many|after|twice/, JSON.stringify(query).slice(0, 80));
    }
  });
});
