"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const httpError_1 = require("../api/httpError");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const tableManagerService_1 = require("./tableManagerService");
const tournamentContentService_1 = require("./tournamentContentService");
const statisticsStore_1 = require("./statisticsStore");
const actor = { uid: "user-ana", name: "Ana" };
async function seed(tag) {
    const id = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }] });
    const tournament = firebase_1.db.collection("tournaments").doc(id);
    await tournament.update({ numRounds: 3, isTeams: false });
    for (let slot = 1; slot <= 4; slot += 1) {
        await tournament.collection("players").doc(String(slot)).set({ id: slot, assignedEmaId: `${tag}-${slot}`, nonMember: null });
    }
    await tournament.collection("players").doc("9").set({ id: 9, assignedEmaId: null, nonMember: null });
    return { id, tournament };
}
function saveTotals(tournamentId, roundId, expectedVersion, scores) {
    return (0, tableManagerService_1.saveTableState)({
        tournamentId, roundId, tableId: 1, expectedVersion, handPatches: [], actor,
        tablePatch: {
            playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4",
            useTotalsOnly: true,
            playerEastScore: scores[0], playerSouthScore: scores[1], playerWestScore: scores[2], playerNorthScore: scores[3],
            isCompleted: true,
        },
    });
}
const illness = (fromRound, duringSession = false, excludedFromEma = false) => ({ reason: "ILLNESS", fromRound, duringSession, excludedFromEma });
const substitute = (id, playerId, substitution) => (0, tournamentContentService_1.setPlayerSubstitution)({ tournamentId: id, playerId, substitution, uid: "u1" });
(0, node_test_1.describe)("substitution of a player", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("stores the substitution, logs it and removes it again", async () => {
        const { id, tournament } = await seed(`a${Date.now()}`);
        await substitute(id, 2, illness(2, false, true));
        strict_1.default.deepEqual((await tournament.collection("players").doc("2").get()).get("substitution"), { reason: "ILLNESS", fromRound: 2, duringSession: false, excludedFromEma: true });
        await substitute(id, 2, null);
        strict_1.default.equal((await tournament.collection("players").doc("2").get()).get("substitution"), undefined);
        const log = await tournament.collection("playerLog").get();
        strict_1.default.deepEqual(log.docs.map((doc) => doc.get("kind")).sort(), ["restore", "substitute"]);
    });
    (0, node_test_1.it)("marks the table of the round when the player leaves during the session, and clears it", async () => {
        const { id, tournament } = await seed(`b${Date.now()}`);
        await saveTotals(id, 2, 0, ["50", "30", "10", "-90"]);
        const table = tournament.collection("tables").doc("2_1");
        await substitute(id, 2, illness(2, true));
        const marked = await table.get();
        strict_1.default.equal(marked.get("forcedZeroPlayerId"), "2");
        strict_1.default.equal(marked.get("version"), 2);
        strict_1.default.deepEqual([marked.get("playerEastPoints"), marked.get("playerSouthPoints"), marked.get("playerWestPoints"), marked.get("playerNorthPoints")], ["4", "0", "2", "1"]);
        strict_1.default.equal((await tournament.get()).get("dataVersions.tableVersions.2_1"), 2);
        await substitute(id, 2, null);
        const cleared = await table.get();
        strict_1.default.equal(cleared.get("forcedZeroPlayerId"), undefined);
        strict_1.default.equal(cleared.get("playerSouthPoints"), "2");
    });
    (0, node_test_1.it)("refuses wrong input", async () => {
        const { id } = await seed(`c${Date.now()}`);
        const refused = (promise, status) => strict_1.default.rejects(promise, (error) => error instanceof httpError_1.HttpError && error.status === status);
        await refused(substitute(id, 9, illness(2)), 409);
        await refused(substitute(id, 1, illness(7)), 400);
        await refused(substitute(id, 1, { reason: "LATE", fromRound: 2, duringSession: false, excludedFromEma: true }), 400);
        await refused(substitute(id, 1, { reason: "LATE", fromRound: 2, duringSession: true, excludedFromEma: false }), 400);
        await refused(substitute(id, 1, { reason: "DISQUALIFIED", fromRound: 2, duringSession: false, excludedFromEma: true }), 400);
        await refused(substitute(id, 1, illness(3, true)), 409);
    });
    (0, node_test_1.it)("takes the rounds of the substitute out of the person statistics and the tournament standings", async () => {
        const tag = `d${Date.now()}`;
        const { id } = await seed(tag);
        await saveTotals(id, 1, 0, ["30", "10", "-10", "-30"]);
        await saveTotals(id, 2, 0, ["30", "10", "-10", "-30"]);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 2);
        await substitute(id, 1, illness(2));
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 1);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-2`).get()).get("tables"), 2);
        const totals = (await firebase_1.db.collection("statsTournaments").doc(id).get()).get("totals");
        strict_1.default.equal(totals["1"].score, 30);
        strict_1.default.equal(totals["2"].score, 20);
        strict_1.default.deepEqual(await (0, statisticsStore_1.loadStandings)(`${tag}-2`, [id]), [{ tournamentId: id, rank: 2, of: 4 }]);
        await substitute(id, 1, null);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 2);
        strict_1.default.equal((await firebase_1.db.collection("statsTournaments").doc(id).get()).get("totals")["1"].score, 60);
    });
    (0, node_test_1.it)("counts the zeros of a disqualified player in the person statistics", async () => {
        const tag = `e${Date.now()}`;
        const { id } = await seed(tag);
        await saveTotals(id, 1, 0, ["30", "10", "-10", "-30"]);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("score"), 30);
        await substitute(id, 1, { reason: "DISQUALIFIED", fromRound: 2, duringSession: false, excludedFromEma: false });
        const player = await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get();
        strict_1.default.equal(player.get("tables"), 1);
        strict_1.default.equal(player.get("points"), 0);
        strict_1.default.equal(player.get("score"), 0);
        const totals = (await firebase_1.db.collection("statsTournaments").doc(id).get()).get("totals");
        strict_1.default.equal(totals["1"].score, 0);
        strict_1.default.equal(totals["1"].points, 0);
        await substitute(id, 1, null);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("score"), 30);
    });
    (0, node_test_1.it)("leaves an excluded player out of the person statistics but keeps the earlier standings", async () => {
        const tag = `f${Date.now()}`;
        const { id } = await seed(tag);
        await saveTotals(id, 1, 0, ["30", "10", "-10", "-30"]);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 1);
        await substitute(id, 1, illness(2, false, true));
        const player = await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get();
        strict_1.default.equal(player.get("tables") ?? 0, 0);
        strict_1.default.equal((await firebase_1.db.collection("statsTournaments").doc(id).get()).get("totals")["1"].score, 30);
        await substitute(id, 1, null);
        strict_1.default.equal((await firebase_1.db.collection("statsPlayers").doc(`${tag}-1`).get()).get("tables"), 1);
    });
});
//# sourceMappingURL=playerSubstitution.integration.test.js.map