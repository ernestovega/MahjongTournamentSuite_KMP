import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { parsePlayerImportFile } from "../services/playerImport";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("player import routes", () => {
  let server: TestServer;
  let tournamentId = "";
  let editor: TestAccount;
  let otherEditor: TestAccount;
  let player: TestAccount;
  const file = (text: string) => ({ fileName: "players.csv", dataBase64: Buffer.from(text).toString("base64") });

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const otherId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    await db.doc("emaPlayerRegistry/00000001").set({ firstName: "Ana", lastName: "Perez", country: "ESP" });
    await db.doc(`tournaments/${tournamentId}/players/1`).set({ id: 1, team: 0, assignedEmaId: null });
    editor = await createAccount({ role: "EDITOR", memberOf: [tournamentId] });
    otherEditor = await createAccount({ role: "EDITOR", memberOf: [otherId] });
    player = await createAccount({ role: "PLAYER", playerOf: [tournamentId] });
  });
  after(async () => { await server.close(); });

  const preview = (token: string | null, body: unknown) =>
    server.request("POST", `/tournaments/${tournamentId}/player-import/preview`, { token, body });
  const apply = (token: string | null, body: unknown) =>
    server.request("POST", `/tournaments/${tournamentId}/player-import/apply`, { token, body });

  it("lets an editor of the tournament preview a file without changing anything", async () => {
    const { status, body } = await preview(editor.token, file("id,ema id\n1,1\n"));

    assert.equal(status, 200);
    assert.equal((body as { rows: Array<{ status: string }> }).rows[0].status, "NEW");
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/1`).get()).get("assignedEmaId"), null);
  });

  it("refuses no token, a player and an editor of another tournament", async () => {
    assert.equal((await preview(null, file("id,ema id\n1,1\n"))).status, 401);
    assert.equal((await preview(player.token, file("id,ema id\n1,1\n"))).status, 403);
    assert.equal((await preview(otherEditor.token, file("id,ema id\n1,1\n"))).status, 403);
    assert.equal((await apply(otherEditor.token, { ...file("id,ema id\n1,1\n"), phase: "ASSIGN", rows: [2] })).status, 403);
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/1`).get()).get("assignedEmaId"), null);
  });

  it("sends no-store and nosniff headers and refuses a body that is not JSON", async () => {
    const response = await fetch(`${server.baseUrl}/tournaments/${tournamentId}/player-import/preview`, {
      method: "POST",
      headers: { authorization: `Bearer ${editor.token}`, "content-type": "application/json" },
      body: "{not json",
    });

    assert.equal(response.status, 400);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal(response.headers.get("x-content-type-options"), "nosniff");
    assert.equal(response.headers.get("x-powered-by"), null);
  });

  it("refuses a request without a file or with a file that is not a table", async () => {
    assert.equal((await preview(editor.token, {})).status, 400);
    assert.equal((await preview(editor.token, file("name,email\nAna,a@b.es"))).status, 400);
  });

  it("refuses an unknown step and too many rows in one step", async () => {
    assert.equal((await apply(editor.token, { ...file("id,ema id\n1,1\n"), phase: "NOW" })).status, 400);
    assert.equal((await apply(editor.token, { ...file("id,ema id\n1,1\n") })).status, 400);
    const rows = Array.from({ length: 26 }, (_, index) => index + 2);
    assert.equal((await apply(editor.token, { ...file("id,ema id\n1,1\n"), phase: "ASSIGN", rows })).status, 400);
    assert.equal((await apply(editor.token, { ...file("id,ema id\n1,1\n"), phase: "ASSIGN", rows: ["x"] })).status, 400);
  });

  it("assigns the player in the ASSIGN step and records the activity", async () => {
    const { status, body } = await apply(editor.token, { ...file("id,ema id\n1,1\n"), phase: "ASSIGN", rows: [2] });

    assert.equal(status, 200);
    assert.equal((body as { assigned: number }).assigned, 1);
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/1`).get()).get("assignedEmaId"), "00000001");
    const log = await db.collection("activity").where("tournamentId", "==", tournamentId).where("action", "==", "player.import").get();
    assert.ok(log.size >= 1);
  });

  const exportPlayersFile = (token: string | null, id = tournamentId) =>
    server.request("GET", `/tournaments/${id}/player-import/export`, { token });

  it("lets an editor of the tournament download the players as an Excel file", async () => {
    const { status, body } = await exportPlayersFile(editor.token);

    assert.equal(status, 200);
    const { fileName, dataBase64 } = body as { fileName: string; dataBase64: string };
    assert.equal(fileName, "players.xlsx");
    const parsed = await parsePlayerImportFile(fileName, Buffer.from(dataBase64, "base64"));
    assert.equal(parsed.rows[0].id, "1");
    assert.ok(parsed.hasEmailColumn);
  });

  it("refuses the download to no token, a player and an editor of another tournament", async () => {
    assert.equal((await exportPlayersFile(null)).status, 401);
    assert.equal((await exportPlayersFile(player.token)).status, 403);
    assert.equal((await exportPlayersFile(otherEditor.token)).status, 403);
  });

  it("changes nothing and records no import activity when it exports", async () => {
    const before = await db.collection("activity").where("tournamentId", "==", tournamentId).where("action", "==", "player.import").get();
    const slotBefore = (await db.doc(`tournaments/${tournamentId}/players/1`).get()).data();

    await exportPlayersFile(editor.token);

    const after = await db.collection("activity").where("tournamentId", "==", tournamentId).where("action", "==", "player.import").get();
    assert.equal(after.size, before.size);
    assert.deepEqual((await db.doc(`tournaments/${tournamentId}/players/1`).get()).data(), slotBefore);
  });

  it("sends no-store and nosniff headers with the file", async () => {
    const response = await fetch(`${server.baseUrl}/tournaments/${tournamentId}/player-import/export`, {
      headers: { authorization: `Bearer ${editor.token}` },
    });

    assert.equal(response.status, 200);
    assert.equal(response.headers.get("cache-control"), "no-store");
    assert.equal(response.headers.get("x-content-type-options"), "nosniff");
  });
});
