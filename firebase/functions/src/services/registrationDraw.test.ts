import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { drawAssignmentProblem } from "./registrationDraw";
import type { StoredRegistration } from "./registrationsService";

function person(emaId: string, teamId: string | null = null): StoredRegistration {
  return {
    emaId, name: emaId, country: "ES", email: "", status: "CONFIRMED", order: Number(emaId), paid: false, teamId,
    language: "en", source: "SELF", manageToken: "", pendingUntil: null,
  };
}

describe("draw of a tournament with registrations", () => {
  const four = ["00000001", "00000002", "00000003", "00000004"];

  it("accepts one slot for each confirmed person", () => {
    const result = drawAssignmentProblem({
      confirmed: four.map((emaId) => person(emaId)), teamNames: new Map(), isTeams: false,
      players: [1, 2, 3, 4].map((id) => ({ id, team: 0 })),
      assignments: four.map((emaId, index) => ({ playerId: index + 1, emaId })),
    });
    assert.deepEqual(result, { teamNames: [] });
  });

  it("refuses a missing, unknown or repeated person or slot", () => {
    const base = { confirmed: four.map((emaId) => person(emaId)), teamNames: new Map(), isTeams: false, players: [1, 2, 3, 4].map((id) => ({ id, team: 0 })) };
    const missing = drawAssignmentProblem({ ...base, assignments: four.slice(0, 3).map((emaId, index) => ({ playerId: index + 1, emaId })) });
    assert.equal("code" in missing && missing.code, "BAD_ASSIGNMENTS");
    const twice = drawAssignmentProblem({ ...base, assignments: [...four.slice(0, 3), "00000001"].map((emaId, index) => ({ playerId: index + 1, emaId })) });
    assert.equal("code" in twice && twice.code, "BAD_ASSIGNMENTS");
    const unknown = drawAssignmentProblem({ ...base, assignments: [...four.slice(0, 3), "99999999"].map((emaId, index) => ({ playerId: index + 1, emaId })) });
    assert.equal("code" in unknown && unknown.code, "BAD_ASSIGNMENTS");
  });

  it("needs full teams and keeps each registration team in one team of the draw", () => {
    const eight = [...four, "00000005", "00000006", "00000007", "00000008"];
    const confirmed = eight.map((emaId, index) => person(emaId, index < 4 ? "red" : "blue"));
    const teamNames = new Map([["red", "Red dragons"], ["blue", "Blue winds"]]);
    const players = eight.map((_, index) => ({ id: index + 1, team: index < 4 ? 1 : 2 }));
    const good = drawAssignmentProblem({
      confirmed, teamNames, isTeams: true, players, assignments: eight.map((emaId, index) => ({ playerId: index + 1, emaId })),
    });
    assert.deepEqual(good, { teamNames: [{ teamId: 1, name: "Red dragons" }, { teamId: 2, name: "Blue winds" }] });

    const mixed = drawAssignmentProblem({
      confirmed, teamNames, isTeams: true, players,
      assignments: eight.map((emaId, index) => ({ playerId: index === 0 ? 5 : index === 4 ? 1 : index + 1, emaId })),
    });
    assert.equal("code" in mixed && mixed.code, "BAD_ASSIGNMENTS");

    const incomplete = drawAssignmentProblem({
      confirmed: confirmed.map((registration, index) => (index === 7 ? { ...registration, teamId: null } : registration)),
      teamNames, isTeams: true, players, assignments: eight.map((emaId, index) => ({ playerId: index + 1, emaId })),
    });
    assert.ok("code" in incomplete);
    assert.equal(incomplete.code, "INCOMPLETE_TEAMS");
    assert.deepEqual((incomplete.details as { emaIds: string[] }).emaIds, ["00000005", "00000006", "00000007", "00000008"]);
  });
});
