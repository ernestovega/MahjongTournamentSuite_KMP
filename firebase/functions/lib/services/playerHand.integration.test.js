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
    isDraw: false, isLastHand: false,
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
    (0, node_test_1.it)("marks the table completed when the player flags the last hand", async () => {
        const fixture = await seedTable();
        await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: { ...hand("1", "4"), isLastHand: true }, expectedVersion: 0 });
        const table = await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1);
        strict_1.default.equal(table.isCompleted, true);
        strict_1.default.equal(table.playerEastScore, "34");
        await rejects((0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: hand("2", "3") }), 409);
    });
    (0, node_test_1.it)("records a draw as a hand with zero for every seat", async () => {
        const fixture = await seedTable();
        const draw = (0, playerHandService_1.parseHandInput)({ isDraw: true });
        const preview = await (0, playerHandService_1.previewHand)({ ...at(fixture), uid: fixture.uid, input: draw });
        strict_1.default.deepEqual(preview.handScores, [0, 0, 0, 0]);
        await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: draw, expectedVersion: 0 });
        const saved = await (0, firestoreFixtures_1.readHand)(fixture.tournamentId, 1, 1, 1);
        strict_1.default.equal(saved.isDone, true);
        strict_1.default.equal(saved.playerWinnerId, "");
        strict_1.default.equal(saved.handScore, "0");
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1)).playerEastScore, "0");
    });
    (0, node_test_1.it)("parses the penalties of a request", () => {
        const base = { playerSouthPenalty: "-15", playerEastPenalty: "5", playerWestPenalty: "5", playerNorthPenalty: "5" };
        strict_1.default.throws(() => (0, playerHandService_1.parsePenaltyInput)({}), httpError_1.HttpError);
        strict_1.default.throws(() => (0, playerHandService_1.parsePenaltyInput)({ playerSouthPenalty: "abc" }), httpError_1.HttpError);
        // Every penalty needs a negative value.
        strict_1.default.throws(() => (0, playerHandService_1.parsePenaltyInput)({ playerSouthPenalty: "+5" }), httpError_1.HttpError);
        strict_1.default.throws(() => (0, playerHandService_1.parsePenaltyInput)({ playerSouthPenalty: "-5;5", playerEastPenalty: ";5" }), httpError_1.HttpError);
        strict_1.default.deepEqual((0, playerHandService_1.parsePenaltyInput)(base), ["5", "-15", "5", "5"]);
        // A second penalty in the same request.
        strict_1.default.deepEqual((0, playerHandService_1.parsePenaltyInput)({ playerSouthPenalty: "-15;5", playerEastPenalty: "+5;-6", playerWestPenalty: "5;2" }), ["5;-6", "-15;5", "5;2", ""]);
    });
    (0, node_test_1.it)("adds penalties to the hand in play without finishing the hand", async () => {
        const fixture = await seedTable();
        const first = await (0, playerHandService_1.addPenalties)({
            ...at(fixture), uid: fixture.uid, penalties: ["5", "-15", "5", "5"], expectedVersion: 0,
        });
        strict_1.default.equal(first.handId, 1);
        strict_1.default.equal(first.tableVersion, 1);
        // A second penalty goes after the first one.
        await (0, playerHandService_1.addPenalties)({ ...at(fixture), uid: fixture.uid, penalties: ["", "", "-4", ""], expectedVersion: 1 });
        const pending = await (0, firestoreFixtures_1.readHand)(fixture.tournamentId, 1, 1, 1);
        strict_1.default.equal(pending.isDone, false);
        strict_1.default.deepEqual([pending.playerEastPenalty, pending.playerSouthPenalty, pending.playerWestPenalty, pending.playerNorthPenalty], ["5;", "-15;", "5;-4", "5;"]);
        // The hand in play keeps its penalties when the result is saved, and they count in the totals.
        const result = await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 2 });
        strict_1.default.deepEqual(result.handScores, [39, -23, -7, -13]);
        const saved = await (0, firestoreFixtures_1.readHand)(fixture.tournamentId, 1, 1, 1);
        strict_1.default.equal(saved.playerWestPenalty, "5;-4");
    });
    (0, node_test_1.it)("refuses penalties from a player who is not at the table or with an old table version", async () => {
        const fixture = await seedTable();
        await rejects((0, playerHandService_1.addPenalties)({ ...at(fixture), uid: fixture.otherUid, penalties: ["", "-5", "", ""], expectedVersion: 0 }), 403);
        await rejects((0, playerHandService_1.addPenalties)({ ...at(fixture), uid: fixture.uid, penalties: ["", "-5", "", ""], expectedVersion: 7 }), 409);
    });
    (0, node_test_1.it)("keeps the table open when the hand is not the last one", async () => {
        const fixture = await seedTable();
        await (0, playerHandService_1.recordHand)({ ...at(fixture), uid: fixture.uid, input: hand("1", "4"), expectedVersion: 0 });
        strict_1.default.equal((await (0, firestoreFixtures_1.readTable)(fixture.tournamentId, 1, 1)).isCompleted, false);
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
//# sourceMappingURL=playerHand.integration.test.js.map