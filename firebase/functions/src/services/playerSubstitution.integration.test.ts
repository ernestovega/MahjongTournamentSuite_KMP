import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { HttpError } from "../api/httpError";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { saveTableState } from "./tableManagerService";
import { setPlayerSubstitution } from "./tournamentContentService";
import { loadStandings } from "./statisticsStore";
import type { PlayerSubstitution } from "./playerSubstitution";

const actor = { uid: "user-ana", name: "Ana" };

async function seed(tag: string) {
  const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }] });
  const tournament = db.collection("tournaments").doc(id);
  await tournament.update({ numRounds: 3, isTeams: false });
  for (let slot = 1; slot <= 4; slot += 1) {
    await tournament.collection("players").doc(String(slot)).set({ id: slot, assignedEmaId: `${tag}-${slot}` });
  }
  await tournament.collection("players").doc("9").set({ id: 9, assignedEmaId: null });
  return { id, tournament };
}

function saveTotals(tournamentId: string, roundId: number, expectedVersion: number, scores: [string, string, string, string]) {
  return saveTableState({
    tournamentId, roundId, tableId: 1, expectedVersion, handPatches: [], actor,
    tablePatch: {
      playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
      useTotalsOnly: true,
      playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
      isCompleted: true,
    },
  });
}

const illness = (fromRound: number, duringSession = false, excludedFromEma = false): PlayerSubstitution =>
  ({ reason: "ILLNESS", fromRound, duringSession, excludedFromEma });

const substitute = (id: string, playerId: number, substitution: PlayerSubstitution | null) =>
  setPlayerSubstitution({ tournamentId: id, playerId, substitution, uid: "u1" });

describe("substitution of a player", () => {
  before(requireEmulator);

  it("stores the substitution, logs it and removes it again", async () => {
    const { id, tournament } = await seed(`a${Date.now()}`);

    await substitute(id, 2, illness(2, false, true));
    assert.deepEqual((await tournament.collection("players").doc("2").get()).get("substitution"),
      { reason: "ILLNESS", fromRound: 2, duringSession: false, excludedFromEma: true });

    await substitute(id, 2, null);
    assert.equal((await tournament.collection("players").doc("2").get()).get("substitution"), undefined);
    const log = await tournament.collection("playerLog").get();
    assert.deepEqual(log.docs.map((doc) => doc.get("kind")).sort(), ["restore", "substitute"]);
  });

  it("marks the table of the round when the player leaves during the session, and clears it", async () => {
    const { id, tournament } = await seed(`b${Date.now()}`);
    await saveTotals(id, 2, 0, ["50", "30", "10", "-90"]);
    const table = tournament.collection("tables").doc("2_1");

    await substitute(id, 2, illness(2, true));
    const marked = await table.get();
    assert.equal(marked.get("forcedZeroPlayerId"), "2");
    assert.equal(marked.get("version"), 2);
    assert.deepEqual([marked.get("playerEastPoints"), marked.get("playerSouthPoints"), marked.get("playerWestPoints"), marked.get("playerNorthPoints")],
      ["4", "0", "2", "1"]);
    assert.equal((await tournament.get()).get("dataVersions.tableVersions.2_1"), 2);

    await substitute(id, 2, null);
    const cleared = await table.get();
    assert.equal(cleared.get("forcedZeroPlayerId"), undefined);
    assert.equal(cleared.get("playerSouthPoints"), "2");
  });

  it("refuses wrong input", async () => {
    const { id } = await seed(`c${Date.now()}`);
    const refused = (promise: Promise<void>, status: number) =>
      assert.rejects(promise, (error: unknown) => error instanceof HttpError && error.status === status);

    await refused(substitute(id, 9, illness(2)), 409);
    await refused(substitute(id, 1, illness(7)), 400);
    await refused(substitute(id, 1, { reason: "LATE", fromRound: 2, duringSession: false, excludedFromEma: true }), 400);
    await refused(substitute(id, 1, { reason: "LATE", fromRound: 2, duringSession: true, excludedFromEma: false }), 400);
    await refused(substitute(id, 1, { reason: "DISQUALIFIED", fromRound: 2, duringSession: false, excludedFromEma: true }), 400);
    await refused(substitute(id, 1, illness(3, true)), 409);
  });

  it("takes the rounds of the substitute out of the person statistics and the tournament standings", async () => {
    const tag = `d${Date.now()}`;
    const { id } = await seed(tag);
    await saveTotals(id, 1, 0, ["30", "10", "-10", "-30"]);
    await saveTotals(id, 2, 0, ["30", "10", "-10", "-30"]);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 2);

    await substitute(id, 1, illness(2));

    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 1);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-2`).get()).get("tables"), 2);
    const totals = (await db.collection("statsTournaments").doc(id).get()).get("totals");
    assert.equal(totals["1"].score, 30);
    assert.equal(totals["2"].score, 20);
    assert.deepEqual((await loadStandings(`${tag}-2`, [id])).map(({ points: _points, ...rest }) => rest), [{ tournamentId: id, rank: 2, of: 4, score: 20 }]);

    await substitute(id, 1, null);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 2);
    assert.equal((await db.collection("statsTournaments").doc(id).get()).get("totals")["1"].score, 60);
  });

  it("counts the zeros of a disqualified player in the person statistics", async () => {
    const tag = `e${Date.now()}`;
    const { id } = await seed(tag);
    await saveTotals(id, 1, 0, ["30", "10", "-10", "-30"]);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("score"), 30);

    await substitute(id, 1, { reason: "DISQUALIFIED", fromRound: 2, duringSession: false, excludedFromEma: false });

    const player = await db.collection("statsPlayers").doc(`${tag}-1`).get();
    assert.equal(player.get("tables"), 1);
    assert.equal(player.get("points"), 0);
    assert.equal(player.get("score"), 0);
    const totals = (await db.collection("statsTournaments").doc(id).get()).get("totals");
    assert.equal(totals["1"].score, 0);
    assert.equal(totals["1"].points, 0);

    await substitute(id, 1, null);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("score"), 30);
  });

  it("leaves an excluded player out of the person statistics but keeps the earlier standings", async () => {
    const tag = `f${Date.now()}`;
    const { id } = await seed(tag);
    await saveTotals(id, 1, 0, ["30", "10", "-10", "-30"]);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 1);

    await substitute(id, 1, illness(2, false, true));

    const player = await db.collection("statsPlayers").doc(`${tag}-1`).get();
    assert.equal(player.get("tables") ?? 0, 0);
    assert.equal((await db.collection("statsTournaments").doc(id).get()).get("totals")["1"].score, 30);

    await substitute(id, 1, null);
    assert.equal((await db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 1);
  });
});
