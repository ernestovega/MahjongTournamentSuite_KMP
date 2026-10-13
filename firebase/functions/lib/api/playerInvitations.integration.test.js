"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_crypto_1 = require("node:crypto");
const node_test_1 = require("node:test");
const firebase_1 = require("../firebase");
const firestoreFixtures_1 = require("../testing/firestoreFixtures");
const authFixtures_1 = require("../testing/authFixtures");
const tournamentContentService_1 = require("../services/tournamentContentService");
const playerAccountsService_1 = require("../services/playerAccountsService");
const mailService_1 = require("../services/mailService");
(0, node_test_1.describe)("player invitations", () => {
    let server;
    let editor;
    let tournamentId = "";
    const emaA = `EMA-${(0, node_crypto_1.randomUUID)()}`;
    const emaB = `EMA-${(0, node_crypto_1.randomUUID)()}`;
    async function seedSlot(id, emaId) {
        await firebase_1.db.doc(`tournaments/${tournamentId}/players/${id}`).set({
            id, name: `P${id}`, team: 0, assignedEmaId: null, nonMember: null, assignedCountry: "",
        });
        if (emaId) {
            await firebase_1.db.doc(`emaPlayerRegistry/${emaId}`).set({ country: "ES" });
            await (0, tournamentContentService_1.assignTournamentPlayer)({ tournamentId, playerId: id, emaId, nonMember: null });
        }
    }
    const sent = [];
    let failNext = false;
    async function lastMailTo(email) {
        const mails = sent.filter((mail) => mail.to === email);
        strict_1.default.ok(mails.length >= 1, "an invitation email was sent");
        return decodeURIComponent(mails[mails.length - 1].text.match(/token=([^\s]+)/)[1]);
    }
    (0, node_test_1.before)(async () => {
        (0, mailService_1.setMailTransportForTests)(async (email) => {
            if (failNext) {
                failNext = false;
                throw new Error("SMTP down");
            }
            sent.push(email);
        });
        (0, authFixtures_1.requireAuthEmulator)();
        server = await (0, authFixtures_1.startServer)();
        tournamentId = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        editor = await (0, authFixtures_1.createAccount)({ role: "EDITOR", memberOf: [tournamentId] });
        await seedSlot(1, emaA);
        await seedSlot(2, emaB);
        await seedSlot(3, null);
    });
    (0, node_test_1.after)(async () => {
        (0, mailService_1.setMailTransportForTests)(null);
        await server.close();
    });
    const act = (action, players) => server.request("POST", `/tournaments/${tournamentId}/player-accounts/${action}`, { token: editor.token, body: { players } });
    const outcomeOf = (response, playerId) => response.body.results.find((result) => result.playerId === playerId);
    /** Sends an invitation and returns the outcome of the one player. */
    const invite = async (playerId, email) => {
        const response = await act("send", [{ playerId, email }]);
        return { status: response.status, outcome: outcomeOf(response, playerId)?.outcome, message: outcomeOf(response, playerId)?.message };
    };
    (0, node_test_1.it)("refuses a slot without an EMA id", async () => {
        strict_1.default.equal((await invite(3, `${(0, node_crypto_1.randomUUID)()}@example.test`)).outcome, "FAILED");
    });
    (0, node_test_1.it)("creates a new account with the invitation link and lets it sign in as a player", async () => {
        const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
        const created = await invite(1, email);
        strict_1.default.equal(created.outcome, "INVITED");
        const token = await lastMailTo(email);
        strict_1.default.equal((await server.request("POST", "/player/invitations/accept", { body: { token, password: "short" } })).status, 400);
        const accepted = await server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } });
        strict_1.default.equal(accepted.status, 200);
        const user = await firebase_1.auth.getUserByEmail(email);
        strict_1.default.deepEqual(user.customClaims, { player: true });
        strict_1.default.equal((await firebase_1.db.doc(`users/${user.uid}`).get()).get("emaId"), emaA);
        strict_1.default.equal((await firebase_1.db.doc(`emaIdIndex/${emaA}`).get()).get("uid"), user.uid);
        const slot = (await firebase_1.db.doc(`tournaments/${tournamentId}/players/1`).get());
        strict_1.default.equal(slot.get("accountUid"), user.uid);
        strict_1.default.equal(slot.get("invitationStatus"), "ACCEPTED");
        strict_1.default.ok((await firebase_1.db.doc(`users/${user.uid}/playerTournaments/${tournamentId}`).get()).exists);
        // The token works once.
        strict_1.default.equal((await server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } })).status, 409);
    });
    (0, node_test_1.it)("keeps the invitation when the email cannot be sent, so the editor can resend", async () => {
        await seedSlot(7, `EMA-${(0, node_crypto_1.randomUUID)()}`);
        const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
        failNext = true;
        const failed = await invite(7, email);
        strict_1.default.equal(failed.outcome, "FAILED");
        strict_1.default.equal((await firebase_1.db.doc(`tournaments/${tournamentId}/players/7`).get()).get("invitationStatus"), "INVITED");
        const resent = await act("resend", [{ playerId: 7 }]);
        strict_1.default.equal(outcomeOf(resent, 7)?.outcome, "INVITED");
        strict_1.default.ok(await lastMailTo(email));
    });
    (0, node_test_1.it)("refuses a second invitation for a slot that has an account", async () => {
        const result = await invite(1, `${(0, node_crypto_1.randomUUID)()}@example.test`);
        // The account exists, so the action sends the registration notice, never a second invitation.
        strict_1.default.notEqual(result.outcome, "INVITED");
    });
    (0, node_test_1.it)("revokes a pending invitation", async () => {
        await seedSlot(4, `EMA-${(0, node_crypto_1.randomUUID)()}`);
        const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
        await invite(4, email);
        const token = await lastMailTo(email);
        const revoked = await act("revoke", [{ playerId: 4 }]);
        strict_1.default.equal(outcomeOf(revoked, 4)?.outcome, "REVOKED");
        strict_1.default.equal((await server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } })).status, 409);
        strict_1.default.equal((await firebase_1.db.doc(`tournaments/${tournamentId}/players/4`).get()).get("invitationStatus"), "NONE");
    });
    (0, node_test_1.it)("clears the account link when the EMA id of the slot changes", async () => {
        const slot = await firebase_1.db.doc(`tournaments/${tournamentId}/players/1`).get();
        const uid = String(slot.get("accountUid"));
        await firebase_1.db.doc(`emaPlayerRegistry/EMA-NEW`).set({ country: "ES" });
        await (0, tournamentContentService_1.assignTournamentPlayer)({ tournamentId, playerId: 1, emaId: "EMA-NEW", nonMember: null });
        const changed = await firebase_1.db.doc(`tournaments/${tournamentId}/players/1`).get();
        strict_1.default.equal(changed.get("accountUid"), undefined);
        strict_1.default.equal(changed.get("invitationStatus"), "NONE");
        strict_1.default.equal((await firebase_1.db.doc(`users/${uid}/playerTournaments/${tournamentId}`).get()).exists, false);
    });
    (0, node_test_1.it)("an account cannot use two EMA ids", async () => {
        const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
        await seedSlot(5, `EMA-${(0, node_crypto_1.randomUUID)()}`);
        await seedSlot(6, `EMA-${(0, node_crypto_1.randomUUID)()}`);
        strict_1.default.equal((await invite(5, email)).outcome, "INVITED");
        await server.request("POST", "/player/invitations/accept", { body: { token: await lastMailTo(email), password: "Passw0rd!-new" } });
        const second = await invite(6, email);
        strict_1.default.equal(second.outcome, "FAILED");
        strict_1.default.match(second.message ?? "", /another EMA player/);
    });
    (0, node_test_1.it)("sends to the email of an existing account without an invitation: the account links at once", async () => {
        const existing = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        await seedSlot(12, `EMA-${(0, node_crypto_1.randomUUID)()}`);
        const result = await act("send", [{ playerId: 12, email: existing.email }]);
        strict_1.default.equal(outcomeOf(result, 12)?.outcome, "NOTIFIED");
        const slot = await firebase_1.db.doc(`tournaments/${tournamentId}/players/12`).get();
        strict_1.default.equal(slot.get("accountUid"), existing.uid);
        strict_1.default.ok((await firebase_1.db.doc(`users/${existing.uid}/playerTournaments/${tournamentId}`).get()).exists);
        strict_1.default.deepEqual((await firebase_1.auth.getUser(existing.uid)).customClaims, { editor: true, player: true });
    });
    (0, node_test_1.it)("links the slots of an EMA id when an admin sets the EMA id of an account", async () => {
        const account = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
        await seedSlot(13, ema);
        const before = sent.length;
        await (0, playerAccountsService_1.setAccountEmaId)({ uid: account.uid, emaId: ema });
        strict_1.default.equal((await firebase_1.db.doc(`tournaments/${tournamentId}/players/13`).get()).get("accountUid"), account.uid);
        strict_1.default.equal(sent.length, before, "no email is sent");
        strict_1.default.equal((await firebase_1.db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);
        await (0, playerAccountsService_1.setAccountEmaId)({ uid: account.uid, emaId: null });
        strict_1.default.equal((await firebase_1.db.doc(`tournaments/${tournamentId}/players/13`).get()).get("accountUid"), undefined);
        strict_1.default.equal((await firebase_1.db.doc(`emaIdIndex/${ema}`).get()).exists, false);
    });
    (0, node_test_1.it)("refuses an EMA id that belongs to another account", async () => {
        const first = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        const second = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
        const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
        await firebase_1.db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
        await (0, playerAccountsService_1.setAccountEmaId)({ uid: first.uid, emaId: ema });
        await strict_1.default.rejects((0, playerAccountsService_1.setAccountEmaId)({ uid: second.uid, emaId: ema }), /belongs to another account/);
    });
    (0, node_test_1.describe)("accounts created with an EMA id", () => {
        const createAccountWith = (token, email, emaId) => server.request("POST", "/admin/users", { token, body: { email, role: "EDITOR", alias: "x", emaId, tournamentAssignments: [] } });
        (0, node_test_1.it)("refuses an EMA id and an email that belong to another account", async () => {
            const admin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
            const owner = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
            const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            await firebase_1.db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
            await (0, playerAccountsService_1.setAccountEmaId)({ uid: owner.uid, emaId: ema });
            const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
            strict_1.default.equal((await createAccountWith(admin.token, email, ema)).status, 409);
            await strict_1.default.rejects(firebase_1.auth.getUserByEmail(email));
            strict_1.default.equal((await createAccountWith(admin.token, owner.email)).status, 409);
            strict_1.default.equal((await server.request("PUT", `/admin/users/${admin.uid}`, {
                token: admin.token,
                body: { email: admin.email, role: "ADMIN", alias: "x", tournamentAssignments: [], emaId: ema },
            })).status, 409);
        });
        (0, node_test_1.it)("refuses an unknown EMA id and an editor who sets one", async () => {
            const admin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
            const unknown = await createAccountWith(admin.token, `${(0, node_crypto_1.randomUUID)()}@example.test`, `EMA-${(0, node_crypto_1.randomUUID)()}`);
            strict_1.default.equal(unknown.status, 404);
            const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            await firebase_1.db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
            strict_1.default.equal((await createAccountWith(editor.token, `${(0, node_crypto_1.randomUUID)()}@example.test`, ema)).status, 403);
        });
        (0, node_test_1.it)("lets an admin promote a player-only account to editor and keeps its player access", async () => {
            const admin = await (0, authFixtures_1.createAccount)({ role: "ADMIN" });
            const player = await (0, authFixtures_1.createAccount)({ role: "PLAYER" });
            const response = await server.request("PUT", `/admin/users/${player.uid}`, {
                token: admin.token,
                body: { email: player.email, role: "EDITOR", alias: "x", tournamentAssignments: [] },
            });
            strict_1.default.equal(response.status, 200);
            strict_1.default.deepEqual((await firebase_1.auth.getUser(player.uid)).customClaims, { player: true, editor: true });
        });
    });
});
//# sourceMappingURL=playerInvitations.integration.test.js.map