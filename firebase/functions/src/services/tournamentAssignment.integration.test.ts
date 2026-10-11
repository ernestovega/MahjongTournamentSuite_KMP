import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { getTournamentManifest } from "./dataVersionsService";
import { saveTableState } from "./tableManagerService";
import { assignTournamentPlayer } from "./tournamentContentService";

async function seedPlayers(tournamentId: string): Promise<void> {
  const tournament = db.collection("tournaments").doc(tournamentId);
  await tournament.update({ isTeams: false });
  for (const emaId of ["A", "B", "C"]) {
    await db.collection("emaPlayerRegistry").doc(emaId).set({ country: "ES" });
  }
  await tournament.collection("players").doc("1").set({ id: 1, assignedEmaId: "A", nonMember: null, assignedCountry: "ES" });
  await tournament.collection("emaPlayerAssignments").doc("A").set({ playerId: 1 });
  await tournament.collection("players").doc("2").set({ id: 2, assignedEmaId: null, nonMember: null, assignedCountry: "" });
}

function assign(tournamentId: string, playerId: number, emaId: string | null) {
  return assignTournamentPlayer({ tournamentId, playerId, emaId, nonMember: null });
}

async function startATable(tournamentId: string): Promise<void> {
  await saveTableState({
    tournamentId,
    roundId: 1,
    tableId: 1,
    expectedVersion: 0,
    tablePatch: {},
    handPatches: [{ handId: 1, patch: { handScore: "8" } }],
  });
}

describe("changing players after results start", () => {
  before(requireEmulator);

  it("allows a change before any table has data", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedPlayers(id);

    await assign(id, 1, "B");

    assert.equal((await db.collection("tournaments").doc(id).collection("players").doc("1").get()).get("assignedEmaId"), "B");
  });

  it("refuses to replace a player after a table has data", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedPlayers(id);
    await startATable(id);

    await assert.rejects(assign(id, 1, "B"), (error: unknown) => error instanceof HttpError && error.status === 409);
  });

  it("still allows filling an empty slot after a table has data", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedPlayers(id);
    await startATable(id);

    await assign(id, 2, "C");

    assert.equal((await db.collection("tournaments").doc(id).collection("players").doc("2").get()).get("assignedEmaId"), "C");
  });

  it("stays consistent when a player change and the first result happen together", async () => {
    // Production Firestore locks the documents that a transaction reads, so the check and the save are ordered.
    // The emulator lets a blind write pass such a lock. So this test checks consistency, not the order.
    for (let attempt = 0; attempt < 6; attempt++) {
      const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
      await seedPlayers(id);

      const [assigned, saved] = await Promise.allSettled([assign(id, 1, "B"), startATable(id)]);

      assert.equal(saved.status, "fulfilled", `attempt ${attempt}`);
      if (assigned.status === "rejected") {
        assert.ok(assigned.reason instanceof HttpError && assigned.reason.status === 409, `attempt ${attempt}`);
      }
      const player = await db.collection("tournaments").doc(id).collection("players").doc("1").get();
      assert.equal(player.get("assignedEmaId"), assigned.status === "fulfilled" ? "B" : "A", `attempt ${attempt}`);
      assert.equal((await db.collection("tournaments").doc(id).get()).get("tablesWithProgress"), 1);
    }
  });

  it("changes the players revision in the same commit as the data", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedPlayers(id);

    await assign(id, 2, "C");

    const manifest = await getTournamentManifest(id);
    assert.ok(manifest.resources.players.revision >= 1);
  });
});
