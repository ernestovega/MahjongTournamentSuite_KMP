import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { ACTIVITY_COLLECTION } from "../services/activityLog";
import { createTournament } from "../services/tournamentsService";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { validDrawTables } from "../testing/draw";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";

/**
 * The route that changes the number of players of a tournament before its first hand, through the real app: who
 * may call it, how it reads the body, and what it records. The service rules are in playerCountChange.integration.
 */
describe("change of the number of players through the API", () => {
  let server: TestServer;
  let superAdmin: TestAccount;
  let clubAdmin: TestAccount;
  let foreignClubAdmin: TestAccount;
  let player: TestAccount;

  before(async () => {
    requireAuthEmulator();
    await seedOrganization({ id: "org-es", country: "ES" });
    await seedOrganization({ id: "org-se", country: "SE" });
    server = await startServer();
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    clubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    foreignClubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-se"] });
    player = await createAccount({ role: "PLAYER" });
  });
  after(async () => server.close());

  async function tournamentOf(numPlayers: number) {
    const tournament = await createTournament({
      name: "Player count API", shortName: "PCAPI", organizerIds: ["org-es"], eventStartDate: "2027-02-01", eventEndDate: "2027-02-01",
      hostCountry: "ES", hostCity: "Madrid", isTeams: false, numPlayers, numRounds: 2, numTries: 1,
      players: Array.from({ length: numPlayers }, (_, index) => ({ id: index + 1, team: 0 })),
      tables: validDrawTables(numPlayers, 2), roundSchedules: fullRoundSchedules(2, "2027-02-01"), createdByUid: superAdmin.uid,
    });
    return tournament.id;
  }

  const body = (numPlayers: number, removedSlotIds: number[] = []) => ({
    numTries: 12,
    players: Array.from({ length: numPlayers }, (_, index) => ({ id: index + 1, team: 0 })),
    tables: validDrawTables(numPlayers, 2),
    removedSlotIds,
    waitingEmaIds: [],
  });

  it("lets an admin and the club admin of the organizer change it, and records the activity", async () => {
    const id = await tournamentOf(20);
    const reply = await server.request("POST", `/tournaments/${id}/player-count`, { token: clubAdmin.token, body: body(16, [17, 18, 19, 20]) });
    assert.equal(reply.status, 200, JSON.stringify(reply.body));
    assert.equal((reply.body as { numPlayers: number }).numPlayers, 16);
    assert.equal((reply.body as { hasTableData: boolean }).hasTableData, false);

    const again = await server.request("POST", `/tournaments/${id}/player-count`, { token: superAdmin.token, body: body(20) });
    assert.equal(again.status, 200, JSON.stringify(again.body));
    assert.equal((again.body as { numPlayers: number }).numPlayers, 20);

    const activity = await db.collection(ACTIVITY_COLLECTION).where("tournamentId", "==", id).get();
    const entries = activity.docs.map((doc) => doc.data()).filter((entry) => entry.action === "tournament.playerCount");
    assert.equal(entries.length, 2);
    assert.deepEqual(entries.map((entry) => [entry.details.from, entry.details.to]).sort(), [[16, 20], [20, 16]]);
    const removal = entries.find((entry) => entry.details.to === 16);
    assert.deepEqual(removal?.details.removed, ["#17", "#18", "#19", "#20"]);
  });

  it("refuses people without the permission in that tournament", async () => {
    const id = await tournamentOf(20);
    const anonymous = await server.request("POST", `/tournaments/${id}/player-count`, { token: null, body: body(16, [1, 2, 3, 4]) });
    assert.equal(anonymous.status, 401);
    for (const account of [foreignClubAdmin, player]) {
      const reply = await server.request("POST", `/tournaments/${id}/player-count`, { token: account.token, body: body(16, [1, 2, 3, 4]) });
      assert.equal(reply.status, 403, JSON.stringify(reply.body));
    }
    assert.equal((await db.doc(`tournaments/${id}`).get()).get("numPlayers"), 20);
  });

  it("answers 400 to a body that is not a draw, and 409 once a table has data", async () => {
    const id = await tournamentOf(20);
    const api = (payload: unknown) => server.request("POST", `/tournaments/${id}/player-count`, { token: superAdmin.token, body: payload });
    assert.equal((await api({ ...body(16, [1, 2, 3, 4]), players: "x" })).status, 400);
    assert.equal((await api({ ...body(16, [1, 2, 3, 4]), removedSlotIds: ["a"] })).status, 400);
    assert.equal((await api({ ...body(24), waitingEmaIds: ["not-an-ema"] })).status, 400);
    const badTable = body(16, [1, 2, 3, 4]);
    badTable.tables[0] = { ...badTable.tables[0], playerIds: [1, 2, 3] };
    assert.equal((await api(badTable)).status, 400);

    await db.doc(`tournaments/${id}`).update({ tablesWithProgress: 1 });
    const started = await api(body(16, [1, 2, 3, 4]));
    assert.equal(started.status, 409);
    assert.equal((started.body as { details?: { code?: string } }).details?.code ?? (started.body as { code?: string }).code, "TOURNAMENT_STARTED");
  });
});
