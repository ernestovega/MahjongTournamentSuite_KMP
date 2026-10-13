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
const dataVersionsService_1 = require("./dataVersionsService");
const playerHandService_1 = require("./playerHandService");
const tableManagerService_1 = require("./tableManagerService");
/** A hands-mode table with seats 1..4 from east to north. Slot 1 and slot 2 have a player account. */
async function seedTable(options = {}) {
    const tournamentId = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
    await firebase_1.db.doc(`tournaments/${tournamentId}/tables/1_1`).update({
        useTotalsOnly: options.totalsOnly ?? false,
        usePointsCalculation: true,
        ...(options.seats === false ? {} : { playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4" }),
    });
    const uid = `u-${tournamentId}-1`;
    const otherUid = `u-${tournamentId}-9`;
    await firebase_1.db.doc(`users/${uid}/playerTournaments/${tournamentId}`).set({ playerId: 1, emaId: "E1" });
    await firebase_1.db.doc(`users/${otherUid}/playerTournaments/${tournamentId}`).set({ playerId: 9, emaId: "E9" });
    return { tournamentId, uid, otherUid };
}
const at = (fixture) => ({ tournamentId: fixture.tournamentId, roundId: 1, tableId: 1 });
const hand = (winner, looser, score = "10") => ({
    playerWinnerId: winner, playerLooserId: looser, handScore: score, isChickenHand: false,
});
async function rejects(promise, status) {
    await strict_1.default.rejects(promise, (error) => error instanceof httpError_1.HttpError && error.status === status);
}
(0, node_test_1.describe)("hand entry by a player", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    (0, node_test_1.it)("previews the hand and the table totals without writing", async () => {
        const fixture = await seedTable();
        const preview = await (0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") });
        strict_1.default.equal(preview.handId, 1);
        strict_1.default.deepEqual(preview.seats, ["1", "2", "3", "4"]);
        strict_1.default.deepEqual(preview.handScores, [34, -8, -8, -18]);
        strict_1.default.deepEqual(preview.tableScores, ["34", "-8", "-8", "-18"]);
        strict_1.default.deepEqual(preview.tablePoints, ["4", "1,5", "1,5", "0"]);
        strict_1.default.equal(preview.tableVersion, 0);
        strict_1.default.equal((await (0, firestoreFixtures_1.readHand)(fixture.tournamentId, 1, 1, 1)).isDone, false);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1)).version, 0);
    });
    (0, node_test_1.it)("records the hand, updates the totals and logs the player", async () => {
        const fixture = await seedTable();
        const preview = await (0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") });
        const result = await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: preview.tableVersion });
        strict_1.default.equal(result.tableVersion, 1);
        const stored = await (0, firestoreFixtures_1.readHand)(fixture.tournamentId, 1, 1, 1);
        strict_1.default.equal(stored.isDone, true);
        strict_1.default.equal(stored.playerWinnerId, "1");
        const table = await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1);
        strict_1.default.equal(table.version, 1);
        strict_1.default.equal(table.playerEastScore, "34");
        strict_1.default.equal(table.playerNorthPoints, "0");
        strict_1.default.equal((await (0, dataVersionsService_1.getTournamentManifest)(fixture.tournamentId)).tableVersions["1_1"], 1);
        const changes = await (0, firestoreFixtures_1.readChanges)(fixture.tournamentId);
        strict_1.default.equal(changes[changes.length - 1]?.uid, fixture.uid);
    });
    (0, node_test_1.it)("uses the next free hand and adds to the totals", async () => {
        const fixture = await seedTable();
        await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 });
        const preview = await (0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("2", "") });
        strict_1.default.equal(preview.handId, 2);
        strict_1.default.deepEqual(preview.handScores, [-18, 54, -18, -18]);
        strict_1.default.deepEqual(preview.tableScores, ["16", "46", "-26", "-36"]);
    });
    (0, node_test_1.it)("refuses a record when the table changed after the review", async () => {
        const fixture = await seedTable();
        const preview = await (0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4") });
        await (0, tableManagerService_1.saveTableState)({
            ...at(fixture), expectedVersion: 0, tablePatch: {},
            handPatches: [{ handId: 1, patch: { playerWinnerId: "2", playerLooserId: "3", handScore: "9", isDone: true } }],
        });
        await rejects((0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: preview.tableVersion }), 409);
    });
    (0, node_test_1.it)("refuses a player who does not sit at the table or is not registered", async () => {
        const fixture = await seedTable();
        await rejects((0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.otherUid, input: hand("1", "4") }), 403);
        await rejects((0, playerHandService_1.previewHand)({ ...at(fixture), uid: "nobody", input: hand("1", "4") }), 403);
    });
    (0, node_test_1.it)("refuses totals-only tables and tables without four seats", async () => {
        const totalsOnly = await seedTable({ totalsOnly: true });
        const noSeats = await seedTable({ seats: false });
        await rejects((0, playerHandService_1.previewHand)({ ...at(totalsOnly), uid: totalsOnly.uid, input: hand("1", "4") }), 409);
        await rejects((0, playerHandService_1.previewHand)({ ...at(noSeats), uid: noSeats.uid, input: hand("1", "4") }), 409);
    });
    (0, node_test_1.it)("refuses values that are not a valid hand", async () => {
        const fixture = await seedTable();
        await rejects((0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "1") }), 400);
        await rejects((0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("9", "1") }), 400);
        strict_1.default.throws(() => (0, playerHandService_1.parseHandInput)({ playerWinnerId: "1", playerLooserId: "4", handScore: "7" }), httpError_1.HttpError);
        strict_1.default.throws(() => (0, playerHandService_1.parseHandInput)({ playerWinnerId: "1", playerLooserId: "", handScore: "10", isChickenHand: true }), httpError_1.HttpError);
    });
    (0, node_test_1.it)("applies one hand when two records race with the same version", async () => {
        const fixture = await seedTable();
        const results = await Promise.allSettled([
            (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 }),
            (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("2", "3"), expectedVersion: 0 }),
        ]);
        strict_1.default.equal(results.filter((result) => result.status === "fulfilled").length, 1);
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1)).version, 1);
    });
});
(0, node_test_1.describe)("seat assignment by a player", () => {
    (0, node_test_1.before)(firestoreFixtures_1.requireEmulator);
    const seats = { east: "3", south: "1", west: "4", north: "2" };
    (0, node_test_1.it)("lets a player of the table assign the seats and keeps the mode of the table", async () => {
        const fixture = await seedTable({ seats: false });
        const result = await (0, playerHandService_1.assignSeats)({ ...at(fixture), uid: fixture.uid, seats, expectedVersion: 0 });
        strict_1.default.deepEqual(result.seats, ["3", "1", "4", "2"]);
        const table = await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1);
        strict_1.default.equal(table.playerEastId, "3");
        strict_1.default.equal(table.playerNorthId, "2");
        strict_1.default.equal(table.useTotalsOnly, false);
        strict_1.default.equal(table.version, 1);
        const totalsOnly = await seedTable({ totalsOnly: true, seats: false });
        await (0, playerHandService_1.assignSeats)({ ...at(totalsOnly), uid: totalsOnly.uid, seats, expectedVersion: 0 });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(totalsOnly.tournamentId, 1, 1)).useTotalsOnly, true);
        // The hand entry works right after.
        const preview = await (0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("3", "2") });
        strict_1.default.deepEqual(preview.seats, ["3", "1", "4", "2"]);
    });
    (0, node_test_1.it)("refuses a player who does not play at the table", async () => {
        const fixture = await seedTable({ seats: false });
        await rejects((0, playerHandService_1.assignSeats)({ ...at(fixture), uid: fixture.otherUid, seats, expectedVersion: 0 }), 403);
    });
    (0, node_test_1.it)("refuses seats with a player from another table or a repeated player", async () => {
        const fixture = await seedTable({ seats: false });
        await rejects((0, playerHandService_1.assignSeats)({ ...at(fixture), uid: fixture.uid, seats: { ...seats, north: "7" }, expectedVersion: 0 }), 400);
        strict_1.default.throws(() => (0, playerHandService_1.parseSeatAssignment)({ playerEastId: "1", playerSouthId: "1", playerWestId: "3", playerNorthId: "4" }), httpError_1.HttpError);
    });
    (0, node_test_1.it)("refuses to change the seats after a hand is saved", async () => {
        const fixture = await seedTable();
        await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 });
        await rejects((0, playerHandService_1.assignSeats)({ ...at(fixture), uid: fixture.uid, seats, expectedVersion: 1 }), 409);
    });
    (0, node_test_1.it)("refuses when an editor entered totals or the table version changed", async () => {
        const withTotals = await seedTable({ seats: false });
        await firebase_1.db.doc(`tournaments/${withTotals.tournamentId}/tables/1_1`).update({ playerEastScore: "10" });
        await rejects((0, playerHandService_1.assignSeats)({ ...at(withTotals), uid: withTotals.uid, seats, expectedVersion: 0 }), 409);
        const changed = await seedTable({ seats: false });
        await rejects((0, playerHandService_1.assignSeats)({ ...at(changed), uid: changed.uid, seats, expectedVersion: 5 }), 409);
    });
});
//# sourceMappingURL=playerHand.integration.test.js.map