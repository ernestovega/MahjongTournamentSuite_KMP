import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it } from "node:test";

import { db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator } from "../testing/authFixtures";
import { purgeFinishedRegistrations, REGISTRATION_DATA_DAYS } from "./registrationJobs";

const NOW = new Date("2027-06-15T03:10:00Z");
const tag = randomUUID().slice(0, 6);

async function tournament(id: string, eventEndDate: string, people: string[]): Promise<void> {
  await db.doc(`tournaments/${id}`).set({ name: id, eventStartDate: eventEndDate, eventEndDate });
  for (const emaId of people) {
    await db.doc(`tournaments/${id}/registrations/${emaId}`).set({
      emaId, name: `Person ${emaId}`, email: `${emaId}@example.test`, manageToken: `token-${emaId}`, status: "CONFIRMED", sharedEmail: false,
    });
  }
  await db.collection(`tournaments/${id}/registrationLog`).add({ kind: "registered", emaId: people[0] ?? "", name: "x" });
}

async function food(emaId: string): Promise<void> {
  await db.doc(`personPrivate/${emaId}`).set({ foodRestrictions: "Gluten free", foodConsentAt: new Date() });
}

describe("purge of the registrations of finished tournaments", () => {
  before(() => requireAuthEmulator());

  it("keeps the data until the days pass, then clears emails, links, the log and the food restrictions", async () => {
    const old = `purge-old-${tag}`;
    const recent = `purge-recent-${tag}`;
    const [alone, withAccount, inRecent] = ["91", "92", "93"].map((n) => `${n}${tag}`);
    // Ended one day more than the days ago.
    const oldEnd = new Date(NOW.getTime() - (REGISTRATION_DATA_DAYS + 1) * 86_400_000).toISOString().slice(0, 10);
    // Ended one day less than the days ago: still in use.
    const recentEnd = new Date(NOW.getTime() - (REGISTRATION_DATA_DAYS - 1) * 86_400_000).toISOString().slice(0, 10);
    await tournament(old, oldEnd, [alone, withAccount, inRecent]);
    await tournament(recent, recentEnd, [inRecent]);
    await Promise.all([alone, withAccount, inRecent].map(food));
    const account = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(account.uid, withAccount);

    const result = await purgeFinishedRegistrations(NOW);

    assert.ok(result.tournaments.includes(old));
    assert.ok(!result.tournaments.includes(recent));
    const registration = await db.doc(`tournaments/${old}/registrations/${alone}`).get();
    assert.equal(registration.get("email"), "");
    assert.equal(registration.get("manageToken"), undefined);
    assert.equal(registration.get("name"), `Person ${alone}`);
    assert.equal((await db.collection(`tournaments/${old}/registrationLog`).get()).size, 0);
    assert.equal((await db.doc(`tournaments/${recent}/registrations/${inRecent}`).get()).get("email"), `${inRecent}@example.test`);
    // Without an account and in no other tournament: deleted. With an account, or still registered elsewhere: kept.
    assert.equal((await db.doc(`personPrivate/${alone}`).get()).exists, false);
    assert.equal((await db.doc(`personPrivate/${withAccount}`).get()).exists, true);
    assert.equal((await db.doc(`personPrivate/${inRecent}`).get()).exists, true);

    // Once per tournament.
    const again = await purgeFinishedRegistrations(NOW);
    assert.ok(!again.tournaments.includes(old));
  });
});
