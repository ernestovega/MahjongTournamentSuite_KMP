import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, linkedAccountUid, requireAuthEmulator, signInTestAccount, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { assignTournamentPlayer, listTournamentPlayers } from "../services/tournamentContentService";
import { createPlayerAccount, getPlayerRegistration, listPlayerTournamentIds, setAccountEmaId } from "../services/playerAccountsService";
import { setMailTransportForTests, type OutgoingEmail } from "../services/mailService";
import { setPasswordResetSenderForTests } from "../services/firebaseAuthRest";

type ActionBody = { results: Array<{ playerId: number; outcome: string; message?: string }> };
type EmailBody = { results: Array<{ playerId: number; outcome: string; message?: string }> };

describe("player accounts", () => {
  let server: TestServer;
  let editor: TestAccount;
  let tournamentId = "";

  async function seedSlot(id: number, emaId: string | null, inTournament = tournamentId): Promise<void> {
    await db.doc(`tournaments/${inTournament}/players/${id}`).set({
      id, name: `P${id}`, team: 0, assignedEmaId: null, assignedCountry: "",
    });
    if (emaId) {
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ country: "ES" });
      await assignTournamentPlayer({ tournamentId: inTournament, playerId: id, emaId });
    }
  }

  const accountOf = (playerId: number) => linkedAccountUid(tournamentId, playerId);
  const sent: OutgoingEmail[] = [];
  const resets: string[] = [];
  let failNextMail = false;
  let failNextReset = false;
  const uniqueEmail = () => `${randomUUID()}@example.test`;
  const newEma = () => `EMA-${randomUUID()}`;

  before(async () => {
    setMailTransportForTests(async (email) => {
      if (failNextMail) {
        failNextMail = false;
        throw new Error("SMTP down");
      }
      sent.push(email);
    });
    setPasswordResetSenderForTests(async (email) => {
      if (failNextReset) {
        failNextReset = false;
        throw new Error("Google API down");
      }
      resets.push(email);
    });
    requireAuthEmulator();
    server = await startServer();
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    editor = await createAccount({ role: "EDITOR", memberOf: [tournamentId] });
    await seedSlot(3, null);
  });
  after(async () => {
    setMailTransportForTests(null);
    setPasswordResetSenderForTests(null);
    await server.close();
  });

  const act = (action: "send" | "resend" | "revoke", players: Array<{ playerId: number; email?: string }>, headers?: Record<string, string>) =>
    server.request("POST", `/tournaments/${tournamentId}/player-accounts/${action}`, { token: editor.token, body: { players }, headers });
  const outcomeOf = (response: { body: unknown }, playerId: number) =>
    (response.body as ActionBody).results.find((result) => result.playerId === playerId);
  const slotOf = (id: number) => db.doc(`tournaments/${tournamentId}/players/${id}`).get();
  /** Runs an action for one player and returns its outcome and message. */
  const run = async (action: "send" | "resend" | "revoke", playerId: number, email?: string) => {
    const response = await act(action, [{ playerId, email }]);
    return { status: response.status, outcome: outcomeOf(response, playerId)?.outcome, message: outcomeOf(response, playerId)?.message };
  };
  /** Gives an account a password and signs it in, as the person does after the password reset email. */
  async function activate(email: string): Promise<void> {
    await auth.updateUser((await auth.getUserByEmail(email)).uid, { password: "Passw0rd!-test", emailVerified: true });
    await signInTestAccount(email);
  }
  const accountWith = async (ema: string) => {
    const account = await createAccount({ role: "PLAYER" });
    await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
    await setAccountEmaId({ uid: account.uid, emaId: ema });
    return account;
  };

  describe("invite: an email for a player with no account", () => {
    it("refuses a slot without an EMA id", async () => {
      assert.equal((await run("send", 3, uniqueEmail())).outcome, "FAILED");
    });

    it("refuses a slot that has no account and no email", async () => {
      await seedSlot(40, newEma());
      const result = await run("send", 40);
      assert.equal(result.outcome, "FAILED");
      assert.match(result.message ?? "", /No email/);
    });

    it("creates the account without a password, links it, and sends the password reset email", async () => {
      const ema = newEma();
      await seedSlot(1, ema);
      const email = uniqueEmail();
      const mailsBefore = sent.length;

      const result = await run("send", 1, email.toUpperCase());

      assert.equal(result.outcome, "INVITED");
      const user = await auth.getUserByEmail(email);
      assert.ok(!user.passwordHash);
      assert.deepEqual(user.customClaims, { player: true });
      assert.equal((await db.doc(`users/${user.uid}`).get()).get("emaId"), ema);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), user.uid);
      const slot = await slotOf(1);
      assert.equal(await accountOf(1), user.uid);
      assert.ok(slot.get("notifiedAt") != null);
      assert.equal(slot.get("invitedEmail"), undefined);
      assert.equal((await getPlayerRegistration(user.uid, tournamentId))?.playerId, 1);
      // No link is stored: the EMA number is the link.
      assert.equal(slot.get("accountUid"), undefined);
      assert.equal((await db.collection(`users/${user.uid}/playerTournaments`).get()).size, 0);
      assert.deepEqual(resets.filter((address) => address === email), [email]);
      assert.equal(sent.length, mailsBefore, "the registration email is not sent to an account without a password");

      const listed = (await listTournamentPlayers(tournamentId)).find((player) => player.id === 1)!;
      assert.equal(listed.email, email);
      assert.equal(listed.invitationStatus, "INVITED");
      assert.ok(listed.contactedAt != null);
    });

    it("after the person chooses a password, the slot shows the player as accepted and send is skipped", async () => {
      const ema = newEma();
      await seedSlot(41, ema);
      const email = uniqueEmail();
      await run("send", 41, email);

      await activate(email);

      const listed = (await listTournamentPlayers(tournamentId)).find((player) => player.id === 41)!;
      assert.equal(listed.invitationStatus, "ACCEPTED");
      assert.equal((await run("send", 41)).outcome, "SKIPPED");
    });

    it("gives a new account the full EMA name as alias, and the user can change it", async () => {
      const ema = newEma();
      await db.doc(`emaPlayerRegistry/${ema}`).set({
        firstName: "Ana", lastName: "Pérez", country: "ESP", photoUrl: "https://example.test/ana.jpg",
      });
      await db.doc(`tournaments/${tournamentId}/players/30`).set({ id: 30, name: "P30", team: 0, assignedEmaId: null, assignedCountry: "" });
      await assignTournamentPlayer({ tournamentId, playerId: 30, emaId: ema });
      const email = uniqueEmail();
      assert.equal((await run("send", 30, email)).outcome, "INVITED");
      await activate(email);

      const token = await signInTestAccount(email);
      const profile = await server.request("GET", "/auth/me", { token });
      assert.equal(profile.status, 200);
      assert.equal((profile.body as { alias: string }).alias, "Ana Pérez");
      assert.equal((profile.body as { emaId: string }).emaId, ema);
      assert.equal((profile.body as { photoUrl: string }).photoUrl, "https://example.test/ana.jpg");

      const changed = await server.request("PUT", "/auth/me", { token, body: { alias: "  Anita  " } });
      assert.equal((changed.body as { alias: string }).alias, "Anita");
      assert.equal((await server.request("PUT", "/auth/me", { token, body: { alias: "   " } })).status, 400);
      assert.equal((await server.request("PUT", "/auth/me", { token, body: { alias: "x".repeat(61) } })).status, 400);
    });

    it("links every tournament of the EMA number when the account is created", async () => {
      const ema = newEma();
      const otherTournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
      await seedSlot(20, ema);
      await seedSlot(5, ema, otherTournamentId);
      const email = uniqueEmail();

      assert.equal((await run("send", 20, email)).outcome, "INVITED");

      const user = await auth.getUserByEmail(email);
      assert.equal(await accountOf(20), user.uid);
      assert.equal(await linkedAccountUid(otherTournamentId, 5), user.uid);
      assert.deepEqual((await listPlayerTournamentIds(user.uid)).sort(), [tournamentId, otherTournamentId].sort());
    });

    it("shows the account of a slot that was assigned before the account existed, and shows none after the account loses the EMA number", async () => {
      const ema = newEma();
      await seedSlot(64, ema);
      assert.equal(await accountOf(64), null);

      const account = await accountWith(ema);

      assert.equal(await accountOf(64), account.uid);
      assert.equal((await getPlayerRegistration(account.uid, tournamentId))?.playerId, 64);

      await setAccountEmaId({ uid: account.uid, emaId: null });

      assert.equal(await accountOf(64), null);
      assert.equal(await getPlayerRegistration(account.uid, tournamentId), null);
    });

    it("keeps the account when the reset email fails, and resend sends it again", async () => {
      await seedSlot(7, newEma());
      const email = uniqueEmail();
      failNextReset = true;

      const failed = await run("send", 7, email);

      assert.equal(failed.outcome, "FAILED");
      const user = await auth.getUserByEmail(email);
      assert.equal(await accountOf(7), user.uid);
      assert.equal((await slotOf(7)).get("notifiedAt"), undefined);
      assert.equal((await run("resend", 7)).outcome, "INVITED");
      assert.ok(resets.includes(email));
    });

    it("never gives an account an EMA number because of an email: the email of another player is refused", async () => {
      const owner = await accountWith(newEma());
      const ownerEma = (await db.doc(`users/${owner.uid}`).get()).get("emaId");
      const ema = newEma();
      await seedSlot(42, ema);

      const result = await run("send", 42, owner.email);

      assert.equal(result.outcome, "FAILED");
      assert.match(result.message ?? "", /already in use/i);
      assert.equal(await accountOf(42), null);
      assert.equal((await db.doc(`users/${owner.uid}`).get()).get("emaId"), ownerEma);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).exists, false);
    });

    it("never gives an EMA number to an editor account because of an email", async () => {
      const boss = await createAccount({ role: "EDITOR" });
      await seedSlot(43, newEma());

      assert.equal((await run("send", 43, boss.email)).outcome, "FAILED");

      assert.equal((await db.doc(`users/${boss.uid}`).get()).get("emaId"), undefined);
      assert.deepEqual((await auth.getUser(boss.uid)).customClaims, { editor: true });
    });

    it("two players cannot share one email: the second gets a refusal and no account", async () => {
      const shared = uniqueEmail();
      await seedSlot(44, newEma());
      await seedSlot(45, newEma());

      assert.equal((await run("send", 44, shared)).outcome, "INVITED");
      assert.equal((await run("send", 45, shared)).outcome, "FAILED");

      assert.notEqual(await accountOf(44), null);
      assert.equal(await accountOf(45), null);
    });

    it("refuses an invalid email", async () => {
      await seedSlot(46, newEma());
      const result = await run("send", 46, "not-an-email");
      assert.equal(result.outcome, "FAILED");
      assert.equal(await accountOf(46), null);
    });

    it("rolls back the new account when the EMA number cannot be claimed", async () => {
      const ema = newEma();
      await accountWith(ema);
      const email = uniqueEmail();

      await assert.rejects(createPlayerAccount({ email, emaId: ema }), /belongs to another account/);

      await assert.rejects(auth.getUserByEmail(email));
    });

    it("refuses an EMA number that is not in the registry", async () => {
      const email = uniqueEmail();
      await assert.rejects(createPlayerAccount({ email, emaId: newEma() }), /not found/i);
      await assert.rejects(auth.getUserByEmail(email));
    });
  });

  describe("an account exists already: its email always wins", () => {
    it("refuses a different email for a linked slot and changes nothing", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await seedSlot(47, ema);

      const result = await run("send", 47, uniqueEmail());

      assert.equal(result.outcome, "FAILED");
      assert.match(result.message ?? "", new RegExp(account.email.replace(".", "\\.")));
      assert.equal((await auth.getUser(account.uid)).email, account.email);
      assert.equal((await slotOf(47)).get("notifiedAt"), undefined);
    });

    it("accepts the same email, also in other letter case", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await activate(account.email);
      await seedSlot(48, ema);

      assert.equal((await run("send", 48, account.email.toUpperCase())).outcome, "NOTIFIED");
    });

    it("E: assigning an EMA number to a slot links the existing account at once and sends no email", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      const mails = sent.length;

      await seedSlot(49, ema);

      assert.equal(await accountOf(49), account.uid);
      assert.equal(sent.length, mails);
    });

    it("F: assigning an EMA number with no account leaves the slot unlinked and sends nothing", async () => {
      const mails = sent.length;
      const reset = resets.length;

      await seedSlot(50, newEma());

      assert.equal(await accountOf(50), null);
      assert.equal(sent.length, mails);
      assert.equal(resets.length, reset);
    });

    it("I: a linked slot of an account with a password gets one reminder with send, and resend repeats it", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await activate(account.email);
      await seedSlot(51, ema);
      const mailsToAccount = () => sent.filter((mail) => mail.to === account.email).length;

      assert.equal((await run("send", 51)).outcome, "NOTIFIED");
      assert.equal(mailsToAccount(), 1);
      assert.equal((await run("send", 51)).outcome, "SKIPPED");
      assert.equal(mailsToAccount(), 1);
      assert.equal((await run("resend", 51)).outcome, "NOTIFIED");
      assert.equal(mailsToAccount(), 2);
    });

    it("a linked slot of an account with no password gets the password reset email, not the reminder", async () => {
      const ema = newEma();
      const email = uniqueEmail();
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      await createPlayerAccount({ email, emaId: ema });
      await seedSlot(52, ema);
      const mails = sent.length;

      const result = await run("send", 52);

      assert.equal(result.outcome, "INVITED");
      assert.ok(resets.includes(email));
      assert.equal(sent.length, mails);
    });

    it("keeps the link and lets the editor repeat when the reminder cannot be sent", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await activate(account.email);
      await seedSlot(53, ema);
      failNextMail = true;

      const failed = await run("send", 53);

      assert.equal(failed.outcome, "FAILED");
      assert.equal(await accountOf(53), account.uid);
      assert.equal((await run("resend", 53)).outcome, "NOTIFIED");
    });

    it("writes the reminder in Spanish when the app asks for Spanish, and in English otherwise", async () => {
      const spanishEma = newEma();
      const spanish = await accountWith(spanishEma);
      await activate(spanish.email);
      await seedSlot(54, spanishEma);
      const englishEma = newEma();
      const english = await accountWith(englishEma);
      await activate(english.email);
      await seedSlot(55, englishEma);

      await act("send", [{ playerId: 54 }], { "accept-language": "es-ES,es;q=0.9" });
      await act("send", [{ playerId: 55 }], { "accept-language": "de-DE" });

      assert.match(sent.filter((mail) => mail.to === spanish.email).pop()!.subject, /^Estás inscrito en /);
      assert.match(sent.filter((mail) => mail.to === english.email).pop()!.subject, /^You are registered in /);
    });

    it("L: revoke only forgets the email, the account follows the EMA number, and send tells the player again", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await activate(account.email);
      await seedSlot(56, ema);
      assert.equal((await run("send", 56)).outcome, "NOTIFIED");

      assert.equal((await run("revoke", 56)).outcome, "REVOKED");

      const slot = await slotOf(56);
      assert.equal(slot.get("notifiedAt"), undefined);
      assert.equal(await accountOf(56), account.uid, "the account stays linked, because the EMA number links it");
      assert.equal((await getPlayerRegistration(account.uid, tournamentId))?.playerId, 56);
      assert.equal((await db.doc(`users/${account.uid}`).get()).get("emaId"), ema);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);
      assert.equal((await run("revoke", 56)).outcome, "SKIPPED");

      assert.equal((await run("send", 56)).outcome, "NOTIFIED");
      assert.equal(await accountOf(56), account.uid);
    });

    it("M: a slot with another EMA number has no account, and the new person got no email yet", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await seedSlot(57, ema);
      assert.equal((await run("send", 57)).outcome, "NOTIFIED");
      assert.equal(await accountOf(57), account.uid);
      const other = newEma();
      await db.doc(`emaPlayerRegistry/${other}`).set({ country: "ES" });

      await assignTournamentPlayer({ tournamentId, playerId: 57, emaId: other });

      assert.equal(await accountOf(57), null);
      assert.equal((await slotOf(57)).get("notifiedAt"), undefined);
      assert.equal(await getPlayerRegistration(account.uid, tournamentId), null);
    });

    it("an account cannot use two EMA numbers", async () => {
      const email = uniqueEmail();
      await seedSlot(58, newEma());
      await seedSlot(59, newEma());
      assert.equal((await run("send", 58, email)).outcome, "INVITED");

      const second = await run("send", 59, email);

      assert.equal(second.outcome, "FAILED");
      assert.equal(await accountOf(59), null);
    });
  });

  describe("save emails without sending anything", () => {
    const save = (players: Array<{ playerId: number; email: string }>) =>
      server.request("PUT", `/tournaments/${tournamentId}/player-accounts/emails`, { token: editor.token, body: { players } });
    const outcomes = (response: { body: unknown }) => (response.body as EmailBody).results.map((item) => item.outcome);

    it("creates an account for a player with none, keeps the email of an account, and refuses an email that others own", async () => {
      await seedSlot(60, newEma());
      const linkedEma = newEma();
      const linked = await accountWith(linkedEma);
      await seedSlot(61, linkedEma);
      await seedSlot(62, newEma());
      await seedSlot(63, newEma());
      const owner = await accountWith(newEma());
      const email = uniqueEmail();
      const mails = sent.length;
      const reset = resets.length;

      const response = await save([
        { playerId: 60, email },
        { playerId: 61, email: linked.email },
        { playerId: 62, email: uniqueEmail() },
        { playerId: 62, email: uniqueEmail() },
        { playerId: 63, email: owner.email },
        { playerId: 999, email: uniqueEmail() },
        { playerId: 3, email: uniqueEmail() },
      ]);

      assert.equal(response.status, 200);
      assert.deepEqual(outcomes(response), ["CREATED", "UNCHANGED", "CREATED", "KEPT_ACCOUNT_EMAIL", "FAILED", "FAILED", "FAILED"]);
      assert.equal(await accountOf(60), (await auth.getUserByEmail(email)).uid);
      assert.equal(await accountOf(63), null);
      assert.equal(sent.length, mails);
      assert.equal(resets.length, reset);
    });

    it("needs an editor of the tournament", async () => {
      const outsider = await createAccount({ role: "EDITOR" });
      const response = await server.request("PUT", `/tournaments/${tournamentId}/player-accounts/emails`, {
        token: outsider.token, body: { players: [{ playerId: 60, email: uniqueEmail() }] },
      });
      assert.equal(response.status, 403);
    });
  });

  describe("accounts and EMA numbers", () => {
    it("links the slots of an EMA number when an admin sets the EMA number of an account", async () => {
      const account = await createAccount({ role: "EDITOR" });
      const ema = newEma();
      await seedSlot(13, ema);
      const before = sent.length;

      assert.deepEqual((await auth.getUser(account.uid)).customClaims, { editor: true });

      await setAccountEmaId({ uid: account.uid, emaId: ema });

      assert.deepEqual((await auth.getUser(account.uid)).customClaims, { editor: true, player: true });
      assert.equal(await accountOf(13), account.uid);
      assert.equal(sent.length, before, "no email is sent");
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);

      await setAccountEmaId({ uid: account.uid, emaId: null });

      assert.deepEqual((await auth.getUser(account.uid)).customClaims, { editor: true }, "no EMA number, no player capability");
      assert.equal(await accountOf(13), null);
      assert.equal((await slotOf(13)).get("notifiedAt"), undefined);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).exists, false);
    });

    it("D: changing the EMA number of an account releases the old tournaments and links the new ones", async () => {
      const first = newEma();
      const second = newEma();
      const account = await accountWith(first);
      await seedSlot(36, first);
      await seedSlot(37, second);
      assert.equal(await accountOf(36), account.uid);

      await setAccountEmaId({ uid: account.uid, emaId: second });

      assert.equal(await accountOf(36), null);
      assert.equal(await accountOf(37), account.uid);
      assert.equal((await db.doc(`emaIdIndex/${first}`).get()).exists, false);
    });

    it("refuses an EMA number that belongs to another account", async () => {
      const first = await createAccount({ role: "EDITOR" });
      const second = await createAccount({ role: "EDITOR" });
      const ema = newEma();
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      await setAccountEmaId({ uid: first.uid, emaId: ema });

      await assert.rejects(setAccountEmaId({ uid: second.uid, emaId: ema }), /belongs to another account/);
    });

    it("C: an account with no EMA number sees no tournament", async () => {
      const account = await createAccount({ role: "EDITOR" });

      assert.deepEqual(await listPlayerTournamentIds(account.uid), []);
      assert.equal(await getPlayerRegistration(account.uid, tournamentId), null);
    });
  });

  describe("accounts created with an EMA number", () => {
    const createAccountWith = (token: string, email: string, emaId?: string) =>
      server.request("POST", "/admin/users", { token, body: { email, role: "EDITOR", alias: "x", emaId, tournamentAssignments: [] } });

    it("needs an EMA number for a player account, and lets only an admin set the EMA number of an editor", async () => {
      const body = { email: uniqueEmail(), role: "PLAYER", alias: "x", emaId: newEma() };

      assert.equal((await server.request("POST", "/admin/users", { token: editor.token, body: { ...body, emaId: undefined } })).status, 400);
      assert.equal((await server.request("POST", "/admin/users", { token: editor.token, body: { ...body, role: "EDITOR", tournamentAssignments: [] } })).status, 403);
      await assert.rejects(auth.getUserByEmail(body.email));
    });

    it("refuses an EMA number and an email that belong to another account", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      const owner = await createAccount({ role: "EDITOR" });
      const ema = newEma();
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      await setAccountEmaId({ uid: owner.uid, emaId: ema });

      const email = uniqueEmail();
      assert.equal((await createAccountWith(admin.token, email, ema)).status, 409);
      await assert.rejects(auth.getUserByEmail(email));
      assert.equal((await createAccountWith(admin.token, owner.email)).status, 409);
      assert.equal((await server.request("PUT", `/admin/users/${admin.uid}`, {
        token: admin.token,
        body: { email: admin.email, role: "ADMIN", alias: "x", tournamentAssignments: [], emaId: ema },
      })).status, 409);
    });

    it("refuses an unknown EMA number and an editor who sets one", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      const unknown = await createAccountWith(admin.token, uniqueEmail(), newEma());
      assert.equal(unknown.status, 404);

      const ema = newEma();
      await db.doc(`emaPlayerRegistry/${ema}`).set({ country: "ES" });
      assert.equal((await createAccountWith(editor.token, uniqueEmail(), ema)).status, 403);
    });

    it("lets an editor change the EMA number of a player account, but not of an editor account", async () => {
      const player = await createAccount({ role: "PLAYER" });
      const other = await createAccount({ role: "EDITOR" });
      const ema = newEma();
      await seedSlot(22, ema);
      const put = (account: TestAccount, role: string) => server.request("PUT", `/admin/users/${account.uid}`, {
        token: editor.token,
        body: { email: account.email, role, alias: "x", tournamentAssignments: [], emaId: ema },
      });

      assert.equal((await put(other, "EDITOR")).status, 403);
      assert.equal((await put(player, "PLAYER")).status, 200);

      assert.equal((await db.doc(`users/${player.uid}`).get()).get("emaId"), ema);
      assert.equal(await accountOf(22), player.uid);
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

  describe("the invitation model is gone", () => {
    it("has no route to accept an invitation, to list invitations or to list invited people", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      assert.equal((await server.request("POST", "/player/invitations/accept", { body: { token: "a.b.c", password: "Passw0rd!-new" } })).status, 404);
      assert.equal((await server.request("GET", `/tournaments/${tournamentId}/player-invitations`, { token: editor.token })).status, 404);
      assert.equal((await server.request("GET", "/admin/invitations", { token: admin.token })).status, 404);
    });

    it("stores no invitation document when an invitation goes out", async () => {
      await seedSlot(64, newEma());
      await run("send", 64, uniqueEmail());
      assert.equal((await db.collection(`tournaments/${tournamentId}/playerInvitations`).get()).size, 0);
    });
  });
});
