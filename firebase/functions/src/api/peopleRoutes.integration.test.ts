import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { createTournament } from "../services/tournamentsService";
import { createAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { validDrawTables } from "../testing/draw";
import { fullRoundSchedules } from "../testing/schedule";

/** The people routes of a tournament: the substitution of a player, set and removed, the lookup of an account, and the rename. */
describe("people routes", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId = "";

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    const tournament = await createTournament({
      name: "People open", shortName: "PPL", eventStartDate: "2026-10-01", eventEndDate: "2026-10-02",
      hostCountry: "ES", hostCity: "Madrid", isTeams: false, numPlayers: 16, numRounds: 2, numTries: 0,
      players: Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: 0, name: `Player ${index + 1}`, country: "ES" })),
      tables: validDrawTables(16, 2),
      roundSchedules: fullRoundSchedules(2) as Array<{ roundId: number; date: string | null; startTime: string | null }>,
      createdByUid: "creator",
    });
    tournamentId = tournament.id;
    await db.doc("emaPlayerRegistry/07880001").set({ firstName: "Sub", lastName: "Player", country: "ES" });
    await seatEmaId(tournamentId, 1, "07880001");
  });
  after(async () => {
    await server.close();
  });

  const substitution = (playerId: number, method: "PUT" | "DELETE", body?: unknown) =>
    server.request(method, `/tournaments/${tournamentId}/players/${playerId}/substitution`, { token: admin.token, body });

  it("sets the substitution of a player during the session and removes it", async () => {
    const set = await substitution(1, "PUT", { reason: "ILLNESS", fromRound: 2, duringSession: true, excludedFromEma: true });
    assert.equal(set.status, 200, JSON.stringify(set.body));
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/1`).get()).get("substitution")?.reason, "ILLNESS");

    const cleared = await substitution(1, "DELETE");
    assert.equal(cleared.status, 200, JSON.stringify(cleared.body));
    assert.equal((await db.doc(`tournaments/${tournamentId}/players/1`).get()).get("substitution"), undefined);
  });

  it("refuses a substitution that breaks its rules", async () => {
    const refused = async (playerId: number, body: unknown, status: number) => {
      const response = await substitution(playerId, "PUT", body);
      assert.equal(response.status, status, `${JSON.stringify(body)}: ${JSON.stringify(response.body)}`);
    };
    await refused(1, { reason: "LATE", fromRound: 1, excludedFromEma: true }, 400);
    await refused(1, { reason: "LATE", fromRound: 1, duringSession: true }, 400);
    await refused(1, { reason: "DISQUALIFIED", fromRound: 9 }, 400);
    await refused(2, { reason: "LATE", fromRound: 1 }, 409);
    await refused(99, { reason: "LATE", fromRound: 1 }, 404);
  });

  it("finds an account by its email for the members of the tournament", async () => {
    const found = await server.request("GET", `/tournaments/${tournamentId}/users/lookup?email=${encodeURIComponent(admin.email)}`, { token: admin.token });
    assert.equal(found.status, 200, JSON.stringify(found.body));
  });

  it("renames the tournament and refuses an empty name", async () => {
    const rename = (name: unknown) => server.request("PUT", `/tournaments/${tournamentId}`, { token: admin.token, body: { name } });
    assert.equal((await rename("  People cup  ")).status, 200);
    assert.equal((await db.doc(`tournaments/${tournamentId}`).get()).get("name"), "People cup");
    assert.equal((await rename(" ")).status, 400);
  });
});
