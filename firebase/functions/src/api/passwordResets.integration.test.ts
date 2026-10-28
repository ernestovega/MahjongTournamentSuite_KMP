import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { setPasswordResetSenderForTests } from "../services/firebaseAuthRest";

type ResetBody = { results: Array<{ uid: string; outcome: string; reason?: string }> };

describe("mass password reset", () => {
  let server: TestServer;
  let admin: TestAccount;
  let editor: TestAccount;
  const resets: string[] = [];

  before(async () => {
    requireAuthEmulator();
    setPasswordResetSenderForTests(async (email) => {
      if (email.startsWith("refused-")) throw new Error("Google API down");
      resets.push(email);
    });
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
    editor = await createAccount({ role: "EDITOR" });
  });
  after(async () => {
    setPasswordResetSenderForTests(null);
    await server.close();
  });

  const send = (uids: string[], token: string | null = admin.token) =>
    server.request("POST", "/admin/users/password-resets", { token, body: { uids } });
  const outcomes = (response: { body: unknown }) => (response.body as ResetBody).results.map((item) => [item.outcome, item.reason]);

  it("sends the password reset email to every account of the list", async () => {
    const first = await createAccount({ role: "PLAYER" });
    const second = await createAccount({ role: "EDITOR" });
    const before = resets.length;

    const response = await send([first.uid, second.uid]);

    assert.equal(response.status, 200);
    assert.deepEqual(outcomes(response), [["SENT", undefined], ["SENT", undefined]]);
    assert.deepEqual(resets.slice(before).sort(), [first.email, second.email].sort());
  });

  it("gives a failure to an unknown, a disabled and an account without email, and still serves the others", async () => {
    const good = await createAccount({ role: "PLAYER" });
    const disabled = await createAccount({ role: "PLAYER", disabled: true });
    const noEmail = await auth.createUser({});
    const before = resets.length;

    const response = await send([good.uid, "does-not-exist", disabled.uid, noEmail.uid]);

    assert.deepEqual(outcomes(response), [["SENT", undefined], ["FAILED", "NOT_FOUND"], ["FAILED", "DISABLED"], ["FAILED", "NO_EMAIL"]]);
    assert.deepEqual(resets.slice(before), [good.email]);
  });

  it("gives EMAIL_FAILED to the account whose email the API refuses, and the others still get the email", async () => {
    const good = await createAccount({ role: "PLAYER" });
    const refused = await auth.createUser({ email: `refused-${Date.now()}@example.test` });

    const response = await send([refused.uid, good.uid]);

    assert.deepEqual(outcomes(response), [["FAILED", "EMAIL_FAILED"], ["SENT", undefined]]);
  });

  it("counts an account once when the list repeats it", async () => {
    const account = await createAccount({ role: "PLAYER" });
    const before = resets.length;

    const response = await send([account.uid, account.uid]);

    assert.equal((response.body as ResetBody).results.length, 1);
    assert.equal(resets.length - before, 1);
  });

  it("refuses an empty list, a list over 200 accounts and an empty uid", async () => {
    assert.equal((await send([])).status, 400);
    assert.equal((await send(Array.from({ length: 201 }, (_, index) => `uid-${index}`))).status, 400);
    assert.equal((await send([""])).status, 400);
  });

  it("is for admins only", async () => {
    const account = await createAccount({ role: "PLAYER" });
    const before = resets.length;

    assert.equal((await send([account.uid], editor.token)).status, 403);
    assert.equal((await send([account.uid], account.token)).status, 403);
    assert.equal((await send([account.uid], null)).status, 401);
    assert.equal(resets.length, before);
  });

  it("records the activity", async () => {
    const account = await createAccount({ role: "PLAYER" });

    await send([account.uid]);

    const log = await db.collection("activity").where("action", "==", "user.passwordReset").get();
    assert.ok(log.size >= 1);
  });
});
