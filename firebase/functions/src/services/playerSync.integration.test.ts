import assert from "node:assert/strict";
import { mkdir, mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it, mock } from "node:test";

import { db } from "../firebase";
import { requireEmulator } from "../testing/firestoreFixtures";
import { runEmaPlayerRegistrySync } from "./playerSyncService";

/**
 * The sync reads a copy of the EMA ranking site from a folder (EMA_LOCAL_SOURCE_DIR), so the test needs no network.
 * The players have no photo ("vide.jpg"), so the sync stores nothing in Storage.
 */
let source = "";

const row = (emaId: string, lastName: string, firstName: string, country: string) => `<div class="TCTT_ligne">
  <p class="contenuCelluleC">1</p>
  <p class="contenuCelluleC"><a href="../Players/${emaId}.html">${emaId}</a></p>
  <p class="contenuCelluleG">${lastName}</p>
  <p class="contenuCelluleG">${firstName}</p>
  <p class="contenuCelluleC"><a href="../Country/${country}_Information.html"><img src="../Img/flag/16/x.png"/></a></p>
</div>`;

async function page(path: string, html: string) {
  const file = join(source, "ranking", path);
  await mkdir(join(file, ".."), { recursive: true });
  await writeFile(file, html, "utf8");
}

async function player(emaId: string) {
  await page(`Players/${emaId}.html`, `<html><img src="photo/vide.jpg"/></html>`);
}

const registry = (emaId: string) => db.collection("emaPlayerRegistry").doc(emaId).get();

