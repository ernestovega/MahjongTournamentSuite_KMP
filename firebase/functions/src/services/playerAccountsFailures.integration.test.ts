import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it, mock } from "node:test";

import { auth, db, storage } from "../firebase";
import { createAccount, requireAuthEmulator } from "../testing/authFixtures";
import { deleteEmaPlayers } from "./emaPlayerDeletes";
import * as playerAccountsService from "./playerAccountsService";
import * as usersService from "./usersService";

const randomEmaId = () => String(10_000_000 + Math.floor(Math.random() * 89_000_000));

async function registryPlayer(emaId: string, data: Record<string, unknown> = {}): Promise<void> {
  await db.doc(`emaPlayerRegistry/${emaId}`).set({ emaId, firstName: "ANA", lastName: "RUIZ", country: "ESP", ...data });
}

/** Player accounts and EMA players when two changes meet or a store fails in the middle. */
describe("player accounts and EMA players that fail", () => {
  before(requireAuthEmulator);

  it("gives an account only one of two EMA ids set at the same time", async () => {
    const account = await createAccount({ role: "PLAYER" });
    const [first, second] = [randomEmaId(), randomEmaId()];
    await registryPlayer(first);
    await registryPlayer(second);
    const outcomes = await Promise.allSettled([
      playerAccountsService.setAccountEmaId({ uid: account.uid, emaId: first }),
      playerAccountsService.setAccountEmaId({ uid: account.uid, emaId: second }),
    ]);
    const refused = outcomes.filter((outcome) => outcome.status === "rejected") as PromiseRejectedResult[];
    assert.equal(refused.length, 1, JSON.stringify(outcomes));
    assert.equal((refused[0].reason as { details?: { code?: string } }).details?.code, "ACCOUNT_HAS_OTHER_EMA");
    const kept = (await db.doc(`users/${account.uid}`).get()).get("emaId");
    assert.ok(kept === first || kept === second);
  });

  it("removes the new account again when its profile cannot be written", async () => {
    const emaId = randomEmaId();
    await registryPlayer(emaId);
    const email = `${randomUUID()}@example.test`;
    const failing = mock.method(usersService, "createUserProfile", async () => {
      throw new Error("store unavailable");
    });
    try {
      await assert.rejects(playerAccountsService.createPlayerAccount({ email, emaId }), /store unavailable/);
    } finally {
      failing.mock.restore();
    }
    await assert.rejects(auth.getUserByEmail(email), (error: { code?: string }) => error.code === "auth/user-not-found");
    assert.equal((await db.doc(`emaIdIndex/${emaId}`).get()).exists, false);
  });

  it("deletes the photo of a deleted EMA player, and reports a player whose delete fails", async () => {
    const [withPhoto, broken] = [randomEmaId(), randomEmaId()];
    await registryPlayer(withPhoto, { photoUrl: "https://example.test/photo.jpg" });
    await registryPlayer(broken);
    await storage.file(`playerPhotos/${withPhoto}.jpg`).save(Buffer.from("jpeg"), { contentType: "image/jpeg" });
    const quiet = mock.method(console, "error", () => {});
    const real = playerAccountsService.tournamentIdsOfEmaId;
    const lookup = mock.method(playerAccountsService, "tournamentIdsOfEmaId", async (emaId: string) => {
      if (emaId === broken) throw new Error("store unavailable");
      return real(emaId);
    });
    try {
      assert.deepEqual(await deleteEmaPlayers([withPhoto, broken]), [
        { emaId: withPhoto, outcome: "DELETED" },
        { emaId: broken, outcome: "FAILED", reason: "UNKNOWN" },
      ]);
    } finally {
      lookup.mock.restore();
      quiet.mock.restore();
    }
    assert.equal((await storage.file(`playerPhotos/${withPhoto}.jpg`).exists())[0], false);
  });

  it("reads an account without an email", () => {
    assert.deepEqual(playerAccountsService.accountInfoOf({ uid: "u", providerData: [] }), { uid: "u", email: null, hasPassword: false });
  });

  it("finds the owners of emails that have no EMA id or an empty one", async () => {
    const account = await createAccount({ role: "EDITOR" });
    const blank = await createAccount({ role: "EDITOR" });
    await db.doc(`users/${blank.uid}`).set({ emaId: "" }, { merge: true });
    const owners = await playerAccountsService.loadAccountsByEmail([account.email, blank.email]);
    assert.deepEqual([owners.get(account.email)?.emaId, owners.get(blank.email)?.emaId], [null, null]);
  });

  it("gives an EMA id claimed by two accounts at the same time to only one of them", async () => {
    const [first, second] = [await createAccount({ role: "PLAYER" }), await createAccount({ role: "PLAYER" })];
    const emaId = randomEmaId();
    await registryPlayer(emaId);
    const outcomes = await Promise.allSettled([first, second].map((account) => playerAccountsService.setAccountEmaId({ uid: account.uid, emaId })));
    const refused = outcomes.filter((outcome) => outcome.status === "rejected") as PromiseRejectedResult[];
    assert.equal(refused.length, 1, JSON.stringify(outcomes));
    assert.equal((refused[0].reason as { details?: { code?: string } }).details?.code, "EMA_ID_TAKEN");
  });

  it("tells the clients about the EMA players when an account with its own photo gets an EMA id", async () => {
    const account = await createAccount({ role: "PLAYER" });
    const emaId = randomEmaId();
    await registryPlayer(emaId);
    await db.doc(`users/${account.uid}`).set({ photoUrl: "https://example.test/own.jpg" }, { merge: true });
    const version = async () => JSON.stringify((await db.doc("_meta/dataVersions").get()).get("resources.emaPlayers") ?? null);
    const before = await version();
    await playerAccountsService.setAccountEmaId({ uid: account.uid, emaId });
    assert.notEqual(await version(), before);
  });

  it("refuses to remind an account without an email, in English when no language is given", async () => {
    const tournamentId = `accounts-${randomUUID()}`;
    const emaId = randomEmaId();
    await registryPlayer(emaId);
    const user = await auth.createUser({});
    await playerAccountsService.setAccountEmaId({ uid: user.uid, emaId });
    await db.doc(`tournaments/${tournamentId}/players/1`).set({ id: 1, assignedEmaId: emaId });
    const results = await playerAccountsService.runAccountAction({ tournamentId, items: [{ playerId: 1 }] });
    assert.deepEqual(results.map((result) => result.reason), ["NO_EMAIL"]);
  });

  it("saves the pending email of slots that only have their document id", async () => {
    const tournamentId = `accounts-${randomUUID()}`;
    const emaId = randomEmaId();
    await db.doc(`tournaments/${tournamentId}`).set({ name: "Accounts" });
    await db.doc(`tournaments/${tournamentId}/players/7`).set({ assignedEmaId: emaId, email: "OLD@example.test" });
    const results = await playerAccountsService.savePendingEmails({ tournamentId, items: [{ playerId: 7, email: `${randomUUID()}@example.test` }] });
    assert.deepEqual(results.map((result) => result.outcome), ["SAVED"]);
  });
});
