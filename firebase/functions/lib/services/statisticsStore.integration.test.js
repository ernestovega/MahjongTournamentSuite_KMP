"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const statisticsStore_1 = require("./statisticsStore");
const tableManagerService_1 = require("./tableManagerService");
const actor = { uid: "user-ana", name: "Ana" };
/** Unique EMA numbers, so tests do not share player sums. */
const ema = (tag, slot) => `${tag}-${slot}`;
async function seedSlots(tournamentId, tag) {
    for (let slot = 1; slot <= 4; slot += 1) {
        await firebase_1.db.collection("tournaments").doc(tournamentId).collection("players").doc(String(slot)).set({ assignedEmaId: ema(tag, slot) });
    }
}
function saveTotals(tournamentId, expectedVersion, scores) {
    return (0, tableManagerService_1.saveTableState)({
        tournamentId, roundId: 1, tableId: 1, expectedVersion, handPatches: [], actor,
        tablePatch: {
            playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
            useTotalsOnly: true,
            playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
            isCompleted: true,
        },
    });
}
const playerDoc = (id) => firebase_1.db.collection("statsPlayers").doc(id).get();
(0, node_test_1.describe)("statistics store", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("keeps the sums in the same transaction as the table save, and corrects them on an edit", async () => {
        const tag = `a${Date.now()}`;
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedSlots(id, tag);
        const before = await (0, statisticsStore_1.loadOverview)();
        await saveTotals(id, 0, ["30", "10", "-10", "-30"]);
        const first = await playerDoc(ema(tag, 1));
        strict_1.default.equal(first.get("tables"), 1);
        strict_1.default.equal(first.get("place1"), 1);
        strict_1.default.equal(first.get("score"), 30);
        strict_1.default.equal((await firebase_1.db.collection("statsTables").doc(`${id}_1_1`).get()).exists, true);
        strict_1.default.equal((await (0, statisticsStore_1.loadOverview)()).tablesCompleted, before.tablesCompleted + 1);
        // An edit of the score moves the numbers. It does not add them a second time.
        await saveTotals(id, 1, ["50", "10", "-10", "-50"]);
        const edited = await playerDoc(ema(tag, 1));
        strict_1.default.equal(edited.get("tables"), 1);
        strict_1.default.equal(edited.get("score"), 50);
        strict_1.default.equal((await (0, statisticsStore_1.loadOverview)()).tablesCompleted, before.tablesCompleted + 1);
        const person = await (0, statisticsStore_1.loadPersonStatistics)(ema(tag, 4), null);
        strict_1.default.equal(person.tournaments.length, 1);
        strict_1.default.equal(person.tournaments[0].results[0].position, 4);
        strict_1.default.deepEqual(await (0, statisticsStore_1.loadStandings)(ema(tag, 4), [id, "other"]), [{ tournamentId: id, rank: 4, of: 4 }]);
        strict_1.default.deepEqual(await (0, statisticsStore_1.loadStandings)(ema(tag, 1), [id]), [{ tournamentId: id, rank: 1, of: 4 }]);
        strict_1.default.equal((await (0, statisticsStore_1.loadPersonStatistics)(ema(tag, 4), new Set(["other"]))).tournaments.length, 0);
    });
    (0, node_test_1.it)("takes the numbers out when the table is reset or the tournament is removed", async () => {
        const tag = `b${Date.now()}`;
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedSlots(id, tag);
        await saveTotals(id, 0, ["30", "10", "-10", "-30"]);
        await (0, tableManagerService_1.resetTable)({ tournamentId: id, roundId: 1, tableId: 1, actor });
        strict_1.default.equal((await playerDoc(ema(tag, 1))).get("tables"), 0);
        strict_1.default.equal((await firebase_1.db.collection("statsTables").doc(`${id}_1_1`).get()).exists, false);
        await saveTotals(id, 2, ["30", "10", "-10", "-30"]);
        strict_1.default.equal((await playerDoc(ema(tag, 1))).get("tables"), 1);
        await (0, statisticsStore_1.removeTournamentStats)(id);
        strict_1.default.equal((await playerDoc(ema(tag, 1))).get("tables"), 0);
        strict_1.default.equal((await firebase_1.db.collection("statsTournaments").doc(id).get()).exists, false);
    });
    (0, node_test_1.it)("builds the statistics of a tournament again without counting a table twice", async () => {
        const tag = `c${Date.now()}`;
        const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedSlots(id, tag);
        await saveTotals(id, 0, ["30", "10", "-10", "-30"]);
        strict_1.default.equal(await (0, statisticsStore_1.refreshTournamentStats)(id), 1);
        strict_1.default.equal((await playerDoc(ema(tag, 2))).get("tables"), 1);
    });
    (0, node_test_1.it)("lists the players of the leaderboards", async () => {
        const overview = await (0, statisticsStore_1.loadOverview)();
        strict_1.default.ok(overview.players.length <= 15);
        strict_1.default.ok(overview.tournaments >= 1);
    });
});
//# sourceMappingURL=statisticsStore.integration.test.js.map