import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { assignTournamentPlayer } from "../services/tournamentContentService";
import { setAccountEmaId } from "../services/playerAccountsService";
import { setMailTransportForTests, type OutgoingEmail } from "../services/mailService";

type ActionBody = { results: Array<{ playerId: number; outcome: string; message?: string }> };

describe("player invitations", () => {
  let server: TestServer;
  let editor: TestAccount;
  let tournamentId = "";
  const emaA = `EMA-${randomUUID()}`;
  const emaB = `EMA-${randomUUID()}`;

  async function seedSlot(id: number, emaId: string | null): Promise<void> {
    await db.doc(`tournaments/${tournamentId}/players/${id}`).set({
      id, name: `P${id}`, team: 0, assignedEmaId: null, nonMember: null, assignedCountry: "",
    });
    if (emaId) {
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ country: "ES" });
      await assignTournamentPlayer({ tournamentId, playerId: id, emaId, nonMember: null });
    }
  }

  const sent: OutgoingEmail[] = [];
  let failNext = false;

  async function lastMailTo(email: string): Promise<string> {
    const mails = sent.filter((mail) => mail.to === email);
    assert.ok(mails.length >= 1, "an invitation email was sent");
    return decodeURIComponent(mails[mails.length - 1].text.match(/token=([^\s]+)/)![1]);
  }

  before(async () => {
    setMailTransportForTests(async (email) => {
      if (failNext) {
        failNext = false;
        throw new Error("SMTP down");
      }
      sent.push(email);
    });
    requireAuthEmulator();
    server = await startServer();
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    editor = await createAccount({ role: "EDITOR", memberOf: [tournamentId] });
    await seedSlot(1, emaA);
    await seedSlot(2, emaB);
    await seedSlot(3, null);
  });
  after(async () => {
    setMailTransportForTests(null);
    await server.close();
  });

  const act = (action: "send" | "resend" | "revoke", players: Array<{ playerId: number; email?: string }>) =>
    server.request("POST", `/tournaments/${tournamentId}/player-accounts/${action}`, { token: editor.token, body: { players } });
  const outcomeOf = (response: { body: unknown }, playerId: number) =>
    (response.body as ActionBody).results.find((result) => result.playerId === playerId);
  /** Sends an invitation and returns the outcome of the one player. */
  const invite = async (playerId: number, email: string) => {
    const response = await act("send", [{ playerId, email }]);
    return { status: response.status, outcome: outcomeOf(response, playerId)?.outcome, message: outcomeOf(response, playerId)?.message };
  };

  it("refuses a slot without an EMA id", async () => {
    assert.equal((await invite(3, `${randomUUID()}@example.test`)).outcome, "FAILED");
  });

  it("creates a new account with the invitation link and lets it sign in as a player", async () => {
    const email = `${randomUUID()}@example.test`;
    const created = await invite(1, email);
    assert.equal(created.outcome, "INVITED");
    const token = await lastMailTo(email);

    assert.equal((await server.request("POST", "/player/invitations/accept", { body: { token, password: "short" } })).status, 400);
    const accepted = await server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } });
    assert.equal(accepted.status, 200);

    const user = await auth.getUserByEmail(email);
    assert.deepEqual(user.customClaims, { player: true });
    assert.equal((await db.doc(`users/${user.uid}`).get()).get("emaId"), emaA);
    assert.equal((await db.doc(`emaIdIndex/${emaA}`).get()).get("uid"), user.uid);
    const slot = (await db.doc(`tournaments/${tournamentId}/players/1`).get());
    assert.equal(slot.get("accountUid"), user.uid);
    assert.equal(slot.get("invitationStatus"), "ACCEPTED");
    assert.ok((await db.doc(`users/${user.uid}/playerTournaments/${tournamentId}`).get()).exists);

    // The token works once.
    assert.equal((await server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } })).status, 409);
  });

  it("keeps the invitation when the email cannot be sent, so the editor can resend", async () => {
    await seedSlot(7, `EMA-${randomUUID()}`);
    const email = `${randomUUID()}@example.test`;
    failNext = true;

    const failed = await invite(7, email);

    assert.equal(failed.outcome, "FAILED");
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/7`).get()).get("invitationStatus"), "INVITED");
    const resent = await act("resend", [{ playerId: 7 }]);
    assert.equal(outcomeOf(resent, 7)?.outcome, "INVITED");
    assert.ok(await lastMailTo(email));
  });

  it("refuses a second invitation for a slot that has an account", async () => {
    const result = await invite(1, `${randomUUID()}@example.test`);
    // The account exists, so the action sends the registration notice, never a second invitation.
    assert.notEqual(result.outcome, "INVITED");
  });

  it("revokes a pending invitation", async () => {
    await seedSlot(4, `EMA-${randomUUID()}`);
    const email = `${randomUUID()}@example.test`;
    await invite(4, email);
    const token = await lastMailTo(email);

    const revoked = await act("revoke", [{ playerId: 4 }]);
    assert.equal(outcomeOf(revoked, 4)?.outcome, "REVOKED");
    assert.equal((await server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } })).status, 409);
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/4`).get()).get("invitationStatus"), "NONE");
  });

  it("clears the account link when the EMA id of the slot changes", async () => {
    const slot = await db.doc(`tournaments/${tournamentId}/players/1`).get();
    const uid = String(slot.get("accountUid"));
    await db.doc(`emaPlayerRegistry/EMA-NEW`).set({ country: "ES" });

    await assignTournamentPlayer({ tournamentId, playerId: 1, emaId: "EMA-NEW", nonMember: null });

    const changed = await db.doc(`tournaments/${tournamentId}/players/1`).get();
    assert.equal(changed.get("accountUid"), undefined);
    assert.equal(changed.get("invitationStatus"), "NONE");
    assert.equal((await db.doc(`users/${uid}/playerTournaments/${tournamentId}`).get()).exists, false);
  });

  it("an account cannot use two EMA ids", async () => {
    const email = `${randomUUID()}@example.test`;
    await seedSlot(5, `EMA-${randomUUID()}`);
    await seedSlot(6, `EMA-${randomUUID()}`);
    assert.equal((await invite(5, email)).outcome, "INVITED");
    await server.request("POST", "/player/invitations/accept", { body: { token: await lastMailTo(email), password: "Passw0rd!-new" } });

    const second = await invite(6, email);

    assert.equal(second.outcome, "FAILED");
    assert.match(second.message ?? "", /another EMA player/);
  });

  it("sends to the email of an existing account without an invitation: the account links at once", async () => {
    const existing = await createAccount({ role: "EDITOR" });
    await seedSlot(12, `EMA-${randomUUID()}`);

    const result = await act("send", [{ playerId: 12, email: existing.email }]);

    assert.equal(outcomeOf(result, 12)?.outcome, "NOTIFIED");
    const slot = await db.doc(`tournaments/${tournamentId}/players/12`).get();
    assert.equal(slot.get("accountUid"), existing.uid);
    assert.ok((await db.doc(`users/${existing.uid}/playerTournaments/${tournamentId}`).get()).exists);
    assert.deepEqual((await auth.getUser(existing.uid)).customClaims, { editor: true, player: true });
  });

  it("links the slots of an EMA id when an admin sets the EMA id of an account", async () => {
    const account = await createAccount({ role: "EDITOR" });
    const ema = `EMA-${randomUUID()}`;
    await seedSlot(13, ema);
    const before = sent.length;

    await setAccountEmaId({ uid: account.uid, emaId: ema });

    assert.equal((await db.doc(`tournaments/${tournamentId}/players/13`).get()).get("accountUid"), account.uid);
    assert.equal(sent.length, before, "no email is sent");
    assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);

    await setAccountEmaId({ uid: account.uid, emaId: null });

    assert.equal((await db.doc(`tournaments/${tournamentId}/players/13`).get()).get("accountUid"), undefined);
    assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).exists, false);
  });

  it("refuses an EMA id that belongs to another account", async () => {
    const first = await createAccount({ role: "EDITOR" });
    const second = await createAccount({ role: "EDITOR" });
    const ema = `EMA-${randomUUID()}`;
    await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
    await setAccountEmaId({ uid: first.uid, emaId: ema });

    await assert.rejects(setAccountEmaId({ uid: second.uid, emaId: ema }), /belongs to another account/);
  });

  describe("accounts created with an EMA id", () => {
    const createAccountWith = (token: string, email: string, emaId?: string) =>
      server.request("POST", "/admin/users", { token, body: { email, role: "EDITOR", alias: "x", emaId, tournamentAssignments: [] } });

    it("refuses an EMA id and an email that belong to another account", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      const owner = await createAccount({ role: "EDITOR" });
      const ema = `EMA-${randomUUID()}`;
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      await setAccountEmaId({ uid: owner.uid, emaId: ema });

      const email = `${randomUUID()}@example.test`;
      assert.equal((await createAccountWith(admin.token, email, ema)).status, 409);
      await assert.rejects(auth.getUserByEmail(email));
      assert.equal((await createAccountWith(admin.token, owner.email)).status, 409);
      assert.equal((await server.request("PUT", `/admin/users/${admin.uid}`, {
        token: admin.token,
        body: { email: admin.email, role: "ADMIN", alias: "x", tournamentAssignments: [], emaId: ema },
      })).status, 409);
    });

    it("refuses an unknown EMA id and an editor who sets one", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      const unknown = await createAccountWith(admin.token, `${randomUUID()}@example.test`, `EMA-${randomUUID()}`);
      assert.equal(unknown.status, 404);

      const ema = `EMA-${randomUUID()}`;
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      assert.equal((await createAccountWith(editor.token, `${randomUUID()}@example.test`, ema)).status, 403);
    });

    it("lets an admin promote a player-only account to editor and keeps its player access", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      const player = await createAccount({ role: "PLAYER" });

      const response = await server.request("PUT", `/admin/users/${player.uid}`, {
        token: admin.token,
        body: { email: player.email, role: "EDITOR", alias: "x", tournamentAssignments: [] },
      });

      assert.equal(response.status, 200);
      assert.deepEqual((await auth.getUser(player.uid)).customClaims, { player: true, editor: true });
    });
  });
});
