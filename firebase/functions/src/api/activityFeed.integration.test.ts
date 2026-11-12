import assert from "node:assert/strict";
import { after, before, beforeEach, describe, it } from "node:test";

import { Timestamp } from "firebase-admin/firestore";

import { db } from "../firebase";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { ACTIVITY_COLLECTION } from "../services/activityLog";

type Entry = {
  id: string;
  kind: string;
  at: number;
  action: string;
  uid: string | null;
  source: string | null;
  tournamentId: string | null;
  tournamentName: string | null;
  target: string | null;
  details: Array<{ key: string; value: string }>;
  roundId: number | null;
  tableId: number | null;
  changes: Array<{ field: string; from: string | null; to: string | null; handId?: number }>;
};
type Page = { entries: Entry[]; nextCursor: string | null };

const BASE = 4_000_000_000_000;
const T1 = "feed-test-one";
const T2 = "feed-test-two";

describe("activity feed API", () => {
  let server: TestServer;
  let admin: TestAccount;
  let editor: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
    editor = await createAccount({ role: "EDITOR" });
  });
  after(() => server.close());
  beforeEach(async () => {
    await db.recursiveDelete(db.collection(ACTIVITY_COLLECTION));
    await db.recursiveDelete(db.collection("tournaments").doc(T1));
    await db.recursiveDelete(db.collection("tournaments").doc(T2));
  });

  async function activity(offset: number, action: string, tournamentId: string | null, uid = "u1") {
    await db.collection(ACTIVITY_COLLECTION).add({
      action, uid, name: `Name ${uid}`, tournamentId, target: "player 5", details: { teamId: 3 },
      at: Timestamp.fromMillis(BASE + offset),
    });
  }

  async function tableSave(offset: number, tournamentId: string, uid = "u2", source = "player") {
    await db.collection("tournaments").doc(tournamentId).set({ name: `Tournament ${tournamentId}` }, { merge: true });
    await db.collection("tournaments").doc(tournamentId).collection("changes").add({
      kind: "save", roundId: 2, tableId: 4, version: 7, uid, name: "Player 123", source,
      tableChanges: [{ field: "isCompleted", from: false, to: true }],
      handChanges: [{ handId: 3, field: "handScore", from: null, to: "8" }],
      handChangeCount: 1,
      at: Timestamp.fromMillis(BASE + offset),
    });
  }

  async function readAll(query: string, limit: number): Promise<Entry[][]> {
    const pages: Entry[][] = [];
    let cursor = "";
    for (let guard = 0; guard < 30; guard += 1) {
      const response = await server.request("GET", `/admin/activity?limit=${limit}${query}${cursor}`, { token: admin.token });
      assert.equal(response.status, 200, JSON.stringify(response.body));
      const page = response.body as Page;
      pages.push(page.entries);
      // Records of other tests are older than these ones, so the first older record ends the reading.
      if (!page.nextCursor || page.entries.some((entry) => entry.at < BASE)) return pages;
      cursor = `&cursor=${encodeURIComponent(page.nextCursor)}`;
    }
    throw new Error("The cursor never ended");
  }

  /** Only the records of these tests, which are far in the future. */
  const mine = (entries: Entry[]) => entries.filter((entry) => entry.at >= BASE);

  it("merges both logs newest first, one page at a time, with nothing lost or repeated", async () => {
    await activity(1000, "player.assign", T1);
    await tableSave(2000, T1);
    await activity(3000, "team.update", T2);
    await tableSave(4000, T2);
    await tableSave(5000, T1);
    await activity(6000, "tournament.update", T1);

    for (const limit of [1, 2, 4, 50]) {
      const all = mine((await readAll("", limit)).flat());
      assert.deepEqual(all.map((entry) => entry.at - BASE), [6000, 5000, 4000, 3000, 2000, 1000], `limit ${limit}`);
      assert.equal(new Set(all.map((entry) => entry.id)).size, 6);
    }
  });

  it("does not lose records written at the same time", async () => {
    for (let i = 0; i < 4; i += 1) {
      await activity(1000, "player.assign", T1);
      await tableSave(1000, T1);
    }
    const all = mine((await readAll("", 1)).flat());
    assert.equal(all.length, 8);
    assert.equal(new Set(all.map((entry) => entry.id)).size, 8);
  });

  it("describes a table save with its table, origin and changes", async () => {
    await tableSave(1000, T1);
    const [entry] = mine((await readAll(`&tournamentId=${T1}`, 10)).flat());
    assert.equal(entry.kind, "table");
    assert.equal(entry.action, "table.save");
    assert.equal(entry.source, "player");
    assert.equal(entry.tournamentId, T1);
    assert.equal(entry.tournamentName, `Tournament ${T1}`);
    assert.equal(entry.roundId, 2);
    assert.equal(entry.tableId, 4);
    assert.deepEqual(entry.changes, [
      { field: "isCompleted", from: "false", to: "true" },
      { field: "handScore", from: null, to: "8", handId: 3 },
    ]);
  });

  it("filters by tournament, person and log", async () => {
    await activity(1000, "player.assign", T1, "u1");
    await activity(2000, "team.update", T2, "u1");
    await tableSave(3000, T1, "u2");
    await tableSave(4000, T2, "u1", "editor");

    const byTournament = mine((await readAll(`&tournamentId=${T1}`, 2)).flat());
    assert.deepEqual(byTournament.map((entry) => entry.at - BASE), [3000, 1000]);

    const byPerson = mine((await readAll("&uid=u1", 2)).flat());
    assert.deepEqual(byPerson.map((entry) => entry.at - BASE), [4000, 2000, 1000]);

    const both = mine((await readAll(`&uid=u1&tournamentId=${T2}`, 2)).flat());
    assert.deepEqual(both.map((entry) => entry.at - BASE), [4000, 2000]);

    const tables = mine((await readAll("&source=tables", 2)).flat());
    assert.deepEqual(tables.map((entry) => entry.kind), ["table", "table"]);

    const other = mine((await readAll("&source=other", 2)).flat());
    assert.deepEqual(other.map((entry) => entry.kind), ["activity", "activity"]);
  });

  it("filters by origin: admin app, player app, or the server and the scripts", async () => {
    await activity(1000, "player.assign", T1, "u1");
    await activity(2000, "script.run", null, "script");
    await tableSave(3000, T1, "u2", "player");
    await tableSave(4000, T1, "u1", "editor");
    await tableSave(5000, T2, "system", "system");
    // Many script runs between admin records: the admin pages must still reach the oldest admin record.
    for (let i = 0; i < 5; i += 1) await activity(500 + i, "script.run", null, "script");
    await activity(100, "team.update", T1, "u1");

    for (const limit of [1, 2, 50]) {
      const admin = mine((await readAll("&origin=admin", limit)).flat());
      assert.deepEqual(admin.map((entry) => entry.at - BASE), [4000, 1000, 100], `admin limit ${limit}`);
    }
    const player = mine((await readAll("&origin=player", 2)).flat());
    assert.deepEqual(player.map((entry) => entry.at - BASE), [3000]);
    const system = mine((await readAll("&origin=system", 2)).flat());
    assert.deepEqual(system.map((entry) => entry.at - BASE), [5000, 2000, 504, 503, 502, 501, 500]);
    const adminOfOne = mine((await readAll("&origin=admin&uid=u1", 1)).flat());
    assert.deepEqual(adminOfOne.map((entry) => entry.at - BASE), [4000, 1000, 100]);
  });

  it("keeps only the records of the period, from inclusive to exclusive", async () => {
    await activity(1000, "player.assign", T1);
    await tableSave(2000, T1);
    await activity(3000, "team.update", T1);
    await tableSave(4000, T1);

    const period = mine((await readAll(`&from=${BASE + 2000}&to=${BASE + 4000}`, 1)).flat());
    assert.deepEqual(period.map((entry) => entry.at - BASE), [3000, 2000]);
    const since = mine((await readAll(`&from=${BASE + 3000}`, 50)).flat());
    assert.deepEqual(since.map((entry) => entry.at - BASE), [4000, 3000]);
  });

  it("refuses a bad cursor, source, origin or period", async () => {
    for (const query of ["cursor=nope", "source=x", "origin=x", "from=abc", "from=-1", "from=10&to=5", `cursor=${Buffer.from("{\"a\":1}").toString("base64url")}`]) {
      const response = await server.request("GET", `/admin/activity?${query}`, { token: admin.token });
      assert.equal(response.status, 400, query);
    }
  });

  it("is only for administrators", async () => {
    assert.equal((await server.request("GET", "/admin/activity", { token: editor.token })).status, 403);
    assert.equal((await server.request("GET", "/admin/activity", { token: null })).status, 401);
  });
});
