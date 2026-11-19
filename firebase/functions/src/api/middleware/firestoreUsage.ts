import { AsyncLocalStorage } from "node:async_hooks";

import type { NextFunction, Request, Response } from "express";

/**
 * The Firestore operations of one request, as Firestore bills them: a document read per document returned (at least
 * one per query), one read per 1000 index entries of a count, a write per document written, a delete per document
 * deleted. The benchmark and the bots of the game day ask for them with the header X-MTS-Usage: 1, and the answer
 * carries them in X-MTS-Firestore. See docs/benchmark.md ("Cost").
 */
export interface FirestoreUsage {
  reads: number;
  writes: number;
  deletes: number;
}

export const USAGE_REQUEST_HEADER = "x-mts-usage";
export const USAGE_RESPONSE_HEADER = "X-MTS-Firestore";

const storage = new AsyncLocalStorage<FirestoreUsage>();

/** The usage of the request that runs now, or null outside a request (scripts, tasks). */
export function currentUsage(): FirestoreUsage | null {
  return storage.getStore() ?? null;
}

/** Runs [block] with its own counters. Returns the result and the counters. */
export async function withUsage<T>(block: () => Promise<T>): Promise<{ result: T; usage: FirestoreUsage }> {
  const usage: FirestoreUsage = { reads: 0, writes: 0, deletes: 0 };
  const result = await storage.run(usage, block);
  return { result, usage };
}

export function formatUsage(usage: FirestoreUsage): string {
  return `reads=${usage.reads}; writes=${usage.writes}; deletes=${usage.deletes}`;
}

/** The classes of the Firestore SDK whose methods are counted. Tests pass small fakes with the same methods. */
export interface FirestoreClasses {
  Firestore: { prototype: object };
  Query: { prototype: object };
  AggregateQuery: { prototype: object };
  Transaction: { prototype: object };
  WriteBatch: { prototype: object };
  DocumentReference: { prototype: object };
}

type AnyFunction = (this: unknown, ...args: unknown[]) => unknown;
const INSTALLED = Symbol.for("mts.firestoreUsage.installed");

function wrap(prototype: object, name: string, around: (original: AnyFunction) => AnyFunction): void {
  const target = prototype as Record<string, unknown>;
  const original = target[name];
  if (typeof original !== "function") return;
  target[name] = around(original as AnyFunction);
}

function add(field: keyof FirestoreUsage, count: number): void {
  const usage = storage.getStore();
  if (usage != null && count > 0) usage[field] += count;
}

/**
 * Counts the operations at the few places of the SDK that every read and write goes through. Only the requests with
 * counters (the middleware below) pay anything, and the wrappers change no result. Installing twice does nothing.
 */
export function installFirestoreUsageCounters(classes: FirestoreClasses): void {
  const marker = classes.Query.prototype as Record<symbol, unknown>;
  if (marker[INSTALLED]) return;
  marker[INSTALLED] = true;
  const isDocument = (value: unknown) => value instanceof (classes.DocumentReference as unknown as new () => object);

  // DocumentReference.get() goes through Firestore.getAll() too.
  wrap(classes.Firestore.prototype, "getAll", (original) => function (this: unknown, ...args: unknown[]) {
    add("reads", args.filter(isDocument).length);
    return original.apply(this, args);
  });
  // Every query, also inside a transaction: one read per document, and one when nothing matches.
  wrap(classes.Query.prototype, "_get", (original) => async function (this: unknown, ...args: unknown[]) {
    const response = (await original.apply(this, args)) as { result?: { size?: number } };
    add("reads", Math.max(1, response?.result?.size ?? 0));
    return response;
  });
  wrap(classes.AggregateQuery.prototype, "_get", (original) => async function (this: unknown, ...args: unknown[]) {
    const response = (await original.apply(this, args)) as { result?: { data?: () => Record<string, unknown> } };
    const values = Object.values(response?.result?.data?.() ?? {}).filter((value): value is number => typeof value === "number");
    const entries = values.length === 0 ? 0 : Math.max(...values);
    add("reads", Math.max(1, Math.ceil(entries / 1000)));
    return response;
  });
  // A transaction reads documents itself; its queries go through Query._get above.
  wrap(classes.Transaction.prototype, "get", (original) => function (this: unknown, ...args: unknown[]) {
    if (isDocument(args[0])) add("reads", 1);
    return original.apply(this, args);
  });
  wrap(classes.Transaction.prototype, "getAll", (original) => function (this: unknown, ...args: unknown[]) {
    add("reads", args.filter(isDocument).length);
    return original.apply(this, args);
  });
  // The writes of a batch, of a transaction and of the BulkWriter (recursiveDelete) are committed here.
  const deletesOf = new WeakMap<object, number>();
  wrap(classes.WriteBatch.prototype, "delete", (original) => function (this: unknown, ...args: unknown[]) {
    deletesOf.set(this as object, (deletesOf.get(this as object) ?? 0) + 1);
    return original.apply(this, args);
  });
  wrap(classes.WriteBatch.prototype, "_commit", (original) => function (this: unknown, ...args: unknown[]) {
    const operations = ((this as { _ops?: unknown[] })._ops ?? []).length;
    const deletes = Math.min(operations, deletesOf.get(this as object) ?? 0);
    add("deletes", deletes);
    add("writes", operations - deletes);
    return original.apply(this, args);
  });
}

/**
 * Gives each request its counters. When the request asks for them (X-MTS-Usage: 1), the answer carries them in
 * X-MTS-Firestore. The header holds what the request did before its answer started, which is all of it for the API.
 */
export function firestoreUsage() {
  return (req: Request, res: Response, next: NextFunction) => {
    const usage: FirestoreUsage = { reads: 0, writes: 0, deletes: 0 };
    if (req.header(USAGE_REQUEST_HEADER) === "1") {
      const writeHead = res.writeHead;
      res.writeHead = function (this: Response, ...args: unknown[]) {
        if (!res.headersSent) res.setHeader(USAGE_RESPONSE_HEADER, formatUsage(usage));
        return (writeHead as (...a: unknown[]) => Response).apply(this, args);
      } as typeof res.writeHead;
    }
    storage.run(usage, () => next());
  };
}

/** Counts the operations of [db], the Firestore of the API. Its SDK does not export every class, so some come from it. */
export function installFirestoreUsageCountersOn(db: import("firebase-admin/firestore").Firestore): void {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const sdk = require("firebase-admin/firestore") as typeof import("firebase-admin/firestore");
  installFirestoreUsageCounters({
    Firestore: db.constructor as unknown as { prototype: object },
    Query: sdk.Query,
    AggregateQuery: db.collection("_").count().constructor as unknown as { prototype: object },
    Transaction: sdk.Transaction,
    WriteBatch: sdk.WriteBatch,
    DocumentReference: sdk.DocumentReference,
  });
}
