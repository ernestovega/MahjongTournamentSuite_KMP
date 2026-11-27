import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { MOUNTED_ROUTERS } from "./app";
import { setMailTransportForTests } from "../services/mailService";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

type Layer = { route?: { path: string; methods: Record<string, boolean> } };

/** Every route that takes a body, from the routers that the app mounts. */
function writeRoutes(): Array<{ method: string; path: string }> {
  return MOUNTED_ROUTERS.flatMap(([prefix, build]) =>
    (build() as unknown as { stack: Layer[] }).stack.flatMap((layer) =>
      layer.route == null
        ? []
        : Object.keys(layer.route.methods)
          .filter((method) => method === "post" || method === "put" || method === "patch")
          .map((method) => ({ method: method.toUpperCase(), path: prefix + layer.route!.path })),
    ),
  );
}

/**
 * A client that sends a write without any body, not even `{}`: every route answers with a refusal or does nothing,
 * never with a server error. Each route falls back to an empty body.
 */
describe("write routes without a body", () => {
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

  it("answers every write without a body with no server error", async () => {
    const values: Record<string, string> = {
      tournamentId, roundId: "1", tableId: "1", playerId: "1", teamId: "1", emaId: "07720001",
      uid: "no-such-account", roleId: "no-such-role", requestId: "no-such-request", organizationId: "no-such-organization",
    };
    const routes = writeRoutes();
    assert.ok(routes.length > 50, `${routes.length} routes`);
    const failures: string[] = [];
    for (const route of routes) {
      const path = route.path.replace(/:(\w+)/g, (_whole, name: string) => values[name] ?? name);
      const response = await server.request(route.method, path, { token: admin.token, noBody: true });
      if (response.status >= 500 || response.status === 429) failures.push(`${route.method} ${route.path}: ${response.status} ${JSON.stringify(response.body)}`);
    }
    assert.deepEqual(failures, []);
  });
});
