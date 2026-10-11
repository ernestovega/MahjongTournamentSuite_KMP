import assert from "node:assert/strict";
import { before, describe, it } from "node:test";

import { HttpError } from "../api/httpError";
import {
  readHand,
  readTable,
  readTournament,
  requireEmulator,
  seedTournament,
} from "../testing/firestoreFixtures";
import { getTournamentManifest } from "./dataVersionsService";
import {
  getAllTablesWithHands,
  getRoundTablesWithHands,
  resetTable,
  saveTableState,
} from "./tableManagerService";

const BASE = { roundId: 1 };

function save(
  tournamentId: string,
  tableId: number,
  expectedVersion: number,
  tablePatch: Record<string, string | boolean> = {},
  handPatches: Array<{ handId: number; patch: Record<string, string | boolean> }> = [],
  roundId = BASE.roundId,
) {
  return saveTableState({ tournamentId, roundId, tableId, expectedVersion, tablePatch, handPatches });
}

async function conflictOf(promise: Promise<unknown>): Promise<HttpError> {
  try {
    await promise;
  } catch (error) {
    assert.ok(error instanceof HttpError, `expected HttpError, got ${String(error)}`);
    return error;
  }
  assert.fail("expected the call to fail");
}

describe("table save with the Firestore emulator", () => {
  before(requireEmulator);

  it("increments the table version and records it in the manifest", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const result = await save(id, 1, 0, { playerEastId: "5" });

    assert.equal(result.table.version, 1);
    assert.equal(result.table.playerEastId, "5");
    const manifest = await getTournamentManifest(id);
    assert.equal(manifest.tableVersions["1_1"], 1);
    assert.equal(manifest.resources.tables.revision, 2);
  });

  it("accepts a chain of saves that each use the version of the previous response", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    let version = 0;
    for (let i = 0; i < 5; i++) {
      const result = await save(id, 1, version, { playerEastScore: String(i) });
      version = result.table.version;
    }

    assert.equal(version, 5);
    assert.equal((await getTournamentManifest(id)).tableVersions["1_1"], 5);
  });

  it("rejects an old version with a conflict that carries the current table", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 1, 0, { playerEastId: "7" });

    const error = await conflictOf(save(id, 1, 0, { playerEastId: "9" }));

    assert.equal(error.status, 409);
    const details = error.details as { expectedVersion: number; currentVersion: number; current: { table: { playerEastId: string } } };
    assert.equal(details.expectedVersion, 0);
    assert.equal(details.currentVersion, 1);
    assert.equal(details.current.table.playerEastId, "7");
    assert.equal((await readTable(id, 1, 1)).playerEastId, "7");
  });

  it("rejects a version from the future", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const error = await conflictOf(save(id, 1, 5, { playerEastId: "9" }));

    assert.equal(error.status, 409);
    assert.equal((await readTable(id, 1, 1)).version, 0);
  });

  it("does not change the manifest when a save fails", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await conflictOf(save(id, 1, 3, { playerEastId: "9" }));

    const manifest = await getTournamentManifest(id);

    assert.equal(manifest.resources.tables.revision, 1);
    assert.deepEqual(manifest.tableVersions, {});
  });

  it("lets exactly one of two saves with the same version win", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const results = await Promise.allSettled([
      save(id, 1, 0, { playerEastId: "1" }, [{ handId: 1, patch: { handScore: "8" } }]),
      save(id, 1, 0, { playerEastId: "2" }, [{ handId: 2, patch: { handScore: "9" } }]),
    ]);

    const winners = results.filter((result) => result.status === "fulfilled");
    const losers = results.filter((result) => result.status === "rejected");
    assert.equal(winners.length, 1);
    assert.equal(losers.length, 1);
    assert.equal(((losers[0] as PromiseRejectedResult).reason as HttpError).status, 409);
    const table = await readTable(id, 1, 1);
    assert.equal(table.version, 1);
    // The loser must not leave a partial write: only the hand of the winner changed.
    const winnerEast = (winners[0] as PromiseFulfilledResult<{ table: { playerEastId: string } }>).value.table.playerEastId;
    assert.equal(table.playerEastId, winnerEast);
    const handOne = (await readHand(id, 1, 1, 1)).handScore;
    const handTwo = (await readHand(id, 1, 1, 2)).handScore;
    assert.deepEqual([handOne, handTwo], winnerEast === "1" ? ["8", ""] : ["", "9"]);
  });

  it("applies many saves for different tables of one tournament at the same time", async () => {
    const tables = Array.from({ length: 8 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
    const id = await seedTournament({ tables });

    const results = await Promise.allSettled(tables.map((table) => save(id, table.tableId, 0, { playerEastId: String(table.tableId) })));

    const failures = results.filter((result) => result.status === "rejected").map((result) => String((result as PromiseRejectedResult).reason));
    assert.deepEqual(failures, []);
    const manifest = await getTournamentManifest(id);
    assert.equal(manifest.resources.tables.revision, 1 + tables.length);
    for (const table of tables) assert.equal(manifest.tableVersions[`1_${table.tableId}`], 1);
  });

  it("keeps the edits of two users on different tables", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });

    await Promise.all([
      save(id, 1, 0, {}, [{ handId: 3, patch: { handScore: "10" } }]),
      save(id, 2, 0, {}, [{ handId: 3, patch: { handScore: "20" } }]),
    ]);

    assert.equal((await readHand(id, 1, 1, 3)).handScore, "10");
    assert.equal((await readHand(id, 1, 2, 3)).handScore, "20");
  });

  it("fails with not found for a missing hand and writes nothing", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const error = await conflictOf(save(id, 1, 0, { playerEastId: "1" }, [{ handId: 17, patch: { handScore: "8" } }]));

    assert.equal(error.status, 404);
    assert.equal((await readTable(id, 1, 1)).version, 0);
    assert.equal((await readTable(id, 1, 1)).playerEastId, undefined);
  });

  it("fails with not found for a missing table", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const error = await conflictOf(save(id, 9, 0, {}));

    assert.equal(error.status, 404);
  });

  it("keeps a failed save of a deleted tournament from creating data", async () => {
    const error = await conflictOf(save("test-does-not-exist", 1, 0, {}));

    assert.equal(error.status, 404);
  });

  it("separates tables of different rounds with the same table number", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }] });

    await save(id, 1, 0, { playerEastId: "1" }, [], 1);
    await save(id, 1, 0, { playerEastId: "2" }, [], 2);

    const manifest = await getTournamentManifest(id);
    assert.deepEqual(manifest.tableVersions, { "1_1": 1, "2_1": 1 });
  });
});

