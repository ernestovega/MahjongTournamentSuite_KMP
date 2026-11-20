// The invitations of a game day (invitations.mjs): new players open their email, players of earlier game days sign in
// with their account. Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";

import { Api, Metrics } from "../api.mjs";
import { openInvitations } from "../invitations.mjs";
import { withFetch } from "./support.mjs";

const link = (code) => `https://mts.example.test/__/auth/action?mode=resetPassword&oobCode=${code}&apiKey=key`;

/** A mailbox with one email of each address, from [at]. */
function fakeMailbox(emails) {
  return async () => ({
    search: async (to) => (emails.has(to) ? [to] : []),
    read: async (to) => ({ subject: "Reset", date: emails.get(to).at, links: [link(emails.get(to).code)] }),
    close: () => {},
  });
}

test("a new player opens the invitation, and a returning player signs in or asks for a new password", async () => {
  const plan = {
    slots: [
      { slot: 1, email: "new@example.test" },
      { slot: 2, email: "back@example.test", returning: true },
      { slot: 3, email: "forgot@example.test", returning: true },
    ],
  };
  const now = new Date().toUTCString();
  const emails = new Map([
    ["new@example.test", { code: "new", at: now }],
    ["forgot@example.test", { code: "forgot", at: now }],
  ]);
  const calls = [];
  const results = await withFetch(async (method, path, body) => {
    calls.push(`${method} ${path} ${body?.email ?? body?.oobCode ?? ""}`);
    if (path === "/auth/signIn") return body.email === "back@example.test" ? [200, { idToken: "t", refreshToken: "r" }] : [400, { message: "INVALID_LOGIN_CREDENTIALS" }];
    if (path === "/auth/passwordReset") return [200, {}];
    if (path.startsWith("/v1/accounts:resetPassword")) return [200, {}];
    return [404, {}];
  }, () => openInvitations({
    plan, slots: [1, 2, 3], password: "secret", minutes: 1,
    api: new Api("https://api.example.test/api", new Metrics()), pause: async () => {}, openMailbox: fakeMailbox(emails),
  }));
  assert.deepEqual(results.map((result) => `${result.slot} ${result.outcome}`), ["1 PASSWORD_SET", "2 SIGNED_IN", "3 PASSWORD_SET"]);
  // The new player gets no sign-in or Forgot password: the invitation is the email.
  assert.ok(!calls.some((call) => call.endsWith("new@example.test")));
  assert.ok(calls.includes("POST /auth/passwordReset forgot@example.test"));
});

test("a returning player skips an email of the earlier game day and waits for the new one", async () => {
  const plan = { slots: [{ slot: 1, email: "forgot@example.test", returning: true }] };
  const emails = new Map([["forgot@example.test", { code: "old", at: new Date(Date.now() - 3600_000).toUTCString() }]]);
  const results = await withFetch(async (method, path) => {
    if (path === "/auth/signIn") return [400, {}];
    if (path === "/auth/passwordReset") return [200, {}];
    return [200, {}];
  }, () => openInvitations({
    plan, slots: [1], password: "secret", minutes: 0.001,
    api: new Api("https://api.example.test/api", new Metrics()), pause: async () => {}, openMailbox: fakeMailbox(emails),
  }));
  assert.deepEqual(results.map((result) => result.outcome), ["NO_EMAIL"]);
});

test("a returning player taps Forgot password again when the first answer is a network or server failure", async () => {
  const plan = { slots: [{ slot: 1, email: "forgot@example.test", returning: true }] };
  const emails = new Map([["forgot@example.test", { code: "new", at: new Date(Date.now() + 1000).toUTCString() }]]);
  let resets = 0;
  const results = await withFetch(async (method, path) => {
    if (path === "/auth/signIn") return [400, {}];
    if (path === "/auth/passwordReset") return (resets += 1) === 1 ? [503, {}] : [200, {}];
    return [200, {}];
  }, () => openInvitations({
    plan, slots: [1], password: "secret", minutes: 1,
    api: new Api("https://api.example.test/api", new Metrics()), pause: async () => {}, openMailbox: fakeMailbox(emails),
  }));
  assert.equal(resets, 2);
  assert.deepEqual(results.map((result) => result.outcome), ["PASSWORD_SET"]);
});
