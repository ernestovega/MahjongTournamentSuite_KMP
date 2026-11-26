import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization, seedTournament } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";

type Body = Record<string, unknown>;

/**
 * What the tournament and organization routes refuse that routeErrors does not try: a player or a table of a new
 * tournament that is not valid, organizers that are not a list or repeat, a venue without its fields, and an
 * organization with a field that is not valid.
 */
describe("tournament and organization refusals", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId = "";
  const organizationId = `org-refusals-${randomUUID().slice(0, 6)}`;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    await seedOrganization({ id: organizationId });
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  });
  after(async () => { await server.close(); });

  const players = Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: 1 + Math.floor(index / 4), name: `Player ${index + 1}` }));
  const tables = [1, 2, 3, 4].map((tableId) => ({ roundId: 1, tableId, playerIds: [1, 2, 3, 4].map((seat) => (tableId - 1) * 4 + seat) }));
  const newTournament: Body = {
    name: "Refused", shortName: "REF", organizerIds: [organizationId], eventStartDate: "2027-02-06", eventEndDate: "2027-02-06",
    hostCountry: "ES", hostCity: "Madrid", venueName: "Club", venueAddress: "Calle 1", contactEmail: "info@club.es",
    numPlayers: 16, numRounds: 1, numTries: 1, players, tables, roundSchedules: fullRoundSchedules(1, "2027-02-06"),
  };
  const withPlayer = (player: unknown): Body => ({ ...newTournament, players: [player, ...players.slice(1)] });
  const withTable = (table: unknown): Body => ({ ...newTournament, tables: [table, ...tables.slice(1)] });
  const goodTable = tables[0];

  const settings: Body = {
    name: "Open", shortName: "OPEN", eventStartDate: "2027-02-06", eventEndDate: "2027-02-07", hostCountry: "ES", hostCity: "Madrid",
    venueName: "Club", venueAddress: "Calle 1", contactEmail: "info@club.es",
  };
  const organization: Body = { name: `Club ${randomUUID().slice(0, 6)}`, type: "CLUB", country: "ES" };

  const cases = (): Array<{ what: string; method: string; path: string; body?: unknown; status: number; message: RegExp }> => [
    { what: "a player without a number", method: "POST", path: "/tournaments", body: withPlayer({ id: "one", team: 1 }), status: 400, message: /player id/ },
    { what: "a player without a team", method: "POST", path: "/tournaments", body: withPlayer({ id: 1, team: 1.5 }), status: 400, message: /player team/ },
    { what: "a table without a round", method: "POST", path: "/tournaments", body: withTable({ ...goodTable, roundId: null }), status: 400, message: /roundId/ },
    { what: "a table without a number", method: "POST", path: "/tournaments", body: withTable({ ...goodTable, tableId: "x" }), status: 400, message: /tableId/ },
    { what: "a table of three", method: "POST", path: "/tournaments", body: withTable({ ...goodTable, playerIds: [1, 2, 3] }), status: 400, message: /playerIds/ },
    { what: "a seat that is not a number", method: "POST", path: "/tournaments", body: withTable({ ...goodTable, playerIds: [1, 2, 3, "four"] }), status: 400, message: /playerIds entry/ },
    { what: "a table done as text", method: "POST", path: "/tournaments", body: withTable({ ...goodTable, isCompleted: "yes" }), status: 400, message: /isCompleted/ },
    { what: "a table with totals as text", method: "POST", path: "/tournaments", body: withTable({ ...goodTable, useTotalsOnly: 1 }), status: 400, message: /useTotalsOnly/ },
    { what: "organizers that are not a list", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, organizerIds: organizationId }, status: 400, message: /must be an array/ },
    { what: "organizers that are all blank", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, organizerIds: [" "] }, status: 400, message: /at least one organizer/ },
    { what: "an organizer twice", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, organizerIds: [organizationId, organizationId] }, status: 400, message: /must not repeat/ },
    { what: "settings without a venue", method: "PUT", path: `/tournaments/${tournamentId}/settings`, body: { ...settings, venueName: "" }, status: 400, message: /./ },
    { what: "an organization name that is too long", method: "POST", path: "/admin/organizations", body: { ...organization, name: "x".repeat(81) }, status: 400, message: /at most 80/ },
    { what: "an organization of an unknown type", method: "POST", path: "/admin/organizations", body: { ...organization, type: "LEAGUE" }, status: 400, message: /CLUB or FEDERATION/ },
    { what: "an organization of a country that does not exist", method: "POST", path: "/admin/organizations", body: { ...organization, country: "XX" }, status: 400, message: /country/ },
    { what: "an organization color that is not #RRGGBB", method: "POST", path: "/admin/organizations", body: { ...organization, primaryColor: "green" }, status: 400, message: /RRGGBB/ },
    { what: "a logo type without its data", method: "POST", path: "/admin/organizations", body: { ...organization, logoContentType: "image/png" }, status: 400, message: /go together/ },
    { what: "a change to an organization that does not exist", method: "PUT", path: "/admin/organizations/no-such-organization", body: organization, status: 404, message: /not found/ },
  ];

  it("answers each bad request with its status and a message", async () => {
    const wrong: string[] = [];
    for (const item of cases()) {
      const { status, body } = await server.request(item.method, item.path, { token: admin.token, body: item.body });
      const message = String((body as Body | null)?.message ?? (body as Body | null)?.error ?? "");
      if (status !== item.status || !item.message.test(message)) wrong.push(`${item.what}: HTTP ${status} ${message}`);
    }
    assert.deepEqual(wrong, []);
  });

  it("creates the tournament once the players and the tables are valid", async () => {
    const response = await server.request("POST", "/tournaments", { token: admin.token, body: newTournament });
    assert.equal(response.status, 200, JSON.stringify(response.body));
  });
});
