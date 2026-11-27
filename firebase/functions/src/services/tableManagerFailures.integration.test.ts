import assert from "node:assert/strict";
import { before, describe, it, mock } from "node:test";

import { DocumentReference, FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { readTable, requireEmulator, seedTournament } from "../testing/firestoreFixtures";
import { logTableConflict, resetTable, saveTableState } from "./tableManagerService";

/** The writes that follow a table save, and the record of a refused save, when the store fails under them. */
describe("table saves when the store fails", () => {
  before(requireEmulator);

  it("keeps the save when the shared writes after it fail every time, and reports it", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const errors: unknown[] = [];
    const quiet = mock.method(console, "error", (...args: unknown[]) => { errors.push(args); });
    const failing = (name: "update" | "set") => {
      const real = DocumentReference.prototype[name] as (...args: unknown[]) => unknown;
      return mock.method(DocumentReference.prototype, name, function (this: DocumentReference, ...args: unknown[]) {
        if ((new Error().stack ?? "").includes("writeWithRetries")) return Promise.reject(new Error("store unavailable"));
        return real.apply(this, args);
      });
    };
    const mocks = [failing("update"), failing("set")];
    try {
      const saved = await saveTableState({ tournamentId, roundId: 1, tableId: 1, expectedVersion: 0, tablePatch: { playerEastId: "5" }, handPatches: [] });
      assert.equal(saved.table.version, 1);
    } finally {
      for (const each of mocks) each.mock.restore();
      quiet.mock.restore();
    }
    assert.equal((await readTable(tournamentId, 1, 1)).version, 1);
    assert.ok(errors.length >= 1);
    assert.match(String((errors[0] as unknown[])[0]), /shared write after a table save failed/);
  });

  it("never fails because the record of a refused save cannot be written", async () => {
    const broken = mock.method(db, "collection", () => {
      throw new Error("store unavailable");
    });
    try {
      await logTableConflict({ tournamentId: "t1", roundId: 1, tableId: 1, expectedVersion: 0, actor: null });
    } finally {
      broken.mock.restore();
    }
  });

  it("resets a table that has no version and hands that only have their document id", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], withHands: true });
    const tableRef = db.doc(`tournaments/${tournamentId}/tables/1_1`);
    await tableRef.update({ version: FieldValue.delete() });
    const hands = await tableRef.collection("hands").get();
    await Promise.all(hands.docs.map((hand) => hand.ref.update({ handId: FieldValue.delete() })));
    await resetTable({ tournamentId, roundId: 1, tableId: 1 });
    assert.equal((await readTable(tournamentId, 1, 1)).version, 1);
    const after = await tableRef.collection("hands").get();
    assert.ok(after.docs.every((hand) => hand.get("handId") === Number(hand.id)), JSON.stringify(after.docs.map((hand) => hand.data())));
  });
});
