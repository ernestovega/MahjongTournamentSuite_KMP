import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { loadOverview, loadPersonStatistics, loadStandings, refreshTournamentStats, removeTournamentStats } from "./statisticsStore";
import { resetTable, saveTableState } from "./tableManagerService";

const actor = { uid: "user-ana", name: "Ana" };

/** Unique EMA numbers, so tests do not share player sums. */
const ema = (tag: string, slot: number) => `${tag}-${slot}`;

async function seedSlots(tournamentId: string, tag: string) {
  for (let slot = 1; slot <= 4; slot += 1) {
    await db.collection("tournaments").doc(tournamentId).collection("players").doc(String(slot)).set({ assignedEmaId: ema(tag, slot) });
  }
}

function saveTotals(tournamentId: string, expectedVersion: number, scores: [string, string, string, string]) {
  return saveTableState({
    tournamentId, roundId: 1, tableId: 1, expectedVersion, handPatches: [], actor,
    tablePatch: {
      playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
      useTotalsOnly: true,
      playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
      isCompleted: true,
    },
  });
}

const playerDoc = (id: string) => db.collection("statsPlayers").doc(id).get();

describe("statistics store", () => {
  before(requireEmulator);

  it("keeps the sums in the same transaction as the table save, and corrects them on an edit", async () => {
    const tag = `a${Date.now()}`;
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedSlots(id, tag);
    const before = await loadOverview();

    await saveTotals(id, 0, ["30", "10", "-10", "-30"]);
    const first = await playerDoc(ema(tag, 1));
    assert.equal(first.get("tables"), 1);
    assert.equal(first.get("place1"), 1);
    assert.equal(first.get("score"), 30);
    assert.equal((await db.collection("statsTables").doc(`${id}_1_1`).get()).exists, true);
    assert.equal((await loadOverview()).tablesCompleted, before.tablesCompleted + 1);

    // An edit of the score moves the numbers. It does not add them a second time.
    await saveTotals(id, 1, ["50", "10", "-10", "-50"]);
    const edited = await playerDoc(ema(tag, 1));
    assert.equal(edited.get("tables"), 1);
    assert.equal(edited.get("score"), 50);
    assert.equal((await loadOverview()).tablesCompleted, before.tablesCompleted + 1);

    const person = await loadPersonStatistics(ema(tag, 4), null);
    assert.equal(person.tournaments.length, 1);
    assert.equal(person.tournaments[0].results[0].position, 4);
    assert.deepEqual(await loadStandings(ema(tag, 4), [id, "other"]), [{ tournamentId: id, rank: 4, of: 4 }]);
    assert.deepEqual(await loadStandings(ema(tag, 1), [id]), [{ tournamentId: id, rank: 1, of: 4 }]);
    assert.equal((await loadPersonStatistics(ema(tag, 4), new Set(["other"]))).tournaments.length, 0);
  });

  it("takes the numbers out when the table is reset or the tournament is removed", async () => {
    const tag = `b${Date.now()}`;
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedSlots(id, tag);
    await saveTotals(id, 0, ["30", "10", "-10", "-30"]);

    await resetTable({ tournamentId: id, roundId: 1, tableId: 1, actor });
    assert.equal((await playerDoc(ema(tag, 1))).get("tables"), 0);
    assert.equal((await db.collection("statsTables").doc(`${id}_1_1`).get()).exists, false);

    await saveTotals(id, 2, ["30", "10", "-10", "-30"]);
    assert.equal((await playerDoc(ema(tag, 1))).get("tables"), 1);
    await removeTournamentStats(id);
    assert.equal((await playerDoc(ema(tag, 1))).get("tables"), 0);
    assert.equal((await db.collection("statsTournaments").doc(id).get()).exists, false);
  });

  it("builds the statistics of a tournament again without counting a table twice", async () => {
    const tag = `c${Date.now()}`;
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedSlots(id, tag);
    await saveTotals(id, 0, ["30", "10", "-10", "-30"]);

    assert.equal(await refreshTournamentStats(id), 1);
    assert.equal((await playerDoc(ema(tag, 2))).get("tables"), 1);
  });

  it("lists the players of the leaderboards", async () => {
    const overview = await loadOverview();
    assert.ok(overview.players.length <= 15);
    assert.ok(overview.tournaments >= 1);
  });
});
