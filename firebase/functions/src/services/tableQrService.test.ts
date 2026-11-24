import assert from "node:assert/strict";
import test from "node:test";

import { buildTableQrPdf, myTableQrLink, qrModules, readCardsPerSheet, sheetGrid, tableQrTexts, type CardsPerSheet } from "./tableQrService";

test("the link of the QR uses the public link of the tournament or its id", () => {
  assert.equal(myTableQrLink("https://mts.mahjongmadrid.com/", "abc", "6thmmc"), "https://mts.mahjongmadrid.com/6thmmc/my-table");
  assert.equal(myTableQrLink("https://mts---staging.web.app", "abc", ""), "https://mts---staging.web.app/t/abc/my-table");
});

test("every language has the texts of the cards, and an unknown one falls back to English", () => {
  assert.equal(tableQrTexts("es").table, "Mesa");
  assert.equal(tableQrTexts("xx" as never).table, "Table");
});

test("the cards per sheet are 1, 2, 4, 6 or 8, and 4 by default", () => {
  assert.equal(readCardsPerSheet("1"), 1);
  assert.equal(readCardsPerSheet(2), 2);
  assert.equal(readCardsPerSheet("4"), 4);
  assert.equal(readCardsPerSheet(undefined), 4);
  assert.equal(readCardsPerSheet("3"), 4);
  assert.equal(readCardsPerSheet("6"), 6);
  assert.equal(readCardsPerSheet(8), 8);
  assert.deepEqual([1, 2, 4, 6, 8].map((n) => { const g = sheetGrid(n as CardsPerSheet); return g.columns * g.rows; }), [1, 2, 4, 6, 8]);
  // Every card is landscape.
  [1, 2, 4, 6, 8].forEach((n) => { const g = sheetGrid(n as CardsPerSheet); assert.ok(g.width > g.height, `${n} per sheet`); });
});

test("the QR code is a square of modules with the finder pattern in the corner", () => {
  const modules = qrModules("https://mts.mahjongmadrid.com/6thmmc/my-table");
  assert.ok(modules.length >= 21);
  modules.forEach((row) => assert.equal(row.length, modules.length));
  assert.deepEqual(modules[0].slice(0, 7), [true, true, true, true, true, true, true]);
  assert.deepEqual(modules[1].slice(0, 7), [true, false, false, false, false, false, true]);
});

test("the PDF has one page for each group of cards", async () => {
  for (const [perSheet, pages] of [[1, 15], [2, 8], [4, 4], [6, 3], [8, 2]] as const) {
    const pdf = await buildTableQrPdf({
      tournamentName: "Madrid Open 2027", logo: null, organizerName: "Madrid Riichi Club", organizerColor: "#02B16B", link: "https://x.test/open/my-table", tableCount: 15,
      texts: tableQrTexts("es"), perSheet,
    });
    assert.equal(pdf.subarray(0, 5).toString(), "%PDF-");
    assert.equal((pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? []).length, pages, `${perSheet} per sheet`);
  }
});

test("a logo that cannot be read, or an organizer without a name, still makes the PDF", async () => {
  // A logo in a format pdfkit cannot read shows the initial of the organizer; without a name, the corner stays empty.
  for (const [logo, organizerName] of [[Buffer.from("not an image"), "Madrid Riichi Club"], [null, ""], [null, "  "]] as const) {
    const pdf = await buildTableQrPdf({
      tournamentName: "Madrid Open 2027", logo, organizerName, organizerColor: "#02B16B", link: "https://mts.example.test/open/my-table", tableCount: 3,
      texts: tableQrTexts("en"), perSheet: 4,
    });
    assert.equal(pdf.subarray(0, 5).toString(), "%PDF-", `${organizerName} / ${logo == null ? "no logo" : "bad logo"}`);
  }
});
