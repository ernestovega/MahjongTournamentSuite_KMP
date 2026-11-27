import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { db } from "../firebase";
import { buildTableQrPdf, loadTableQrDocument, tableQrTexts } from "./tableQrService";

/** The QR cards of a tournament whose documents miss fields, and of names that hardly fit. */
describe("table QR cards of malformed documents", () => {
  it("fills the cards with defaults when the tournament and its organizer miss fields", async () => {
    await db.doc("organizations/table-qr-lost").set({ name: "Lost", logoPath: "organizationLogos/table-qr-not-there.png", primaryColor: "red" });
    const bare = db.doc(`tournaments/table-qr-malformed-${Date.now()}`);
    await bare.set({ numPlayers: 8, organizerIds: ["table-qr-lost"] });
    const model = await loadTableQrDocument(bare.id, 4);
    assert.equal(model.tournamentName, "");
    assert.equal(model.logo, null);
    assert.equal(model.organizerName, "Lost");
    assert.match(model.organizerColor, /^#[0-9A-F]{6}$/);
    assert.notEqual(model.organizerColor, "RED");
    assert.match(model.link, new RegExp(`/t/${bare.id}/my-table$`));
    assert.equal(model.tableCount, 2);

    const orphan = db.doc(`tournaments/table-qr-orphan-${Date.now()}`);
    await orphan.set({ numPlayers: 4, organizerIds: [], name: "Orphan" });
    assert.equal((await loadTableQrDocument(orphan.id, 1)).organizerName, "Orphan");

    const linked = db.doc(`tournaments/table-qr-linked-${Date.now()}`);
    await linked.set({ numPlayers: 4, name: "Linked", registration: { slug: "linked-open" } });
    assert.match((await loadTableQrDocument(linked.id, 1)).link, /\/linked-open\/my-table$/);
  });

  it("draws a very long name at the smallest size, an initial outside Latin and a logo that is not an image", async () => {
    const base = {
      logo: null, organizerName: "Жемчуг", organizerColor: "#123456", link: "https://example.test/t/x/my-table", tableCount: 1,
      texts: tableQrTexts("en"), perSheet: 2 as const,
    };
    assert.ok((await buildTableQrPdf({ ...base, tournamentName: "A very long tournament name ".repeat(12) })).length > 0);
    assert.ok((await buildTableQrPdf({ ...base, tournamentName: "Short", logo: Buffer.from("not an image") })).length > 0);
  });
});
