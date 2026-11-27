import assert from "node:assert/strict";
import { after, before, describe, it, mock } from "node:test";

import { MOUNTED_ROUTERS } from "./app";
import * as config from "../config";
import { db } from "../firebase";
import * as featureFlags from "../services/featureFlagsService";
import * as mapLinkResolver from "../services/mapLinkResolver";
import * as placesLookup from "../services/placesLookup";
import { setMailTransportForTests } from "../services/mailService";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

type Layer = { route?: { path: string; methods: Record<string, boolean> } };

function allRoutes(): Array<{ method: string; path: string }> {
  return MOUNTED_ROUTERS.flatMap(([prefix, build]) =>
    (build() as unknown as { stack: Layer[] }).stack.flatMap((layer) =>
      layer.route == null
        ? []
        : Object.keys(layer.route.methods).map((method) => ({ method: method.toUpperCase(), path: prefix + layer.route!.path })),
    ),
  );
}

const STORE_METHODS = ["collection", "collectionGroup", "doc", "runTransaction", "batch", "getAll"] as const;

/** The calls that reach the database. They fail as the database would: with a rejected promise. */
const ASYNC_CALLS = new Set(["get", "set", "update", "delete", "create", "add", "listDocuments", "listCollections", "getAll", "commit", "runTransaction"]);

/** [value] as it would be while the database is unreachable: references and queries build, and every read or write fails. */
function unreachable<T>(value: T): T {
  if (value == null || typeof value !== "object") return value;
  return new Proxy(value as object, {
    get(target, property, receiver) {
      const member = Reflect.get(target, property, receiver);
      if (typeof member !== "function") return member;
      if (ASYNC_CALLS.has(String(property))) return () => Promise.reject(new Error("store unavailable"));
      return (...args: unknown[]) => unreachable(member.apply(target, args));
    },
  }) as T;
}

/** Makes the database fail for the calls whose stack names [where]. Returns the function that ends it. */
function failStore(where: string): () => void {
  const store = db as unknown as Record<string, (...args: unknown[]) => unknown>;
  const mocks = STORE_METHODS.map((name) => {
    const real = store[name].bind(db);
    return mock.method(store, name, (...args: unknown[]) => {
      if (!(new Error().stack ?? "").includes(where)) return real(...args);
      return name === "runTransaction" || name === "getAll" ? Promise.reject(new Error("store unavailable")) : unreachable(real(...args));
    });
  });
  return () => {
    for (const failing of mocks) failing.mock.restore();
  };
}

/**
 * Every route while the database fails under its handler: the request ends with the generic server error and the
 * failure stays out of the answer. The checks of the token and the permissions still read the database, since only
 * calls made from a route file fail.
 */
describe("routes when the database fails", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId = "";

  before(async () => {
    requireAuthEmulator();
    setMailTransportForTests(async () => {});
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  });
  after(async () => {
    setMailTransportForTests(null);
    await server.close();
  });

  it("answers each request with a server error that says nothing of the failure", async () => {
    const values: Record<string, string> = {
      tournamentId, roundId: "1", tableId: "1", playerId: "1", teamId: "1", emaId: "07720001",
      uid: admin.uid, roleId: "no-such-role", requestId: "no-such-request", organizationId: "no-such-organization",
    };
    const restore = failStore("/api/routes/");
    const leaks: string[] = [];
    let serverErrors = 0;
    try {
      for (const route of allRoutes()) {
        const path = route.path.replace(/:(\w+)/g, (_whole, name: string) => values[name] ?? `no-such-${name}`);
        const response = await server.request(route.method, path, { token: admin.token, body: {} });
        if (response.status === 500) serverErrors++;
        if (JSON.stringify(response.body ?? "").includes("store unavailable")) leaks.push(`${route.method} ${route.path}`);
      }
    } finally {
      restore();
    }
    assert.deepEqual(leaks, []);
    assert.ok(serverErrors > 20, `${serverErrors} server errors`);
  });

  it("answers with a server error when the database fails under a permission check or a player check", async () => {
    const player = await createAccount({ role: "PLAYER", playerOf: [tournamentId] });
    // New accounts: the access of an account that was checked a moment ago is kept and needs no read.
    const [first, second] = [await createAccount({ role: "SUPER_ADMIN" }), await createAccount({ role: "SUPER_ADMIN" })];
    for (const [where, method, path, token] of [
      ["/api/middleware/requirePermission", "GET", `/tournaments/${tournamentId}/players`, first.token],
      ["/api/middleware/requirePermission", "POST", "/venues/resolve-link", second.token],
      ["/api/middleware/requireRegisteredPlayer", "GET", `/player/tournaments/${tournamentId}/my-hidden-tables`, player.token],
      ["/api/routes/", "GET", `/player/tournaments/${tournamentId}/my-hidden-tables`, player.token],
      ["/api/routes/", "POST", `/player/tournaments/${tournamentId}/tables/1/1/penalties`, player.token],
    ] as const) {
      const restore = failStore(where);
      try {
        const response = await server.request(method, path, { token, body: { url: "https://maps.app.goo.gl/x", expectedVersion: 0, playerEastPenalty: "-10" } });
        assert.equal(response.status, 500, `${where} ${method} ${path}: ${JSON.stringify(response.body)}`);
      } finally {
        restore();
      }
    }
  });

  it("refuses a tournament id that cannot name a document, before any player check", async () => {
    const player = await createAccount({ role: "PLAYER" });
    const response = await server.request("GET", `/player/tournaments/${"x".repeat(201)}/my-hidden-tables`, { token: player.token });
    assert.equal(response.status, 400);
  });

  it("answers the flags with a server error when they cannot be read", async () => {
    const failing = mock.method(featureFlags, "getFeatureFlags", async () => {
      throw new Error("flags unavailable");
    });
    try {
      assert.equal((await server.request("GET", "/config")).status, 500);
    } finally {
      failing.mock.restore();
    }
  });

  it("resolves a venue link without Places when its key cannot be read", async () => {
    const key = mock.method(config.PLACES_API_KEY, "value", () => {
      throw new Error("no secret here");
    });
    const resolve = mock.method(mapLinkResolver, "resolveMapLink", async () => "https://www.google.com/maps/place/Madrid");
    let keyGiven: unknown = "not asked";
    const lookup = mock.method(placesLookup, "lookupPlace", async (_url: string, apiKey: string) => {
      keyGiven = apiKey;
      return null;
    });
    try {
      const response = await server.request("POST", "/venues/resolve-link", { token: admin.token, body: { url: "https://maps.app.goo.gl/x" } });
      assert.equal(response.status, 200);
      assert.deepEqual(response.body, { url: "https://www.google.com/maps/place/Madrid", place: null });
      assert.equal(keyGiven, "");
    } finally {
      key.mock.restore();
      resolve.mock.restore();
      lookup.mock.restore();
    }
  });

  it("sends a plain answer to a client that does not accept gzip", async () => {
    const response = await server.request("GET", "/health", { headers: { "accept-encoding": "identity" } });
    assert.equal(response.status, 200);
  });
});
