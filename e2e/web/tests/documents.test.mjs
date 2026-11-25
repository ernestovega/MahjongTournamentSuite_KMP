// Web E2E of the files of a tournament: the ID cards, the table QR cards, the ID list and the EMA report that the admin
// downloads, and the Green Book that the Player opens from its Rules menu. The files must open: a PDF with its pages,
// a workbook with the names and the EMA numbers of the players.
import assert from "node:assert/strict";
import { inflateRawSync } from "node:zlib";
import { after, afterEach, before, describe, it } from "node:test";

import { startSite } from "../server.mjs";
import { API, PASSWORD, ROOT, button, launch, openApp, saveOpenPages, tapButton } from "../support.mjs";

const LIVE = "test-live-ongoing";

async function adminToken() {
  const response = await fetch(`${API}/api/auth/signIn`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "admin@e2e.test", password: PASSWORD }),
  });
  assert.equal(response.status, 200, "admin sign-in");
  return (await response.json()).idToken;
}

/** Downloads a file of the API and returns its type and bytes. */
async function download(method, path, token, body) {
  const response = await fetch(`${API}/api${path}`, {
    method,
    headers: { authorization: `Bearer ${token}`, ...(body ? { "content-type": "application/json" } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const bytes = Buffer.from(await response.arrayBuffer());
  assert.equal(response.status, 200, `${method} ${path}: HTTP ${response.status} ${bytes.subarray(0, 200).toString()}`);
  return { type: response.headers.get("content-type") ?? "", bytes };
}

/** The pages of a PDF: its objects of type Page (not Pages). */
function pdfPages(bytes) {
  assert.equal(bytes.subarray(0, 5).toString(), "%PDF-", "a PDF starts with %PDF-");
  return (bytes.toString("latin1").match(/\/Type\s*\/Page(?![s\w])/g) ?? []).length;
}

/** The text of the files in a zip (an xlsx), by name. Enough of the zip format for the files of the API. */
function unzip(bytes) {
  assert.equal(bytes.readUInt32LE(0), 0x04034b50, "an xlsx is a zip");
  const end = bytes.lastIndexOf(Buffer.from([0x50, 0x4b, 0x05, 0x06]));
  const count = bytes.readUInt16LE(end + 10);
  let at = bytes.readUInt32LE(end + 16);
  const files = {};
  for (let index = 0; index < count; index += 1) {
    const method = bytes.readUInt16LE(at + 10);
    const size = bytes.readUInt32LE(at + 20);
    const nameLength = bytes.readUInt16LE(at + 28);
    const extraLength = bytes.readUInt16LE(at + 30);
    const commentLength = bytes.readUInt16LE(at + 32);
    const local = bytes.readUInt32LE(at + 42);
    const name = bytes.subarray(at + 46, at + 46 + nameLength).toString();
    const start = local + 30 + bytes.readUInt16LE(local + 26) + bytes.readUInt16LE(local + 28);
    const data = bytes.subarray(start, start + size);
    files[name] = (method === 8 ? inflateRawSync(data) : data).toString("utf8");
    at += 46 + nameLength + extraLength + commentLength;
  }
  return files;
}

describe("Tournament files", () => {
  let site;
  let browser;
  let token;
  let players;

  before(async () => {
    site = await startSite({ root: ROOT, api: API });
    browser = await launch();
    token = await adminToken();
    players = (await (await fetch(`${API}/api/public/tournaments/${LIVE}/players`)).json()).players;
  });
  afterEach(async (t) => saveOpenPages(t.name));
  after(async () => {
    await browser?.close();
    await site?.close();
  });

  it("the ID cards and the table QR cards are PDFs with a page for each", { timeout: 180_000 }, async () => {
    const cards = await download("GET", `/tournaments/${LIVE}/id-cards`, token);
    assert.match(cards.type, /application\/pdf/);
    assert.ok(pdfPages(cards.bytes) >= players.length, `${pdfPages(cards.bytes)} pages for ${players.length} players`);

    const sheet = await download("GET", `/tournaments/${LIVE}/id-cards?layout=sheet&cardsPerSheet=8&scalePercent=100&cutMarks=true`, token);
    assert.ok(pdfPages(sheet.bytes) < pdfPages(cards.bytes), "a sheet holds several cards");

    const qr = await download("GET", `/tournaments/${LIVE}/table-qr-codes?perSheet=4`, token);
    assert.match(qr.type, /application\/pdf/);
    assert.ok(pdfPages(qr.bytes) >= 1);
  });

  it("the ID list and the EMA report are workbooks with the players", { timeout: 120_000 }, async () => {
    const list = unzip((await download("GET", `/tournaments/${LIVE}/id-list`, token)).bytes);
    assert.ok(list["xl/workbook.xml"], "the ID list is a workbook");
    const listText = Object.values(list).join("\n");
    const named = players.filter((it) => it.name && listText.includes(it.name.split(" ")[0]));
    assert.ok(named.length >= players.length / 2, `${named.length} of ${players.length} players are in the ID list`);

    // The admin app sends the ranking that it shows; the report adds the EMA numbers of the players.
    const rows = players.map((player, index) => ({ playerId: player.id, place: index + 1, tablePoints: 10 - (index % 10), score: 100 - index }));
    const report = unzip((await download("POST", `/tournaments/${LIVE}/ema-report`, token, { rows })).bytes);
    const reportText = Object.values(report).join("\n");
    const withEma = players.filter((it) => it.assignedEmaId);
    assert.ok(withEma.length > 0);
    for (const player of withEma.slice(0, 5)) assert.ok(reportText.includes(player.assignedEmaId), `EMA ${player.assignedEmaId} in the report`);
  });

  it("the Player opens the Green Book from the Rules menu", async () => {
    // A wide window shows the Rules button in the top bar (on a phone it is in the menu of the three dots).
    const page = await openApp(browser, site.url, "/", { phone: false });
    await tapButton(page, "Rules");
    // Headless Chromium downloads a PDF instead of showing it, so the test waits for the request of the new tab.
    const requested = page.context().waitForEvent("request", { predicate: (request) => request.url().includes("/green-book/"), timeout: 15_000 });
    await tapButton(page, "Green Book");
    const url = (await requested).url();
    assert.match(url, /\/green-book\/GreenBook_EN\.pdf$/);
    const pdf = await fetch(url);
    assert.equal(pdf.status, 200);
    assert.equal(Buffer.from(await pdf.arrayBuffer()).subarray(0, 5).toString(), "%PDF-");
    await page.context().close();
  });
});
