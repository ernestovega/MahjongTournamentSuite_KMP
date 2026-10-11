import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { getTournamentManifest } from "./dataVersionsService";
import { saveTableState } from "./tableManagerService";
import { updateTournamentTeam } from "./tournamentContentService";

async function seedTeams(): Promise<string> {
  const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  const tournament = db.collection("tournaments").doc(id);
  await tournament.update({ isTeams: true });
  for (const emaId of ["A", "B", "C"]) {
    await db.collection("emaPlayerRegistry").doc(emaId).set({ country: "ES" });
  }
  await tournament.collection("players").doc("1").set({ id: 1, team: 1, assignedEmaId: null, nonMember: null, assignedCountry: "" });
  await tournament.collection("players").doc("2").set({ id: 2, team: 1, assignedEmaId: null, nonMember: null, assignedCountry: "" });
  await tournament.collection("players").doc("3").set({ id: 3, team: 2, assignedEmaId: null, nonMember: null, assignedCountry: "" });
  return id;
}

async function startATable(tournamentId: string): Promise<void> {
  await saveTableState({
    tournamentId, roundId: 1, tableId: 1, expectedVersion: 0, tablePatch: {},
    handPatches: [{ handId: 1, patch: { handScore: "8" } }],
  });
}

const isConflict = (error: unknown) => error instanceof HttpError && error.status === 409;

describe("team changes", () => {
  before(requireEmulator);

  it("assigns players to the slots of a team before results start", async () => {
    const id = await seedTeams();

    await updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] });

    const players = db.collection("tournaments").doc(id).collection("players");
    assert.equal((await players.doc("1").get()).get("assignedEmaId"), "A");
    assert.equal((await players.doc("2").get()).get("assignedEmaId"), "B");
    assert.equal((await db.collection("tournaments").doc(id).collection("teams").doc("1").get()).get("name"), "Reds");
  });

  it("raises the team and player revisions", async () => {
    const id = await seedTeams();
    const before = await getTournamentManifest(id);

    await updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", null] });

    const after = await getTournamentManifest(id);
    assert.ok(after.resources.teams.revision > before.resources.teams.revision);
    assert.ok(after.resources.players.revision > before.resources.players.revision);
  });

  it("refuses to change the players of a team after a table has data", async () => {
    const id = await seedTeams();
    await updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] });
    await startATable(id);

    await assert.rejects(updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "C"] }), isConflict);
  });

  it("still allows a new team name after a table has data", async () => {
    const id = await seedTeams();
    await updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] });
    await startATable(id);

    await updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Blues", emaIds: ["A", "B"] });

    assert.equal((await db.collection("tournaments").doc(id).collection("teams").doc("1").get()).get("name"), "Blues");
  });

  it("refuses the same player in two slots", async () => {
    const id = await seedTeams();

    await assert.rejects(updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "A"] }), isConflict);
  });

  it("refuses a team name that another team uses", async () => {
    const id = await seedTeams();
    await updateTournamentTeam({ tournamentId: id, teamId: 2, name: "Reds", emaIds: ["C"] });

    await assert.rejects(updateTournamentTeam({ tournamentId: id, teamId: 1, name: "reds", emaIds: ["A", "B"] }), isConflict);
  });

  it("refuses a team in a tournament without teams", async () => {
    const id = await seedTeams();
    await db.collection("tournaments").doc(id).update({ isTeams: false });

    await assert.rejects(updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Reds", emaIds: ["A", "B"] }), isConflict);
  });

  it("fails with not found for a team that does not exist", async () => {
    const id = await seedTeams();

    await assert.rejects(
      updateTournamentTeam({ tournamentId: id, teamId: 9, name: "Ghosts", emaIds: ["A"] }),
      (error: unknown) => error instanceof HttpError && error.status === 404,
    );
  });
});
