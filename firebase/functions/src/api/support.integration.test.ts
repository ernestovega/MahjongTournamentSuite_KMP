import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it } from "node:test";

import { setMailTransportForTests, type OutgoingEmail } from "../services/mailService";
import { setSupportRecipientForTests } from "../services/supportService";
import { startServer, type TestServer } from "../testing/authFixtures";

/** The Contact form of both apps (POST /support/message): public, with a hidden field for bots and tight limits. */
describe("the support form", () => {
  let server: TestServer;
  let sent: OutgoingEmail[];

  const valid = { name: "Ana Pérez", email: "ana@example.com", message: "Hello\r\nI cannot sign in.", app: "player", appVersion: "1.0.400", platform: "web" };

  // The limits live in the router, so each test gets a new server and starts with its own limits.
  beforeEach(async () => {
    sent = [];
    setMailTransportForTests(async (email) => { sent.push(email); });
    setSupportRecipientForTests("support@mts.test");
    server = await startServer();
  });
  afterEach(async () => {
    setMailTransportForTests(null);
    setSupportRecipientForTests(null);
    await server.close();
  });

  it("sends a valid message to the support mailbox, with the sender as the reply address", async () => {
    const response = await server.request("POST", "/support/message", { body: valid });

    assert.equal(response.status, 200);
    assert.deepEqual(response.body, { ok: true });
    assert.equal(sent.length, 1);
    assert.equal(sent[0].to, "support@mts.test");
    assert.equal(sent[0].replyTo, "ana@example.com");
    assert.match(sent[0].subject, /^\[MTS support\] Ana Pérez: Hello I cannot sign in\./);
    assert.match(sent[0].text, /App: player 1\.0\.400 \(web\)/);
  });

  it("answers ok to a bot that filled the hidden field, and sends nothing", async () => {
    const response = await server.request("POST", "/support/message", { body: { ...valid, website: "https://spam.example" } });

    assert.equal(response.status, 200);
    assert.equal(sent.length, 0);
  });

  it("refuses a wrong email and an empty message without sending", async () => {
    const wrongEmail = await server.request("POST", "/support/message", { body: { ...valid, email: "not-an-email" } });
    const empty = await server.request("POST", "/support/message", { body: { ...valid, message: "   " } });

    assert.equal(wrongEmail.status, 400);
    assert.equal(empty.status, 400);
    assert.equal(sent.length, 0);
  });

  it("says that support is not available when the server has no support address", async () => {
    setSupportRecipientForTests(null);
    const saved = process.env.MTS_SUPPORT_EMAIL;
    delete process.env.MTS_SUPPORT_EMAIL;
    try {
      const response = await server.request("POST", "/support/message", { body: valid });
      assert.equal(response.status, 503);
      assert.equal(sent.length, 0);
    } finally {
      if (saved !== undefined) process.env.MTS_SUPPORT_EMAIL = saved;
    }
  });

  it("takes 3 messages from one address in 15 minutes and refuses the 4th", async () => {
    for (let i = 0; i < 3; i++) {
      const response = await server.request("POST", "/support/message", { body: { ...valid, message: `Message ${i}` } });
      assert.equal(response.status, 200);
    }
    const fourth = await server.request("POST", "/support/message", { body: valid });

    assert.equal(fourth.status, 429);
    assert.equal(sent.length, 3);
  });
});
