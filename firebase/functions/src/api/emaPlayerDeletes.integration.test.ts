import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";

type DeleteBody = { results: Array<{ emaId: string; outcome: string; reason?: string }> };
type PreviewBody = { players: Array<{ emaId: string; tournaments: Array<{ id: string; name: string }> }> };

describe("bulk delete of EMA players", () => {
  let server: TestServer;
  let admin: TestAccount;
  let next = 92_000_000;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  const remove = (emaIds: string[], token: string | null = admin.token) =>
    server.request("POST", "/ema-player-registry/delete", { token, body: { emaIds } });
  const preview = (emaIds: string[], token: string | null = admin.token) =>
    server.request("POST", "/ema-player-registry/delete/preview", { token, body: { emaIds } });
  const outcomes = (response: { body: unknown }) => (response.body as DeleteBody).results.map((item) => [item.outcome, item.reason]);
  const registry = (emaId: string) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId);
  const newPlayer = async () => {
    const emaId = String(next++);
    await registry(emaId).set({ firstName: "Test", lastName: emaId, country: "ESP" });
    return emaId;
  };
  const accountExists = async (uid: string) => auth.getUser(uid).then(() => true, () => false);

  it("deletes a player without tournament data and keeps its account, without the EMA number", async () => {
    const emaId = await newPlayer();
    const account = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(account.uid, emaId);

    const response = await remove([emaId]);

    assert.equal(response.status, 200);
    assert.deepEqual(outcomes(response), [["DELETED", undefined]]);
    assert.equal((await registry(emaId).get()).exists, false);
    assert.equal(await accountExists(account.uid), true);
    assert.equal((await db.doc(`emaIdIndex/${emaId}`).get()).exists, false);
    assert.equal((await db.doc(`users/${account.uid}`).get()).get("emaId"), undefined);
  });

  it("deactivates a player with tournament data instead of deleting it, and keeps its account linked", async () => {
    const emaId = await newPlayer();
    await db.doc("tournaments/ema-delete-tournament").set({ name: "Open de Madrid" });
    await seatEmaId("ema-delete-tournament", 1, emaId);
    const account = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(account.uid, emaId);

    const listed = await preview([emaId]);
    assert.equal(listed.status, 200);
    assert.deepEqual((listed.body as PreviewBody).players, [
      { emaId, tournaments: [{ id: "ema-delete-tournament", name: "Open de Madrid" }] },
    ]);

    const response = await remove([emaId]);

    assert.deepEqual(outcomes(response), [["DISABLED", undefined]]);
    const stored = await registry(emaId).get();
    assert.equal(stored.exists, true);
    assert.equal(stored.get("disabled"), true);
    assert.equal(await accountExists(account.uid), true);
    assert.equal((await db.doc(`emaIdIndex/${emaId}`).get()).get("uid"), account.uid);
    const players = (await server.request("GET", "/ema-player-registry/", { token: admin.token })).body as { players: Array<{ emaId: string; disabled: boolean }> };
    assert.equal(players.players.find((player) => player.emaId === emaId)?.disabled, true);
  });

  it("reactivates a deactivated player when an admin saves it with disabled false", async () => {
    const emaId = await newPlayer();
    await registry(emaId).update({ disabled: true });

    const response = await server.request("PUT", `/ema-player-registry/${emaId}`, {
      token: admin.token, body: { emaId, firstName: "Test", lastName: emaId, country: "ESP", disabled: false },
    });

    assert.equal(response.status, 200);
    assert.equal((await registry(emaId).get()).get("disabled"), false);
  });

  it("leaves out of the preview the players without data, and says when a player does not exist", async () => {
    const free = await newPlayer();
    assert.deepEqual(((await preview([free])).body as PreviewBody).players, []);
    assert.deepEqual(outcomes(await remove(["99999998", free])), [["FAILED", "NOT_FOUND"], ["DELETED", undefined]]);
  });

  it("refuses an empty list, more than 50 players and an EMA number that is not a number", async () => {
    assert.equal((await remove([])).status, 400);
    assert.equal((await remove(Array.from({ length: 51 }, (_, index) => String(93_000_000 + index)))).status, 400);
    assert.equal((await remove(["abc"])).status, 400);
    assert.equal((await preview([])).status, 400);
  });

  it("is only for admins", async () => {
    const editor = await createAccount({ role: "EDITOR" });
    const player = await newPlayer();

    assert.equal((await remove([player], editor.token)).status, 403);
    assert.equal((await preview([player], editor.token)).status, 403);
    assert.equal((await registry(player).get()).exists, true);
  });
});
