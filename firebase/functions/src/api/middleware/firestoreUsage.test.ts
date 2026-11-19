import assert from "node:assert/strict";
import { describe, it } from "node:test";
import express from "express";

import {
  currentUsage,
  firestoreUsage,
  installFirestoreUsageCounters,
  USAGE_REQUEST_HEADER,
  USAGE_RESPONSE_HEADER,
  withUsage,
} from "./firestoreUsage";

// Small fakes with the methods of the SDK that the counters wrap.
class DocumentReference {}
class Firestore {
  async getAll(...refs: unknown[]) { return refs; }
}
class Query {
  constructor(private readonly size: number) {}
  async _get() { return { result: { size: this.size } }; }
}
class AggregateQuery {
  constructor(private readonly count: number) {}
  async _get() { return { result: { data: () => ({ count: this.count }) } }; }
}
class Transaction {
  async get(_ref: unknown) { return null; }
  async getAll(...refs: unknown[]) { return refs; }
}
class WriteBatch {
  _ops: unknown[] = [];
  set() { this._ops.push("set"); return this; }
  delete() { this._ops.push("delete"); return this; }
  async _commit() { return []; }
}

installFirestoreUsageCounters({ Firestore, Query, AggregateQuery, Transaction, WriteBatch, DocumentReference });
// A second install must not count twice.
installFirestoreUsageCounters({ Firestore, Query, AggregateQuery, Transaction, WriteBatch, DocumentReference });

describe("Firestore usage", () => {
  it("counts reads as Firestore bills them", async () => {
    const { usage } = await withUsage(async () => {
      await new Firestore().getAll(new DocumentReference(), new DocumentReference(), { fieldMask: ["a"] });
      await new Query(7)._get();
      await new Query(0)._get(); // an empty query is still one read
      await new AggregateQuery(2500)._get(); // one read per 1000 index entries
      await new AggregateQuery(3)._get();
      await new Transaction().get(new DocumentReference());
      await new Transaction().get(new Query(4)); // the query counts itself when it runs
      await new Transaction().getAll(new DocumentReference());
    });
    assert.deepEqual(usage, { reads: 2 + 7 + 1 + 3 + 1 + 1 + 1, writes: 0, deletes: 0 });
  });

  it("counts writes and deletes of a commit", async () => {
    const { usage } = await withUsage(async () => {
      const batch = new WriteBatch().set().set().delete();
      await batch._commit();
    });
    assert.deepEqual(usage, { reads: 0, writes: 2, deletes: 1 });
  });

  it("counts nothing outside a request", async () => {
    await new Query(5)._get();
    assert.equal(currentUsage(), null);
  });

  it("keeps the counters of parallel requests apart", async () => {
    const [a, b] = await Promise.all([
      withUsage(async () => { await new Query(3)._get(); }),
      withUsage(async () => { await new Query(10)._get(); await new Query(1)._get(); }),
    ]);
    assert.equal(a.usage.reads, 3);
    assert.equal(b.usage.reads, 11);
  });

  it("answers with the counters only when the request asks for them", async () => {
    const app = express();
    app.use(firestoreUsage());
    app.get("/x", async (_req, res) => {
      await new Query(4)._get();
      res.json({ ok: true });
    });
    const server = app.listen(0);
    try {
      const port = (server.address() as { port: number }).port;
      const asked = await fetch(`http://127.0.0.1:${port}/x`, { headers: { [USAGE_REQUEST_HEADER]: "1" } });
      assert.equal(asked.headers.get(USAGE_RESPONSE_HEADER), "reads=4; writes=0; deletes=0");
      const plain = await fetch(`http://127.0.0.1:${port}/x`);
      assert.equal(plain.headers.get(USAGE_RESPONSE_HEADER), null);
    } finally {
      server.close();
    }
  });
});
