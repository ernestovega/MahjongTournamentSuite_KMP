import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { ACTIVITY_COLLECTION } from "../services/activityLog";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestServer } from "../testing/authFixtures";

/** What an account does for itself: sign in, refresh the session and delete the own account. */
describe("the own account", () => {
  let server: TestServer;

  before(async () => {
    requireAuthEmulator();
    // The sign-in goes through the Auth REST API of the emulator, which takes any key.
    process.env.MTS_ID_TOOLKIT_API_KEY ??= "test-key";
    server = await startServer();
  });
  after(async () => { await server.close(); });

  it("signs in with email and password and refreshes the session with the refresh token", async () => {
    const account = await createAccount({ role: "PLAYER" });

    const signIn = await server.request("POST", "/auth/signIn", { body: { email: account.email, password: "Passw0rd!-test" } });
    assert.equal(signIn.status, 200);
    const tokens = signIn.body as { idToken: string; refreshToken: string; uid: string };
    assert.equal(tokens.uid, account.uid);
    assert.ok(tokens.idToken.length > 0 && tokens.refreshToken.length > 0);

    const refresh = await server.request("POST", "/auth/refresh", { body: { refreshToken: tokens.refreshToken } });
    assert.equal(refresh.status, 200);
    const fresh = (refresh.body as { idToken: string }).idToken;
    // The new token works on a route that needs an account.
    assert.equal((await server.request("GET", "/auth/me", { token: fresh })).status, 200);
  });

  it("refuses a wrong password and an unknown email with the same answer, and a request without data", async () => {
    const account = await createAccount({ role: "PLAYER" });

    const wrong = await server.request("POST", "/auth/signIn", { body: { email: account.email, password: "nope-nope-1" } });
    const unknown = await server.request("POST", "/auth/signIn", { body: { email: "nobody-here@example.test", password: "nope-nope-1" } });
    assert.equal(wrong.status, 401);
    assert.equal(unknown.status, 401);
    assert.deepEqual(wrong.body, unknown.body);
    assert.equal((await server.request("POST", "/auth/signIn", { body: { email: account.email } })).status, 400);
    assert.equal((await server.request("POST", "/auth/refresh", { body: {} })).status, 400);
  });

  it("refuses the sign-in of a disabled account", async () => {
    const account = await createAccount({ role: "EDITOR", disabled: true });

    const response = await server.request("POST", "/auth/signIn", { body: { email: account.email, password: "Passw0rd!-test" } });
    assert.equal(response.status, 403);
  });

  it("deletes the own account and its profile, keeps the EMA player and writes the activity", async () => {
    const account = await createAccount({ role: "PLAYER" });
    const emaId = String(20_000_000 + Math.floor(Math.random() * 9_000_000));
    await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Own", lastName: "Delete", country: "ES" });
    await giveEmaIdToAccount(account.uid, emaId);

    const response = await server.request("DELETE", "/auth/me", { token: account.token });

    assert.equal(response.status, 200);
    await assert.rejects(auth.getUser(account.uid));
    assert.equal((await db.doc(`users/${account.uid}`).get()).exists, false);
    // The person keeps the results: the EMA player stays, without the account.
    const player = await db.doc(`emaPlayerRegistry/${emaId}`).get();
    assert.equal(player.exists, true);
    const activity = await db.collection(ACTIVITY_COLLECTION).where("target", "==", `user ${account.uid}`).get();
    assert.equal(activity.size, 1);
    assert.equal(activity.docs[0].get("action"), "user.delete");
    assert.deepEqual(activity.docs[0].get("details"), { own: true });
  });
});
