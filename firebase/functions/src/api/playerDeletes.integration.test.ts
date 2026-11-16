import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "../services/playersService";

type DeleteBody = { results: Array<{ emaId: string; outcome: string; reason?: string }> };

describe("bulk EMA player delete", () => {
  let server: TestServer;
  let admin: TestAccount;
  let next = 91_000_000;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  const remove = (emaIds: string[], token: string | null = admin.token) =>
    server.request("POST", "/ema-player-registry/delete", { token, body: { emaIds } });
  const outcomes = (response: { body: unknown }) => (response.body as DeleteBody).results.map((item) => [item.outcome, item.reason]);
  const registry = (emaId: string) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId);
  const newPlayer = async () => {
    const emaId = String(next++);
    await registry(emaId).set({ firstName: "Test", lastName: emaId, country: "ESP" });
    return emaId;
  };
  const exists = async (emaId: string) => (await registry(emaId).get()).exists;

  it("deletes the players that nobody uses", async () => {
    const first = await newPlayer();
    const second = await newPlayer();

    const response = await remove([first, second]);

    assert.equal(response.status, 200);
    assert.deepEqual(outcomes(response), [["DELETED", undefined], ["DELETED", undefined]]);
    assert.equal(await exists(first), false);
    assert.equal(await exists(second), false);
  });

  it("keeps a player of a tournament, a player of an account and an unknown one, and still deletes the others", async () => {
    const inTournament = await newPlayer();
    const withAccount = await newPlayer();
    const free = await newPlayer();
    await seatEmaId("bulk-delete-tournament", 1, inTournament);
    const account = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(account.uid, withAccount);

    const response = await remove([inTournament, withAccount, "99999999", free]);

    assert.deepEqual(outcomes(response), [
      ["FAILED", "IN_TOURNAMENT"], ["FAILED", "HAS_ACCOUNT"], ["FAILED", "NOT_FOUND"], ["DELETED", undefined],
    ]);
    assert.equal(await exists(inTournament), true);
    assert.equal(await exists(withAccount), true);
    assert.equal(await exists(free), false);
  });

  it("refuses an empty list and an EMA number that is not a number", async () => {
    assert.equal((await remove([])).status, 400);
    assert.equal((await remove(["abc"])).status, 400);
  });

  it("is only for admins", async () => {
    const editor = await createAccount({ role: "EDITOR" });
    const player = await newPlayer();

    assert.equal((await remove([player], editor.token)).status, 403);
    assert.equal(await exists(player), true);
  });
});
