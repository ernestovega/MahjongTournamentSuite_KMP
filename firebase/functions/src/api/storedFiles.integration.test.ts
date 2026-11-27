import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { db, storage } from "../firebase";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

const PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
const JPEG = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 0, 16, 74, 70, 73, 70, 0]).toString("base64");
const GIF = Buffer.from("GIF89a\u0001\u0000\u0001\u0000\u0000\u0000\u0000;", "latin1").toString("base64");

const filesIn = async (prefix: string) => (await storage.getFiles({ prefix }))[0].map((file) => file.name);

/** The files that the backend keeps in Storage: the own photo of an account, the photo of an EMA player and the logo of an organization. */
describe("stored files", () => {
  let server: TestServer;
  let admin: TestAccount;
  let player: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    player = await createAccount({ role: "PLAYER" });
  });
  after(async () => {
    await server.close();
  });

  it("saves, replaces and removes the own photo of an account", async () => {
    const put = await server.request("PUT", "/auth/me/photo", { token: player.token, body: { contentType: "image/png", dataBase64: PNG } });
    assert.equal(put.status, 200, JSON.stringify(put.body));
    assert.equal((put.body as { hasOwnPhoto: boolean }).hasOwnPhoto, true);
    assert.deepEqual(await filesIn(`userPhotos/${player.uid}.`), [`userPhotos/${player.uid}.png`]);

    // A photo of another type replaces the old file.
    const replaced = await server.request("PUT", "/auth/me/photo", { token: player.token, body: { contentType: "image/gif", dataBase64: GIF } });
    assert.equal(replaced.status, 200, JSON.stringify(replaced.body));
    assert.deepEqual(await filesIn(`userPhotos/${player.uid}.`), [`userPhotos/${player.uid}.gif`]);

    const removed = await server.request("DELETE", "/auth/me/photo", { token: player.token });
    assert.equal(removed.status, 200, JSON.stringify(removed.body));
    assert.equal((removed.body as { hasOwnPhoto: boolean }).hasOwnPhoto, false);
    assert.deepEqual(await filesIn(`userPhotos/${player.uid}.`), []);
  });

  it("lets a super admin set and remove the photo of another account", async () => {
    const path = `/admin/users/${player.uid}/photo`;
    const put = await server.request("PUT", path, { token: admin.token, body: { contentType: "image/jpeg", dataBase64: JPEG } });
    assert.equal(put.status, 200, JSON.stringify(put.body));
    assert.equal((put.body as { hasOwnPhoto: boolean }).hasOwnPhoto, true);
    assert.equal((await server.request("DELETE", path, { token: admin.token })).status, 200);
    assert.equal((await db.doc(`users/${player.uid}`).get()).get("photoUrl"), undefined);
  });

  it("saves the photo of an EMA player", async () => {
    const emaId = "07680001";
    await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Photo", lastName: "Player", country: "ES" });
    const put = await server.request("PUT", `/ema-player-registry/${emaId}/photo`, { token: admin.token, body: { contentType: "image/png", dataBase64: PNG } });
    assert.equal(put.status, 200, JSON.stringify(put.body));
    assert.match(String((await db.doc(`emaPlayerRegistry/${emaId}`).get()).get("photoUrl")), /firebasestorage/);
  });

  it("keeps the logo of an organization through its changes and its removal", async () => {
    const body = { name: `Logo club ${randomUUID().slice(0, 6)}`, type: "CLUB", country: "ES" };
    const created = await server.request("POST", "/admin/organizations", {
      token: admin.token, body: { ...body, logoContentType: "image/png", logoDataBase64: PNG },
    });
    assert.equal(created.status, 201, JSON.stringify(created.body));
    const id = (created.body as { id: string }).id;
    const firstLogo = String((await db.doc(`organizations/${id}`).get()).get("logoPath"));
    assert.deepEqual(await filesIn(firstLogo), [firstLogo]);

    // A new logo takes the place of the old file.
    const changed = await server.request("PUT", `/admin/organizations/${id}`, {
      token: admin.token, body: { ...body, logoContentType: "image/jpeg", logoDataBase64: JPEG },
    });
    assert.equal(changed.status, 200, JSON.stringify(changed.body));
    const secondLogo = String((await db.doc(`organizations/${id}`).get()).get("logoPath"));
    assert.notEqual(secondLogo, firstLogo);
    assert.deepEqual(await filesIn(firstLogo), []);

    const removed = await server.request("PUT", `/admin/organizations/${id}`, { token: admin.token, body: { ...body, removeLogo: true } });
    assert.equal(removed.status, 200, JSON.stringify(removed.body));
    assert.equal((removed.body as { logoUrl: string | null }).logoUrl, null);
    assert.deepEqual(await filesIn(secondLogo), []);

    const again = await server.request("PUT", `/admin/organizations/${id}`, {
      token: admin.token, body: { ...body, logoContentType: "image/png", logoDataBase64: PNG },
    });
    assert.equal(again.status, 200, JSON.stringify(again.body));
    const lastLogo = String((await db.doc(`organizations/${id}`).get()).get("logoPath"));
    const deleted = await server.request("DELETE", `/admin/organizations/${id}`, { token: admin.token });
    assert.equal(deleted.status, 200, JSON.stringify(deleted.body));
    assert.deepEqual(await filesIn(lastLogo), []);
  });
});