describe("tournament completion with concurrent saves", () => {
  before(requireEmulator);

  it("completes the tournament when the last incomplete table is completed", async () => {
    const id = await seedTournament({
      tables: [{ roundId: 1, tableId: 1, isCompleted: true }, { roundId: 1, tableId: 2 }],
    });

    await save(id, 2, 0, { isCompleted: true });

    assert.equal((await readTournament(id)).isCompleted, true);
  });

  it("keeps the tournament open while another table is incomplete", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });

    await save(id, 1, 0, { isCompleted: true });

    assert.equal((await readTournament(id)).isCompleted, false);
  });

  it("reopens a completed tournament when a table is marked incomplete", async () => {
    const id = await seedTournament({
      isCompleted: true,
      tables: [{ roundId: 1, tableId: 1, isCompleted: true }],
    });

    await save(id, 1, 0, { isCompleted: false });

    assert.equal((await readTournament(id)).isCompleted, false);
  });

  it("completes the tournament when the last two tables complete at the same moment", async () => {
    for (let attempt = 0; attempt < 5; attempt++) {
      const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });

      await Promise.all([save(id, 1, 0, { isCompleted: true }), save(id, 2, 0, { isCompleted: true })]);

      assert.equal((await readTournament(id)).isCompleted, true, `attempt ${attempt}`);
    }
  });

  it("does not complete the tournament when one table completes while another reopens", async () => {
    for (let attempt = 0; attempt < 5; attempt++) {
      const id = await seedTournament({
        tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2, isCompleted: true }],
      });

      await Promise.all([save(id, 1, 0, { isCompleted: true }), save(id, 2, 0, { isCompleted: false })]);

      const states = [(await readTable(id, 1, 1)).isCompleted, (await readTable(id, 1, 2)).isCompleted];
      assert.equal((await readTournament(id)).isCompleted, states.every(Boolean), `attempt ${attempt}`);
    }
  });
});

