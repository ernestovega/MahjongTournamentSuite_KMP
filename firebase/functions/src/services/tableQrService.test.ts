import assert from "node:assert/strict";
import test from "node:test";

import { buildTableQrPdf, qrModules, tableQrLink, tableQrTexts } from "./tableQrService";

test("the link of a table uses the public link of the tournament or its id", () => {
  assert.equal(tableQrLink("https://mts.mahjongmadrid.com/", "abc", "6thmmc", 5), "https://mts.mahjongmadrid.com/6thmmc/table/5");
  assert.equal(tableQrLink("https://mts---staging.web.app", "abc", "", 12), "https://mts---staging.web.app/t/abc/table/12");
});

test("every language has the texts of the cards, and an unknown one falls back to English", () => {
  assert.equal(tableQrTexts("es").table, "Mesa");
  assert.equal(tableQrTexts("xx" as never).table, "Table");
});

test("the QR code is a square of modules with the finder pattern in the corner", () => {
  const modules = qrModules("https://mts.mahjongmadrid.com/6thmmc/table/5");
  assert.ok(modules.length >= 21);
  modules.forEach((row) => assert.equal(row.length, modules.length));
  // The finder pattern: a dark square of 7 modules with a light ring inside.
  assert.deepEqual(modules[0].slice(0, 7), [true, true, true, true, true, true, true]);
  assert.deepEqual(modules[1].slice(0, 7), [true, false, false, false, false, false, true]);
});

test("the PDF holds four tables on each page", async () => {
  const tables = Array.from({ length: 15 }, (_, index) => ({ tableId: index + 1, link: tableQrLink("https://x.test", "t", "open", index + 1) }));
  const pdf = await buildTableQrPdf({ tournamentName: "Madrid Open 2027", primaryColor: "#02B16B", tables, texts: tableQrTexts("es") });
  assert.equal(pdf.subarray(0, 5).toString(), "%PDF-");
  const pages = pdf.toString("latin1").match(/\/Type \/Page\b/g) ?? [];
  assert.equal(pages.length, 4);
});
