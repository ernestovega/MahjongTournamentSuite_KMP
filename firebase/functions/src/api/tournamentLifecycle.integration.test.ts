import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { setRoundCloseEnqueuer } from "../services/matchTimeTaskService";
import { createTournament } from "../services/tournamentsService";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { validDrawTables } from "../testing/draw";
import { fullRoundSchedules } from "../testing/schedule";

/**
 * A whole tournament through the API, as the apps play it: an admin creates it and assigns the EMA players, the Timer
 * starts each round, the players set the seats and write every hand from their own phones, and the last hand of the
 * last table finishes the tournament. The final standings come from the public routes.
 */
const PLAYERS = 16;
const ROUNDS = 2;

type Table = { roundId: number; tableId: number; playerIds: number[] };

describe("a tournament from its creation to the final ranking", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId: string;
  let tables: Table[];
  /** The player account and the EMA id of every slot. */
  const accounts = new Map<number, { account: TestAccount; emaId: string }>();
  const emaPrefix = String(70_000_000 + Math.floor(Math.random() * 9_000_000)).slice(0, 6);

  before(async () => {
    requireAuthEmulator();
    // A Play plans the close of the round with Cloud Tasks. Nothing runs here.
    setRoundCloseEnqueuer(async () => undefined);
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
  });
  after(async () => {
    setRoundCloseEnqueuer();
    await server.close();
  });

  const api = (method: string, path: string, token: string | null, body?: unknown) =>
    server.request(method, path, { token, body });

  async function tableOf(roundId: number, tableId: number) {
    const reply = await api("GET", `/tournaments/${tournamentId}/tables/${roundId}/${tableId}`, admin.token);
    assert.equal(reply.status, 200, JSON.stringify(reply.body));
    return (reply.body as { table: Record<string, unknown> }).table;
  }

  /** One player of the table writes a hand from the phone. */
  async function playerWrites(table: Table, writer: number, hand: Record<string, unknown>) {
    const current = await tableOf(table.roundId, table.tableId);
    return api(
      "POST",
      `/player/tournaments/${tournamentId}/tables/${table.roundId}/${table.tableId}/hands`,
      accounts.get(writer)!.account.token,
      { isChickenHand: false, isDraw: false, isLastHand: false, playerLooserId: "", ...hand, expectedVersion: Number(current.version ?? 0) },
    );
  }

  it("creates the tournament with every table empty and every slot open", async () => {
    tables = validDrawTables(PLAYERS, ROUNDS);
    const tournament = await createTournament({
      name: "Lifecycle open",
      shortName: "LIFE",
      primaryColor: "#02B16B",
      eventStartDate: "2026-10-01",
      eventEndDate: "2026-10-01",
      hostCountry: "ES",
      hostCity: "Madrid",
      isTeams: false,
      numPlayers: PLAYERS,
      numRounds: ROUNDS,
      numTries: 0,
      players: Array.from({ length: PLAYERS }, (_, index) => ({ id: index + 1, team: 0 })),
      tables,
      roundSchedules: fullRoundSchedules(ROUNDS),
      timeZone: "UTC",
      matchDurationMinutes: 120,
      matchEndMode: "STOP",
      matchExtraMinutes: 15,
      createdByUid: admin.uid,
    });
    tournamentId = tournament.id;

    const stored = await db.doc(`tournaments/${tournamentId}`).get();
    assert.equal(stored.get("isCompleted"), false);
    const reply = await api("GET", `/public/tournaments/${tournamentId}/round-clocks`, null);
    assert.equal(reply.status, 200);
    assert.deepEqual((reply.body as { roundClocks: unknown[] }).roundClocks, []);
  });

  it("assigns an EMA player with an account to every slot", async () => {
    for (let slot = 1; slot <= PLAYERS; slot++) {
      const emaId = `${emaPrefix}${String(slot).padStart(2, "0")}`;
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: `Life${slot}`, lastName: "PLAYER", country: "ESP" });
      const account = await createAccount({ role: "PLAYER" });
      await giveEmaIdToAccount(account.uid, emaId);
      const reply = await api("PUT", `/tournaments/${tournamentId}/players/${slot}`, admin.token, { emaId });
      assert.equal(reply.status, 200, JSON.stringify(reply.body));
      accounts.set(slot, { account, emaId });
    }
  });

  it("refuses the hands before the starting gong", async () => {
    const [first] = tables;
    const reply = await playerWrites(first, first.playerIds[0], { playerWinnerId: String(first.playerIds[0]), handScore: "8" });
    assert.equal(reply.status, 409, JSON.stringify(reply.body));
  });

  for (let round = 1; round <= ROUNDS; round++) {
    it(`plays round ${round}: Play on the Timer, the seats and 16 hands at every table from the phones`, async () => {
      const play = await api("POST", `/tournaments/${tournamentId}/rounds/${round}/clock`, admin.token, { action: "start" });
      assert.equal(play.status, 200, JSON.stringify(play.body));

      for (const table of tables.filter((item) => item.roundId === round)) {
        // The first player of the draw sets the seats in the order of the draw.
        const [east, south, west, north] = table.playerIds;
        const current = await tableOf(round, table.tableId);
        const seats = await api(
          "POST",
          `/player/tournaments/${tournamentId}/tables/${round}/${table.tableId}/seats`,
          accounts.get(east)!.account.token,
          { playerEastId: String(east), playerSouthId: String(south), playerWestId: String(west), playerNorthId: String(north), expectedVersion: Number(current.version ?? 0) },
        );
        assert.equal(seats.status, 200, JSON.stringify(seats.body));

        // Slot 1 wins every hand of its tables by self draw. The other tables only draw.
        const winsAll = table.playerIds.includes(1);
        for (let hand = 1; hand <= 16; hand++) {
          // Every seat writes some hands, as different phones of the table do.
          const writer = table.playerIds[hand % 4];
          const reply = await playerWrites(table, writer, winsAll ? { playerWinnerId: "1", handScore: "8" } : { isDraw: true });
          assert.equal(reply.status, 200, `round ${round} table ${table.tableId} hand ${hand}: ${JSON.stringify(reply.body)}`);
        }

        // The phone does not offer "last hand" at hand 16. The sixteenth hand completes the table anyway.
        const done = await tableOf(round, table.tableId);
        assert.equal(done.isCompleted, true, `round ${round} table ${table.tableId} is not completed after 16 hands`);
        const more = await playerWrites(table, east, { isDraw: true });
        assert.equal(more.status, 409, JSON.stringify(more.body));
      }

      const stored = await db.doc(`tournaments/${tournamentId}`).get();
      assert.equal(stored.get("isCompleted"), round === ROUNDS, `after round ${round}`);
    });
  }

  it("shows the final ranking: the player who won every hand is first", async () => {
    const winner = accounts.get(1)!;
    const reply = await api("GET", `/public/statistics/persons/${winner.emaId}/standings?tournaments=${tournamentId}`, null);
    assert.equal(reply.status, 200, JSON.stringify(reply.body));
    const [standing] = (reply.body as { standings: Array<{ rank: number; of: number; points: number }> }).standings;
    assert.equal(standing.rank, 1);
    assert.equal(standing.of, PLAYERS);
    assert.equal(standing.points, 4 * ROUNDS);

    // A player who only drew gets 1,75 points per round.
    const drawer = [...accounts.keys()].find((slot) => tables.every((table) => !table.playerIds.includes(1) || !table.playerIds.includes(slot)))!;
    const other = await api("GET", `/public/statistics/persons/${accounts.get(drawer)!.emaId}/standings?tournaments=${tournamentId}`, null);
    const [drawStanding] = (other.body as { standings: Array<{ rank: number; points: number }> }).standings;
    assert.equal(drawStanding.points, 1.75 * ROUNDS);
  });

  it("refuses hands and seats after the tournament is completed", async () => {
    const last = tables[tables.length - 1];
    const reply = await playerWrites(last, last.playerIds[0], { isDraw: true });
    assert.equal(reply.status, 409, JSON.stringify(reply.body));
  });
});
