// Web E2E of the admin: change the number of players of a drawn tournament that has not started
// (docs/player-count-change.md). The test removes team 1 (#1 to #4) of a team tournament of 28 players: the app makes
// the new draw in the browser and the server keeps the numbers of the others.
import assert from "node:assert/strict";
import { after, afterEach, before, describe, it } from "node:test";

import { startSite } from "../server.mjs";
import { ADMIN_ROOT, API, ROOT, button, launch, openAdmin, resetTournaments, saveOpenPages, tapButton } from "../support.mjs";

const UPCOMING = "test-registration-open";

const get = async (path) => (await fetch(`${API}/api${path}`)).json();

describe("Admin web", () => {
  let site;
  let browser;

  before(async () => {
    site = await startSite({ root: ROOT, admin: ADMIN_ROOT, api: API });
    browser = await launch();
    await resetTournaments(UPCOMING);
    await fetch(`${API}/__e2e/mail`, { method: "DELETE" });
  });
  afterEach(async (t) => saveOpenPages(t.name));
  after(async () => {
    await resetTournaments(UPCOMING);
    await browser?.close();
    await site?.close();
  });

  it("changes the number of players from 28 to 24 before the tournament starts", { timeout: 300_000 }, async () => {
    const page = await openAdmin(browser, site.url, "admin@e2e.test", [
      "TournamentsRoute {}",
      `TournamentRoute {"tournamentId":"${UPCOMING}"}`,
      `PlayerCountRoute {"tournamentId":"${UPCOMING}"}`,
    ]);
    await button(page, /^#1 · /).waitFor();
    for (const number of [1, 2, 3, 4]) await tapButton(page, new RegExp(`^#${number} · `));
    await tapButton(page, "Continue");
    await tapButton(page, "Make the new draw");
    // The red dialog: this cannot be undone.
    await tapButton(page, "Make the new draw");

    // The browser makes the draw (about a minute here), then the server saves it.
    const start = Date.now();
    let tournament;
    while (Date.now() - start < 240_000) {
      tournament = (await get("/public/tournaments")).tournaments.find((it) => it.id === UPCOMING);
      if (tournament.numPlayers === 24) break;
      await page.waitForTimeout(2000);
    }
    assert.equal(tournament.numPlayers, 24, "the tournament keeps 28 players");

    const { players } = await get(`/public/tournaments/${UPCOMING}/players`);
    assert.deepEqual(players.map((it) => it.id).sort((a, b) => a - b), Array.from({ length: 24 }, (_, index) => index + 5));
    for (let round = 1; round <= 5; round++) {
      const { tables } = await get(`/public/tournaments/${UPCOMING}/tables?roundId=${round}`);
      assert.equal(tables.length, 6, `round ${round}`);
      const seated = tables.flatMap((it) => it.playerIds).sort((a, b) => a - b);
      assert.deepEqual(seated, Array.from({ length: 24 }, (_, index) => index + 5), `round ${round} seats everyone once`);
    }
    const { emails } = await (await fetch(`${API}/__e2e/mail`)).json();
    assert.ok(emails.length > 0, "the people affected get an email");
    await page.context().close();
  });
});
