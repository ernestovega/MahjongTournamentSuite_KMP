import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("what anyone can read without an account", () => {
  let server: TestServer;
  let open = "";
  let hidden = "";
  let player: TestAccount;

  const get = async (path: string, token?: string) => {
    const response = await fetch(server.baseUrl + path, { headers: token ? { authorization: `Bearer ${token}` } : {} });
    return { status: response.status, cache: response.headers.get("cache-control") ?? "", body: await response.json() as any };
  };

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    open = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    hidden = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${open}`).update({ name: "Open", updatedAt: new Date(), createdByUid: "creator", createdByName: "Creator" });
    await db.doc(`tournaments/${hidden}`).update({ name: "Hidden", updatedAt: new Date(), isPublic: false });
    await db.doc("emaPlayerRegistry/20000001").set({ firstName: "Ana", lastName: "Public", country: "ESP", photoUrl: "https://example.test/a.png" });
    player = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(player.uid, "20000001");
    await seatEmaId(open, 1, "20000001");
    await db.doc(`tournaments/${open}/players/1`).update({ name: "Ana Public", team: 2, email: "ana@example.test" });
  });
  after(async () => { await server.close(); });

  it("lists the public tournaments without the creator", async () => {
    const { status, cache, body } = await get("/public/tournaments");

    assert.equal(status, 200);
    assert.match(cache, /public/);
    assert.match(cache, /s-maxage=/);
    const ids = body.tournaments.map((t: { id: string }) => t.id);
    assert.ok(ids.includes(open));
    assert.ok(!ids.includes(hidden));
    const tournament = body.tournaments.find((t: { id: string }) => t.id === open);
    assert.equal(tournament.createdByUid, undefined);
    assert.equal(tournament.createdByName, undefined);
  });

  it("serves the content of a public tournament and hides a private one", async () => {
    for (const suffix of ["", "/sync/manifest", "/players", "/teams", "/rounds", "/tables", "/tables-with-hands",
      "/rounds/1/tables-with-hands", "/tables/1/1", "/round-clocks"]) {
      assert.equal((await get(`/public/tournaments/${open}${suffix}`)).status, 200, suffix);
      assert.equal((await get(`/public/tournaments/${hidden}${suffix}`)).status, 404, suffix);
    }
  });

  it("never shows emails or accounts, even to a signed-in admin", async () => {
    const admin = await createAccount({ role: "ADMIN" });
    const { body } = await get(`/public/tournaments/${open}/players`, admin.token);
    const slot = body.players.find((p: { id: number }) => p.id === 1);

    assert.equal(slot.name, "Ana Public");
    assert.equal(slot.email, null);
    assert.equal(slot.accountUid, null);
    assert.equal(slot.hasAccount, false);
    assert.equal(slot.hasEmail, false);
  });

  it("caches a completed tournament longer than one in play", async () => {
    const live = await get(`/public/tournaments/${open}/rounds`);
    await db.doc(`tournaments/${open}`).update({ isCompleted: true });
    const done = await get(`/public/tournaments/${open}/rounds`);
    await db.doc(`tournaments/${open}`).update({ isCompleted: false });
    const seconds = (cache: string) => Number(/s-maxage=(\d+)/.exec(cache)?.[1]);

    assert.ok(seconds(done.cache) > seconds(live.cache));
  });

  it("lists the EMA registry without photos", async () => {
    const { body } = await get("/public/players");
    const ana = body.players.find((p: { emaId: string }) => p.emaId === "20000001");

    assert.equal(ana.lastName, "Public");
    assert.equal(ana.photoUrl, null);
  });

  it("reads the statistics of a person and the overview", async () => {
    assert.equal((await get("/public/statistics/persons/20000001")).status, 200);
    assert.equal((await get(`/public/statistics/persons/20000001/standings?tournaments=${open},${hidden}`)).status, 200);
    assert.equal((await get("/public/statistics/overview")).status, 200);
  });

  it("tells a signed-in account its slot and team, and never caches it", async () => {
    const { status, cache, body } = await get("/me", player.token);

    assert.equal(status, 200);
    assert.match(cache, /no-store/);
    assert.equal(body.emaId, "20000001");
    assert.deepEqual(body.tournaments, [{ tournamentId: open, playerId: 1, teamId: 2 }]);
  });

  it("answers an account without a player with an empty list", async () => {
    const editor = await createAccount({ role: "EDITOR" });
    const { body } = await get("/me", editor.token);

    assert.deepEqual(body, { emaId: "", tournaments: [] });
  });
  it("shows a person who asked to stay hidden only as a number", async () => {
    await db.doc("emaPlayerRegistry/20000001").update({ hiddenFromPublic: true });
    try {
      const slot = (await get(`/public/tournaments/${open}/players`)).body.players.find((p: { id: number }) => p.id === 1);
      assert.equal(slot.name, "#1");
      assert.equal(slot.assignedEmaId, null);
      assert.equal(slot.country, "");
      const registry = (await get("/public/players")).body.players;
      assert.ok(!registry.some((p: { emaId: string }) => p.emaId === "20000001"));
      assert.equal((await get("/public/statistics/persons/20000001")).status, 404);
      assert.equal((await get(`/public/statistics/persons/20000001/standings?tournaments=${open}`)).status, 404);
      // The person still finds the own slot when signed in.
      assert.deepEqual((await get("/me", player.token)).body.tournaments, [{ tournamentId: open, playerId: 1, teamId: 2 }]);
    } finally {
      await db.doc("emaPlayerRegistry/20000001").update({ hiddenFromPublic: false });
    }
  });
});
