import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { requireAuthEmulator, startServer, type TestServer } from "../testing/authFixtures";
import { installFirestoreUsageCountersOn, USAGE_REQUEST_HEADER, USAGE_RESPONSE_HEADER, withUsage } from "./middleware/firestoreUsage";

describe("Firestore usage with the real SDK", () => {
  let server: TestServer;
  let tournament = "";

  before(async () => {
    requireAuthEmulator();
    installFirestoreUsageCountersOn(db);
    server = await startServer();
    tournament = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  });

  after(async () => server?.close());

  it("counts the reads, writes and deletes of the SDK", async () => {
    const collection = db.collection(`usageTest${Date.now()}`);
    const { usage } = await withUsage(async () => {
      await collection.doc("a").set({ n: 1 });
      const batch = db.batch();
      batch.set(collection.doc("b"), { n: 2 });
      batch.set(collection.doc("c"), { n: 3 });
      await batch.commit();
      await collection.doc("a").get();
      await db.getAll(collection.doc("a"), collection.doc("b"));
      await collection.get();
      await collection.where("n", "==", 99).get();
      await collection.count().get();
      await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(collection.doc("a"));
        transaction.update(snapshot.ref, { n: 4 });
        transaction.delete(collection.doc("c"));
      });
    });
    assert.deepEqual(usage, { reads: 1 + 2 + 3 + 1 + 1 + 1, writes: 1 + 2 + 1, deletes: 1 });
  });

  it("answers a public read with its counters when asked", async () => {
    const response = await fetch(`${server.baseUrl}/public/tournaments/${tournament}`, { headers: { [USAGE_REQUEST_HEADER]: "1" } });
    assert.equal(response.status, 200);
    const header = response.headers.get(USAGE_RESPONSE_HEADER) ?? "";
    assert.match(header, /^reads=[1-9]\d*; writes=0; deletes=0$/);
  });
});
