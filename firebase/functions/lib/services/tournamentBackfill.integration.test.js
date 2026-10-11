"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const tournamentBackfill_1 = require("./tournamentBackfill");
(0, node_test_1.describe)("backfill of old tournaments", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("previews without writing anything", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }], withHands: false });
        const result = await (0, tournamentBackfill_1.backfillTournament)(id, false);
        strict_1.default.deepEqual(result.missingHands.map((entry) => entry.tableId), ["1_1", "1_2"]);
        strict_1.default.equal(result.applied, false);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 0);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 0);
    });
    (0, node_test_1.it)("creates the 16 hands of every table without hands and sets the counter", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }], withHands: false });
        await (0, tournamentBackfill_1.backfillTournament)(id, true);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 16);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 2, 1), 16);
        const tournament = await (0, firestoreFixtures_1.readTournament)(id);
        strict_1.default.equal(tournament.tablesWithProgress, 0);
    });
    (0, node_test_1.it)("never overwrites hands that already exist", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const { db } = await Promise.resolve().then(() => __importStar(require("../firebase")));
        await db.collection("tournaments").doc(id).collection("tables").doc("1_1").collection("hands").doc("3")
            .update({ handScore: "24", isDone: true });
        const result = await (0, tournamentBackfill_1.backfillTournament)(id, true);
        strict_1.default.deepEqual(result.missingHands, []);
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(id, 1, 1, 3)).handScore, "24");
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 16);
    });
    (0, node_test_1.it)("counts the tables that have data", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({
            tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2, isCompleted: true }, { roundId: 1, tableId: 3 }],
        });
        const { db } = await Promise.resolve().then(() => __importStar(require("../firebase")));
        await db.collection("tournaments").doc(id).collection("tables").doc("1_1")
            .collection("hands").doc("1").update({ handScore: "8" });
        const result = await (0, tournamentBackfill_1.backfillTournament)(id, true);
        strict_1.default.equal(result.tablesWithProgress, 2);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 2);
    });
    (0, node_test_1.it)("can run twice with the same result", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });
        await (0, tournamentBackfill_1.backfillTournament)(id, true);
        const second = await (0, tournamentBackfill_1.backfillTournament)(id, true);
        strict_1.default.deepEqual(second.missingHands, []);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 16);
    });
    (0, node_test_1.it)("handles a tournament with many tables in several batches", async () => {
        const tables = Array.from({ length: 40 }, (_, index) => ({ roundId: 1, tableId: index + 1 }));
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables, withHands: false });
        await (0, tournamentBackfill_1.backfillTournament)(id, true);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 40), 16);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 16);
    });
    (0, node_test_1.it)("lets the first save after the backfill work normally", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }], withHands: false });
        await (0, tournamentBackfill_1.backfillTournament)(id, true);
        const { saveTableState } = await Promise.resolve().then(() => __importStar(require("./tableManagerService")));
        const saved = await saveTableState({
            tournamentId: id, roundId: 1, tableId: 1, expectedVersion: 0, tablePatch: {},
            handPatches: [{ handId: 2, patch: { handScore: "12" } }],
        });
        strict_1.default.equal(saved.table.version, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTournament)(id)).tablesWithProgress, 1);
    });
    (0, node_test_1.it)("completes a table that has only some of its hands and keeps the ones it has", async () => {
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        const { db } = await Promise.resolve().then(() => __importStar(require("../firebase")));
        const hands = db.collection("tournaments").doc(id).collection("tables").doc("1_1").collection("hands");
        for (const handId of [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16])
            await hands.doc(String(handId)).delete();
        await hands.doc("2").update({ handScore: "30", isDone: true });
        const result = await (0, tournamentBackfill_1.backfillTournament)(id, true);
        strict_1.default.deepEqual(result.missingHands, [{ tableId: "1_1", handIds: [4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16] }]);
        strict_1.default.equal(await (0, firestoreFixtures_1.countHandDocs)(id, 1, 1), 16);
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(id, 1, 1, 2)).handScore, "30");
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(id, 1, 1, 10)).handId, 10);
    });
});
//# sourceMappingURL=tournamentBackfill.integration.test.js.map