import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { countHandDocs, readHand, readTournament, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { backfillTournament } from "./tournamentBackfill";

describe("backfill of old tournaments", () => {
  before(requireEmulator);

  it("previews without writing anything", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }], withHands: false });

    const result = await backfillTournament(id, false);

    assert.deepEqual(result.missingHands.map((entry) => entry.tableId), ["1_1", "1_2"]);
    assert.equal(result.applied, false);
    assert.equal(await countHandDocs(id, 1, 1), 0);
    assert.equal((await readTournament(id)).tablesWithProgress, 0);
  });

  it("creates the 16 hands of every table without hands and sets the counter", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }], withHands: false });

    await backfillTournament(id, true);

    assert.equal(await countHandDocs(id, 1, 1), 16);
    assert.equal(await countHandDocs(id, 2, 1), 16);
    const tournament = await readTournament(id);
    assert.equal(tournament.tablesWithProgress, 0);
  });

  it("never overwrites hands that already exist", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const { db } = await import("../firebase");
    await db.collection("tournaments").doc(id).collection("tables").doc("1_1").collection("hands").doc("3")
      .update({ handScore: "24", isDone: true });

    const result = await backfillTournament(id, true);

    assert.deepEqual(result.missingHands, []);
    assert.equal((await readHand(id, 1, 1, 3)).handScore, "24");
    assert.equal(await countHandDocs(id, 1, 1), 16);
  });

  it("counts the tables that have data", async () => {
    const id = await seedTournament({
      tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2, isCompleted: true }, { roundId: 1, tableId: 3 }],
    });
    const { db } = await import("../firebase");
    await db.collection("tournaments").doc(id).collection("tables").doc("1_1")
      .collection("hands").doc("1").update({ handScore: "8" });

    const result = await backfillTournament(id, true);

    assert.equal(result.tablesWithProgress, 2);
    assert.equal((await readTournament(id)).tablesWithProgress, 2);
  });

  it("can run twice with the same result", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });

    await backfillTournament(id, true);
    const second = await backfillTournament(id, true);

    assert.deepEqual(second.missingHands, []);
    assert.equal(await countHandDocs(id, 1, 1), 16);
  });

  it("handles a tournament with many tables in several batches", async () => {
    const tables = Array.from({ length: 40 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
    const id = await seedTournament({ tables, withHands: false });

    await backfillTournament(id, true);

    assert.equal(await countHandDocs(id, 1, 40), 16);
    assert.equal(await countHandDocs(id, 1, 1), 16);
  });

  it("lets the first save after the backfill work normally", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });
    await backfillTournament(id, true);
    const { saveTableState } = await import("./tableManagerService");

    const saved = await saveTableState({
      tournamentId: id, roundId: 1, tableId: 1, expectedVersion: 0, tablePatch: {},
      handPatches: [{ handId: 2, patch: { handScore: "12" } }],
    });

    assert.equal(saved.table.version, 1);
    assert.equal((await readTournament(id)).tablesWithProgress, 1);
  });

  it("completes a table that has only some of its hands and keeps the ones it has", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const { db } = await import("../firebase");
    const hands = db.collection("tournaments").doc(id).collection("tables").doc("1_1").collection("hands");
    for (const handId of [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16]) await hands.doc(String(handId)).delete();
    await hands.doc("2").update({ handScore: "30", isDone: true });

    const result = await backfillTournament(id, true);

    assert.deepEqual(result.missingHands, [{ tableId: "1_1", handIds: [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16] }]);
    assert.equal(await countHandDocs(id, 1, 1), 16);
    assert.equal((await readHand(id, 1, 1, 2)).handScore, "30");
    assert.equal((await readHand(id, 1, 1, 10)).handId, 10);
  });
});
