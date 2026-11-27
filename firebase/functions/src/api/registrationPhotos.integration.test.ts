import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";
import { getGlobalDataVersions } from "../services/dataVersionsService";
import { photosOf } from "../services/registrationsService";

type Row = { emaId: string; photoUrl?: string | null };

/**
 * The photo of each person of the registrations and of the public registry: the one of its account, or else the one of
 * its EMA player, and none when the person hides it. In a file
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

  it("shows the own photo first in the public registry, and no photo of a person who hides it", async () => {
    // One account with its own photo, one that hides its photo (its EMA player has one), and an EMA player without account.
    const own = await createAccount({ role: "PLAYER" });
    const ownEma = await newEma("https://photos.example.test/registry-ema-of-own.jpg");
    await giveEmaIdToAccount(own.uid, ownEma);
    const ownUrl = "https://photos.example.test/registry-own.jpg";
    await db.doc(`users/${own.uid}`).set({ photoUrl: ownUrl }, { merge: true });

    const hidden = await createAccount({ role: "PLAYER" });
    const hiddenEma = await newEma("https://photos.example.test/registry-ema-of-hidden.jpg");
    await giveEmaIdToAccount(hidden.uid, hiddenEma);
    const versionBefore = await emaPlayersRevision();
    const hide = await server.request("PUT", "/auth/me/photo-visibility", { token: hidden.token, body: { publicPhoto: false } });
    assert.equal(hide.status, 200, JSON.stringify(hide.body));
    // The profile still shows the photo to its owner, and says that it is hidden.
    assert.equal((hide.body as { publicPhoto: boolean }).publicPhoto, false);
    assert.equal((hide.body as { photoUrl: string }).photoUrl, "https://photos.example.test/registry-ema-of-hidden.jpg");
    // The apps read the registry again.
    assert.ok((await emaPlayersRevision()) > versionBefore);

    const emaOnly = await newEma("https://photos.example.test/registry-ema.jpg");

    type RegistryRow = { emaId: string; photoUrl: string | null; publicPhotoUrl: string | null };
    const publicRows = ((await server.request("GET", "/public/players")).body as { players: RegistryRow[] }).players;
    const byEma = new Map(publicRows.map((row) => [row.emaId, row]));
    assert.equal(byEma.get(ownEma)?.photoUrl, ownUrl);
    assert.equal(byEma.get(hiddenEma)?.photoUrl, null, "the EMA photo of a person who hides it never leaves the server");
    assert.equal(byEma.get(hiddenEma)?.publicPhotoUrl, null);
    assert.equal(byEma.get(emaOnly)?.photoUrl, "https://photos.example.test/registry-ema.jpg");

    // The admins keep the EMA photo of the registry, and get the public one apart.
    const adminRows = ((await server.request("GET", "/ema-player-registry", { token: admin.token })).body as { players: RegistryRow[] }).players;
    const adminByEma = new Map(adminRows.map((row) => [row.emaId, row]));
    assert.equal(adminByEma.get(hiddenEma)?.photoUrl, "https://photos.example.test/registry-ema-of-hidden.jpg");
    assert.equal(adminByEma.get(hiddenEma)?.publicPhotoUrl, null);
    assert.equal(adminByEma.get(ownEma)?.publicPhotoUrl, ownUrl);

    // Shown again: the EMA photo is back. A value that is not true or false is refused.
    const show = await server.request("PUT", "/auth/me/photo-visibility", { token: hidden.token, body: { publicPhoto: true } });
    assert.equal((show.body as { publicPhoto: boolean }).publicPhoto, true);
    const again = ((await server.request("GET", "/public/players")).body as { players: RegistryRow[] }).players;
    assert.equal(again.find((row) => row.emaId === hiddenEma)?.photoUrl, "https://photos.example.test/registry-ema-of-hidden.jpg");
    const wrong = await server.request("PUT", "/auth/me/photo-visibility", { token: hidden.token, body: { publicPhoto: "no" } });
    assert.equal(wrong.status, 400);
  });

  it("gives no photo in the registrations of a person who hides it", async () => {
    const hidden = await createAccount({ role: "PLAYER" });
    const hiddenEma = await newEma("https://photos.example.test/reg-ema-of-hidden.jpg");
    await giveEmaIdToAccount(hidden.uid, hiddenEma);
    await db.doc(`users/${hidden.uid}`).set({ photoUrl: "https://photos.example.test/reg-own-of-hidden.jpg", publicPhoto: false }, { merge: true });
    const photos = await photosOf([hiddenEma]);
    assert.equal(photos.has(hiddenEma), false);
  });
});

async function emaPlayersRevision(): Promise<number> {
  return Number((await getGlobalDataVersions()).emaPlayers.revision);
}
