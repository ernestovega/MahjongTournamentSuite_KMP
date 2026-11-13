import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { requireAuthEmulator, startServer, type TestServer } from "../testing/authFixtures";

/** The two routes that an app calls before a sign-in: the feature flags and the error reports. */
describe("the routes that need no account", () => {
  let server: TestServer;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
  });
  after(async () => { await server.close(); });

  it("gives the default feature flags on the emulators", async () => {
    const response = await server.request("GET", "/config");

    assert.equal(response.status, 200);
    assert.deepEqual(response.body, { levelsEnabled: false });
    // The flags are the same for everyone, so the CDN may share them for a minute.
    const raw = await fetch(`${server.baseUrl}/config`);
    assert.equal(raw.headers.get("cache-control"), "public, max-age=5, s-maxage=60");
  });

  it("refuses an error report without a valid app, platform and kind", async () => {
    for (const body of [{}, { app: "other", platform: "web", kind: "crash", errorType: "X" }, { app: "player", platform: "web", kind: "crash" }]) {
      const response = await server.request("POST", "/telemetry/errors", { body });
      assert.equal(response.status, 400, JSON.stringify(body));
    }
  });
});
