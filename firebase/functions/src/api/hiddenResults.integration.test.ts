import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { seedTournament } from "../testing/firestoreFixtures";
import { saveTableState } from "../services/tableManagerService";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

/** The results of the last round stay secret until an admin reveals them at the prize giving. */
describe("hidden results", () => {
  let server: TestServer;
  let tournamentId = "";
  let seated: TestAccount;
  let notSeated: TestAccount;
  let admin: TestAccount;

  const get = async (path: string, token?: string) => {
    const response = await fetch(server.baseUrl + path, { headers: token ? { authorization: `Bearer ${token}` } : {} });
    return { status: response.status, cache: response.headers.get("cache-control") ?? "", body: await response.json() as any };
  };
  const tableOf = (tables: Array<{ table: { roundId: number } }>, roundId: number) => tables.find((entry) => entry.table.roundId === roundId) as any;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }, { roundId: 2, tableId: 1 }], isCompleted: true });
    await db.doc(`tournaments/${tournamentId}`).update({ name: "Secret", numRounds: 2, hideResultsFromRound: 2 });
    for (const roundId of [1, 2]) {
      await saveTableState({
        tournamentId, roundId, tableId: 1, expectedVersion: 0, actor: { uid: "editor", name: "Editor" },
        tablePatch: {
          playerEastId: "1", playerSouthId: "2", playerWestId: "3", playerNorthId: "4", isCompleted: true, useTotalsOnly: true,
          playerEastScore: "30", playerSouthScore: "10", playerWestScore: "-10", playerNorthScore: "-30",
        },
        handPatches: [{ handId: 1, patch: { playerWinnerId: "1", playerLooserId: "2", handScore: "24", isDone: true } }],
      });
    }
    seated = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(seated.uid, "30000001");
    await seatEmaId(tournamentId, 1, "30000001");
    notSeated = await createAccount({ role: "PLAYER" });
    await giveEmaIdToAccount(notSeated.uid, "30000005");
    await seatEmaId(tournamentId, 5, "30000005");
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => { await server.close(); });

  it("gives the public the tables of the hidden rounds without results", async () => {
    const all = await get(`/public/tournaments/${tournamentId}/tables-with-hands`);
    assert.equal(all.status, 200);
    const visible = tableOf(all.body.tables, 1);
    const hidden = tableOf(all.body.tables, 2);
    assert.notEqual(visible.table.playerEastScore, "");
    assert.equal(visible.table.resultsHidden, undefined);
    assert.equal(hidden.table.resultsHidden, true);
    assert.equal(hidden.table.playerEastScore, "");
    assert.equal(hidden.table.playerEastPoints, "");
    assert.deepEqual(hidden.table.playerIds, [1, 2, 3, 4]);
    assert.equal(visible.hands[0].handScore, "24");
    assert.ok(hidden.hands.every((hand: { isDone: boolean; handScore: string }) => !hand.isDone && hand.handScore === ""));

    const round = await get(`/public/tournaments/${tournamentId}/rounds/2/tables-with-hands`);
    assert.equal(round.body.tables[0].table.resultsHidden, true);
    const one = await get(`/public/tournaments/${tournamentId}/tables/2/1`);
    assert.equal(one.body.table.resultsHidden, true);
    assert.equal((await get(`/public/tournaments/${tournamentId}/tables/1/1`)).body.table.resultsHidden, undefined);
    const summaries = await get(`/public/tournaments/${tournamentId}/tables`);
    assert.deepEqual(summaries.body.tables.find((t: { roundId: number }) => t.roundId === 2).bestHandScores, []);
    // A completed tournament that hides results keeps the short cache, so the reveal shows soon.
    assert.doesNotMatch(all.cache, /s-maxage=300/);
  });

  it("gives a seated player the own hidden table with its results, and nothing to the others", async () => {
    const own = await get(`/player/tournaments/${tournamentId}/my-hidden-tables`, seated.token);
    assert.equal(own.status, 200);
    assert.match(own.cache, /no-store/);
    assert.equal(own.body.tables.length, 1);
    assert.equal(own.body.tables[0].table.roundId, 2);
    assert.notEqual(own.body.tables[0].table.playerEastScore, "");
    assert.equal(own.body.tables[0].hands[0].handScore, "24");

    assert.deepEqual((await get(`/player/tournaments/${tournamentId}/my-hidden-tables`, notSeated.token)).body.tables, []);
    assert.equal((await get(`/player/tournaments/${tournamentId}/my-hidden-tables`)).status, 401);
  });

  it("lets only the staff reveal the results, and the reveal shows them to everyone", async () => {
    const refused = await server.request("POST", `/tournaments/${tournamentId}/results-visibility`, { token: seated.token, body: { revealed: true } });
    assert.equal(refused.status, 403);
    const wrong = await server.request("POST", `/tournaments/${tournamentId}/results-visibility`, { token: admin.token, body: { revealed: "yes" } });
    assert.equal(wrong.status, 400);

    const drawBefore = (await get(`/public/tournaments/${tournamentId}/sync/manifest`)).body.resources.draw.revision;
    const revealed = await server.request("POST", `/tournaments/${tournamentId}/results-visibility`, { token: admin.token, body: { revealed: true } });
    assert.equal(revealed.status, 200);
    const tournament = (await get(`/public/tournaments/${tournamentId}`)).body;
    assert.equal(tournament.resultsRevealed, true);
    assert.equal(tournament.resultsHiddenFromRound, null);
    assert.equal(tournament.hideResultsFromRound, 2);
    // The apps drop the masked tables they hold.
    assert.equal((await get(`/public/tournaments/${tournamentId}/sync/manifest`)).body.resources.draw.revision, drawBefore + 1);
    const all = await get(`/public/tournaments/${tournamentId}/tables-with-hands`);
    assert.notEqual(tableOf(all.body.tables, 2).table.playerEastScore, "");
    assert.deepEqual((await get(`/player/tournaments/${tournamentId}/my-hidden-tables`, seated.token)).body.tables, []);

    // Hidden again after a reveal by mistake.
    await server.request("POST", `/tournaments/${tournamentId}/results-visibility`, { token: admin.token, body: { revealed: false } });
    assert.equal(tableOf((await get(`/public/tournaments/${tournamentId}/tables-with-hands`)).body.tables, 2).table.resultsHidden, true);
  });

  it("refuses a reveal in a tournament that hides nothing", async () => {
    const open = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const answer = await server.request("POST", `/tournaments/${open}/results-visibility`, { token: admin.token, body: { revealed: true } });
    assert.equal(answer.status, 400);
  });
});
