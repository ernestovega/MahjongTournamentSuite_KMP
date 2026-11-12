import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { PUBLIC_CACHE_SECONDS } from "./routes/public.routes";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestServer } from "../testing/authFixtures";

/** The cache times, the input checks and the parts of the public routes that publicReads does not cover. */
describe("the limits of the public routes", () => {
  let server: TestServer;
  let open = "";
  let hidden = "";

  const get = async (path: string, token?: string) => {
    const response = await fetch(server.baseUrl + path, { headers: token ? { authorization: `Bearer ${token}` } : {} });
    const text = await response.text();
    let body: any = null;
    try { body = text === "" ? null : JSON.parse(text); } catch { body = text; }
    return { status: response.status, cache: response.headers.get("cache-control") ?? "", body };
  };
  const sharedSeconds = (cache: string) => Number(/s-maxage=(\d+)/.exec(cache)?.[1]);
  const browserSeconds = (cache: string) => Number(/(?:^|[ ,])max-age=(\d+)/.exec(cache)?.[1]);

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    open = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 1, tableId: 2 }, { roundId: 2, tableId: 1 }] });
    hidden = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc(`tournaments/${open}`).update({ name: "Limits open", updatedAt: new Date() });
    await db.doc(`tournaments/${hidden}`).update({ name: "Limits hidden", updatedAt: new Date(), isPublic: false });
    await db.doc("countries/ES").set({ code: "es", name: "Spain" });
    await db.doc("countries/XX1").set({ code: "XX1", name: "Not a country" });
  });
  after(async () => { await server.close(); });

  it("gives every kind of answer its own cache time", async () => {
    const expected: Array<[string, number]> = [
      ["/public/sync/manifest", PUBLIC_CACHE_SECONDS.live],
      ["/public/tournaments", PUBLIC_CACHE_SECONDS.lists],
      [`/public/tournaments/${open}`, PUBLIC_CACHE_SECONDS.live],
      ["/public/statistics/overview", PUBLIC_CACHE_SECONDS.lists],
      ["/public/players", PUBLIC_CACHE_SECONDS.registry],
      ["/public/countries", PUBLIC_CACHE_SECONDS.registry],
    ];
    for (const [path, seconds] of expected) {
      const { status, cache } = await get(path);
      assert.equal(status, 200, path);
      assert.match(cache, /^public, /, path);
      assert.equal(sharedSeconds(cache), seconds, path);
      // The browser keeps an answer for a few seconds only, so polling sees new data soon.
      assert.ok(browserSeconds(cache) <= 5, `${path}: ${cache}`);
    }
  });

  it("serves the global and the tournament manifests", async () => {
    const global = (await get("/public/sync/manifest")).body;
    assert.deepEqual(Object.keys(global.resources).sort(), ["countries", "emaPlayers", "tournaments", "users"]);

    const tournament = await get(`/public/tournaments/${open}/sync/manifest`);
    assert.equal(tournament.status, 200);
    assert.equal(typeof tournament.body, "object");
  });

  it("filters the tables by round and reads one table", async () => {
    const all = (await get(`/public/tournaments/${open}/tables`)).body.tables;
    const first = (await get(`/public/tournaments/${open}/tables?roundId=1`)).body.tables;
    assert.equal(all.length, 3);
    assert.equal(first.length, 2);

    const round = (await get(`/public/tournaments/${open}/rounds/2/tables-with-hands`)).body.tables;
    assert.equal(round.length, 1);

    assert.equal((await get(`/public/tournaments/${open}/tables/1/2`)).status, 200);
  });

  it("refuses numbers that are not positive integers", async () => {
    for (const path of [
      `/public/tournaments/${open}/tables?roundId=0`,
      `/public/tournaments/${open}/tables?roundId=abc`,
      `/public/tournaments/${open}/rounds/-1/tables-with-hands`,
      `/public/tournaments/${open}/tables/1/1.5`,
      `/public/tournaments/${open}/tables/x/1`,
    ]) {
      assert.equal((await get(path)).status, 400, path);
    }
  });

  it("refuses an EMA number that is too long and too many tournaments at once", async () => {
    assert.equal((await get(`/public/statistics/persons/${"1".repeat(65)}`)).status, 400);
    const many = Array.from({ length: 26 }, (_, index) => `t${index}`).join(",");
    assert.equal((await get(`/public/statistics/persons/20000001/standings?tournaments=${many}`)).status, 400);
  });

  it("answers 404 for a tournament that does not exist", async () => {
    assert.equal((await get("/public/tournaments/does-not-exist")).status, 404);
    assert.equal((await get("/public/tournaments/does-not-exist/players")).status, 404);
  });

  it("gives the same answer with and without a token", async () => {
    const admin = await createAccount({ role: "ADMIN" });
    // A cached public answer must never depend on who asks, so an admin does not see a private tournament here.
    assert.equal((await get(`/public/tournaments/${hidden}`)).status, 404);
    assert.equal((await get(`/public/tournaments/${hidden}`, admin.token)).status, 404);
    const list = (await get("/public/tournaments", admin.token)).body.tournaments.map((t: { id: string }) => t.id);
    assert.ok(!list.includes(hidden));
  });

  it("has no write routes", async () => {
    for (const path of ["/public/tournaments", `/public/tournaments/${open}`, "/public/players"]) {
      const response = await fetch(server.baseUrl + path, { method: "POST", headers: { "content-type": "application/json" }, body: "{}" });
      assert.ok(response.status === 404 || response.status === 405, `${path}: ${response.status}`);
    }
  });

  it("lists only the valid countries, sorted by name, with upper case codes", async () => {
    const countries = (await get("/public/countries")).body.countries as Array<{ code: string; name: string }>;
    assert.ok(countries.some((country) => country.code === "ES" && country.name === "Spain"));
    assert.ok(!countries.some((country) => country.code === "XX1"));
    const names = countries.map((country) => country.name);
    assert.deepEqual(names, [...names].sort((a, b) => a.localeCompare(b)));
  });

  it("leaves a private tournament out of the standings of a person", async () => {
    await db.doc(`tournaments/${open}/players/1`).set({ assignedEmaId: "20000077" }, { merge: true });
    await db.doc(`tournaments/${hidden}/players/1`).set({ assignedEmaId: "20000077" }, { merge: true });
    const { status, body } = await get(`/public/statistics/persons/20000077/standings?tournaments=${open},${hidden}`);
    assert.equal(status, 200);
    assert.ok(!body.standings.some((standing: { tournamentId: string }) => standing.tournamentId === hidden));
  });

  it("asks for a token on /me", async () => {
    assert.equal((await get("/me")).status, 401);
  });
});
