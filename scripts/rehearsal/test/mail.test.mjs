// The mailbox of the game day (mail.mjs): how long it is tried when the mail host does not answer, and the links of
// an invitation. Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";

import { Mailbox, OPEN_ATTEMPTS, oobCode, openRetryDelayMs, resetLink } from "../mail.mjs";

test("a mailbox that does not open is tried for more than 8 minutes, as the host was once down for more than 3", async () => {
  const waits = [];
  const mailbox = new Mailbox({ user: "u", password: "p", pause: async (ms) => waits.push(ms) });
  let tries = 0;
  mailbox.connect = async () => {
    tries += 1;
    throw new Error("timeout");
  };
  const log = console.log;
  console.log = () => {};
  try {
    await assert.rejects(mailbox.open(), /timeout/);
  } finally {
    console.log = log;
  }
  assert.equal(tries, OPEN_ATTEMPTS);
  assert.deepEqual(waits, Array.from({ length: OPEN_ATTEMPTS - 1 }, (_, index) => openRetryDelayMs(index + 1)));
  // 8.5 minutes of waits, and each try waits up to 30 s for the host: about 12 minutes in all.
  const minutes = waits.reduce((sum, ms) => sum + ms, 0) / 60_000;
  assert.equal(minutes, 8.5);
  assert.equal(Math.max(...waits), 60_000);
});

test("a mailbox that opens on the third try is used", async () => {
  const mailbox = new Mailbox({ user: "u", password: "p", pause: async () => {} });
  let tries = 0;
  mailbox.connect = async function connect() {
    tries += 1;
    if (tries < 3) throw new Error("refused");
    return this;
  };
  const log = console.log;
  console.log = () => {};
  try {
    assert.equal(await mailbox.open(), mailbox);
  } finally {
    console.log = log;
  }
  assert.equal(tries, 3);
});

test("the link of an invitation and its code", () => {
  const link = "https://mts---staging.firebaseapp.com/__/auth/action?mode=resetPassword&oobCode=ABC123&apiKey=k";
  assert.equal(resetLink({ links: ["https://example.test/other", link] }), link);
  assert.equal(resetLink({ links: ["https://example.test/other"] }), null);
  assert.equal(oobCode(link), "ABC123");
});
