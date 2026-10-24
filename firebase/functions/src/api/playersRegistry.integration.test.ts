import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("EMA player registry routes", () => {
  let server: TestServer;
  let admin: TestAccount;
  let editor: TestAccount;
  let player: TestAccount;
  let tournamentId = "";
  const body = (emaId: string, extra: Record<string, unknown> = {}) =>
    ({ emaId, firstName: "ana maria", lastName: "perez", country: "esp", ...extra });

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
    editor = await createAccount({ role: "EDITOR" });
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    player = await createAccount({ role: "PLAYER", playerOf: [tournamentId] });
  });
  after(async () => { await server.close(); });

  it("lets only an admin create a player and pads the EMA number", async () => {
    assert.equal((await server.request("POST", "/ema-player-registry", { token: editor.token, body: body("20000001") })).status, 403);
    assert.equal((await server.request("POST", "/ema-player-registry", { token: null, body: body("20000001") })).status, 401);

    const created = await server.request("POST", "/ema-player-registry", { token: admin.token, body: body("2000001") });

    assert.equal(created.status, 200);
    const player = created.body as { emaId: string; country: string };
    assert.equal(player.emaId, "02000001");
    assert.equal(player.country, "ESP");
  });

  it("refuses a duplicate, a missing name, a bad country and a bad EMA number", async () => {
    await server.request("POST", "/ema-player-registry", { token: admin.token, body: body("20000002") });

    const request = (payload: unknown) => server.request("POST", "/ema-player-registry", { token: admin.token, body: payload });
    assert.equal((await request(body("20000002"))).status, 400);
    assert.equal((await request(body("20000003", { firstName: " " }))).status, 400);
    assert.equal((await request(body("20000003", { country: "ES" }))).status, 400);
    assert.equal((await request(body("abc"))).status, 400);
    assert.equal((await request(body("123456789"))).status, 400);
  });

  it("updates a player and moves the slots of the tournaments when the EMA number changes", async () => {
    await server.request("POST", "/ema-player-registry", { token: admin.token, body: body("20000010") });
    await db.doc(`tournaments/${tournamentId}/players/1`).set({ id: 1, team: 0, assignedEmaId: "20000010" });
    await db.doc(`tournaments/${tournamentId}/emaPlayerAssignments/20000010`).set({ playerId: 1 });

    const renamed = await server.request("PUT", "/ema-player-registry/20000010", {
      token: admin.token, body: body("20000011", { lastName: "lopez" }),
    });

    assert.equal(renamed.status, 200);
    assert.equal((await db.doc("emaPlayerRegistry/20000010").get()).exists, false);
    assert.equal(String((await db.doc("emaPlayerRegistry/20000011").get()).get("lastName")).toLowerCase(), "lopez");
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/1`).get()).get("assignedEmaId"), "20000011");
    assert.equal((await db.doc(`tournaments/${tournamentId}/emaPlayerAssignments/20000010`).get()).exists, false);
    assert.equal((await db.doc(`tournaments/${tournamentId}/emaPlayerAssignments/20000011`).get()).get("playerId"), 1);
  });

  it("refuses to update a missing player or to take another EMA number", async () => {
    await server.request("POST", "/ema-player-registry", { token: admin.token, body: body("20000020") });
    await server.request("POST", "/ema-player-registry", { token: admin.token, body: body("20000021") });

    assert.equal((await server.request("PUT", "/ema-player-registry/29999999", { token: admin.token, body: body("29999999") })).status, 404);
    assert.equal((await server.request("PUT", "/ema-player-registry/20000020", { token: admin.token, body: body("20000021") })).status, 400);
    assert.equal((await server.request("PUT", "/ema-player-registry/20000020", { token: editor.token, body: body("20000020") })).status, 403);
  });

  it("lists every player for an editor and only the people of their tournaments for a player", async () => {
    await db.doc(`tournaments/${tournamentId}/players/2`).set({ id: 2, team: 0, assignedEmaId: "20000021" });

    const asEditor = await server.request("GET", "/ema-player-registry", { token: editor.token });
    const asPlayer = await server.request("GET", "/ema-player-registry", { token: player.token });

    assert.ok((asEditor.body as { players: unknown[] }).players.length >= 5);
    assert.deepEqual(
      (asPlayer.body as { players: Array<{ emaId: string }> }).players.map((entry) => entry.emaId).sort(),
      ["20000011", "20000021"],
    );
  });

  it("refuses a photo with a wrong type, a wrong signature or no data before it reaches the storage", async () => {
    const photo = (payload: unknown) => server.request("PUT", "/ema-player-registry/20000021/photo", { token: admin.token, body: payload });

    assert.equal((await photo({})).status, 400);
    assert.equal((await photo({ contentType: "image/svg+xml", dataBase64: Buffer.from("<svg/>").toString("base64") })).status, 400);
    assert.equal((await photo({ contentType: "image/png", dataBase64: Buffer.from("not a png").toString("base64") })).status, 400);
    assert.equal((await server.request("PUT", "/ema-player-registry/20000021/photo", {
      token: editor.token, body: { contentType: "image/png", dataBase64: "AA==" },
    })).status, 403);
  });
});
