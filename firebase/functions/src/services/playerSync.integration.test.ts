import assert from "node:assert/strict";
import { mkdir, mkdtemp, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { after, before, describe, it } from "node:test";

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
});
