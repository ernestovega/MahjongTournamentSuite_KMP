import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db } from "../firebase";
import {
  assignTournamentPlayer,
  listTournamentPlayers,
  listTournamentRounds,
  listTournamentTables,
  listTournamentTeams,
  overwriteTournamentPlayers,
  setPlayerSubstitution,
  updateTournamentTeam,
} from "./tournamentContentService";

const codeOf = (error: unknown) => (error as { details?: { code?: string } }).details?.code;
const refusedWith = (code: string) => (error: unknown) => {
  assert.equal(codeOf(error), code, String(error));
  return true;
};

let counter = 0;
async function tournament(data: Record<string, unknown>, players: Record<string, Record<string, unknown>>) {
  counter++;
  const ref = db.doc(`tournaments/content-malformed-${Date.now()}-${counter}`);
  await ref.set(data);
  for (const [id, player] of Object.entries(players)) await ref.collection("players").doc(id).set(player);
  return ref;
}

/**
 * The slots, teams and tables of a tournament read from documents with missing fields, as an old version or a manual
 * edit could leave them. Each field falls back to its default, so the lists and the changes still work.
 */
describe("tournament content with malformed documents", () => {
  it("lists slots, teams, rounds and tables without their fields", async () => {
    const ref = await tournament({ isTeams: true }, {
      1: { id: 1, team: 1, assignedEmaId: "07670001", name: "A" },
      2: {},
      3: { team: 2 },
    });
    await ref.collection("teams").doc("1").set({});
    await ref.collection("teams").doc("x").set({});
    await ref.collection("tables").doc("1-1").set({ roundId: 1, tableId: 1 });
    await ref.collection("rounds").doc("1").set({ roundId: 1 });
    await db.doc("emaPlayerRegistry/07670001").set({ firstName: "Ana", lastName: "Soto" });

    const players = await listTournamentPlayers(ref.id, { withNames: true });
    assert.equal(players.find((player) => player.id === 1)?.displayName, "ANA SOTO");
    assert.equal(players.find((player) => Number.isNaN(player.id))?.team, 0);
    assert.deepEqual(await listTournamentTeams(ref.id), [{ id: 1, name: "Team 1", playerIds: [1] }, { id: 2, name: "Team 2", playerIds: [3] }]);
    assert.deepEqual(await listTournamentRounds(ref.id), [{ roundId: 1 }]);
    const [table] = await listTournamentTables(ref.id, null);
    assert.deepEqual([table.version, table.playerIds, table.isCompleted, table.useTotalsOnly, table.completionStatus], [0, [], false, false, "empty"]);

    await assert.rejects(listTournamentTeams(`${ref.id}-missing`), /Tournament not found/);
    const single = await tournament({ isTeams: false }, {});
    assert.deepEqual(await listTournamentTeams(single.id), []);
  });

  it("moves the people of a team across slots without ids and team names", async () => {
    const slot = (team: number, emaId?: string, email?: string) => ({ team, ...(emaId == null ? {} : { assignedEmaId: emaId }), ...(email == null ? {} : { email }) });
    const ref = await tournament({ isTeams: true }, {
      1: slot(1, "07670011", "one@example.test"),
      2: slot(1, "07670012"),
      3: slot(1, "07670013"),
      4: slot(1, "  "),
      5: slot(2, "07670015"),
      6: slot(2, "07670016"),
      7: slot(2),
      8: slot(2),
    });
    await ref.collection("teams").doc("2").set({ id: 2 });
    for (const emaId of ["07670011", "07670012", "07670013", "07670015", "07670016"]) {
      await ref.collection("emaPlayerAssignments").doc(emaId).set({ playerId: 0 });
    }
    await db.doc("emaPlayerRegistry/07670015").set({ firstName: "Five" });

    await assert.rejects(
      updateTournamentTeam({ tournamentId: ref.id, teamId: 1, name: "Uno", emaIds: ["07670015", null, null] }),
      refusedWith("TEAM_SLOTS_MISMATCH"),
    );
    // Five comes from team 2: Two takes its old slot, Three has no slot left, One moves with its pending email.
    await updateTournamentTeam({ tournamentId: ref.id, teamId: 1, name: "Uno", emaIds: ["07670015", "07670011", null, null] });
    const slots = new Map((await ref.collection("players").get()).docs.map((doc) => [doc.id, doc]));
    assert.equal(slots.get("1")?.get("assignedEmaId"), "07670015");
    assert.equal(slots.get("1")?.get("assignedCountry"), "");
    assert.equal(slots.get("2")?.get("assignedEmaId"), "07670011");
    assert.equal(slots.get("2")?.get("email"), "one@example.test");
    assert.equal(slots.get("5")?.get("assignedEmaId"), "07670012");
    assert.equal(slots.get("3")?.get("assignedEmaId"), null);
    assert.equal((await ref.collection("emaPlayerAssignments").doc("07670013").get()).exists, false);
    assert.equal((await ref.collection("emaPlayerAssignments").doc("07670015").get()).get("playerId"), 1);
  });

  it("refuses a person already in another slot, and clears the substitution of a replaced person", async () => {
    const ref = await tournament({ isTeams: false, numRounds: 1 }, {
      1: { id: 1, assignedEmaId: "07670021", substitution: { reason: "LATE", fromRound: 1 } },
      2: { id: 2, assignedEmaId: "07670022" },
      3: { id: 3 },
    });
    await ref.collection("emaPlayerAssignments").doc("07670021").set({ playerId: 1 });
    await ref.collection("tables").doc("1-1").set({ roundId: 1, tableId: 1, playerIds: [1, 2, 3, 4], forcedZeroPlayerId: "1" });
    await db.doc("emaPlayerRegistry/07670023").set({ firstName: "New" });
    await db.doc("emaPlayerRegistry/07670022").set({ firstName: "Two" });

    await assert.rejects(assignTournamentPlayer({ tournamentId: ref.id, playerId: 3, emaId: "07670021" }), refusedWith("EMA_ALREADY_IN_TOURNAMENT"));
    // Slot 2 holds the person without its index document.
    await assert.rejects(assignTournamentPlayer({ tournamentId: ref.id, playerId: 3, emaId: "07670022" }), refusedWith("EMA_ALREADY_IN_TOURNAMENT"));
    await assert.rejects(assignTournamentPlayer({ tournamentId: ref.id, playerId: 3, emaId: "07670029" }), /EMA player not found/);
    await assert.rejects(assignTournamentPlayer({ tournamentId: ref.id, playerId: 9, emaId: null }), /Player not found/);

    await assignTournamentPlayer({ tournamentId: ref.id, playerId: 1, emaId: "07670023" });
    const replaced = await ref.collection("players").doc("1").get();
    assert.equal(replaced.get("substitution"), undefined);
    assert.equal(replaced.get("assignedCountry"), "");
    const table = await ref.collection("tables").doc("1-1").get();
    assert.equal(table.get("forcedZeroPlayerId"), undefined);
    assert.equal(table.get("version"), 1);
  });

  it("overwrites slots without ids and refuses a tournament that is missing, started or with an unknown person", async () => {
    const overwrite = (tournamentId: string, slots: Array<{ slotId: number; emaId: string | null; email: string | null }>) =>
      overwriteTournamentPlayers({ tournamentId, slots, teamNames: [], uid: null });
    await assert.rejects(overwrite("content-malformed-missing", []), /Tournament not found/);
    const started = await tournament({ tablesWithProgress: 1 }, {});
    await assert.rejects(overwrite(started.id, []), refusedWith("TOURNAMENT_STARTED"));

    const ref = await tournament({}, {
      1: { assignedEmaId: "07670031", substitution: { reason: "LATE", fromRound: 1 } },
      2: { assignedEmaId: "07670032" },
    });
    await db.doc("emaPlayerRegistry/07670033").set({ firstName: "Three" });
    await assert.rejects(overwrite(ref.id, [{ slotId: 1, emaId: "07670039", email: null }]), /EMA player not found/);
    // Slot 2 is not in the list, so it becomes empty.
    const result = await overwrite(ref.id, [{ slotId: 1, emaId: "07670033", email: null }]);
    assert.equal(result.changedSlots, 2);
    const first = await ref.collection("players").doc("1").get();
    assert.equal(first.get("assignedEmaId"), "07670033");
    assert.equal(first.get("assignedCountry"), "");
    assert.equal(first.get("substitution"), undefined);
    assert.equal((await ref.collection("players").doc("2").get()).get("assignedEmaId"), null);
  });

  it("refuses a substitution in a tournament without rounds or a table without seats", async () => {
    const ref = await tournament({}, { 1: { id: 1, assignedEmaId: "07670041" } });
    await assert.rejects(
      setPlayerSubstitution({ tournamentId: ref.id, playerId: 1, substitution: { reason: "LATE", fromRound: 1 } as never, uid: null }),
      /The round is not part of the tournament/,
    );
    await ref.update({ numRounds: 1 });
    await ref.collection("tables").doc("1-1").set({ roundId: 1, tableId: 1 });
    await assert.rejects(
      setPlayerSubstitution({ tournamentId: ref.id, playerId: 1, substitution: { reason: "ILLNESS", fromRound: 1, duringSession: true } as never, uid: null }),
      refusedWith("NO_TABLE_IN_ROUND"),
    );
  });
});
