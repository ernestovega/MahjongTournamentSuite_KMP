import assert from "node:assert/strict";
import { after, before, describe, it, mock } from "node:test";

import { auth, db } from "../firebase";
import * as accountDeletes from "../services/accountDeletes";
import * as accountRequests from "../services/accountRequests";
import * as activityFeed from "../services/activityFeed";
import * as customPermissions from "../services/customPermissions";
import * as firebaseAuthRest from "../services/firebaseAuthRest";
import * as organizationsService from "../services/organizationsService";
import * as usersService from "../services/usersService";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

const broken = () => {
  throw new Error("store unavailable");
};

/**
 * The admin routes when a store fails under them: each answers with an error and leaves nothing behind. Also the
 * requests that send no email, no alias or empty list items, which fall back to their defaults.
 */
describe("admin routes when a store fails", () => {
  let server: TestServer;
  let admin: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  it("answers each read with an error while its store fails", async () => {
    const reads: Array<[string, object, string]> = [
      ["/admin/activity", activityFeed, "listActivityFeed"],
      ["/admin/users", usersService, "listManagedUsers"],
      ["/admin/account-requests", accountRequests, "listPendingAccountRequests"],
      ["/admin/organizations", organizationsService, "listOrganizations"],
      ["/admin/roles", customPermissions, "listCustomRoles"],
      // Last: the earlier requests have already checked the token, so only the route reads the account.
      ["/admin/whoami", auth, "getUser"],
    ];
    const wrong: string[] = [];
    for (const [path, owner, name] of reads) {
      const real = (owner as Record<string, (...args: unknown[]) => unknown>)[name].bind(owner);
      // The token check reads the account too: only the route meets the failure.
      const failing = mock.method(owner as Record<string, (...args: unknown[]) => unknown>, name, (...args: unknown[]) =>
        (new Error().stack ?? "").includes("admin.routes") ? broken() : real(...args));
      try {
        const response = await server.request("GET", path, { token: admin.token });
        if (response.status !== 500) wrong.push(`${path}: ${response.status} ${JSON.stringify(response.body)}`);
      } finally {
        failing.mock.restore();
      }
    }
    assert.deepEqual(wrong, []);
  });

  it("removes a new account again when its welcome email fails", async () => {
    const email = `welcome-${Date.now()}@example.test`;
    const emaId = `0${String(Date.now()).slice(-7)}`;
    await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Welcome", lastName: "Failed" });
    const failing = mock.method(firebaseAuthRest, "sendPasswordResetEmail", broken);
    try {
      const response = await server.request("POST", "/admin/users", { token: admin.token, body: { email, role: "PLAYER", emaId } });
      assert.equal(response.status, 500, JSON.stringify(response.body));
    } finally {
      failing.mock.restore();
    }
    await assert.rejects(auth.getUserByEmail(email), /no user record/i);
  });

  it("keeps the email of an account when a change sends none, and refuses empty list items", async () => {
    const editor = await createAccount({ role: "SUPER_ADMIN" });
    const changed = await server.request("PUT", `/admin/users/${editor.uid}`, { token: admin.token, body: { role: "SUPER_ADMIN" } });
    assert.equal(changed.status, 200, JSON.stringify(changed.body));
    assert.equal((changed.body as { email: string }).email, editor.email);

    const resets = await server.request("POST", "/admin/users/password-resets", { token: admin.token, body: { uids: [null] } });
    assert.equal(resets.status, 400);
    const deletes = await server.request("POST", "/admin/users/delete", { token: admin.token, body: { uids: [null] } });
    assert.equal(deletes.status, 400);
  });

  it("enables an account again, and counts a failed delete as unknown", async () => {
    const other = await createAccount({ role: "PLAYER", disabled: true });
    const enabled = await server.request("PUT", `/admin/users/${other.uid}/disabled`, { token: admin.token, body: { disabled: false } });
    assert.equal(enabled.status, 200, JSON.stringify(enabled.body));
    assert.equal((await auth.getUser(other.uid)).disabled, false);

    const failing = mock.method(accountDeletes, "deleteAccountChecked", broken);
    try {
      const response = await server.request("POST", "/admin/users/delete", { token: admin.token, body: { uids: [other.uid] } });
      assert.equal(response.status, 200, JSON.stringify(response.body));
      assert.deepEqual((response.body as { results: unknown[] }).results, [{ uid: other.uid, outcome: "FAILED", reason: "UNKNOWN" }]);
    } finally {
      failing.mock.restore();
    }
  });

  it("refuses to disable the last enabled super admin", async () => {
    const other = await createAccount({ role: "SUPER_ADMIN" });
    const failing = mock.method(usersService, "countEnabledSuperAdmins", async () => 1);
    try {
      const response = await server.request("PUT", `/admin/users/${other.uid}/disabled`, { token: admin.token, body: { disabled: true } });
      assert.equal(response.status, 400, JSON.stringify(response.body));
      assert.match(JSON.stringify(response.body), /last enabled super admin/);
    } finally {
      failing.mock.restore();
    }
  });
});
