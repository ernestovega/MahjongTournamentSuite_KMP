"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const authFixtures_1 = require("../testing/authFixtures");
(0, node_test_1.describe)("what a player can read", () => {
    let server;
    let t1 = "";
    let t2 = "";
    let player;
    let editor;
    (0, node_test_1.before)(async () => {
        (0, authFixtures_1.requireAuthEmulator)();
        server = await (0, authFixtures_1.startServer)();
        t1 = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        t2 = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        for (const [tournamentId, emaId] of [[t1, "10000001"], [t1, "10000002"], [t2, "10000003"]]) {
            await firebase_1.db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Name", lastName: emaId, country: "ESP" });
        }
        await firebase_1.db.doc(`tournaments/${t1}/players/1`).set({
            id: 1, team: 0, assignedEmaId: "10000001", accountUid: "someone", invitationStatus: "ACCEPTED", invitedEmail: "x@example.test",
        });
        await firebase_1.db.doc(`tournaments/${t1}/players/2`).set({ id: 2, team: 0, assignedEmaId: "10000002" });
        await firebase_1.db.doc(`tournaments/${t2}/players/1`).set({ id: 1, team: 0, assignedEmaId: "10000003" });
        await firebase_1.db.doc(`tournaments/${t1}`).update({ name: "Mine" });
        await firebase_1.db.doc(`tournaments/${t2}`).update({ name: "Other" });
        player = await (0, authFixtures_1.createAccount)({ role: "PLAYER", playerOf: [t1] });
        editor = await (0, authFixtures_1.createAccount)({ role: "EDITOR", memberOf: [t2] });
    });
    (0, node_test_1.after)(async () => { await server.close(); });
    (0, node_test_1.it)("lists only the tournaments where the player is registered", async () => {
        const { status, body } = await server.request("GET", "/tournaments", { token: player.token });
        strict_1.default.equal(status, 200);
        strict_1.default.deepEqual(body.tournaments.map((t) => t.id), [t1]);
    });
    (0, node_test_1.it)("hides accounts and emails of other players", async () => {
        const { body } = await server.request("GET", `/tournaments/${t1}/players`, { token: player.token });
        const players = body.players;
        strict_1.default.equal(players.length, 2);
        for (const slot of players) {
            strict_1.default.equal(slot.accountUid, null);
            strict_1.default.equal(slot.invitedEmail, null);
            strict_1.default.equal(slot.invitationStatus, "NONE");
        }
    });
    (0, node_test_1.it)("lists registry players only from the tournaments of the player", async () => {
        const { body } = await server.request("GET", "/ema-player-registry", { token: player.token });
        strict_1.default.deepEqual(body.players.map((p) => p.emaId).sort(), ["10000001", "10000002"]);
    });
    (0, node_test_1.it)("refuses writes and editor reads in the player tournament", async () => {
        strict_1.default.equal((await server.request("PUT", `/tournaments/${t1}/tables/1/1/state`, { token: player.token, body: {} })).status, 403);
        strict_1.default.equal((await server.request("GET", `/tournaments/${t1}/members`, { token: player.token })).status, 403);
        strict_1.default.equal((await server.request("GET", `/tournaments/${t1}/player-invitations`, { token: player.token })).status, 403);
        strict_1.default.equal((await server.request("GET", `/tournaments/${t2}/players`, { token: player.token })).status, 403);
    });
    (0, node_test_1.it)("keeps the editor view of an editor", async () => {
        const { body } = await server.request("GET", `/tournaments/${t2}/players`, { token: editor.token });
        strict_1.default.equal(body.players.length, 1);
    });
});
//# sourceMappingURL=playerReads.integration.test.js.map