"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const httpError_1 = require("../api/httpError");
const firebase_1 = require("../firebase");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const tableManagerService_1 = require("./tableManagerService");
const actor = { uid: "user-ana", name: "Ana" };
function save(tournamentId, tableId, expectedVersion, tablePatch = {}, handPatches = [], who = actor) {
    return (0, tableManagerService_1.saveTableState)({ tournamentId, roundId: 1, tableId, expectedVersion, tablePatch, handPatches, actor: who });
}
(0, node_test_1.describe)("save cost and contention", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("lets 8 editors of different tables save at the same time without lock waits", async () => {
        const tables = Array.from({ length: 8 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables });
        const started = Date.now();
        const results = await Promise.allSettled(tables.map((table) => save(id, table.tableId, 0, { playerEastId: String(table.tableId) }, [{ handId: 1, patch: { handScore: "8" } }])));
        const elapsed = Date.now() - started;
        strict_1.default.deepEqual(results.filter((result) => result.status === "rejected"), []);
        // Before the fix this took about 7 s on the emulator, because every save locked all incomplete tables.
        strict_1.default.ok(elapsed < 3000, `8 parallel saves took ${elapsed} ms`);
    });
    (0, node_test_1.it)("completes the tournament once when two editors finish the last two tables together", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
        await Promise.all([save(id, 1, 0, { isCompleted: true }), save(id, 2, 0, { isCompleted: true })]);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, true);
    });
    (0, node_test_1.it)("returns the same data that a later read returns", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const saved = await save(id, 1, 0, { playerEastId: "3", manualPlayerEastScore: "10" }, [
            { handId: 2, patch: { handScore: "12", isDone: true } },
        ]);
        strict_1.default.deepEqual(saved, await (0, tableManagerService_1.getTableWithHands)({ tournamentId: id, roundId: 1, tableId: 1 }));
        strict_1.default.equal(saved.table.version, 1);
    });
    (0, node_test_1.it)("changes the global tournaments revision only when a table completes or reopens", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
        const start = await (0, firestoreFixtures_1.readGlobalTournamentsRevision)();
        await save(id, 1, 0, { playerEastId: "1" });
        await save(id, 1, 1, {}, [{ handId: 1, patch: { handScore: "8" } }]);
        strict_1.default.equal(await (0, firestoreFixtures_1.readGlobalTournamentsRevision)(), start, "ordinary saves do not touch the global document");
        await save(id, 1, 2, { isCompleted: true });
        strict_1.default.equal(await (0, firestoreFixtures_1.readGlobalTournamentsRevision)(), start + 1, "completing a table does");
        await save(id, 1, 3, { playerEastId: "2" });
        strict_1.default.equal(await (0, firestoreFixtures_1.readGlobalTournamentsRevision)(), start + 1, "saving a complete table again does not");
        await save(id, 1, 4, { isCompleted: false });
        strict_1.default.equal(await (0, firestoreFixtures_1.readGlobalTournamentsRevision)(), start + 2, "reopening it does");
    });
});
(0, node_test_1.describe)("tables without hand documents", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("refuses a save and writes nothing", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });
        await strict_1.default.rejects(save(id, 1, 0, { playerEastId: "1" }), (error) => error instanceof httpError_1.HttpError && error.status === 404);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 0);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).version, 0);
    });
    (0, node_test_1.it)("refuses a save when only some hands exist, also for a hand that exists", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await firebase_1.db.collection("tournaments").doc(id).collection("tables").doc("1_1").collection("hands").doc("9").delete();
        await strict_1.default.rejects(save(id, 1, 0, {}, [{ handId: 1, patch: { handScore: "8" } }]), (error) => error instanceof httpError_1.HttpError && error.status === 404);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).version, 0);
    });
    (0, node_test_1.it)("refuses a reset with a clean 404 and changes nothing", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });
        await strict_1.default.rejects((0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1, actor }), (error) => error instanceof httpError_1.HttpError && error.status === 404);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).version, 0);
    });
});
(0, node_test_1.describe)("started tables counter", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("counts a table when it gets data and uncounts it after a reset", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
        await save(id, 1, 0, {}, [{ handId: 1, patch: { handScore: "8" } }]);
        await save(id, 2, 0, { playerEastId: "1" });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 2);
        await save(id, 1, 1, {}, [{ handId: 2, patch: { handScore: "9" } }]);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 2, "more data in a started table does not count again");
        await (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1, actor });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 1);
        await (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1, actor });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 1, "resetting an empty table changes nothing");
    });
    (0, node_test_1.it)("stays exact when many editors start different tables at the same time", async () => {
        const tables = Array.from({ length: 6 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables });
        await Promise.all(tables.map((table) => save(id, table.tableId, 0, {}, [{ handId: 1, patch: { handScore: "8" } }])));
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 6);
    });
});
(0, node_test_1.describe)("change log", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("records who changed which fields, with the old and the new value", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "4", isCompleted: false }, [
            { handId: 2, patch: { handScore: "12", isDone: true } },
        ]);
        const [entry] = await (0, firestoreFixtures_1.readChanges)(id);
        strict_1.default.equal(entry.kind, "save");
        strict_1.default.equal(entry.uid, "user-ana");
        strict_1.default.equal(entry.name, "Ana");
        strict_1.default.equal(entry.version, 1);
        strict_1.default.deepEqual(entry.tableChanges, [{ field: "playerEastId", from: null, to: "4" }]);
        strict_1.default.deepEqual(entry.handChanges, [
            { handId: 2, field: "handScore", from: "", to: "12" },
            { handId: 2, field: "isDone", from: false, to: true },
        ]);
        strict_1.default.ok(entry.at, "has a server time");
    });
    (0, node_test_1.it)("writes one entry for each save and one for a reset", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "1" });
        await save(id, 1, 1, { playerEastId: "2" });
        await (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1, actor: { uid: "admin", name: null } });
        const log = await (0, firestoreFixtures_1.readChanges)(id);
        strict_1.default.deepEqual(log.map((entry) => [entry.kind, entry.version, entry.uid]), [
            ["save", 1, "user-ana"],
            ["save", 2, "user-ana"],
            ["reset", 3, "admin"],
        ]);
    });
    (0, node_test_1.it)("writes no entry for a rejected save", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "1" });
        await strict_1.default.rejects(save(id, 1, 0, { playerEastId: "2" }));
        strict_1.default.equal((await (0, firestoreFixtures_1.readChanges)(id)).length, 1);
    });
    (0, node_test_1.it)("skips derived totals and values that did not change", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "1" });
        await save(id, 1, 1, { playerEastId: "1", playerEastScore: "32", manualPlayerEastScore: "10" });
        const [, second] = await (0, firestoreFixtures_1.readChanges)(id);
        strict_1.default.deepEqual(second.tableChanges, [{ field: "manualPlayerEastScore", from: null, to: "10" }]);
    });
    (0, node_test_1.it)("works without a known user, for old clients", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "1" }, [], null);
        const [entry] = await (0, firestoreFixtures_1.readChanges)(id);
        strict_1.default.equal(entry.uid, null);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).lastEditedByUid, undefined);
    });
    (0, node_test_1.it)("keeps the last editor on the table", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "1" });
        const table = await (0, firestoreFixtures_1.readTable)(id, 1, 1);
        strict_1.default.equal(table.lastEditedByUid, "user-ana");
        strict_1.default.equal(table.lastEditedByName, "Ana");
    });
});
//# sourceMappingURL=tableManagerCost.integration.test.js.map