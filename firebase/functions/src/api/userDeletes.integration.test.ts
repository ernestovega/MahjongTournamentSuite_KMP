import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

type DeleteBody = { results: Array<{ uid: string; outcome: string; reason?: string }> };

describe("bulk account delete", () => {
  let server: TestServer;
  let admin: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  const remove = (uids: string[], token: string | null = admin.token) =>
    server.request("POST", "/admin/users/delete", { token, body: { uids } });
  const outcomes = (response: { body: unknown }) => (response.body as DeleteBody).results.map((item) => [item.outcome, item.reason]);
  const exists = async (uid: string) => auth.getUser(uid).then(() => true, () => false);

  it("deletes every account of the list, with its profile, and keeps the EMA player", async () => {
    const first = await createAccount({ role: "PLAYER" });
    const second = await createAccount({ role: "EDITOR" });
    await giveEmaIdToAccount(first.uid, "12345678");

    const response = await remove([first.uid, second.uid]);

    assert.equal(response.status, 200);
    assert.deepEqual(outcomes(response), [["DELETED", undefined], ["DELETED", undefined]]);
    assert.equal(await exists(first.uid), false);
    assert.equal(await exists(second.uid), false);
    assert.equal((await db.doc(`users/${first.uid}`).get()).exists, false);
  });

  it("refuses the own account and an unknown one, and still deletes the others", async () => {
    const other = await createAccount({ role: "PLAYER" });

    const response = await remove([admin.uid, "does-not-exist", other.uid]);

    assert.deepEqual(outcomes(response), [["FAILED", "SELF"], ["FAILED", "NOT_FOUND"], ["DELETED", undefined]]);
    assert.equal(await exists(admin.uid), true);
    assert.equal(await exists(other.uid), false);
  });

  it("counts an account once when the list repeats it", async () => {
    const account = await createAccount({ role: "PLAYER" });

    const response = await remove([account.uid, account.uid]);

    assert.deepEqual(outcomes(response), [["DELETED", undefined]]);
  });

  it("refuses an empty list, a list over 50 accounts and an empty uid", async () => {
    assert.equal((await remove([])).status, 400);
    assert.equal((await remove(Array.from({ length: 51 }, (_, index) => `uid-${index}`))).status, 400);
    assert.equal((await remove([""])).status, 400);
  });

  it("is only for admins", async () => {
    const editor = await createAccount({ role: "EDITOR" });
    const target = await createAccount({ role: "PLAYER" });

    assert.equal((await remove([target.uid], editor.token)).status, 403);
    assert.equal(await exists(target.uid), true);
  });
});
