import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { Timestamp } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, signInTestAccount, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { assignTournamentPlayer, listTournamentPlayers } from "../services/tournamentContentService";
import { listInvitedPeople, setAccountEmaId } from "../services/playerAccountsService";
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

  describe("language of the emails", () => {
    before(async () => {
      for (const id of [11, 12, 13]) await seedSlot(id, `EMA-${randomUUID()}`);
    });

    const inviteWith = async (playerId: number, email: string, acceptLanguage?: string) => {
      const response = await server.request("POST", `/tournaments/${tournamentId}/player-accounts/send`, {
        token: editor.token,
        body: { players: [{ playerId, email }] },
        headers: acceptLanguage ? { "accept-language": acceptLanguage } : undefined,
      });
      assert.equal(response.status, 200);
      return sent.filter((mail) => mail.to === email).pop()!;
    };

    it("writes the invitation in Spanish when the app asks for Spanish", async () => {
      const mail = await inviteWith(11, `${randomUUID()}@example.test`, "es-ES,es;q=0.9,en;q=0.5");
      assert.match(mail.subject, /^Invitación a /);
      assert.match(mail.text, /Abre este enlace para aceptar/);
      assert.match(mail.html, /Aceptar la invitación/);
    });

    it("writes the invitation in English without a header or for another language", async () => {
      const withoutHeader = await inviteWith(12, `${randomUUID()}@example.test`);
      assert.match(withoutHeader.subject, /^Invitation to /);
      const german = await inviteWith(13, `${randomUUID()}@example.test`, "de-DE");
      assert.match(german.subject, /^Invitation to /);
    });
  });

  it("refuses a slot without an EMA id", async () => {
    assert.equal((await invite(3, `${randomUUID()}@example.test`)).outcome, "FAILED");
  });

  it("creates a new account with the invitation link and lets it sign in as a player", async () => {
    const email = `${randomUUID()}@example.test`;
    const created = await invite(1, email);
    assert.equal(created.outcome, "INVITED");
    const token = await lastMailTo(email);
    const mail = sent.filter((m) => m.to === email).pop()!;
    assert.match(mail.text, /This is the player app of the tournament\./);
    assert.match(mail.html ?? "", /This is the player app of the tournament\./);

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

  it("gives a new account the full EMA name as alias and lets the user change it", async () => {
    const ema = `EMA-${randomUUID()}`;
    await db.doc(`emaPlayerRegistry/${ema}`).set({
      firstName: "Ana", lastName: "Pérez", country: "ESP", photoUrl: "https://example.test/ana.jpg",
    });
    await db.doc(`tournaments/${tournamentId}/players/30`).set({
      id: 30, name: "P30", team: 0, assignedEmaId: null, nonMember: null, assignedCountry: "",
    });
    await assignTournamentPlayer({ tournamentId, playerId: 30, emaId: ema, nonMember: null });
    const email = `${randomUUID()}@example.test`;
    assert.equal((await invite(30, email)).outcome, "INVITED");
    const accepted = await server.request("POST", "/player/invitations/accept", {
      body: { token: await lastMailTo(email), password: "Passw0rd!-test" },
    });
    assert.equal(accepted.status, 200);

    const token = await signInTestAccount(email);
    const profile = await server.request("GET", "/auth/me", { token });
    assert.equal(profile.status, 200);
    assert.equal((profile.body as { alias: string }).alias, "Ana Pérez");
    assert.equal((profile.body as { emaId: string }).emaId, ema);
    assert.equal((profile.body as { photoUrl: string }).photoUrl, "https://example.test/ana.jpg");

    const changed = await server.request("PUT", "/auth/me", { token, body: { alias: "  Anita  " } });
    assert.equal(changed.status, 200);
    assert.equal((changed.body as { alias: string }).alias, "Anita");
    assert.equal((changed.body as { emaId: string }).emaId, ema);
    assert.equal((await server.request("PUT", "/auth/me", { token, body: { alias: "   " } })).status, 400);
    assert.equal((await server.request("PUT", "/auth/me", { token, body: { alias: "x".repeat(61) } })).status, 400);
  });

  it("links every tournament of the EMA id when the player accepts one invitation", async () => {
    const ema = `EMA-${randomUUID()}`;
    const otherTournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await seedSlot(20, ema);
    await db.doc(`tournaments/${otherTournamentId}/players/5`).set({
      id: 5, name: "P5", team: 0, assignedEmaId: null, nonMember: null, assignedCountry: "",
    });
    await assignTournamentPlayer({ tournamentId: otherTournamentId, playerId: 5, emaId: ema, nonMember: null });
    const email = `${randomUUID()}@example.test`;
    assert.equal((await invite(20, email)).outcome, "INVITED");

    const accepted = await server.request("POST", "/player/invitations/accept", {
      body: { token: await lastMailTo(email), password: "Passw0rd!-new" },
    });

    assert.equal(accepted.status, 200);
    const user = await auth.getUserByEmail(email);
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/20`).get()).get("accountUid"), user.uid);
    assert.equal((await db.doc(`tournaments/${otherTournamentId}/players/5`).get()).get("accountUid"), user.uid);
    assert.ok((await db.doc(`users/${user.uid}/playerTournaments/${otherTournamentId}`).get()).exists);
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

    it("needs an EMA id for a player account, and lets only an admin set the EMA id of an editor", async () => {
      const body = { email: `${randomUUID()}@example.test`, role: "PLAYER", alias: "x", emaId: `EMA-${randomUUID()}` };

      assert.equal((await server.request("POST", "/admin/users", { token: editor.token, body: { ...body, emaId: undefined } })).status, 400);
      assert.equal((await server.request("POST", "/admin/users", { token: editor.token, body: { ...body, role: "EDITOR", tournamentAssignments: [] } })).status, 403);
      await assert.rejects(auth.getUserByEmail(body.email));
    });

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

    it("lets an editor change the EMA id of a player account, but not of an editor account", async () => {
      const player = await createAccount({ role: "PLAYER" });
      const other = await createAccount({ role: "EDITOR" });
      const ema = `EMA-${randomUUID()}`;
      await seedSlot(22, ema);
      const put = (account: TestAccount, role: string) => server.request("PUT", `/admin/users/${account.uid}`, {
        token: editor.token,
        body: { email: account.email, role, alias: "x", tournamentAssignments: [], emaId: ema },
      });

      assert.equal((await put(other, "EDITOR")).status, 403);
      assert.equal((await put(player, "PLAYER")).status, 200);

      assert.equal((await db.doc(`users/${player.uid}`).get()).get("emaId"), ema);
      assert.equal((await db.doc(`tournaments/${tournamentId}/players/22`).get()).get("accountUid"), player.uid);
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

  describe("state table cases (docs/player-accounts-states.md)", () => {
    const accept = (token: string) =>
      server.request("POST", "/player/invitations/accept", { body: { token, password: "Passw0rd!-new" } });
    const slotOf = (id: number) => db.doc(`tournaments/${tournamentId}/players/${id}`).get();
    const accountWith = async (ema: string) => {
      const account = await createAccount({ role: "EDITOR" });
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      await setAccountEmaId({ uid: account.uid, emaId: ema });
      return account;
    };

    it("E: assigning an EMA id to a slot links the existing account at once and sends no email", async () => {
      const ema = `EMA-${randomUUID()}`;
      const account = await accountWith(ema);
      const before = sent.length;

      await seedSlot(30, ema);

      assert.equal((await slotOf(30)).get("accountUid"), account.uid);
      assert.equal(sent.length, before);
    });

    it("F: assigning an EMA id with no account leaves the slot unlinked and sends no email", async () => {
      const before = sent.length;

      await seedSlot(31, `EMA-${randomUUID()}`);

      assert.equal((await slotOf(31)).get("accountUid"), undefined);
      assert.equal(sent.length, before);
    });

    it("G: resend gives a new link and the old link stops working", async () => {
      await seedSlot(32, `EMA-${randomUUID()}`);
      const email = `${randomUUID()}@example.test`;
      await invite(32, email);
      const oldToken = await lastMailTo(email);

      assert.equal(outcomeOf(await act("resend", [{ playerId: 32 }]), 32)?.outcome, "INVITED");
      const newToken = await lastMailTo(email);

      assert.notEqual(newToken, oldToken);
      assert.equal((await accept(oldToken)).status, 404);
      assert.equal((await accept(newToken)).status, 200);
    });

    it("I: a linked slot gets one reminder with send, and resend repeats it", async () => {
      const ema = `EMA-${randomUUID()}`;
      const account = await accountWith(ema);
      await seedSlot(33, ema);
      const mailsToAccount = () => sent.filter((mail) => mail.to === account.email).length;

      assert.equal(outcomeOf(await act("send", [{ playerId: 33 }]), 33)?.outcome, "NOTIFIED");
      assert.equal(mailsToAccount(), 1);
      assert.equal(outcomeOf(await act("send", [{ playerId: 33 }]), 33)?.outcome, "SKIPPED");
      assert.equal(mailsToAccount(), 1);
      assert.equal(outcomeOf(await act("resend", [{ playerId: 33 }]), 33)?.outcome, "NOTIFIED");
      assert.equal(mailsToAccount(), 2);
    });

    it("K: an expired invitation cannot be accepted and the slot stays unlinked", async () => {
      await seedSlot(34, `EMA-${randomUUID()}`);
      const email = `${randomUUID()}@example.test`;
      await invite(34, email);
      const pending = await db.collection(`tournaments/${tournamentId}/playerInvitations`)
        .where("playerId", "==", 34).where("status", "==", "PENDING").get();
      await pending.docs[0].ref.update({ expiresAt: Timestamp.fromMillis(Date.now() - 1000) });

      assert.equal((await accept(await lastMailTo(email))).status, 409);
      assert.equal((await slotOf(34)).get("accountUid"), undefined);
    });

    it("L: revoke unlinks only that tournament, keeps the account and its EMA id, and send links again", async () => {
      const ema = `EMA-${randomUUID()}`;
      const account = await accountWith(ema);
      await seedSlot(35, ema);

      assert.equal(outcomeOf(await act("revoke", [{ playerId: 35 }]), 35)?.outcome, "REVOKED");

      const slot = await slotOf(35);
      assert.equal(slot.get("accountUid"), undefined);
      assert.equal(slot.get("invitationStatus"), "NONE");
      assert.equal((await db.doc(`users/${account.uid}/playerTournaments/${tournamentId}`).get()).exists, false);
      assert.equal((await db.doc(`users/${account.uid}`).get()).get("emaId"), ema);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);

      assert.equal(outcomeOf(await act("send", [{ playerId: 35 }]), 35)?.outcome, "NOTIFIED");
      assert.equal((await slotOf(35)).get("accountUid"), account.uid);
    });

    it("D: changing the EMA id of an account releases the old tournaments and links the new ones", async () => {
      const first = `EMA-${randomUUID()}`;
      const second = `EMA-${randomUUID()}`;
      const account = await accountWith(first);
      await seedSlot(36, first);
      await seedSlot(37, second);
      assert.equal((await slotOf(36)).get("accountUid"), account.uid);

      await setAccountEmaId({ uid: account.uid, emaId: second });

      assert.equal((await slotOf(36)).get("accountUid"), undefined);
      assert.equal((await slotOf(37)).get("accountUid"), account.uid);
      assert.equal((await db.doc(`emaIdIndex/${first}`).get()).exists, false);
    });

    it("C: an account with no EMA id sees no tournament", async () => {
      const account = await createAccount({ role: "EDITOR" });

      assert.equal((await db.collection(`users/${account.uid}/playerTournaments`).get()).size, 0);
    });

    it("P: an invitation marks every tournament of the person, and revoke or resend works from any of them", async () => {
      const ema = `EMA-${randomUUID()}`;
      const otherTournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
      await seedSlot(40, ema);
      await db.doc(`tournaments/${otherTournamentId}/players/6`).set({
        id: 6, name: "P6", team: 0, assignedEmaId: null, nonMember: null, assignedCountry: "",
      });
      await assignTournamentPlayer({ tournamentId: otherTournamentId, playerId: 6, emaId: ema, nonMember: null });
      const email = `${randomUUID()}@example.test`;
      const statusIn = async (id: string, playerId: number) =>
        (await listTournamentPlayers(id)).find((player) => player.id === playerId)?.invitationStatus;

      await invite(40, email);

      assert.equal(await statusIn(tournamentId, 40), "INVITED");
      assert.equal(await statusIn(otherTournamentId, 6), "INVITED");
      const listed = (await listInvitedPeople()).filter((item) => item.emaId === ema);
      assert.deepEqual(listed.map((item) => [item.tournamentId, item.email]), [[tournamentId, email]]);
      assert.equal(listed[0].expired, false);
      assert.deepEqual(
        (await listInvitedPeople(new Set([otherTournamentId]))).filter((item) => item.emaId === ema), [],
      );

      const sentBefore = sent.length;
      const blocked = await server.request("POST", `/tournaments/${otherTournamentId}/player-accounts/send`, {
        token: (await createAccount({ role: "ADMIN" })).token, body: { players: [{ playerId: 6, email }] },
      });
      assert.equal((blocked.body as ActionBody).results[0].outcome, "SKIPPED");
      assert.equal(sent.length, sentBefore);

      const revoked = await server.request("POST", `/tournaments/${otherTournamentId}/player-accounts/revoke`, {
        token: (await createAccount({ role: "ADMIN" })).token, body: { players: [{ playerId: 6 }] },
      });
      assert.equal((revoked.body as ActionBody).results[0].outcome, "REVOKED");
      assert.equal(await statusIn(tournamentId, 40), "NONE");
      assert.equal(await statusIn(otherTournamentId, 6), "NONE");
      assert.deepEqual((await listInvitedPeople()).filter((item) => item.emaId === ema), []);
    });
  });
});
