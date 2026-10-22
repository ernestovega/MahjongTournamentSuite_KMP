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
const matchTimeCloseService_1 = require("./matchTimeCloseService");
const playerHandService_1 = require("./playerHandService");
/** A tournament in UTC whose round 1 started [startedMinutesAgo] minutes ago. The match lasts 120 minutes. */
async function seedTimedTable(startedMinutesAgo, mode, doneHands = 0) {
    const tournamentId = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
    const start = new Date(Date.now() - startedMinutesAgo * 60000).toISOString();
    await firebase_1.db.doc(`tournaments/${tournamentId}`).update({
        numRounds: 1,
        timeZone: "UTC",
        matchDurationMinutes: 120,
        matchEndMode: mode,
        matchExtraMinutes: 15,
        roundSchedules: [{ roundId: 1, date: start.slice(0, 10), startTime: start.slice(11, 16) }],
    });
    await firebase_1.db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
        playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4", hasProgress: doneHands > 0,
    });
    for (let handId = 1; handId <= doneHands; handId++) {
        await firebase_1.db.doc(`tournaments/${tournamentId}/tables/1_1/hands/${handId}`).update({
            playerWinnerId: "1", playerLooserId: "2", handScore: "8", isDone: true,
        });
    }
    const uid = `u-${tournamentId}-1`;
    await firebase_1.db.doc(`users/${uid}/playerTournaments/${tournamentId}`).set({ playerId: 1, emaId: "E1" });
    return { tournamentId, uid };
}
const win = () => (0, playerHandService_1.parseHandInput)({ playerWinnerId: "1", playerLooserId: "4", handScore: "10" });
(0, node_test_1.describe)("match time", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("refuses a hand of a player when the time is over", async () => {
        const { tournamentId, uid } = await seedTimedTable(200, "STOP");
        await strict_1.default.rejects((0, playerHandService_1.recordHand)({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 }), (error) => error instanceof httpError_1.HttpError && error.status === 409);
    });
    (0, node_test_1.it)("makes the hand in extra time the last hand of the table", async () => {
        const { tournamentId, uid } = await seedTimedTable(125, "FINISH_HAND");
        await (0, playerHandService_1.recordHand)({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(tournamentId, 1, 1)).isCompleted, true);
    });
    (0, node_test_1.it)("accepts a hand while the match is playing", async () => {
        const { tournamentId, uid } = await seedTimedTable(30, "STOP");
        await (0, playerHandService_1.recordHand)({ tournamentId, roundId: 1, tableId: 1, uid, input: win(), expectedVersion: 0 });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(tournamentId, 1, 1)).isCompleted, false);
    });
    (0, node_test_1.it)("completes an expired table with played hands, once", async () => {
        const { tournamentId } = await seedTimedTable(200, "STOP", 3);
        const first = await (0, matchTimeCloseService_1.closeRoundTables)(tournamentId, 1);
        strict_1.default.equal(first.closed, 1);
        const table = await (0, firestoreFixtures_1.readTable)(tournamentId, 1, 1);
        strict_1.default.equal(table.isCompleted, true);
        const changes = await (0, firestoreFixtures_1.readChanges)(tournamentId);
        strict_1.default.equal(changes[changes.length - 1]?.source, "system");
        // An organizer opens the table again to add the missing data. The server does not close it again.
        await firebase_1.db.doc(`tournaments/${tournamentId}/tables/1_1`).update({ isCompleted: false });
        await (0, matchTimeCloseService_1.closeRoundTables)(tournamentId, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(tournamentId, 1, 1)).isCompleted, false);
    });
    (0, node_test_1.it)("leaves a table without played hands and a table in extra time open", async () => {
        const empty = await seedTimedTable(200, "STOP", 0);
        const extra = await seedTimedTable(125, "FINISH_HAND", 3);
        await (0, matchTimeCloseService_1.closeRoundTables)(empty.tournamentId, 1);
        await (0, matchTimeCloseService_1.closeRoundTables)(extra.tournamentId, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(empty.tournamentId, 1, 1)).isCompleted, false);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(extra.tournamentId, 1, 1)).isCompleted, false);
    });
    (0, node_test_1.it)("completes a table in extra time once the extra time is over", async () => {
        // The match lasts 120 minutes and the extra time 15 minutes, so 130 minutes is still extra time and 140 is not.
        const inExtra = await seedTimedTable(130, "FINISH_HAND", 3);
        const afterExtra = await seedTimedTable(140, "FINISH_HAND", 3);
        await (0, matchTimeCloseService_1.closeRoundTables)(inExtra.tournamentId, 1);
        await (0, matchTimeCloseService_1.closeRoundTables)(afterExtra.tournamentId, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(inExtra.tournamentId, 1, 1)).isCompleted, false);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(afterExtra.tournamentId, 1, 1)).isCompleted, true);
    });
    (0, node_test_1.it)("leaves a table open while its match is still playing", async () => {
        const { tournamentId } = await seedTimedTable(30, "STOP", 3);
        await (0, matchTimeCloseService_1.closeRoundTables)(tournamentId, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(tournamentId, 1, 1)).isCompleted, false);
    });
    (0, node_test_1.it)("skips a tournament without time zone", async () => {
        const { tournamentId } = await seedTimedTable(200, "STOP", 3);
        await firebase_1.db.doc(`tournaments/${tournamentId}`).update({ timeZone: null });
        await (0, matchTimeCloseService_1.closeRoundTables)(tournamentId, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(tournamentId, 1, 1)).isCompleted, false);
    });
});
//# sourceMappingURL=matchTime.integration.test.js.map