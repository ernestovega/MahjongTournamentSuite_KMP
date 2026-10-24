import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("what a player can read", () => {
  let server: TestServer;
  let t1 = "";
  let t2 = "";
  let player: TestAccount;
  let editor: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    t1 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    t2 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    for (const emaId of ["10000001", "10000002", "10000003"]) {
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Name", lastName: emaId, country: "ESP" });
    }
    await db.doc(`tournaments/${t1}/players/1`).set({
      id: 1, team: 0, assignedEmaId: "10000001", accountUid: "someone", invitationStatus: "ACCEPTED", invitedEmail: "x@example.test",
    });
    await db.doc(`tournaments/${t1}/players/2`).set({ id: 2, team: 0, assignedEmaId: "10000002" });
    await db.doc(`tournaments/${t2}/players/1`).set({ id: 1, team: 0, assignedEmaId: "10000003" });
    await db.doc(`tournaments/${t1}`).update({ name: "Mine" });
    await db.doc(`tournaments/${t2}`).update({ name: "Other" });
    player = await createAccount({ role: "PLAYER", playerOf: [t1] });
    editor = await createAccount({ role: "EDITOR", memberOf: [t2] });
  });
  after(async () => { await server.close(); });

  it("lists only the tournaments where the player is registered", async () => {
    const { status, body } = await server.request("GET", "/tournaments", { token: player.token });

    assert.equal(status, 200);
    assert.deepEqual((body as { tournaments: Array<{ id: string }> }).tournaments.map((t) => t.id), [t1]);
  });

  it("lists only the player tournaments for an admin who sends scope=player", async () => {
    const admin = await createAccount({ role: "ADMIN" });
    // The admin list is ordered by updatedAt, so a tournament needs the field to appear in it.
    for (const id of [t1, t2]) await db.doc(`tournaments/${id}`).update({ updatedAt: new Date() });
    const all = await server.request("GET", "/tournaments", { token: admin.token });
    const scoped = await server.request("GET", "/tournaments?scope=player", { token: admin.token });

    assert.ok((all.body as { tournaments: unknown[] }).tournaments.length >= 2);
    assert.deepEqual((scoped.body as { tournaments: unknown[] }).tournaments, []);
  });

  it("lists only the player tournaments for an editor who sends scope=player", async () => {
    const both = await createAccount({ role: "EDITOR", memberOf: [t2], playerOf: [t1] });
    const { body } = await server.request("GET", "/tournaments?scope=player", { token: both.token });

    assert.deepEqual((body as { tournaments: Array<{ id: string }> }).tournaments.map((t) => t.id), [t1]);
  });

  it("hides accounts and emails of other players", async () => {
    const { body } = await server.request("GET", `/tournaments/${t1}/players`, { token: player.token });
    const players = (body as { players: Array<Record<string, unknown>> }).players;

    assert.equal(players.length, 2);
    for (const slot of players) {
      assert.equal(slot.accountUid, null);
      assert.equal(slot.invitedEmail, null);
      assert.equal(slot.invitationStatus, "NONE");
    }
  });

  it("lists registry players only from the tournaments of the player", async () => {
    const { body } = await server.request("GET", "/ema-player-registry", { token: player.token });

    assert.deepEqual((body as { players: Array<{ emaId: string }> }).players.map((p) => p.emaId).sort(), ["10000001", "10000002"]);
  });

  it("refuses writes and editor reads in the player tournament", async () => {
    assert.equal((await server.request("PUT", `/tournaments/${t1}/tables/1/1/state`, { token: player.token, body: {} })).status, 403);
    assert.equal((await server.request("GET", `/tournaments/${t1}/members`, { token: player.token })).status, 403);
    assert.equal((await server.request("GET", `/tournaments/${t1}/player-invitations`, { token: player.token })).status, 403);
    assert.equal((await server.request("GET", `/tournaments/${t2}/players`, { token: player.token })).status, 403);
  });

  it("keeps the editor view of an editor", async () => {
    const { body } = await server.request("GET", `/tournaments/${t2}/players`, { token: editor.token });

    assert.equal((body as { players: unknown[] }).players.length, 1);
  });
});
