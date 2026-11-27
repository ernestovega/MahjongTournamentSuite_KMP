import assert from "node:assert/strict";
import { describe, it, mock } from "node:test";

import { HttpError } from "../api/httpError";
import { setMailTransportForTests } from "./mailService";
import { buildSupportEmail, readSupportMessage, sendSupportMessage, SUPPORT_LIMITS } from "./supportService";

const valid = { name: "Ana", email: "ana@example.com", message: "I cannot sign in.", app: "player", appVersion: "1.2.3", platform: "Android" };

describe("readSupportMessage", () => {
  it("reads a valid message", () => {
    assert.deepEqual(readSupportMessage(valid), valid);
  });

  it("returns null when the hidden field is filled, so a bot gets no email sent", () => {
    assert.equal(readSupportMessage({ ...valid, website: "http://spam.example" }), null);
  });

  it("refuses a missing or wrong reply email, and an empty or too long message", () => {
    for (const body of [
      { ...valid, email: "" },
      { ...valid, email: "not-an-email" },
      { ...valid, email: "a@b.com\nBcc: x@y.com" },
      { ...valid, message: "   " },
      { ...valid, message: "x".repeat(SUPPORT_LIMITS.message + 1) },
    ]) {
      assert.throws(() => readSupportMessage(body), (error: unknown) => error instanceof HttpError && error.status === 400);
    }
  });

  it("keeps the name on one line and fills unknown app details", () => {
    const message = readSupportMessage({ email: "ana@example.com", message: "Hi", name: "Ana\r\nBcc: x@y.com" });
    assert.equal(message?.name, "Ana Bcc: x@y.com");
    assert.equal(message?.app, "unknown");
  });
});

describe("buildSupportEmail", () => {
  it("answers to the sender and escapes the html", () => {
    const email = buildSupportEmail({ ...valid, message: "<b>help</b>" }, "support@example.com");
    assert.equal(email.to, "support@example.com");
    assert.equal(email.replyTo, "ana@example.com");
    assert.match(email.subject, /^\[MTS support\] Ana: <b>help<\/b>$/);
    assert.match(email.html, /&lt;b&gt;help&lt;\/b&gt;/);
    assert.match(email.text, /App: player 1\.2\.3 \(Android\)/);
  });

  it("writes the email of a message without a name, and reads the support address from the secret", async () => {
    const email = buildSupportEmail({ ...valid, name: "" }, "support@example.com");
    assert.match(email.subject, /^\[MTS support\] ana@example\.com: /);
    assert.match(email.text, /^From: ana@example\.com\n/);

    const sent: string[] = [];
    setMailTransportForTests(async (outgoing) => { sent.push(outgoing.to); });
    const before = process.env.MTS_SUPPORT_EMAIL;
    try {
      process.env.MTS_SUPPORT_EMAIL = " desk@example.com ";
      await sendSupportMessage(valid);
      delete process.env.MTS_SUPPORT_EMAIL;
      const quiet = mock.method(console, "error", () => {});
      try {
        await assert.rejects(sendSupportMessage(valid), (error: unknown) => error instanceof HttpError && error.status === 503);
      } finally {
        quiet.mock.restore();
      }
    } finally {
      if (before === undefined) delete process.env.MTS_SUPPORT_EMAIL; else process.env.MTS_SUPPORT_EMAIL = before;
      setMailTransportForTests(null);
    }
    assert.deepEqual(sent, ["desk@example.com"]);
  });
});