describe("table reset with the Firestore emulator", () => {
  before(requireEmulator);

  it("clears the table and the hands and increments the version", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await save(id, 1, 0, { playerEastId: "3", isCompleted: true }, [{ handId: 1, patch: { handScore: "8", isDone: true } }]);

    await resetTable({ tournamentId: id, roundId: 1, tableId: 1 });

    const table = await readTable(id, 1, 1);
    assert.equal(table.version, 2);
    assert.equal(table.playerEastId, "");
    assert.equal(table.isCompleted, false);
    assert.equal(table.completionStatus, "empty");
    assert.equal((await readHand(id, 1, 1, 1)).handScore, "");
    assert.equal((await readTournament(id)).isCompleted, false);
    assert.equal((await getTournamentManifest(id)).tableVersions["1_1"], 2);
  });

  it("makes a save that used the version before the reset fail", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await resetTable({ tournamentId: id, roundId: 1, tableId: 1 });

    const error = await conflictOf(save(id, 1, 0, { playerEastId: "9" }));

    assert.equal(error.status, 409);
    assert.equal((await readTable(id, 1, 1)).playerEastId, "");
  });

  it("always ends with an empty table when a reset and a save run at the same time", async () => {
    for (let attempt = 0; attempt < 8; attempt++) {
      const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

      await Promise.allSettled([
        save(id, 1, 0, { playerEastId: "9" }, [{ handId: 1, patch: { handScore: "12" } }]),
        resetTable({ tournamentId: id, roundId: 1, tableId: 1 }),
      ]);

      const table = await readTable(id, 1, 1);
      const hand = await readHand(id, 1, 1, 1);
      // A reset that runs first rejects the save. A reset that runs last clears the save.
      assert.equal(table.playerEastId, "", `attempt ${attempt}`);
      assert.equal(hand.handScore, "", `attempt ${attempt}`);
    }
  });

  it("fails with not found for a missing table", async () => {
    const id = await seedTournament({ tables: [] });

    await assert.rejects(resetTable({ tournamentId: id, roundId: 1, tableId: 1 }), /Table not found/);
  });
});

describe("table reads with the Firestore emulator", () => {
  before(requireEmulator);

  it("reads all tables of one round with their hands in table order", async () => {
    const id = await seedTournament({
      tables: [{ roundId: 1, tableId: 2 }, { roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }],
    });

    const tables = await getRoundTablesWithHands({ tournamentId: id, roundId: 1 });

    assert.deepEqual(tables.map((entry) => entry.table.tableId), [1, 2]);
    assert.ok(tables.every((entry) => entry.hands.length === 16));
  });

  it("reads every table of every round with its hands, ordered by round and table", async () => {
    const id = await seedTournament({
      tables: [{ roundId: 2, tableId: 1 }, { roundId: 1, tableId: 2 }, { roundId: 1, tableId: 1 }],
    });

    const tables = await getAllTablesWithHands({ tournamentId: id });

    assert.deepEqual(tables.map((entry) => [entry.table.roundId, entry.table.tableId]), [[1, 1], [1, 2], [2, 1]]);
    assert.ok(tables.every((entry) => entry.hands.length === 16));
  });

  it("reads no table for a tournament without tables", async () => {
    const id = await seedTournament({ tables: [] });

    assert.deepEqual(await getAllTablesWithHands({ tournamentId: id }), []);
  });

  it("returns an empty list for a round without tables", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    assert.deepEqual(await getRoundTablesWithHands({ tournamentId: id, roundId: 5 }), []);
  });
});

describe("tournament manifest with the Firestore emulator", () => {
  before(requireEmulator);

  it("returns no table versions before the first save", async () => {
    const id = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });

    const manifest = await getTournamentManifest(id);

    assert.deepEqual(manifest.tableVersions, {});
    assert.equal(manifest.resources.tables.revision, 1);
  });

  it("also returns the revisions that all tournaments share", async () => {
    const id = await seedTournament({ tables: [] });

    const manifest = await getTournamentManifest(id);

    assert.deepEqual(Object.keys(manifest.globalResources).sort(), ["countries", "emaPlayers", "tournaments", "users"]);
    assert.ok(Number.isInteger(manifest.globalResources.tournaments.revision));
  });

  it("fails with not found for a tournament that does not exist", async () => {
    const error = await conflictOf(getTournamentManifest("test-missing-tournament"));

    assert.equal(error.status, 404);
  });
});
