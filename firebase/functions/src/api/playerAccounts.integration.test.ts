import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, linkedAccountUid, requireAuthEmulator, signInTestAccount, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { assignTournamentPlayer, listTournamentPlayers } from "../services/tournamentContentService";
import { createPlayerAccount, getPlayerRegistration, listPlayerTournamentIds, setAccountEmaId } from "../services/playerAccountsService";
import { setPasswordResetSenderForTests } from "../services/firebaseAuthRest";

type ActionBody = { results: Array<{ playerId: number; outcome: string; reason?: string }> };

describe("player accounts", () => {
  let server: TestServer;
  let editor: TestAccount;
  let tournamentId = "";

  /** Creates a slot. `email` is the pending email that the import writes. */
  async function seedSlot(id: number, emaId: string | null, options: { email?: string; inTournament?: string } = {}): Promise<void> {
    const inTournament = options.inTournament ?? tournamentId;
    await db.doc(`tournaments/${inTournament}/players/${id}`).set({
      id, name: `P${id}`, team: 0, assignedEmaId: null, assignedCountry: "",
    });
    if (emaId) {
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ country: "ES" });
      await assignTournamentPlayer({ tournamentId: inTournament, playerId: id, emaId });
    }
    if (options.email) await db.doc(`tournaments/${inTournament}/players/${id}`).update({ email: options.email });
  }

  const accountOf = (playerId: number) => linkedAccountUid(tournamentId, playerId);
  const resets: string[] = [];
  let failNextReset = false;
  const uniqueEmail = () => `${randomUUID()}@example.test`;
  const newEma = () => `EMA-${randomUUID()}`;

  before(async () => {
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
    setPasswordResetSenderForTests(null);
    await server.close();
  });

  const act = (players: Array<{ playerId: number }>, token: string | null = editor.token) =>
    server.request("POST", `/tournaments/${tournamentId}/player-accounts/send`, { token, body: { players } });
  /** Runs the action for one player and returns its outcome and reason. */
  const run = async (playerId: number) => {
    const response = await act([{ playerId }]);
    const result = (response.body as ActionBody).results?.find((item) => item.playerId === playerId);
    return { status: response.status, outcome: result?.outcome, reason: result?.reason };
  };
  const slotOf = (id: number) => db.doc(`tournaments/${tournamentId}/players/${id}`).get();
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

  describe("send to a slot with no account: invite", () => {
    it("fails for a slot without an EMA number", async () => {
      assert.deepEqual(await run(3), { status: 200, outcome: "FAILED", reason: "NO_EMA_ID" });
    });

    it("fails for a slot with no pending email and creates nothing", async () => {
      const ema = newEma();
      await seedSlot(40, ema);

      assert.deepEqual(await run(40), { status: 200, outcome: "FAILED", reason: "NO_EMAIL" });
      assert.equal(await accountOf(40), null);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).exists, false);
    });

    it("fails for a player that does not exist", async () => {
      assert.deepEqual(await run(999), { status: 200, outcome: "FAILED", reason: "PLAYER_NOT_FOUND" });
    });

    it("creates the account with no password and no alias, links it, and sends the password reset email", async () => {
      const ema = newEma();
      const email = uniqueEmail();
      await seedSlot(1, ema, { email });

      assert.deepEqual(await run(1), { status: 200, outcome: "INVITED", reason: undefined });

      const user = await auth.getUserByEmail(email);
      assert.ok(!user.passwordHash);
      assert.deepEqual(user.customClaims, { player: true });
      const profile = await db.doc(`users/${user.uid}`).get();
      assert.equal(profile.get("emaId"), ema);
      assert.equal(profile.get("alias"), "");
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), user.uid);
      assert.equal(await accountOf(1), user.uid);
      assert.equal((await getPlayerRegistration(user.uid, tournamentId))?.playerId, 1);
      assert.deepEqual(resets.filter((address) => address === email), [email]);
      const listed = (await listTournamentPlayers(tournamentId)).find((player) => player.id === 1)!;
      assert.equal(listed.hasAccount, true);
      assert.equal(listed.email, email);
    });

    it("saves the pending email in lower case from the import, and the invite uses it", async () => {
      await seedSlot(65, newEma(), { email: "Mixed.Case@Example.test" });
      assert.equal((await run(65)).outcome, "INVITED");
      assert.ok((await auth.getUserByEmail("mixed.case@example.test")).uid);
    });

    it("links every tournament of the EMA number when the account is created", async () => {
      const ema = newEma();
      const otherTournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
      await seedSlot(20, ema, { email: uniqueEmail() });
      await seedSlot(5, ema, { inTournament: otherTournamentId });

      assert.equal((await run(20)).outcome, "INVITED");

      const uid = (await accountOf(20))!;
      assert.equal(await linkedAccountUid(otherTournamentId, 5), uid);
      assert.deepEqual((await listPlayerTournamentIds(uid)).sort(), [tournamentId, otherTournamentId].sort());
    });

    it("keeps the account when the reset email fails, and the next send resets the password", async () => {
      const email = uniqueEmail();
      await seedSlot(7, newEma(), { email });
      failNextReset = true;

      assert.deepEqual(await run(7), { status: 200, outcome: "FAILED", reason: "EMAIL_FAILED" });
      const user = await auth.getUserByEmail(email);
      assert.equal(await accountOf(7), user.uid);

      assert.equal((await run(7)).outcome, "RESET_SENT");
      assert.ok(resets.includes(email));
    });

    it("fails with EMAIL_IN_USE when an account owns the email, and never links that account to the EMA number", async () => {
      const owner = await accountWith(newEma());
      const ownerEma = (await db.doc(`users/${owner.uid}`).get()).get("emaId");
      const ema = newEma();
      await seedSlot(42, ema, { email: owner.email });

      assert.deepEqual(await run(42), { status: 200, outcome: "FAILED", reason: "EMAIL_IN_USE" });

      assert.equal(await accountOf(42), null);
      assert.equal((await db.doc(`users/${owner.uid}`).get()).get("emaId"), ownerEma);
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).exists, false);
    });

    it("never gives an EMA number to an editor account because of an email", async () => {
      const boss = await createAccount({ role: "EDITOR" });
      await seedSlot(43, newEma(), { email: boss.email });

      assert.equal((await run(43)).reason, "EMAIL_IN_USE");

      assert.equal((await db.doc(`users/${boss.uid}`).get()).get("emaId"), undefined);
      assert.deepEqual((await auth.getUser(boss.uid)).customClaims, { editor: true });
    });

    it("two slots with the same pending email: the second fails and gets no account", async () => {
      const shared = uniqueEmail();
      await seedSlot(44, newEma(), { email: shared });
      await seedSlot(45, newEma(), { email: shared });

      assert.equal((await run(44)).outcome, "INVITED");
      assert.deepEqual(await run(45), { status: 200, outcome: "FAILED", reason: "EMAIL_IN_USE" });
      assert.equal(await accountOf(45), null);
    });

    it("fails for an invalid pending email", async () => {
      await seedSlot(46, newEma(), { email: "not-an-email" });
      assert.equal((await run(46)).outcome, "FAILED");
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

  describe("send to a slot with an account: reset the password", () => {
    it("sends the password reset email to the email of the account, and ignores the pending email", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await seedSlot(47, ema, { email: uniqueEmail() });
      const before = resets.length;

      assert.deepEqual(await run(47), { status: 200, outcome: "RESET_SENT", reason: undefined });

      assert.deepEqual(resets.slice(before), [account.email.toLowerCase()]);
      assert.equal((await auth.getUser(account.uid)).email, account.email);
    });

    it("sends it again every time, also when the person has a password", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await activate(account.email);
      await seedSlot(48, ema);
      const before = resets.length;

      assert.equal((await run(48)).outcome, "RESET_SENT");
      assert.equal((await run(48)).outcome, "RESET_SENT");

      assert.equal(resets.length - before, 2);
    });

    it("fails with EMAIL_FAILED when the reset email cannot be sent, and the account stays", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await seedSlot(49, ema);
      failNextReset = true;

      assert.equal((await run(49)).reason, "EMAIL_FAILED");
      assert.equal(await accountOf(49), account.uid);
    });

    it("E: assigning an EMA number to a slot links the existing account at once and sends no email", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      const before = resets.length;

      await seedSlot(50, ema);

      assert.equal(await accountOf(50), account.uid);
      assert.equal(resets.length, before);
    });

    it("F: assigning an EMA number with no account leaves the slot without account and sends nothing", async () => {
      const before = resets.length;

      await seedSlot(51, newEma());

      assert.equal(await accountOf(51), null);
      assert.equal(resets.length, before);
    });

    it("M: a slot with another EMA number has no account, and the pending email goes away with the old person", async () => {
      const ema = newEma();
      const account = await accountWith(ema);
      await seedSlot(57, ema, { email: uniqueEmail() });
      assert.equal(await accountOf(57), account.uid);
      const other = newEma();
      await db.doc(`emaPlayerRegistry/${other}`).set({ country: "ES" });

      await assignTournamentPlayer({ tournamentId, playerId: 57, emaId: other });

      assert.equal(await accountOf(57), null);
      assert.equal((await slotOf(57)).get("email"), undefined);
      assert.equal(await getPlayerRegistration(account.uid, tournamentId), null);
    });

    it("an account cannot use two EMA numbers: the second slot of one email fails", async () => {
      const email = uniqueEmail();
      await seedSlot(58, newEma(), { email });
      await seedSlot(59, newEma(), { email });
      assert.equal((await run(58)).outcome, "INVITED");

      assert.equal((await run(59)).outcome, "FAILED");
      assert.equal(await accountOf(59), null);
    });
  });

  describe("send for many players", () => {
    it("handles each player by its state in one request", async () => {
      const withAccountEma = newEma();
      const account = await accountWith(withAccountEma);
      await seedSlot(70, withAccountEma);
      await seedSlot(71, newEma(), { email: uniqueEmail() });
      await seedSlot(72, newEma());
      await seedSlot(73, null);
      const before = resets.length;

      const response = await act([{ playerId: 70 }, { playerId: 71 }, { playerId: 72 }, { playerId: 73 }, { playerId: 999 }]);

      assert.equal(response.status, 200);
      assert.deepEqual((response.body as ActionBody).results.map((item) => [item.playerId, item.outcome, item.reason]), [
        [70, "RESET_SENT", undefined],
        [71, "INVITED", undefined],
        [72, "FAILED", "NO_EMAIL"],
        [73, "FAILED", "NO_EMA_ID"],
        [999, "FAILED", "PLAYER_NOT_FOUND"],
      ]);
      assert.equal(resets.length - before, 2);
      assert.equal(await accountOf(70), account.uid);
    });

    it("refuses an empty list, a list over 200 players and a bad player id", async () => {
      assert.equal((await act([])).status, 400);
      assert.equal((await act(Array.from({ length: 201 }, (_, index) => ({ playerId: index + 1 })))).status, 400);
      assert.equal((await act([{ playerId: 0 }])).status, 400);
    });

    it("needs an editor of the tournament", async () => {
      const outsider = await createAccount({ role: "EDITOR" });
      const player = await createAccount({ role: "PLAYER" });

      assert.equal((await act([{ playerId: 1 }], outsider.token)).status, 403);
      assert.equal((await act([{ playerId: 1 }], player.token)).status, 403);
      assert.equal((await act([{ playerId: 1 }], null)).status, 401);
    });

    it("records the activity", async () => {
      await act([{ playerId: 1 }]);
      const log = await db.collection("activity").where("tournamentId", "==", tournamentId).where("action", "==", "account.send").get();
      assert.ok(log.size >= 1);
    });
  });

  describe("who reads the emails", () => {
    it("shows the email to an admin only, and whether a slot has an account to editors", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      const email = uniqueEmail();
      await seedSlot(80, newEma(), { email });
      const read = async (token: string) => {
        const response = await server.request("GET", `/tournaments/${tournamentId}/players`, { token });
        return (response.body as { players: Array<{ id: number; email: string | null; hasAccount: boolean; hasEmail: boolean }> }).players.find((player) => player.id === 80)!;
      };

      assert.deepEqual(await read(admin.token), { ...(await read(admin.token)), email, hasAccount: false });
      assert.equal((await read(editor.token)).email, null);
      assert.equal((await read(editor.token)).hasAccount, false);
      assert.equal((await read(editor.token)).hasEmail, true);
    });
  });

  describe("new accounts have no alias", () => {
    it("lets the person choose an alias after the first sign-in", async () => {
      const ema = newEma();
      const email = uniqueEmail();
      await seedSlot(81, ema, { email });
      await run(81);
      await activate(email);

      const token = await signInTestAccount(email);
      const profile = await server.request("GET", "/auth/me", { token });
      assert.equal(profile.status, 200);
      assert.equal((profile.body as { alias: string }).alias, "");
      assert.equal((profile.body as { emaId: string }).emaId, ema);

      const changed = await server.request("PUT", "/auth/me", { token, body: { alias: "  Anita  " } });
      assert.equal((changed.body as { alias: string }).alias, "Anita");
      assert.equal((await server.request("PUT", "/auth/me", { token, body: { alias: "   " } })).status, 400);
    });
  });

  describe("accounts and EMA numbers", () => {
    it("links the slots of an EMA number when an admin sets the EMA number of an account", async () => {
      const account = await createAccount({ role: "EDITOR" });
      const ema = newEma();
      await seedSlot(13, ema);
      const before = resets.length;

      assert.deepEqual((await auth.getUser(account.uid)).customClaims, { editor: true });

      await setAccountEmaId({ uid: account.uid, emaId: ema });

      assert.deepEqual((await auth.getUser(account.uid)).customClaims, { editor: true, player: true });
      assert.equal(await accountOf(13), account.uid);
      assert.equal(resets.length, before, "no email is sent");
      assert.equal((await db.doc(`emaIdIndex/${ema}`).get()).get("uid"), account.uid);

      await setAccountEmaId({ uid: account.uid, emaId: null });

      assert.deepEqual((await auth.getUser(account.uid)).customClaims, { editor: true }, "no EMA number, no player capability");
      assert.equal(await accountOf(13), null);
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

  describe("the retired routes are gone", () => {
    it("has no route to resend, revoke or save emails, and none for the old invitation model", async () => {
      const admin = await createAccount({ role: "ADMIN" });
      for (const route of ["resend", "revoke"]) {
        assert.equal((await server.request("POST", `/tournaments/${tournamentId}/player-accounts/${route}`, { token: editor.token, body: { players: [{ playerId: 1 }] } })).status, 404);
      }
      assert.equal((await server.request("PUT", `/tournaments/${tournamentId}/player-accounts/emails`, { token: editor.token, body: { players: [] } })).status, 404);
      assert.equal((await server.request("POST", "/player/invitations/accept", { body: { token: "a.b.c", password: "Passw0rd!-new" } })).status, 404);
      assert.equal((await server.request("GET", `/tournaments/${tournamentId}/player-invitations`, { token: editor.token })).status, 404);
      assert.equal((await server.request("GET", "/admin/invitations", { token: admin.token })).status, 404);
    });
  });
});
