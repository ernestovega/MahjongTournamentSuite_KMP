"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const httpError_1 = require("../api/httpError");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const dataVersionsService_1 = require("./dataVersionsService");
const tableManagerService_1 = require("./tableManagerService");
const BASE = { roundId: 1 };
function save(tournamentId, tableId, expectedVersion, tablePatch = {}, handPatches = [], roundId = BASE.roundId) {
    return (0, tableManagerService_1.saveTableState)({ tournamentId, roundId, tableId, expectedVersion, tablePatch, handPatches });
}
async function conflictOf(promise) {
    try {
        await promise;
    }
    catch (error) {
        strict_1.default.ok(error instanceof httpError_1.HttpError, `expected HttpError, got ${String(error)}`);
        return error;
    }
    strict_1.default.fail("expected the call to fail");
}
(0, node_test_1.describe)("table save with the Firestore emulator", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("increments the table version and records it in the manifest", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const result = await save(id, 1, 0, { playerEastId: "5" });
        strict_1.default.equal(result.table.version, 1);
        strict_1.default.equal(result.table.playerEastId, "5");
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.equal(manifest.tableVersions["1_1"], 1);
        strict_1.default.equal(manifest.resources.tables.revision, 2);
    });
    (0, node_test_1.it)("accepts a chain of saves that each use the version of the previous response", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        let version = 0;
        for (let i = 0; i < 5; i++) {
            const result = await save(id, 1, version, { playerEastScore: String(i) });
            version = result.table.version;
        }
        strict_1.default.equal(version, 5);
        strict_1.default.equal((await (0, dataVersionsService_1.getTournamentManifest)(id)).tableVersions["1_1"], 5);
    });
    (0, node_test_1.it)("rejects an old version with a conflict that carries the current table", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "7" });
        const error = await conflictOf(save(id, 1, 0, { playerEastId: "9" }));
        strict_1.default.equal(error.status, 409);
        const details = error.details;
        strict_1.default.equal(details.expectedVersion, 0);
        strict_1.default.equal(details.currentVersion, 1);
        strict_1.default.equal(details.current.table.playerEastId, "7");
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).playerEastId, "7");
    });
    (0, node_test_1.it)("rejects a version from the future", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const error = await conflictOf(save(id, 1, 5, { playerEastId: "9" }));
        strict_1.default.equal(error.status, 409);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).version, 0);
    });
    (0, node_test_1.it)("does not change the manifest when a save fails", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await conflictOf(save(id, 1, 3, { playerEastId: "9" }));
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.equal(manifest.resources.tables.revision, 1);
        strict_1.default.deepEqual(manifest.tableVersions, {});
    });
    (0, node_test_1.it)("lets exactly one of two saves with the same version win", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const results = await Promise.allSettled([
            save(id, 1, 0, { playerEastId: "1" }, [{ handId: 1, patch: { handScore: "8" } }]),
            save(id, 1, 0, { playerEastId: "2" }, [{ handId: 2, patch: { handScore: "9" } }]),
        ]);
        const winners = results.filter((result) => result.status === "fulfilled");
        const losers = results.filter((result) => result.status === "rejected");
        strict_1.default.equal(winners.length, 1);
        strict_1.default.equal(losers.length, 1);
        strict_1.default.equal(losers[0].reason.status, 409);
        const table = await (0, firestoreFixtures_1.readTable)(id, 1, 1);
        strict_1.default.equal(table.version, 1);
        // The loser must not leave a partial write: only the hand of the winner changed.
        const winnerEast = winners[0].value.table.playerEastId;
        strict_1.default.equal(table.playerEastId, winnerEast);
        const handOne = (await (0, firestoreFixtures_1.readHand)(id, 1, 1, 1)).handScore;
        const handTwo = (await (0, firestoreFixtures_1.readHand)(id, 1, 1, 2)).handScore;
        strict_1.default.deepEqual([handOne, handTwo], winnerEast === "1" ? ["8", ""] : ["", "9"]);
    });
    (0, node_test_1.it)("applies many saves for different tables of one tournament at the same time", async () => {
        const tables = Array.from({ length: 8 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables });
        const results = await Promise.allSettled(tables.map((table) => save(id, table.tableId, 0, { playerEastId: String(table.tableId) })));
        const failures = results.filter((result) => result.status === "rejected").map((result) => String(result.reason));
        strict_1.default.deepEqual(failures, []);
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.equal(manifest.resources.tables.revision, 1 + tables.length);
        for (const table of tables)
            strict_1.default.equal(manifest.tableVersions[`1_${table.tableId}`], 1);
    });
    (0, node_test_1.it)("keeps the edits of two users on different tables", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
        await Promise.all([
            save(id, 1, 0, {}, [{ handId: 3, patch: { handScore: "10" } }]),
            save(id, 2, 0, {}, [{ handId: 3, patch: { handScore: "20" } }]),
        ]);
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(id, 1, 1, 3)).handScore, "10");
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(id, 1, 2, 3)).handScore, "20");
    });
    (0, node_test_1.it)("fails with not found for a missing hand and writes nothing", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const error = await conflictOf(save(id, 1, 0, { playerEastId: "1" }, [{ handId: 17, patch: { handScore: "8" } }]));
        strict_1.default.equal(error.status, 404);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).version, 0);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).playerEastId, undefined);
    });
    (0, node_test_1.it)("fails with not found for a missing table", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const error = await conflictOf(save(id, 9, 0, {}));
        strict_1.default.equal(error.status, 404);
    });
    (0, node_test_1.it)("keeps a failed save of a deleted tournament from creating data", async () => {
        const error = await conflictOf(save("test-does-not-exist", 1, 0, {}));
        strict_1.default.equal(error.status, 404);
    });
    (0, node_test_1.it)("separates tables of different rounds with the same table number", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "1" }, [], 1);
        await save(id, 1, 0, { playerEastId: "2" }, [], 2);
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.deepEqual(manifest.tableVersions, { "1_1": 1, "2_1": 1 });
    });
});
(0, node_test_1.describe)("tournament completion with concurrent saves", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("completes the tournament when the last incomplete table is completed", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({
            tables: [{ roundId: 1, tableId: 1, isCompleted: true }, { roundId: 1, tableId: 2 }],
        });
        await save(id, 2, 0, { isCompleted: true });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, true);
    });
    (0, node_test_1.it)("keeps the tournament open while another table is incomplete", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
        await save(id, 1, 0, { isCompleted: true });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, false);
    });
    (0, node_test_1.it)("reopens a completed tournament when a table is marked incomplete", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({
            isCompleted: true,
            tables: [{ roundId: 1, tableId: 1, isCompleted: true }],
        });
        await save(id, 1, 0, { isCompleted: false });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, false);
    });
    (0, node_test_1.it)("completes the tournament when the last two tables complete at the same moment", async () => {
        for (let attempt = 0; attempt < 5; attempt++) {
            const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }] });
            await Promise.all([save(id, 1, 0, { isCompleted: true }), save(id, 2, 0, { isCompleted: true })]);
            strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, true, `attempt ${attempt}`);
        }
    });
    (0, node_test_1.it)("does not complete the tournament when one table completes while another reopens", async () => {
        for (let attempt = 0; attempt < 5; attempt++) {
            const id = await (0, firestoreFixtures_1.seedTournament)({
                tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2, isCompleted: true }],
            });
            await Promise.all([save(id, 1, 0, { isCompleted: true }), save(id, 2, 0, { isCompleted: false })]);
            const states = [(await (0, firestoreFixtures_1.readTable)(id, 1, 1)).isCompleted, (await (0, firestoreFixtures_1.readTable)(id, 1, 2)).isCompleted];
            strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, states.every(Boolean), `attempt ${attempt}`);
        }
    });
});
(0, node_test_1.describe)("table reset with the Firestore emulator", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("clears the table and the hands and increments the version", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await save(id, 1, 0, { playerEastId: "3", isCompleted: true }, [{ handId: 1, patch: { handScore: "8", isDone: true } }]);
        await (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1 });
        const table = await (0, firestoreFixtures_1.readTable)(id, 1, 1);
        strict_1.default.equal(table.version, 2);
        strict_1.default.equal(table.playerEastId, "");
        strict_1.default.equal(table.isCompleted, false);
        strict_1.default.equal(table.completionStatus, "empty");
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(id, 1, 1, 1)).handScore, "");
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).isCompleted, false);
        strict_1.default.equal((await (0, dataVersionsService_1.getTournamentManifest)(id)).tableVersions["1_1"], 2);
    });
    (0, node_test_1.it)("makes a save that used the version before the reset fail", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1 });
        const error = await conflictOf(save(id, 1, 0, { playerEastId: "9" }));
        strict_1.default.equal(error.status, 409);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(id, 1, 1)).playerEastId, "");
    });
    (0, node_test_1.it)("always ends with an empty table when a reset and a save run at the same time", async () => {
        for (let attempt = 0; attempt < 8; attempt++) {
            const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
            await Promise.allSettled([
                save(id, 1, 0, { playerEastId: "9" }, [{ handId: 1, patch: { handScore: "12" } }]),
                (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1 }),
            ]);
            const table = await (0, firestoreFixtures_1.readTable)(id, 1, 1);
            const hand = await (0, firestoreFixtures_1.readHand)(id, 1, 1, 1);
            // A reset that runs first rejects the save. A reset that runs last clears the save.
            strict_1.default.equal(table.playerEastId, "", `attempt ${attempt}`);
            strict_1.default.equal(hand.handScore, "", `attempt ${attempt}`);
        }
    });
    (0, node_test_1.it)("fails with not found for a missing table", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [] });
        await strict_1.default.rejects((0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1 }), /Table not found/);
    });
});
(0, node_test_1.describe)("table reads with the Firestore emulator", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("reads all tables of one round with their hands in table order", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({
            tables: [{ roundId: 1, tableId: 2 }, { roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }],
        });
        const tables = await (0, tableManagerService_1.getRoundTablesWithHands)({ tournamentId: id, roundId: 1 });
        strict_1.default.deepEqual(tables.map((entry) => entry.table.tableId), [1, 2]);
        strict_1.default.ok(tables.every((entry) => entry.hands.length === 16));
    });
    (0, node_test_1.it)("reads every table of every round with its hands, ordered by round and table", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({
            tables: [{ roundId: 2, tableId: 1 }, { roundId: 1, tableId: 2 }, { roundId: 1, tableId: 1 }],
        });
        const tables = await (0, tableManagerService_1.getAllTablesWithHands)({ tournamentId: id });
        strict_1.default.deepEqual(tables.map((entry) => [entry.table.roundId, entry.table.tableId]), [[1, 1], [1, 2], [2, 1]]);
        strict_1.default.ok(tables.every((entry) => entry.hands.length === 16));
    });
    (0, node_test_1.it)("reads no table for a tournament without tables", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [] });
        strict_1.default.deepEqual(await (0, tableManagerService_1.getAllTablesWithHands)({ tournamentId: id }), []);
    });
    (0, node_test_1.it)("returns an empty list for a round without tables", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        strict_1.default.deepEqual(await (0, tableManagerService_1.getRoundTablesWithHands)({ tournamentId: id, roundId: 5 }), []);
    });
});
(0, node_test_1.describe)("tournament manifest with the Firestore emulator", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("returns no table versions before the first save", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.deepEqual(manifest.tableVersions, {});
        strict_1.default.equal(manifest.resources.tables.revision, 1);
    });
    (0, node_test_1.it)("also returns the revisions that all tournaments share", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [] });
        const manifest = await (0, dataVersionsService_1.getTournamentManifest)(id);
        strict_1.default.deepEqual(Object.keys(manifest.globalResources).sort(), ["countries", "emaPlayers", "tournaments", "users"]);
        strict_1.default.ok(Number.isInteger(manifest.globalResources.tournaments.revision));
    });
    (0, node_test_1.it)("fails with not found for a tournament that does not exist", async () => {
        const error = await conflictOf((0, dataVersionsService_1.getTournamentManifest)("test-missing-tournament"));
        strict_1.default.equal(error.status, 404);
    });
});
//# sourceMappingURL=tableManagerService.integration.test.js.map