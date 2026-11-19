import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { loadGroupStatistics, type GroupTournament } from "./statisticsGroups";
import { removeTournamentStats } from "./statisticsStore";
import { saveTableState } from "./tableManagerService";

const actor = { uid: "user-ana", name: "Ana" };

function saveTotals(tournamentId: string, expectedVersion: number, scores: [string, string, string, string]) {
  return saveTableState({
    tournamentId, roundId: 1, tableId: 1, expectedVersion, handPatches: [], actor,
    tablePatch: {
      playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4", useTotalsOnly: true,
      playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
      isCompleted: true,
    },
  });
}

describe("statistics groups", () => {
  before(requireEmulator);

  it("sums the tournaments of a country and the players of a country, and follows a change of a table", async () => {
    const tag = `g${Date.now()}`;
    const ema = (slot: number) => `${tag}-${slot}`;
    const spain = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const sweden = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    for (const id of [spain, sweden]) {
      for (let slot = 1; slot <= 4; slot += 1) {
        await db.doc(`tournaments/${id}/players/${slot}`).set({ assignedEmaId: ema(slot) });
      }
    }
    // Slots 1 and 2 are Spanish, slot 3 has no country, slot 4 is Swedish.
    const countries = ["ESP", "ESP", "", "SWE"];
    for (let slot = 1; slot <= 4; slot += 1) {
      await db.doc(`emaPlayerRegistry/${ema(slot)}`).set({ firstName: "P", lastName: String(slot), country: countries[slot - 1] });
    }
    await saveTotals(spain, 0, ["30", "10", "-10", "-30"]);
    await saveTotals(sweden, 0, ["-30", "-10", "10", "30"]);
    const tournaments: GroupTournament[] = [
      { id: spain, country: "ES", startDate: "2025-04-01" },
      { id: sweden, country: "SE", startDate: "2026-04-01" },
    ];
    const mine = <T extends { emaId: string }>(persons: T[]) => persons.filter((person) => person.emaId.startsWith(tag));

    const all = await loadGroupStatistics(tournaments, {});
    assert.deepEqual(all.tournamentIds, [spain, sweden]);
    assert.equal(all.tables.tablesCompleted, 2);
    const first = mine(all.persons).find((person) => person.emaId === ema(1))!;
    assert.equal(first.tournaments, 2);
    assert.equal(first.score, 0);
    assert.equal(first.place1, 1);

    const inSpain = await loadGroupStatistics(tournaments, { tournamentCountry: "ES" });
    assert.deepEqual(inSpain.tournamentIds, [spain]);
    assert.equal(inSpain.persons.find((person) => person.emaId === ema(1))!.score, 30);

    const spaniards = await loadGroupStatistics(tournaments, { playerCountry: "ESP" });
    assert.deepEqual(mine(spaniards.persons).map((person) => person.emaId).sort(), [ema(1), ema(2)]);
    const spaniardsInSweden = await loadGroupStatistics(tournaments, { playerCountry: "ESP", tournamentCountry: "SE" });
    assert.equal(spaniardsInSweden.persons.find((person) => person.emaId === ema(1))!.score, -30);
    assert.deepEqual(spaniardsInSweden.tournamentIds, [sweden]);
    const nobody = await loadGroupStatistics(tournaments, { playerCountry: "ITA" });
    assert.deepEqual(nobody.tournamentIds.filter((id) => id === spain || id === sweden), []);
    const noCountry = await loadGroupStatistics(tournaments, { playerCountry: "" });
    assert.deepEqual(mine(noCountry.persons).map((person) => person.emaId), [ema(3)]);
    const hidden = await loadGroupStatistics(tournaments, {}, new Set([ema(4)]));
    assert.ok(!hidden.persons.some((person) => person.emaId === ema(4)));

    // An edit raises the revision, so the next read builds the summary again.
    await saveTotals(spain, 1, ["50", "10", "-10", "-50"]);
    const edited = await loadGroupStatistics(tournaments, { tournamentCountry: "ES" });
    assert.equal(edited.persons.find((person) => person.emaId === ema(1))!.score, 50);

    await removeTournamentStats(spain);
    assert.equal((await db.doc(`statsTournamentSummaries/${spain}`).get()).exists, false);
    const removed = await loadGroupStatistics(tournaments, { tournamentCountry: "ES" });
    assert.deepEqual(removed.tournamentIds, []);
  });
});
