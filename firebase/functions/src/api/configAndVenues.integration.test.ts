import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("feature flags and the venue form", () => {
  let server: TestServer;
  let editor: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    editor = await createAccount({ role: "EDITOR" });
  });
  after(async () => { await server.close(); });

  it("gives the feature flags to anyone, with every feature off on the emulators", async () => {
    const response = await server.request("GET", "/config");

    assert.equal(response.status, 200);
    assert.deepEqual(response.body, { levelsEnabled: false });
  });

  it("keeps a full Google Maps link and finds no place without a Places key", async () => {
    const url = "https://www.google.com/maps/place/Casa+de+Asturias/@40.4,-3.7,17z";
    const response = await server.request("POST", "/venues/resolve-link", { token: editor.token, body: { url } });

    assert.equal(response.status, 200);
    const body = response.body as { url: string; place: unknown };
    assert.ok(body.url.startsWith("https://www.google.com/maps/place/"));
    assert.equal(body.place, null);
  });

  it("refuses a link that is not a Google Maps link", async () => {
    const response = await server.request("POST", "/venues/resolve-link", { token: editor.token, body: { url: "https://example.com/somewhere" } });

    assert.equal(response.status, 400);
  });

  it("refuses an empty link", async () => {
    const response = await server.request("POST", "/venues/resolve-link", { token: editor.token, body: {} });

    assert.equal(response.status, 400);
  });
});
