import assert from "node:assert/strict";
import { after, before, describe, it, mock } from "node:test";

import { db } from "../firebase";
import * as versionsPageModule from "../services/versionsPage";
import { createTournament } from "../services/tournamentsService";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

/** A valid draw of 16 people in four teams: table j takes member k of team (j + k) mod 4. */
function teamsDraw() {
  const players = Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: Math.floor(index / 4) + 1 }));
  const tables = [0, 1, 2, 3].map((j) => ({ roundId: 1, tableId: j + 1, playerIds: [0, 1, 2, 3].map((k) => ((j + k) % 4) * 4 + k + 1) }));
  return { players, tables };
}

/** The changes of a team by the API, the address prefix of Hosting, the versions page and the forwarded probe. */
describe("teams and the routes of the app", () => {
  let server: TestServer;
  let admin: TestAccount;

  before(async () => {
    requireAuthEmulator();
    process.env.MTS_FORWARDED_PROBE = "1";
    server = await startServer();
    delete process.env.MTS_FORWARDED_PROBE;
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => {
    await server.close();
  });

  it("renames a team and fills its slots, and refuses a bad or unknown EMA number", async () => {
    const tournament = await createTournament({
      name: "Teams", shortName: "TMS", organizerIds: ["org-es"], eventStartDate: "2027-02-01", eventEndDate: "2027-02-01",
      hostCountry: "ES", hostCity: "Madrid", isTeams: true, numPlayers: 16, numRounds: 1, numTries: 1, ...teamsDraw(),
      createdByUid: "u-test", roundSchedules: [{ roundId: 1, date: "2027-02-01", startTime: "10:00" }],
    });
    const emaIds = ["07750001", "07750002"];
    await Promise.all(emaIds.map((emaId) => db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Team", lastName: emaId, country: "ES" })));
    const path = `/tournaments/${tournament.id}/teams/1`;

    const saved = await server.request("PUT", path, { token: admin.token, body: { name: "Dragons", emaIds: [...emaIds, null, " "] } });
    assert.equal(saved.status, 200, JSON.stringify(saved.body));
    assert.equal((await db.doc(`tournaments/${tournament.id}/teams/1`).get()).get("name"), "Dragons");

    const bad = await server.request("PUT", path, { token: admin.token, body: { name: "Dragons", emaIds: ["not a number"] } });
    assert.match(JSON.stringify(bad.body), /Invalid EMA player/);
    const unknown = await server.request("PUT", path, { token: admin.token, body: { name: "Dragons", emaIds: ["07759999"] } });
    assert.match(JSON.stringify(unknown.body), /EMA player does not exist/);

    const nobody = await server.request("GET", `/tournaments/${tournament.id}/users/lookup?email=nobody-${Date.now()}@example.test`, { token: admin.token });
    assert.equal(nobody.status, 404);
  });

  it("answers the paths that Hosting sends with /api, the versions page and the forwarded probe", async () => {
    assert.equal((await server.request("GET", "/api/health")).status, 200);
    assert.equal((await server.request("GET", "/api")).status, 404);

    const page = mock.method(versionsPageModule, "versionsPage", async () => "<p>versions</p>");
    try {
      const shown = await server.request("GET", "/version");
      assert.equal(shown.body, "<p>versions</p>");
      page.mock.mockImplementation(async () => {
        throw new Error("no versions");
      });
      assert.equal((await server.request("GET", "/version")).status, 500);
    } finally {
      page.mock.restore();
    }

    const probe = await server.request("GET", "/debug/forwarded", { headers: { "x-forwarded-for": "203.0.113.7", cookie: "secret" } });
    const body = probe.body as { xff: string; headers: Record<string, string> };
    assert.equal(body.xff, "203.0.113.7");
    assert.equal(body.headers.cookie, undefined);
  });
});
