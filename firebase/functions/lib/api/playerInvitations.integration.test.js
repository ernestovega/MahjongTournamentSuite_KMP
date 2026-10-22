"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const strict_1 = __importDefault(require("node:assert/strict"));
const node_crypto_1 = require("node:crypto");
const node_test_1 = require("node:test");
const firestore_1 = require("firebase-admin/firestore");
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
        const mail = sent.filter((m) => m.to === email).pop();
        strict_1.default.match(mail.text, /This is the player app of the tournament\./);
        strict_1.default.match(mail.html ?? "", /This is the player app of the tournament\./);
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
    (0, node_test_1.it)("links every tournament of the EMA id when the player accepts one invitation", async () => {
        const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
        const otherTournamentId = await (0, firestoreFixtures_1.seedTournament)({ tables: [{ roundId: 1, tableId: 1 }] });
        await seedSlot(20, ema);
        await firebase_1.db.doc(`tournaments/${otherTournamentId}/players/5`).set({
            id: 5, name: "P5", team: 0, assignedEmaId: null, nonMember: null, assignedCountry: "",
        });
        await (0, tournamentContentService_1.assignTournamentPlayer)({ tournamentId: otherTournamentId, playerId: 5, emaId: ema, nonMember: null });
        const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
        strict_1.default.equal((await invite(20, email)).outcome, "INVITED");
        const accepted = await server.request("POST", "/player/invitations/accept", {
            body: { token: await lastMailTo(email), password: "Passw0rd!-new" },
        });
        strict_1.default.equal(accepted.status, 200);
        const user = await firebase_1.auth.getUserByEmail(email);
        strict_1.default.equal((await firebase_1.db.doc(`tournaments/${tournamentId}/players/20`).get()).get("accountUid"), user.uid);
        strict_1.default.equal((await firebase_1.db.doc(`tournaments/${otherTournamentId}/players/5`).get()).get("accountUid"), user.uid);
        strict_1.default.ok((await firebase_1.db.doc(`users/${user.uid}/playerTournaments/${otherTournamentId}`).get()).exists);
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
        (0, node_test_1.it)("needs an EMA id for a player account, and lets only an admin set the EMA id of an editor", async () => {
            const body = { email: `${(0, node_crypto_1.randomUUID)()}@example.test`, role: "PLAYER", alias: "x", emaId: `EMA-${(0, node_crypto_1.randomUUID)()}` };
            strict_1.default.equal((await server.request("POST", "/admin/users", { token: editor.token, body: { ...body, emaId: undefined } })).status, 400);
            strict_1.default.equal((await server.request("POST", "/admin/users", { token: editor.token, body: { ...body, role: "EDITOR", tournamentAssignments: [] } })).status, 403);
            await strict_1.default.rejects(firebase_1.auth.getUserByEmail(body.email));
        });
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
        (0, node_test_1.it)("lets an editor change the EMA id of a player account, but not of an editor account", async () => {
            const player = await (0, authFixtures_1.createAccount)({ role: "PLAYER" });
            const other = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
            const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            await seedSlot(22, ema);
            const put = (account, role) => server.request("PUT", `/admin/users/${account.uid}`, {
                token: editor.token,
                body: { email: account.email, role, alias: "x", tournamentAssignments: [], emaId: ema },
            });
            strict_1.default.equal((await put(other, "EDITOR")).status, 403);
            strict_1.default.equal((await put(player, "PLAYER")).status, 200);
            strict_1.default.equal((await firebase_1.db.doc(`users/${player.uid}`).get()).get("emaId"), ema);
            strict_1.default.equal((await firebase_1.db.doc(`tournaments/${tournamentId}/players/22`).get()).get("accountUid"), player.uid);
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
    (0, node_test_1.describe)("state table cases (docs/player-accounts-states.md)", () => {
        const accept = (token) => server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } });
        const slotOf = (id) => firebase_1.db.doc(`tournaments/${tournamentId}/players/${id}`).get();
        const accountWith = async (ema) => {
            const account = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
            await firebase_1.db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
            await (0, playerAccountsService_1.setAccountEmaId)({ uid: account.uid, emaId: ema });
            return account;
        };
        (0, node_test_1.it)("E: assigning an EMA id to a slot links the existing account at once and sends no email", async () => {
            const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            const account = await accountWith(ema);
            const before = sent.length;
            await seedSlot(30, ema);
            strict_1.default.equal((await slotOf(30)).get("accountUid"), account.uid);
            strict_1.default.equal(sent.length, before);
        });
        (0, node_test_1.it)("F: assigning an EMA id with no account leaves the slot unlinked and sends no email", async () => {
            const before = sent.length;
            await seedSlot(31, `EMA-${(0, node_crypto_1.randomUUID)()}`);
            strict_1.default.equal((await slotOf(31)).get("accountUid"), undefined);
            strict_1.default.equal(sent.length, before);
        });
        (0, node_test_1.it)("G: resend gives a new link and the old link stops working", async () => {
            await seedSlot(32, `EMA-${(0, node_crypto_1.randomUUID)()}`);
            const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
            await invite(32, email);
            const oldToken = await lastMailTo(email);
            strict_1.default.equal(outcomeOf(await act("resend", [{ playerId: 32 }]), 32)?.outcome, "INVITED");
            const newToken = await lastMailTo(email);
            strict_1.default.notEqual(newToken, oldToken);
            strict_1.default.equal((await accept(oldToken)).status, 404);
            strict_1.default.equal((await accept(newToken)).status, 200);
        });
        (0, node_test_1.it)("I: a linked slot gets one reminder with send, and resend repeats it", async () => {
            const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            const account = await accountWith(ema);
            await seedSlot(33, ema);
            const mailsToAccount = () => sent.filter((mail) => mail.to === account.email).length;
            strict_1.default.equal(outcomeOf(await act("send", [{ playerId: 33 }]), 33)?.outcome, "NOTIFIED");
            strict_1.default.equal(mailsToAccount(), 1);
            strict_1.default.equal(outcomeOf(await act("send", [{ playerId: 33 }]), 33)?.outcome, "SKIPPED");
            strict_1.default.equal(mailsToAccount(), 1);
            strict_1.default.equal(outcomeOf(await act("resend", [{ playerId: 33 }]), 33)?.outcome, "NOTIFIED");
            strict_1.default.equal(mailsToAccount(), 2);
        });
        (0, node_test_1.it)("K: an expired invitation cannot be accepted and the slot stays unlinked", async () => {
            await seedSlot(34, `EMA-${(0, node_crypto_1.randomUUID)()}`);
            const email = `${(0, node_crypto_1.randomUUID)()}@example.test`;
            await invite(34, email);
            const pending = await firebase_1.db.collection(`tournaments/${tournamentId}/playerInvitations`)
                .where("playerId", "==", 34).where("status", "==", "PENDING").get();
            await pending.docs[0].ref.update({ expiresAt: firestore_1.Timestamp.fromMillis(Date.now() - 1000) });
            strict_1.default.equal((await accept(await lastMailTo(email))).status, 409);
            strict_1.default.equal((await slotOf(34)).get("accountUid"), undefined);
        });
        (0, node_test_1.it)("L: revoke unlinks only that tournament, keeps the account and its EMA id, and send links again", async () => {
            const ema = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            const account = await accountWith(ema);
            await seedSlot(35, ema);
            strict_1.default.equal(outcomeOf(await act("revoke", [{ playerId: 35 }]), 35)?.outcome, "REVOKED");
            const slot = await slotOf(35);
            strict_1.default.equal(slot.get("accountUid"), undefined);
            strict_1.default.equal(slot.get("invitationStatus"), "NONE");
            strict_1.default.equal((await firebase_1.db.doc(`users/${account.uid}/playerTournaments/${tournamentId}`).get()).exists, false);
            strict_1.default.equal((await firebase_1.db.doc(`users/${account.uid}`).get()).get("emaId"), ema);
            strict_1.default.equal((await firebase_1.db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);
            strict_1.default.equal(outcomeOf(await act("send", [{ playerId: 35 }]), 35)?.outcome, "NOTIFIED");
            strict_1.default.equal((await slotOf(35)).get("accountUid"), account.uid);
        });
        (0, node_test_1.it)("D: changing the EMA id of an account releases the old tournaments and links the new ones", async () => {
            const first = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            const second = `EMA-${(0, node_crypto_1.randomUUID)()}`;
            const account = await accountWith(first);
            await seedSlot(36, first);
            await seedSlot(37, second);
            strict_1.default.equal((await slotOf(36)).get("accountUid"), account.uid);
            await (0, playerAccountsService_1.setAccountEmaId)({ uid: account.uid, emaId: second });
            strict_1.default.equal((await slotOf(36)).get("accountUid"), undefined);
            strict_1.default.equal((await slotOf(37)).get("accountUid"), account.uid);
            strict_1.default.equal((await firebase_1.db.doc(`emaIdIndex/${first}`).get()).exists, false);
        });
        (0, node_test_1.it)("C: an account with no EMA id sees no tournament", async () => {
            const account = await (0, authFixtures_1.createAccount)({ role: "EDITOR" });
            strict_1.default.equal((await firebase_1.db.collection(`users/${account.uid}/playerTournaments`).get()).size, 0);
        });
    });
});
//# sourceMappingURL=playerInvitations.integration.test.js.map