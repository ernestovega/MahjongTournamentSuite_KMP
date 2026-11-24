import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";

type Row = { emaId: string; photoUrl?: string | null };

/**
 * The photo of each person of the registrations: the one of its account, or else the one of its EMA player. In a file
 * of its own: the writes of an address have a limit per minute that the other registration tests use up.
 */
describe("photos of the registrations", () => {
  let server: TestServer;
  let admin: TestAccount;
  const tag = randomUUID().replace(/[^0-9]/g, "").padEnd(4, "3").slice(0, 3);
  let counter = 0;

  async function newEma(photoUrl?: string): Promise<string> {
    counter++;
    const emaId = `6${tag}${String(counter).padStart(4, "0")}`;
    await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Photo", lastName: `Player ${counter}`, country: "ES", ...(photoUrl ? { photoUrl } : {}) });
    return emaId;
  }

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    await seedOrganization({ id: "org-es" });
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  it("shows the photo of the account of each person, or else the one of its EMA player", async () => {
    const created = await server.request("POST", "/tournaments", {
      token: admin.token,
      body: {
        name: "Registration photos",
        shortName: "PHO",
        organizerIds: ["org-es"],
        eventStartDate: "2027-02-20",
        eventEndDate: "2027-02-21",
        hostCountry: "ES",
        hostCity: "Madrid",
        venueName: "Club",
        venueAddress: "Calle 1",
        contactEmail: "org@example.test",
        isTeams: false,
        numRounds: 2,
        roundSchedules: fullRoundSchedules(2, "2027-02-20"),
        timeZone: "Europe/Madrid",
        registration: { maxPlayers: null, closesOn: null, slug: `pho-${randomUUID().slice(0, 8)}` },
      },
    });
    assert.equal(created.status, 200, JSON.stringify(created.body));
    const id = (created.body as { id: string }).id;
    // The photos come before the registrations: the server keeps them some minutes in memory.
    // An account with its own photo, whose EMA player also has one: the photo of the account wins.
    const account = await createAccount({ role: "PLAYER" });
    const withAccount = await newEma("https://photos.example.test/ema-of-account.jpg");
    await giveEmaIdToAccount(account.uid, withAccount);
    await db.doc(`users/${account.uid}`).set({ photoUrl: "https://photos.example.test/account.jpg" }, { merge: true });
    // No account: the photo of the EMA player. Neither: no photo.
    const emaOnly = await newEma("https://photos.example.test/ema.jpg");
    const none = await newEma();
    for (const emaId of [withAccount, emaOnly, none]) {
      const added = await server.request("POST", `/tournaments/${id}/registrations`, {
        token: admin.token, body: { emaId, email: `${emaId}@example.test`, notify: false },
      });
      assert.equal(added.status, 200, JSON.stringify(added.body));
    }

    const publicRows = ((await server.request("GET", `/public/tournaments/${id}/registrations`)).body as { registrations: Row[] }).registrations;
    const photos = new Map(publicRows.map((row) => [row.emaId, row.photoUrl]));
    assert.equal(photos.get(withAccount), "https://photos.example.test/account.jpg");
    assert.equal(photos.get(emaOnly), "https://photos.example.test/ema.jpg");
    assert.equal(photos.get(none), null);
    const adminRows = ((await server.request("GET", `/tournaments/${id}/registrations`, { token: admin.token })).body as { registrations: Row[] }).registrations;
    assert.equal(new Map(adminRows.map((row) => [row.emaId, row.photoUrl])).get(withAccount), "https://photos.example.test/account.jpg");
  });
});
