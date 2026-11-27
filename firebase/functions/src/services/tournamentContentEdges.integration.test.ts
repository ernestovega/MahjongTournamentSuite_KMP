import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it } from "node:test";

import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { requireEmulator } from "../testing/firestoreFixtures";
import {
  assignTournamentPlayer,
  displayNameOf,
  overwriteTournamentPlayers,
  setPlayerSubstitution,
  updateTournamentTeam,
} from "./tournamentContentService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";

const randomEmaId = () => String(10_000_000 + Math.floor(Math.random() * 89_000_000));

/** A team tournament of 8 slots in two teams, without table data. Slot 8 lost its team field. */
async function teamTournament(): Promise<string> {
  const id = `content-edges-${randomUUID()}`;
  const ref = db.doc(`tournaments/${id}`);
  await ref.set({ isTeams: true, numRounds: 1, tablesWithProgress: 0 });
  for (let slot = 1; slot <= 8; slot++) {
    await ref.collection("players").doc(String(slot)).set({ id: slot, team: slot <= 4 ? 1 : 2, name: `Player ${slot}` });
  }
  await ref.collection("players").doc("8").update({ team: FieldValue.delete() });
  await ref.collection("teams").doc("1").set({ id: 1, name: "Team 1" });
  await ref.collection("teams").doc("2").set({ name: "Team 2" });
  return id;
}

async function registered(country?: string): Promise<string> {
  const emaId = randomEmaId();
  await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).set({ firstName: "Ana", lastName: emaId, ...(country ? { country } : {}) });
  return emaId;
}

/** The people of a tournament with missing fields, moves into empty slots and changes that keep the person. */
describe("tournament people with missing data", () => {
  before(requireEmulator);

  it("has no display name without data", () => {
    assert.equal(displayNameOf(undefined), null);
  });

  it("refuses the team of a tournament that is gone", async () => {
    await assert.rejects(updateTournamentTeam({ tournamentId: `gone-${randomUUID()}`, teamId: 1, name: "A", emaIds: [] }), /Tournament not found/);
  });

  it("moves a player of another team into an empty slot, which leaves the old slot empty", async () => {
    const id = await teamTournament();
    const emaId = await registered("ESP");
    await assignTournamentPlayer({ tournamentId: id, playerId: 5, emaId, uid: null });
    await updateTournamentTeam({ tournamentId: id, teamId: 1, name: "Lions", emaIds: [emaId, null, null, null] });
    const slots = await db.collection(`tournaments/${id}/players`).get();
    const byId = new Map(slots.docs.map((doc) => [doc.id, doc.get("assignedEmaId") ?? null]));
    assert.equal(byId.get("1"), emaId);
    assert.equal(byId.get("5"), null);
  });

  it("keeps the email of a slot whose person stays, and changes only the email in an overwrite", async () => {
    const id = await teamTournament();
    const emaId = await registered();
    await assignTournamentPlayer({ tournamentId: id, playerId: 2, emaId, uid: null });
    await db.doc(`tournaments/${id}/players/2`).update({ email: "ana@example.test" });
    await assignTournamentPlayer({ tournamentId: id, playerId: 2, emaId, uid: null });
    assert.equal((await db.doc(`tournaments/${id}/players/2`).get()).get("email"), "ana@example.test");

    await overwriteTournamentPlayers({ tournamentId: id, slots: [{ slotId: 2, emaId, email: "new@example.test" }], teamNames: [], uid: null });
    const slot = (await db.doc(`tournaments/${id}/players/2`).get()).data();
    assert.equal(slot?.email, "new@example.test");
    assert.equal(slot?.assignedCountry, "");
  });

  it("refuses a substitution with a reason that does not exist", async () => {
    await assert.rejects(
      setPlayerSubstitution({ tournamentId: "t1", playerId: 1, substitution: { reason: "BORED", fromRound: 1, duringSession: false, excludedFromEma: false } as never, uid: null }),
      /Unknown substitution reason/,
    );
  });

  it("sets the same substitution during a session twice on the same table", async () => {
    const id = await teamTournament();
    const emaId = await registered("ESP");
    await assignTournamentPlayer({ tournamentId: id, playerId: 1, emaId, uid: null });
    await db.doc(`tournaments/${id}/tables/1_1`).set({ roundId: 1, tableId: 1, playerIds: [1, 2, 3, 5], version: 0 });
    const substitution = { reason: "ILLNESS", fromRound: 1, duringSession: true, excludedFromEma: false } as const;
    await setPlayerSubstitution({ tournamentId: id, playerId: 1, substitution, uid: null });
    await setPlayerSubstitution({ tournamentId: id, playerId: 1, substitution, uid: null });
    assert.equal((await db.doc(`tournaments/${id}/tables/1_1`).get()).get("forcedZeroPlayerId"), "1");
  });
});
