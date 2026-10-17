import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { HttpError } from "../api/httpError";
import { db } from "../firebase";
import {
  countHandDocs,
  readChanges,
  readGlobalTournamentsRevision,
  readTable,
  readTournament,
  requireEmulator,
  seedTournament,
} from "../testing/firestoreFixtures";
import { getTableWithHands, resetTable, saveTableState } from "./tableManagerService";

const actor = { uid: "user-ana", name: "Ana" };

function save(
  tournamentId: string,
  tableId: number,
  expectedVersion: number,
  tablePatch: Record<string, string | boolean> = {},
  handPatches: Array<{ handId: number; patch: Record<string, string | boolean> }> = [],
  who: { uid: string; name: string | null } | null = actor,
) {
  return saveTableState({ tournamentId, roundId: 1, tableId, expectedVersion, tablePatch, handPatches, actor: who });
}

describe("save cost and contention", () => {
  before(requireEmulator);

  it("lets 8 editors of different tables save at the same time without lock waits", async () => {
    const tables = Array.from({ length: 8 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
    const id = await seedTournament({ tables });
    const started = Date.now();

    const results = await Promise.allSettled(
      tables.map((table) => save(id, table.tableId, 0, { playerEastId: String(table.tableId) }, [{ handId: 1, patch: { handScore: "8" } }])),
    );

    const elapsed = Date.now() - started;
    assert.deepEqual(results.filter((result) => result.status === "rejected"), []);
    // Before the fix this took about 7 s on the emulator, because every save locked all incomplete tables.
    assert.ok(elapsed < 3000, `8 parallel saves took ${elapsed} ms`);
  });

  it("completes the tournament once when two editors finish the last two tables together", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });

    await Promise.all([save(id, 1, 0, { isCompleted: true }), save(id, 2, 0, { isCompleted: true })]);

    assert.equal((await readTournament(id)).isCompleted, true);
  });

  it("returns the same data that a later read returns", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const saved = await save(id, 1, 0, { playerEastId: "3", manualPlayerEastScore: "10" }, [
      { handId: 2, patch: { handScore: "12", isDone: true } },
    ]);

    assert.deepEqual(saved, await getTableWithHands({ tournamentId: id, roundId: 1, tableId: 1 }));
    assert.equal(saved.table.version, 1);
  });

  it("changes the global tournaments revision only when a table completes or reopens", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
    const start = await readGlobalTournamentsRevision();

    await save(id, 1, 0, { playerEastId: "1" });
    await save(id, 1, 1, {}, [{ handId: 1, patch: { handScore: "8" } }]);
    assert.equal(await readGlobalTournamentsRevision(), start, "ordinary saves do not touch the global document");

    await save(id, 1, 2, { isCompleted: true });
    assert.equal(await readGlobalTournamentsRevision(), start + 1, "completing a table does");

    await save(id, 1, 3, { playerEastId: "2" });
    assert.equal(await readGlobalTournamentsRevision(), start + 1, "saving a complete table again does not");

    await save(id, 1, 4, { isCompleted: false });
    assert.equal(await readGlobalTournamentsRevision(), start + 2, "reopening it does");
  });
});

describe("tables without hand documents", () => {
  before(requireEmulator);

  it("refuses a save and writes nothing", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });

    await assert.rejects(save(id, 1, 0, { playerEastId: "1" }), (error: unknown) => error instanceof HttpError && error.status === 404);

    assert.equal(await countHandDocs(id, 1, 1), 0);
    assert.equal((await readTable(id, 1, 1)).version, 0);
  });

  it("refuses a save when only some hands exist, also for a hand that exists", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.collection("tournaments").doc(id).collection("tables").doc("1_1").collection("hands").doc("9").delete();

    await assert.rejects(
      save(id, 1, 0, {}, [{ handId: 1, patch: { handScore: "8" } }]),
      (error: unknown) => error instanceof HttpError && error.status === 404,
    );

    assert.equal((await readTable(id, 1, 1)).version, 0);
  });

  it("refuses a reset with a clean 404 and changes nothing", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });

    await assert.rejects(
      resetTable({ tournamentId: id, roundId: 1, tableId: 1, actor }),
      (error: unknown) => error instanceof HttpError && error.status === 404,
    );

    assert.equal((await readTable(id, 1, 1)).version, 0);
  });
});