describe("EMA player registry sync", () => {
  before(async () => {
    requireEmulator();
    source = await mkdtemp(join(tmpdir(), "ema-source-"));
    process.env.EMA_LOCAL_SOURCE_DIR = source;
    await page("Tournament/Tournaments_all.html", `<a href="Tournaments_2025.html">2025</a> <a href="TR_9901.html">x</a>`);
    await page("Tournament/Tournaments_2025.html", `<a href="TR_9902.html">y</a>`);
    await page("Tournament/TR_9901.html", row("99000001", "K&Ouml;STERS", "ANTON", "NED") + row("99000002", "RUIZ", "BEA", "ESP"));
    await page("Tournament/TR_9902.html", row("99000002", "RUIZ", "BEA", "ESP"));
    await player("99000001");
    await player("99000002");
  });

  after(async () => {
    delete process.env.EMA_LOCAL_SOURCE_DIR;
    await rm(source, { recursive: true, force: true });
  });

  it("seeds the players of every tournament page once, with the decoded names", async () => {
    const report = await runEmaPlayerRegistrySync("seed");

    const added = report.additions.filter((change) => change.emaId.startsWith("99")).map((change) => change.emaId).sort();
    assert.deepEqual(added, ["99000001", "99000002"]);
    const anton = (await registry("99000001")).data();
    assert.equal(anton?.lastName, "KÖSTERS");
    assert.equal(anton?.firstName, "ANTON");
    assert.equal(anton?.country, "NED");
    assert.equal(anton?.photoUrl, null);
    assert.ok((await db.collection("emaRankingTournamentResultIndex").doc("TR_9901.html").get()).exists);
    assert.ok((await db.collection("emaRankingTournamentResultIndex").doc("TR_9902.html").get()).exists);
    const saved = await db.collection("emaPlayerRegistrySyncReports").doc(report.id).get();
    assert.equal(saved.get("mode"), "seed");
    assert.ok((await db.doc("emaPlayerRegistrySyncState/current").get()).get("lastReportId") === report.id);
  });

  it("reads only the new tournament pages on a weekly run and reports what changed", async () => {
    await page("Tournament/Tournaments_2025.html", `<a href="TR_9902.html">y</a> <a href="TR_9903.html">z</a>`);
    // Bea changed country, and a new player played.
    await page("Tournament/TR_9903.html", row("99000002", "RUIZ", "BEA", "ITA") + row("99000003", "GIL", "CARLA", "EUR"));
    await player("99000003");

    const report = await runEmaPlayerRegistrySync("incremental");

    assert.deepEqual(report.additions.map((change) => change.emaId), ["99000003"]);
    assert.deepEqual(report.updates.map((change) => [change.emaId, change.country]), [["99000002", "ITA"]]);
    // A European (EUR) flag is no country.
    assert.equal((await registry("99000003")).get("country"), "");
    assert.equal((await registry("99000002")).get("country"), "ITA");
    // The backup keeps the registry as it was before the run.
    const backup = await db.collection("emaPlayerRegistrySyncReports").doc(report.id).collection("backupPlayers").doc("99000002").get();
    assert.equal(backup.get("country"), "ESP");
  });

  it("changes nothing when there is no new tournament page", async () => {
    const report = await runEmaPlayerRegistrySync("incremental");

    assert.deepEqual(report.additions, []);
    assert.deepEqual(report.updates, []);
  });

  it("stores the photo of a new player, and keeps it while the photo file stays the same", async () => {
    await page("Tournament/Tournaments_2025.html", `<a href="TR_9902.html">y</a> <a href="TR_9903.html">z</a> <a href="TR_9904.html">w</a>`);
    await page("Tournament/TR_9904.html", row("99000004", "PHOTO", "PIA", "ESP"));
    await page("Players/99000004.html", `<html><img src="photo/99000004.jpg"/></html>`);
    await page("Players/photo/99000004.jpg", "jpeg bytes");

    await runEmaPlayerRegistrySync("incremental");
    const stored = await registry("99000004");
    assert.match(String(stored.get("photoUrl")), /playerPhotos%2F99000004\.jpg/);
    assert.match(String(stored.get("photoSourceEtag")), /^"[0-9a-f]{64}"$/);

    await runEmaPlayerRegistrySync("seed");
    assert.equal((await registry("99000004")).get("photoUrl"), stored.get("photoUrl"));
  });

  it("reads the EMA site over the network, and keeps a photo that has not changed there", async () => {
    const local = source;
    delete process.env.EMA_LOCAL_SOURCE_DIR;
    const asked: Array<{ url: string; etag: string | null }> = [];
    const fetching = mock.method(globalThis, "fetch", async (url: string, init?: RequestInit) => {
      const etag = (init?.headers as Record<string, string> | undefined)?.["If-None-Match"] ?? null;
      asked.push({ url, etag });
      if (url.includes("/Players/photo/")) return new Response(null, { status: 304 });
      try {
        return new Response(await readFile(join(local, new URL(url).pathname), "utf8"), { status: 200 });
      } catch {
        return new Response("missing", { status: 404 });
      }
    });
    try {
      await runEmaPlayerRegistrySync("seed");
    } finally {
      fetching.mock.restore();
      process.env.EMA_LOCAL_SOURCE_DIR = local;
    }
    const photo = asked.find((call) => call.url.endsWith("/Players/photo/99000004.jpg"));
    assert.match(String(photo?.etag), /^"[0-9a-f]{64}"$/);
    assert.match(String((await registry("99000004")).get("photoUrl")), /playerPhotos%2F99000004\.jpg/);
  });

  it("stops the run on a local copy that misses a photo or a page", async () => {
    await page("Tournament/Tournaments_2025.html", `<a href="TR_9902.html">y</a> <a href="TR_9903.html">z</a> <a href="TR_9904.html">w</a> <a href="TR_9905.html">v</a>`);
    await page("Tournament/TR_9905.html", row("99000005", "LOST", "LIA", "ESP"));
    await page("Players/99000005.html", `<html><img src="photo/99000005.jpg"/></html>`);
    // The photo file is not in the copy.
    await assert.rejects(runEmaPlayerRegistrySync("incremental"), /ENOENT/);
    // Nor is the page of a new tournament.
    await page("Tournament/Tournaments_2025.html", `<a href="TR_9902.html">y</a> <a href="TR_9906.html">u</a>`);
    await assert.rejects(runEmaPlayerRegistrySync("incremental"), /ENOENT/);
    await page("Tournament/Tournaments_2025.html", `<a href="TR_9902.html">y</a> <a href="TR_9903.html">z</a> <a href="TR_9904.html">w</a>`);
  });

  it("stops the run on a photo that the EMA site refuses or that is no image, and stores a new photo with its type", async () => {
    const local = source;
    delete process.env.EMA_LOCAL_SOURCE_DIR;
    let photo: Response = new Response("down", { status: 500 });
    const fetching = mock.method(globalThis, "fetch", async (url: string) => {
      const path = new URL(url).pathname;
      if (path.endsWith("/Players/photo/99000007.png")) return photo;
      if (path.endsWith("/Tournament/Tournaments_2025.html")) return new Response(`<a href="TR_9907.html">t</a>`, { status: 200 });
      if (path.endsWith("/Tournament/TR_9907.html")) return new Response(row("99000007", "NET", "NOA", "ESP"), { status: 200 });
      if (path.endsWith("/Players/99000007.html")) return new Response(`<html><img src="photo/99000007.png"/></html>`, { status: 200 });
      try {
        return new Response(await readFile(join(local, path), "utf8"), { status: 200 });
      } catch {
        return new Response("missing", { status: 404 });
      }
    });
    try {
      await assert.rejects(runEmaPlayerRegistrySync("incremental"), /EMA photo request failed \(500\): 99000007/);
      photo = new Response("<html/>", { status: 200, headers: { "content-type": "text/html" } });
      await assert.rejects(runEmaPlayerRegistrySync("incremental"), /EMA photo is not an image: 99000007/);
      photo = new Response("png bytes", { status: 200, headers: { "content-type": "image/png", etag: "\"v1\"" } });
      await runEmaPlayerRegistrySync("incremental");
    } finally {
      fetching.mock.restore();
      process.env.EMA_LOCAL_SOURCE_DIR = local;
    }
    const stored = await registry("99000007");
    assert.equal(stored.get("photoSourceEtag"), "\"v1\"");
    assert.match(String(stored.get("photoUrl")), /playerPhotos%2F99000007\.jpg/);
  });

  it("marks more than 500 new tournament pages in several batches", async () => {
    const local = source;
    delete process.env.EMA_LOCAL_SOURCE_DIR;
    const links = Array.from({ length: 501 }, (_, index) => `<a href="TR_${80000 + index}.html">t</a>`).join(" ");
    const fetching = mock.method(globalThis, "fetch", async (url: string) => {
      const path = new URL(url).pathname;
      if (path.endsWith("/Tournament/Tournaments_all.html")) return new Response(links, { status: 200 });
      return new Response("", { status: 200 });
    });
    try {
      await runEmaPlayerRegistrySync("incremental");
    } finally {
      fetching.mock.restore();
      process.env.EMA_LOCAL_SOURCE_DIR = local;
    }
    assert.ok((await db.collection("emaRankingTournamentResultIndex").doc("TR_80000.html").get()).exists);
    assert.ok((await db.collection("emaRankingTournamentResultIndex").doc("TR_80500.html").get()).exists);
  });
});
