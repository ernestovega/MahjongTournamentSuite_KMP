import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
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
    await db.doc(`tournaments/${tournamentId}/players/1`).set({ id: 1, team: 0, assignedEmaId: null, nonMember: null });
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
});
