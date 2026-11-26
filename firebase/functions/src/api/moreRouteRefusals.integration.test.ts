import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { setPasswordResetSenderForTests } from "../services/firebaseAuthRest";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

type Body = Record<string, unknown>;

/**
 * More refusals of the routes, after routeErrors: a field that is missing or not valid in a photo, an EMA player,
 * the staff names, the EMA report, the statistics, a draw and a change of the player count.
 */
describe("more route refusals", () => {
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

  const tooManyNames = Array.from({ length: 201 }, (_, index) => `Staff ${index}`);
  const tooManyTournaments = Array.from({ length: 26 }, (_, index) => `t${index}`).join(",");
  const draw = { numTries: 1, players: [], tables: [], assignments: [] };

  const cases = (): Array<{ what: string; method: string; path: string; body?: unknown; status: number; message: RegExp }> => [
    { what: "a photo without data", method: "PUT", path: "/auth/me/photo", body: { contentType: "image/png" }, status: 400, message: /Photo data is required/ },
    { what: "a new EMA player without a last name", method: "POST", path: "/ema-player-registry", body: { emaId: "19000001", firstName: "Ana", lastName: " " }, status: 400, message: /First name and last name/ },
    { what: "a new EMA player of a two-letter country", method: "POST", path: "/ema-player-registry", body: { emaId: "19000001", firstName: "Ana", lastName: "Lopez", country: "ES" }, status: 400, message: /three-letter/ },
    { what: "an EMA player changed without a first name", method: "PUT", path: "/ema-player-registry/19000001", body: { firstName: "", lastName: "Lopez" }, status: 400, message: /First name and last name/ },
    { what: "an EMA player changed to a two-letter country", method: "PUT", path: "/ema-player-registry/19000001", body: { firstName: "Ana", lastName: "Lopez", country: "ES" }, status: 400, message: /three-letter/ },
    { what: "a registration link that is not a link name", method: "GET", path: "/public/registration-links/Not_A_Link", status: 404, message: /not found/ },
    { what: "201 staff names", method: "PUT", path: `/tournaments/${tournamentId}/id-cards/staff-names`, body: { names: tooManyNames }, status: 400, message: /Too many staff names/ },
    { what: "an EMA report without rows", method: "POST", path: `/tournaments/${tournamentId}/ema-report`, body: { rows: [] }, status: 400, message: /rows are required/ },
    { what: "the statistics of an EMA id that is too long", method: "GET", path: `/tournaments/statistics/persons/${"1".repeat(65)}`, status: 400, message: /Invalid EMA id/ },
    { what: "the standings of 26 tournaments", method: "GET", path: `/tournaments/statistics/persons/10000001/standings?tournaments=${tooManyTournaments}`, status: 400, message: /Too many tournaments/ },
    { what: "a draw with tries as text", method: "POST", path: `/tournaments/${tournamentId}/draw`, body: { ...draw, numTries: "many" }, status: 400, message: /Invalid numTries/ },
    { what: "a draw with a table of three", method: "POST", path: `/tournaments/${tournamentId}/draw`, body: { ...draw, tables: [{ roundId: 1, tableId: 1, playerIds: [1, 2, 3] }] }, status: 400, message: /Invalid table playerIds/ },
    { what: "a new player count with a table of three", method: "POST", path: `/tournaments/${tournamentId}/player-count`, body: { players: [], tables: [{ roundId: 1, tableId: 1, playerIds: [1, 2, 3] }] }, status: 400, message: /Invalid table playerIds/ },
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

  it("answers a password reset for an email that is not valid with INVALID_EMAIL, and passes on other errors", async () => {
    setPasswordResetSenderForTests(async () => { throw new Error("INVALID_EMAIL : bad"); });
    const response = await server.request("POST", "/auth/passwordReset", { body: { email: "not an email" } });
    assert.equal(response.status, 400, JSON.stringify(response.body));
    assert.match(JSON.stringify(response.body), /INVALID_EMAIL/);
    setPasswordResetSenderForTests(async () => { throw new Error("QUOTA_EXCEEDED"); });
    const failure = await server.request("POST", "/auth/passwordReset", { body: { email: "someone@example.test" } });
    assert.equal(failure.status, 500);
    setPasswordResetSenderForTests(null);
  });
});
