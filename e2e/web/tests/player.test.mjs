// Web E2E of the Player: the Wasm app in Chromium against the local test backend (docs/test-environment.md).
//   cd firebase/functions && npm run test-env:backend          # in another terminal
//   ./gradlew :playerApp:wasmJsBrowserDistribution
//   cd e2e/web && npm ci && npx playwright install chromium && npm test
// See e2e/web/README.md.
import assert from "node:assert/strict";
import { after, afterEach, before, describe, it } from "node:test";

import { startSite } from "../server.mjs";
import { API, ROOT, button, freshTree, launch, openApp, resetTournaments, screenText, seeText, signIn, tap, tapButton, typeIn, saveOpenPages } from "../support.mjs";

const LIVE = "test-live-ongoing";

describe("Player web", () => {
  let site;
  let browser;

  before(async () => {
    site = await startSite({ root: ROOT, api: API });
    browser = await launch();
    await resetTournaments(LIVE);
  });
  afterEach(async (t) => saveOpenPages(t.name));
  after(async () => {
    await resetTournaments(LIVE);
    await browser?.close();
    await site?.close();
  });

  it("a guest sees the public tournaments and opens one", async () => {
    const page = await openApp(browser, site.url, "/");
    await tapButton(page, "All");
    // The list is lazy and its order follows the dates of the seed: search, so the card is on screen.
    await typeIn(page, 0, "Live ongoing");
    const card = page.getByRole("button").filter({ hasText: "Test - Live ongoing tournament" }).first();
    await tap(card.getByRole("button", { name: "Open", exact: true }));

    await seeText(page, "Test - Live ongoing tournament");
    for (const tab of ["Tables", "Ranking", "Players", "Teams", "Info"]) await button(page, tab).waitFor();
    await page.context().close();
  });

  it("the link of a tournament opens it without an account", async () => {
    const page = await openApp(browser, site.url, `/t/${LIVE}`);
    await seeText(page, "Test - Live ongoing tournament");
    await button(page, "Ranking").waitFor();
    await page.context().close();
  });

  it("a player signs in, goes to the own table and saves a draw that the table then shows", async () => {
    const page = await openApp(browser, site.url, "/");
    await signIn(page, "player@e2e.test");
    // The reminder of the round in play takes the player to the table.
    await tapButton(page, "Go to my table");
    await freshTree(page, button(page, "Add draw"));
    await seeText(page, "Hand 12/16");

    // A draw has nothing to ask: "Add draw" opens the check of the hand at once (Ernesto, 25-nov).
    await tapButton(page, "Add draw");
    await tapButton(page, "Confirm");
    await page.waitForTimeout(3000);
    await freshTree(page, button(page, "Add draw"));

    await seeText(page, "Hand 13/16");
    assert.match(await screenText(page), /#12 Draw/);
    await page.context().close();
  });

  it("the QR link of the tournament opens the table of the signed-in player", async () => {
    const page = await openApp(browser, site.url, "/");
    await signIn(page, "seated@e2e.test");
    await tapButton(page, "Later");
    // The QR on the tables: /<slug>/my-table, or /t/<id>/my-table for a tournament without a public link.
    await page.goto(`${site.url}/t/${LIVE}/my-table`);
    await seeText(page, "Table 8");
    await button(page, "Add win").waitFor();
    await page.context().close();
  });
});
