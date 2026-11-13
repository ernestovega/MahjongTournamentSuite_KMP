import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("what an account reads outside /public", () => {
  let server: TestServer;
  let t1 = "";
  let t2 = "";
  let player: TestAccount;
  let editor: TestAccount;
  let owner: TestAccount;
  let both: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    t1 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    t2 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    for (const emaId of ["10000001", "10000002", "10000003", "10000004", "10000005"]) {
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Name", lastName: emaId, country: "ESP" });
    }
    owner = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(owner.uid, "10000001");
    await seatEmaId(t1, 1, "10000001");
    await db.doc(`tournaments/${t1}/players/2`).set({ id: 2, team: 0, assignedEmaId: "10000002" });
    await db.doc(`tournaments/${t2}/players/1`).set({ id: 1, team: 0, assignedEmaId: "10000003" });
    await db.doc(`tournaments/${t1}`).update({ name: "Mine" });
    await db.doc(`tournaments/${t2}`).update({ name: "Other" });
    // Two more people play in t1: a player-only account in slot 3 and an editor of t2 in slot 4.
    player = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(player.uid, "10000004");
    await seatEmaId(t1, 3, "10000004");
    both = await createAccount({ role: "EDITOR", memberOf: [t2] });
    await giveEmaIdToAccount(both.uid, "10000005");
    await seatEmaId(t1, 4, "10000005");
    editor = await createAccount({ role: "EDITOR", memberOf: [t2] });
  });
  after(async () => { await server.close(); });

  it("gives a player-only account no tournaments and no tournament reads: the player app reads /public", async () => {
    const list = await server.request("GET", "/tournaments", { token: player.token });
    assert.equal(list.status, 200);
    assert.deepEqual((list.body as { tournaments: unknown[] }).tournaments, []);
    assert.equal((await server.request("GET", `/tournaments/${t1}/players`, { token: player.token })).status, 403);
    assert.equal((await server.request("GET", `/tournaments/${t1}/tables-with-hands`, { token: player.token })).status, 403);
    assert.equal((await server.request("GET", "/tournaments?scope=player", { token: player.token })).status, 200);
  });

  it("lists only the assigned tournaments of an editor who also plays elsewhere", async () => {
    const { body } = await server.request("GET", "/tournaments", { token: both.token });

    assert.deepEqual((body as { tournaments: Array<{ id: string }> }).tournaments.map((t) => t.id), [t2]);
  });

  it("refuses an editor who plays in a tournament but is not assigned to it", async () => {
    assert.equal((await server.request("GET", `/tournaments/${t1}/players`, { token: both.token })).status, 403);
  });

  it("lists registry players only from the tournaments of the player", async () => {
    const { body } = await server.request("GET", "/ema-player-registry", { token: player.token });

    assert.deepEqual((body as { players: Array<{ emaId: string }> }).players.map((p) => p.emaId).sort(), ["10000001", "10000002", "10000004", "10000005"]);
  });

  it("shows an editor whether a slot has an account and an email, and never the email", async () => {
    const reader = await createAccount({ role: "EDITOR", memberOf: [t1] });
    await db.doc(`tournaments/${t1}/players/2`).update({ email: "pending@example.test" });

    const { body } = await server.request("GET", `/tournaments/${t1}/players`, { token: reader.token });
    const players = (body as { players: Array<{ id: number; email: string | null; hasAccount: boolean; hasEmail: boolean }> }).players;

    assert.deepEqual(players.map((slot) => [slot.id, slot.email, slot.hasAccount, slot.hasEmail]), [
      [1, null, true, true], [2, null, false, true], [3, null, true, true], [4, null, true, true],
    ]);
  });

  it("shows an admin the email of the account, or the pending email of a slot with no account", async () => {
    const admin = await createAccount({ role: "ADMIN" });

    const { body } = await server.request("GET", `/tournaments/${t1}/players`, { token: admin.token });
    const players = (body as { players: Array<{ id: number; email: string | null; accountUid: string | null }> }).players;

    assert.deepEqual(players.map((slot) => [slot.id, slot.email, slot.accountUid]), [
      [1, owner.email, owner.uid], [2, "pending@example.test", null], [3, player.email, player.uid], [4, both.email, both.uid],
    ]);
  });

  it("keeps the editor view of an editor", async () => {
    const { body } = await server.request("GET", `/tournaments/${t2}/players`, { token: editor.token });

    assert.equal((body as { players: unknown[] }).players.length, 1);
  });
});
