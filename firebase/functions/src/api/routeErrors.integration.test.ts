import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

type Body = Record<string, unknown>;

/**
 * The refusals of the tournament routes: a bad number in the path, a body that is missing a field, a tournament
 * that does not exist. Each answer has its status and a message, and changes nothing.
 */
describe("tournament routes refuse bad requests", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId = "";

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  });
  after(async () => { await server.close(); });

  const settings: Body = {
    name: "Open", shortName: "OPEN", eventStartDate: "2027-02-06", eventEndDate: "2027-02-07", hostCountry: "ES", hostCity: "Madrid",
    venueName: "Club", venueAddress: "Calle 1", contactEmail: "info@club.es",
  };

  const cases = (): Array<{ what: string; method: string; path: string; body?: unknown; status: number; message?: RegExp }> => [
    { what: "settings without a name", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, name: " " }, status: 400, message: /name is required/ },
    { what: "a short name of 11 characters", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, shortName: "ABCDEFGHIJK" }, status: 400, message: /1 to 10/ },
    { what: "an end before the start", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, eventEndDate: "2027-02-05" }, status: 400, message: /dates/ },
    { what: "a country that does not exist", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, hostCountry: "XX" }, status: 400, message: /country/ },
    { what: "settings without a city", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, hostCity: "" }, status: 400, message: /city/ },
    { what: "a rename to nothing", method: "PUT", path: `/tournaments/${tournamentId}`, body: { name: "  " }, status: 400, message: /name is required/ },
    { what: "a new tournament without fields", method: "POST", path: "/tournaments", body: { name: "X" }, status: 400, message: /Missing or invalid/ },
    { what: "a clock of round 0", method: "POST", path: `/tournaments/${tournamentId}/rounds/0/clock`, body: { action: "START" }, status: 400, message: /positive integer/ },
    { what: "a control of round -1", method: "POST", path: `/tournaments/${tournamentId}/rounds/-1/control`, body: {}, status: 400, message: /positive integer/ },
    { what: "the tables of round abc", method: "GET", path: `/tournaments/${tournamentId}/rounds/abc/tables-with-hands`, status: 400, message: /positive integer/ },
    { what: "table 0", method: "GET", path: `/tournaments/${tournamentId}/tables/1/0`, status: 400, message: /positive integers/ },
    { what: "a reset of table x", method: "POST", path: `/tournaments/${tournamentId}/tables/1/x/reset`, status: 400, message: /positive integers/ },
    { what: "a lookup without an email", method: "GET", path: `/tournaments/${tournamentId}/users/lookup?email=nobody`, status: 400, message: /email/ },
    { what: "player number 0", method: "PUT", path: `/tournaments/${tournamentId}/players/0`, body: { emaId: "10000001" }, status: 400, message: /positive integer/ },
    { what: "an EMA player who does not exist", method: "PUT", path: `/tournaments/${tournamentId}/players/1`, body: { emaId: "99999999" }, status: 400, message: /does not exist/ },
    { what: "a substitution without a reason", method: "PUT", path: `/tournaments/${tournamentId}/players/1/substitution`, body: {}, status: 400, message: /reason/ },
    { what: "removing the substitution of player -2", method: "DELETE", path: `/tournaments/${tournamentId}/players/-2/substitution`, status: 400, message: /positive integer/ },
    { what: "team 0", method: "PUT", path: `/tournaments/${tournamentId}/teams/0`, body: { name: "A", emaIds: [] }, status: 400, message: /teamId/ },
    { what: "a team without a name", method: "PUT", path: `/tournaments/${tournamentId}/teams/1`, body: { name: "", emaIds: [] }, status: 400, message: /1 to 80/ },
    { what: "a team of five", method: "PUT", path: `/tournaments/${tournamentId}/teams/1`, body: { name: "A", emaIds: ["1", "2", "3", "4", "5"] }, status: 400, message: /four/ },
    { what: "one player in two slots", method: "PUT", path: `/tournaments/${tournamentId}/teams/1`, body: { name: "A", emaIds: ["10000001", "10000001"] }, status: 400 },
    { what: "the players of a tournament that does not exist", method: "GET", path: "/tournaments/no-such-tournament/players", status: 404, message: /not found/ },
    { what: "the teams of a tournament that does not exist", method: "GET", path: "/tournaments/no-such-tournament/teams", status: 404, message: /not found/ },
    { what: "the settings of a tournament that does not exist", method: "PUT", path: "/tournaments/no-such-tournament/settings", body: settings, status: 404 },
  ];

  it("answers each bad request with its status and a message", async () => {
    const wrong: string[] = [];
    for (const item of cases()) {
      const { status, body } = await server.request(item.method, item.path, { token: admin.token, body: item.body });
      const message = String((body as Body | null)?.message ?? (body as Body | null)?.error ?? "");
      if (status !== item.status || (item.message && !item.message.test(message))) {
        wrong.push(`${item.what}: HTTP ${status} ${message}`);
      }
    }
    assert.deepEqual(wrong, []);
  });

  it("a refused change leaves the tournament as it was", async () => {
    const before = (await server.request("GET", `/tournaments/${tournamentId}/players`, { token: admin.token })).body;
    await server.request("PUT", `/tournaments/${tournamentId}/players/1`, { token: admin.token, body: { emaId: "99999999" } });
    const now = (await server.request("GET", `/tournaments/${tournamentId}/players`, { token: admin.token })).body;
    assert.deepEqual(now, before);
  });
});
