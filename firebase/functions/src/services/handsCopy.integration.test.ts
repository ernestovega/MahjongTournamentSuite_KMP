import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { backfillHandsCopy, copyHandsIntoTable } from "./handsCopyBackfill";
import {
  getAllTablesWithHands,
  getRoundTablesWithHands,
  getTableWithHands,
  HANDS_COPY_FIELD,
  HANDS_PER_TABLE,
  resetTable,
  saveTableState,
} from "./tableManagerService";

const tableRef = (tournamentId: string, roundId = 1, tableId = 1) =>
  db.doc(`tournaments/${tournamentId}/tables/${roundId}_${tableId}`);
const handRef = (tournamentId: string, handId: number, roundId = 1, tableId = 1) =>
  tableRef(tournamentId, roundId, tableId).collection("hands").doc(String(handId));

type Copy = { version: number; hands: Array<Record<string, unknown>> };
const copyOf = (table: Record<string, unknown>) => table[HANDS_COPY_FIELD] as Copy | undefined;

function save(tournamentId: string, expectedVersion: number, handId = 1, handScore = "8") {
  return saveTableState({
    tournamentId, roundId: 1, tableId: 1, expectedVersion,
    tablePatch: { playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4" },
    handPatches: [{ handId, patch: { playerWinnerId: "1", playerLooserId: "2", handScore, isDone: true } }],
  });
}

describe("the copy of the hands in the table", () => {
  before(requireEmulator);

  it("is written by every save, with the version of the table and the hands after the save", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    await save(id, 0);
    await save(id, 1, 2, "12");

    const table = await readTable(id, 1, 1);
    const copy = copyOf(table)!;
    assert.equal(copy.version, 2);
    assert.equal(copy.hands.length, HANDS_PER_TABLE);
    assert.equal(copy.hands[0].handScore, "8");
    assert.equal(copy.hands[1].handScore, "12");
    assert.equal(copy.hands[1].isDone, true);
    assert.equal(copy.hands[2].handScore, "");
    // The hand documents hold the same.
    assert.equal((await handRef(id, 2).get()).get("handScore"), "12");
  });

  it("is what the reads of many tables return, so they do not read the hand documents", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 0);
    // A change of a hand document that skips the API and the version: the reads keep showing the copy.
    await handRef(id, 1).update({ handScore: "99" });

    const [all, round, one] = await Promise.all([
      getAllTablesWithHands({ tournamentId: id }),
      getRoundTablesWithHands({ tournamentId: id, roundId: 1 }),
      getTableWithHands({ tournamentId: id, roundId: 1, tableId: 1 }),
    ]);

    assert.equal(all[0].hands[0].handScore, "8");
    assert.equal(round[0].hands[0].handScore, "8");
    assert.equal(one.hands[0].handScore, "8");
  });

  it("is ignored when the table has a newer version than the copy, and the hand documents are read", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
    await save(id, 0);
    await handRef(id, 1).update({ handScore: "24" });
    await tableRef(id).update({ version: 2 });

    const all = await getAllTablesWithHands({ tournamentId: id });
    const one = await getTableWithHands({ tournamentId: id, roundId: 1, tableId: 1 });

    assert.equal(all[0].hands[0].handScore, "24");
    assert.equal(all[1].hands.length, HANDS_PER_TABLE, "a table without a copy reads its hand documents");
    assert.equal(one.hands[0].handScore, "24");
  });

  it("is reset with the table", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 0);

    await resetTable({ tournamentId: id, roundId: 1, tableId: 1 });

    const copy = copyOf(await readTable(id, 1, 1))!;
    assert.equal(copy.version, 2);
    assert.ok(copy.hands.every((hand) => hand.handScore === "" && hand.isDone === false));
    assert.equal((await getAllTablesWithHands({ tournamentId: id }))[0].hands[0].handScore, "");
  });
});

describe("the backfill of the copies", () => {
  before(requireEmulator);

  it("previews without writing, then copies the hands without changing the version", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1, version: 3 }] });
    await handRef(id, 4).update({ handScore: "16", isDone: true });

    assert.equal(await copyHandsIntoTable(tableRef(id), false), "copied");
    assert.equal(copyOf(await readTable(id, 1, 1)), undefined);

    assert.equal(await copyHandsIntoTable(tableRef(id), true), "copied");
    const table = await readTable(id, 1, 1);
    assert.equal(table.version, 3);
    assert.equal(copyOf(table)!.version, 3);
    assert.equal(copyOf(table)!.hands[3].handScore, "16");

    assert.equal(await copyHandsIntoTable(tableRef(id), true), "valid");
  });

  it("skips a table whose hands are missing and counts every table", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
    await handRef(id, 16, 1, 2).delete();

    const counts = await backfillHandsCopy({ tournamentIds: [id], apply: true });

    assert.deepEqual(counts, { copied: 1, valid: 0, "missing-hands": 1 });
    assert.equal(copyOf(await readTable(id, 1, 2)), undefined);
  });
});