describe("started tables counter", () => {
  before(requireEmulator);

  it("counts a table when it gets data and uncounts it after a reset", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });

    await save(id, 1, 0, {}, [{ handId: 1, patch: { handScore: "8" } }]);
    await save(id, 2, 0, { playerEastId: "1" });
    assert.equal((await readTournament(id)).tablesWithProgress, 2);

    await save(id, 1, 1, {}, [{ handId: 2, patch: { handScore: "9" } }]);
    assert.equal((await readTournament(id)).tablesWithProgress, 2, "more data in a started table does not count again");

    await resetTable({ tournamentId: id, roundId: 1, tableId: 1, actor });
    assert.equal((await readTournament(id)).tablesWithProgress, 1);

    await resetTable({ tournamentId: id, roundId: 1, tableId: 1, actor });
    assert.equal((await readTournament(id)).tablesWithProgress, 1, "resetting an empty table changes nothing");
  });

  it("stays exact when many editors start different tables at the same time", async () => {
    const tables = Array.from({ length: 6 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
    const id = await seedTournament({ tables });

    await Promise.all(tables.map((table) => save(id, table.tableId, 0, {}, [{ handId: 1, patch: { handScore: "8" } }])));

    assert.equal((await readTournament(id)).tablesWithProgress, 6);
  });
});

describe("change log", () => {
  before(requireEmulator);

  it("records who changed which fields, with the old and the new value", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    await save(id, 1, 0, { playerEastId: "4", isCompleted: false }, [
      { handId: 2, patch: { handScore: "12", isDone: true } },
    ]);

    const [entry] = await readChanges(id);
    assert.equal(entry.kind, "save");
    assert.equal(entry.uid, "user-ana");
    assert.equal(entry.name, "Ana");
    assert.equal(entry.version, 1);
    assert.deepEqual(entry.tableChanges, [{ field: "playerEastId", from: null, to: "4" }]);
    assert.deepEqual(entry.handChanges, [
      { handId: 2, field: "handScore", from: "", to: "12" },
      { handId: 2, field: "isDone", from: false, to: true },
    ]);
    assert.ok(entry.at, "has a server time");
  });

  it("writes one entry for each save and one for a reset", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 1, 0, { playerEastId: "1" });
    await save(id, 1, 1, { playerEastId: "2" });
    await resetTable({ tournamentId: id, roundId: 1, tableId: 1, actor: { uid: "admin", name: null } });

    const log = await readChanges(id);

    assert.deepEqual(log.map((entry) => [entry.kind, entry.version, entry.uid]), [
      ["save", 1, "user-ana"],
      ["save", 2, "user-ana"],
      ["reset", 3, "admin"],
    ]);
  });

  it("writes a conflict entry and no save entry for a rejected save", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 1, 0, { playerEastId: "1" });
    await assert.rejects(save(id, 1, 0, { playerEastId: "2" }));

    const entries = await readChanges(id);
    assert.equal(entries.filter((entry) => entry.kind === "save").length, 1);
    assert.equal(entries.filter((entry) => entry.kind === "conflict").length, 1);
    assert.equal((await readTable(id, 1, 1)).playerEastId, "1");
  });

  it("skips derived totals and values that did not change", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 1, 0, { playerEastId: "1" });

    await save(id, 1, 1, { playerEastId: "1", playerEastScore: "32", manualPlayerEastScore: "10" });

    const [, second] = await readChanges(id);
    assert.deepEqual(second.tableChanges, [{ field: "manualPlayerEastScore", from: null, to: "10" }]);
  });

  it("works without a known user, for old clients", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    await save(id, 1, 0, { playerEastId: "1" }, [], null);

    const [entry] = await readChanges(id);
    assert.equal(entry.uid, null);
    assert.equal((await readTable(id, 1, 1)).lastEditedByUid, undefined);
  });

  it("keeps the last editor on the table", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    await save(id, 1, 0, { playerEastId: "1" });

    const table = await readTable(id, 1, 1);
    assert.equal(table.lastEditedByUid, "user-ana");
    assert.equal(table.lastEditedByName, "Ana");
  });
});
