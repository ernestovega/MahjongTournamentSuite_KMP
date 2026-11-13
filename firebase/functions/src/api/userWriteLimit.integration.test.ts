import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { WRITES_PER_USER_PER_MINUTE } from "./middleware/requireAuth";
import { createAccount, requireAuthEmulator, signInTestAccount, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

describe("the write limit of one user", () => {
  let server: TestServer;
  let editor: TestAccount;
  let other: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    editor = await createAccount({ role: "EDITOR" });
    other = await createAccount({ role: "EDITOR" });
  });
  after(async () => { await server.close(); });

  const resolve = (token: string, address: string) => server.request("POST", "/venues/resolve-link", {
    token,
    body: { url: "https://example.com/somewhere" },
    headers: { "x-forwarded-for": address },
  });

  it("counts the writes of one user from every address and every sign-in", async () => {
    const secondToken = await signInTestAccount(editor.email);
    for (let i = 0; i < WRITES_PER_USER_PER_MINUTE; i++) {
      const response = await resolve(i % 2 === 0 ? editor.token : secondToken, `10.0.${Math.floor(i / 250)}.${i % 250}`);
      assert.equal(response.status, 400);
    }

    const blocked = await resolve(secondToken, "10.9.9.9");
    assert.equal(blocked.status, 429);

    const otherUser = await resolve(other.token, "10.9.9.9");
    assert.equal(otherUser.status, 400);
  });

  it("does not count reads", async () => {
    const response = await server.request("GET", "/auth/me", { token: editor.token, headers: { "x-forwarded-for": "10.9.9.10" } });

    assert.equal(response.status, 200);
  });
});
