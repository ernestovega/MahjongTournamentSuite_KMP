import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";

type DeleteBody = { results: Array<{ emaId: string; outcome: string; reason?: string }> };

describe("bulk delete of the accounts of EMA players", () => {
  let server: TestServer;
  let admin: TestAccount;
  let next = 92_000_000;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  const remove = (emaIds: string[], token: string | null = admin.token) =>
    server.request("POST", "/ema-player-registry/accounts/delete", { token, body: { emaIds } });
  const outcomes = (response: { body: unknown }) => (response.body as DeleteBody).results.map((item) => [item.outcome, item.reason]);
  const registry = (emaId: string) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId);
  const newPlayer = async () => {
    const emaId = String(next++);
    await registry(emaId).set({ firstName: "Test", lastName: emaId, country: "ESP" });
    return emaId;
  };
  const accountExists = async (uid: string) => auth.getUser(uid).then(() => true, () => false);

  it("deletes the account of each player, frees the EMA number, and keeps every EMA player", async () => {
    const inTournament = await newPlayer();
    const free = await newPlayer();
    await db.doc("tournaments/account-delete-tournament").set({ name: "Account delete" });
    await seatEmaId("account-delete-tournament", 1, inTournament);
    const first = await createAccount({ role: "PLAYER" });
    const second = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(first.uid, inTournament);
    await giveEmaIdToAccount(second.uid, free);

    const response = await remove([inTournament, free]);

    assert.equal(response.status, 200);
    assert.deepEqual(outcomes(response), [["DELETED", undefined], ["DELETED", undefined]]);
    assert.equal(await accountExists(first.uid), false);
    assert.equal(await accountExists(second.uid), false);
    assert.equal((await db.doc(`emaIdIndex/${inTournament}`).get()).exists, false);
    assert.equal((await registry(inTournament).get()).exists, true);
    assert.equal((await registry(free).get()).exists, true);
  });

  it("says when a player has no account or the account is the own one, and still deletes the others", async () => {
    const noAccount = await newPlayer();
    const own = await newPlayer();
    const other = await newPlayer();
    await giveEmaIdToAccount(admin.uid, own);
    const account = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(account.uid, other);

    const response = await remove([noAccount, own, other]);

    assert.deepEqual(outcomes(response), [["FAILED", "NO_ACCOUNT"], ["FAILED", "SELF"], ["DELETED", undefined]]);
    assert.equal(await accountExists(admin.uid), true);
  });

  it("refuses an empty list, more than 50 players and an EMA number that is not a number", async () => {
    assert.equal((await remove([])).status, 400);
    assert.equal((await remove(Array.from({ length: 51 }, (_, index) => String(93_000_000 + index)))).status, 400);
    assert.equal((await remove(["abc"])).status, 400);
  });

  it("is only for admins", async () => {
    const editor = await createAccount({ role: "EDITOR" });
    const player = await newPlayer();
    const account = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(account.uid, player);

    assert.equal((await remove([player], editor.token)).status, 403);
    assert.equal(await accountExists(account.uid), true);
  });
});
