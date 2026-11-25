import assert from "node:assert/strict";
import { afterEach, beforeEach, describe, it } from "node:test";

import { refreshIdToken, sendPasswordResetEmail, setPasswordResetSenderForTests, signInWithEmailPassword } from "./firebaseAuthRest";

type Call = { url: string; body: string; type: string };

/** Replaces fetch: each call is kept, and [answer] gives the status and the JSON of the answer. */
function fakeFetch(answer: (call: Call) => [number, unknown]): Call[] {
  const calls: Call[] = [];
  globalThis.fetch = (async (url: string, init: RequestInit) => {
    const call = { url: String(url), body: String(init.body), type: String((init.headers as Record<string, string>)["content-type"]) };
    calls.push(call);
    const [status, json] = answer(call);
    return new Response(JSON.stringify(json), { status });
  }) as typeof fetch;
  return calls;
}

describe("the Auth REST API of Google", () => {
  const original = { fetch: globalThis.fetch, key: process.env.MTS_ID_TOOLKIT_API_KEY, emulator: process.env.FIREBASE_AUTH_EMULATOR_HOST, error: console.error };
  const logged: string[] = [];

  beforeEach(() => {
    process.env.MTS_ID_TOOLKIT_API_KEY = "test-key";
    delete process.env.FIREBASE_AUTH_EMULATOR_HOST;
    logged.length = 0;
    console.error = (...args: unknown[]) => { logged.push(args.map(String).join(" ")); };
  });
  afterEach(() => {
    globalThis.fetch = original.fetch;
    console.error = original.error;
    setPasswordResetSenderForTests(null);
    for (const [name, value] of [["MTS_ID_TOOLKIT_API_KEY", original.key], ["FIREBASE_AUTH_EMULATOR_HOST", original.emulator]] as const) {
      if (value == null) delete process.env[name];
      else process.env[name] = value;
    }
  });

  it("signs in with the password and gives the tokens and the uid", async () => {
    const calls = fakeFetch(() => [200, { idToken: "id", refreshToken: "refresh", localId: "u1" }]);

    assert.deepEqual(await signInWithEmailPassword("ana@example.test", "secret"), { idToken: "id", refreshToken: "refresh", uid: "u1" });
    assert.equal(calls[0].url, "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=test-key");
    assert.deepEqual(JSON.parse(calls[0].body), { email: "ana@example.test", password: "secret", returnSecureToken: true });
  });

  it("a refused sign-in gives the message of Google and logs the answer without the address", async () => {
    fakeFetch(() => [400, { error: { message: "INVALID_LOGIN_CREDENTIALS", errors: [{ message: "ana@example.test is wrong" }] } }]);

    await assert.rejects(signInWithEmailPassword("ana@example.test", "bad"), /INVALID_LOGIN_CREDENTIALS/);
    assert.equal(logged.length, 1);
    assert.ok(!logged[0].includes("ana@example.test"), logged[0]);
    assert.ok(logged[0].includes("<email>"));
  });

  it("an answer without a message gives the status", async () => {
    fakeFetch(() => [503, {}]);
    await assert.rejects(signInWithEmailPassword("a@b.c", "x"), /HTTP 503/);
  });

  it("uses the Auth emulator when a local run sets it", async () => {
    process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
    const calls = fakeFetch(() => [200, { idToken: "id", refreshToken: "r", localId: "u" }]);
    await signInWithEmailPassword("a@b.c", "x");
    assert.ok(calls[0].url.startsWith("http://127.0.0.1:9099/identitytoolkit.googleapis.com/"));
  });

  it("refuses to call Google without the API key", async () => {
    delete process.env.MTS_ID_TOOLKIT_API_KEY;
    fakeFetch(() => [200, {}]);
    await assert.rejects(signInWithEmailPassword("a@b.c", "x"), /MTS_ID_TOOLKIT_API_KEY/);
  });

  it("refreshes the token with a form, and refuses an answer without tokens", async () => {
    const calls = fakeFetch(() => [200, { id_token: "new-id", refresh_token: "new-refresh" }]);
    assert.deepEqual(await refreshIdToken("old"), { idToken: "new-id", refreshToken: "new-refresh" });
    assert.equal(calls[0].url, "https://securetoken.googleapis.com/v1/token?key=test-key");
    assert.equal(calls[0].type, "application/x-www-form-urlencoded");
    assert.equal(calls[0].body, "grant_type=refresh_token&refresh_token=old");

    fakeFetch(() => [200, { id_token: "only-id" }]);
    await assert.rejects(refreshIdToken("old"), /Failed to refresh token/);
    fakeFetch(() => [400, { error: "TOKEN_EXPIRED" }]);
    await assert.rejects(refreshIdToken("old"), /Failed to refresh token/);
  });

  it("sends the password reset email through Google, or through the sender of the tests", async () => {
    const calls = fakeFetch(() => [200, { email: "a@b.c" }]);
    await sendPasswordResetEmail("a@b.c");
    assert.equal(calls[0].url, "https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=test-key");
    assert.deepEqual(JSON.parse(calls[0].body), { requestType: "PASSWORD_RESET", email: "a@b.c" });

    const sent: string[] = [];
    setPasswordResetSenderForTests(async (email) => { sent.push(email); });
    await sendPasswordResetEmail("b@c.d");
    assert.deepEqual(sent, ["b@c.d"]);
    assert.equal(calls.length, 1, "the sender of the tests replaces Google");
  });
});
