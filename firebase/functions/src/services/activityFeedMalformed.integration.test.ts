import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { Timestamp } from "firebase-admin/firestore";

import { db } from "../firebase";
import { listActivityFeed } from "./activityFeed";
import { ACTIVITY_COLLECTION } from "./activityLog";

/** The activity feed over records, tournaments and slots whose documents miss fields: each entry still shows. */
describe("activity feed of malformed documents", () => {
  it("reads table saves and records without their fields, a page at a time", async () => {
    const tournament = db.doc(`tournaments/feed-malformed-${Date.now()}`);
    await tournament.set({});
    await tournament.collection("tables").doc("1_1").set({ playerIds: [1, 2] });
    await tournament.collection("players").doc("1").set({ assignedEmaId: "07740001" });
    await tournament.collection("players").doc("2").set({});
    await db.doc("emaPlayerRegistry/07740001").set({});
    await tournament.collection("changes").doc("a").set({
      at: Timestamp.now(), kind: "save", roundId: 1, tableId: 1, uid: "feed-nobody",
      tableChanges: [{ from: 1, to: 2 }], handChanges: [{ field: "handScore", to: "8" }],
    });
    // A time that is not a timestamp sorts after every timestamp.
    await tournament.collection("changes").doc("b").set({ at: 5 });
    await db.collection(ACTIVITY_COLLECTION).add({ at: Timestamp.now(), tournamentId: tournament.id, action: "tournament.update" });

    const first = await listActivityFeed({ tournamentId: tournament.id, source: "tables" }, 1);
    assert.equal(first.entries.length, 1);
    assert.equal(first.entries[0].tournamentName, "");
    assert.deepEqual(first.entries[0].changes.map((change) => change.field), ["", "handScore"]);
    assert.deepEqual(first.entries[0].players, []);
    const second = await listActivityFeed({ tournamentId: tournament.id, source: "tables" }, 1, first.nextCursor);
    assert.equal(second.entries[0].id.endsWith("/b"), true);

    const all = await listActivityFeed({ tournamentId: tournament.id, source: "all" }, 10);
    assert.ok(all.entries.some((entry) => entry.kind !== "table" && entry.action === "tournament.update"));
    const scriptsOfSomeone = await listActivityFeed({ tournamentId: tournament.id, source: "other", origin: "system", uid: "someone" }, 10);
    assert.deepEqual(scriptsOfSomeone.entries, []);
  });

  it("reads a change log outside a tournament", async () => {
    const far = Timestamp.fromMillis(Date.UTC(2200, 0, 1));
    await db.collection("changes").doc(`feed-malformed-${Date.now()}`).set({ at: far, kind: "save" });
    const page = await listActivityFeed({ source: "tables", fromMs: Date.UTC(2199, 0, 1) }, 5);
    assert.equal(page.entries.length, 1);
    assert.equal(page.entries[0].tournamentId, "");
  });
});
