import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it, mock } from "node:test";

import { db } from "../firebase";
import * as playerHandService from "../services/playerHandService";
import * as roundClockService from "../services/roundClockService";
import * as usersService from "../services/usersService";
import { seedOrganization, seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

const LONG_ID = "x".repeat(201);

/** Route edges that the other tests leave out: the answers of the round control, and the ids and tokens that do not fit. */
describe("route edges", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId = "";

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
  });
  after(async () => {
    await server.close();
  });

  it("answers the round control and the closing of open tables with what the service did", async () => {
    const control = mock.method(roundClockService, "updateRoundControl", async () => ({ changed: true }));
    const complete = mock.method(roundClockService, "completeTables", async () => ({ completed: 1 }));
    try {
      const changed = await server.request("POST", `/tournaments/${tournamentId}/rounds/1/control`, { token: admin.token, body: { completeTables: [1] } });
      assert.equal(changed.status, 200);
      assert.deepEqual(changed.body, { changed: true });
      const call = control.mock.calls[0].arguments[0] as unknown as { tournamentId: string; roundId: number; change: { completeTables: number[] } };
      assert.equal(call.tournamentId, tournamentId);
      assert.equal(call.roundId, 1);
      assert.deepEqual(call.change.completeTables, [1]);

      const closed = await server.request("POST", `/tournaments/${tournamentId}/complete-open-tables`, {
        token: admin.token, body: { tables: [{ roundId: 1, tableId: 1 }] },
      });
      assert.equal(closed.status, 200);
      assert.deepEqual(closed.body, { completed: 1 });
      assert.deepEqual((complete.mock.calls[0].arguments[0] as unknown as { tables: unknown }).tables, [{ roundId: 1, tableId: 1 }]);
    } finally {
      control.mock.restore();
      complete.mock.restore();
    }
  });

  it("refuses a round of the table list that is not a positive whole number", async () => {
    for (const roundId of ["abc", "0", "1.5"]) {
      const response = await server.request("GET", `/tournaments/${tournamentId}/tables?roundId=${roundId}`, { token: admin.token });
      assert.equal(response.status, 400, roundId);
    }
  });

  it("refuses a tournament id that cannot name a document on the open routes", async () => {
    assert.equal((await server.request("GET", `/public/tournaments/${LONG_ID}`)).status, 400);
    assert.equal((await server.request("GET", `/registrations/${LONG_ID}/me`, { token: admin.token })).status, 400);
  });

  it("refuses a registration request with a token that is not valid", async () => {
    const response = await server.request("GET", `/registrations/${tournamentId}/me`, { token: "not-a-token" });
    assert.equal(response.status, 401);
  });

  it("keeps a club admin from making a player of an account that also runs a club it does not manage", async () => {
    const [mine, theirs] = [`org-${randomUUID()}`, `org-${randomUUID()}`];
    await seedOrganization({ id: mine });
    await seedOrganization({ id: theirs });
    const clubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: [mine] });
    const shared = await createAccount({ role: "CLUB_ADMIN", organizations: [mine, theirs] });
    const response = await server.request("PUT", `/admin/users/${shared.uid}`, {
      token: clubAdmin.token, body: { role: "PLAYER", email: shared.email, emaId: "07720001" },
    });
    assert.equal(response.status, 403);
    assert.match(String((response.body as { message?: string }).message), /cannot make it a player/);
  });

  it("refuses a player account as an editor of a tournament, and the removal of a super admin from it", async () => {
    const player = await createAccount({ role: "PLAYER" });
    const asEditor = await server.request("PUT", `/tournaments/${tournamentId}/members/${player.uid}`, { token: admin.token });
    assert.equal(asEditor.status, 400);
    assert.match(String((asEditor.body as { message?: string }).message), /player account cannot be assigned/);
    const removed = await server.request("DELETE", `/tournaments/${tournamentId}/members/${admin.uid}`, { token: admin.token });
    assert.equal(removed.status, 400);
    assert.match(String((removed.body as { message?: string }).message), /Super admins cannot be removed/);
  });

  it("refuses the score sheets of the tables of a tournament without tables", async () => {
    const empty = await seedTournament({ tables: [] });
    await db.doc(`tournaments/${empty}`).update({ numPlayers: 0 });
    const response = await server.request("GET", `/tournaments/${empty}/score-sheets?mode=tables`, { token: admin.token });
    assert.equal(response.status, 409);
  });

  it("answers a penalty of a registered player with what the service saved", async () => {
    const player = await createAccount({ role: "PLAYER", playerOf: [tournamentId] });
    const saving = mock.method(playerHandService, "addPenalties", async () => ({ version: 2 }));
    try {
      const response = await server.request("POST", `/player/tournaments/${tournamentId}/tables/1/1/penalties`, {
        token: player.token, body: { playerEastPenalty: "-10", expectedVersion: 1 },
      });
      assert.equal(response.status, 200);
      assert.deepEqual(response.body, { version: 2 });
      const call = saving.mock.calls[0].arguments[0] as unknown as { uid: string; expectedVersion: number; roundId: number };
      assert.equal(call.uid, player.uid);
      assert.equal(call.expectedVersion, 1);
    } finally {
      saving.mock.restore();
    }
  });

  it("keeps the last enabled super admin from deleting the own account", async () => {
    const last = await createAccount({ role: "SUPER_ADMIN" });
    const count = mock.method(usersService, "countEnabledSuperAdmins", async () => 1);
    try {
      const response = await server.request("DELETE", "/auth/me", { token: last.token });
      assert.equal(response.status, 400);
      assert.match(String((response.body as { message?: string }).message), /last enabled super admin/);
    } finally {
      count.mock.restore();
    }
  });

  it("lists a registration that does not fit among the problems of a new tournament", async () => {
    const response = await server.request("POST", "/tournaments", {
      token: admin.token,
      body: { name: "Open", shortName: "OPEN", numRounds: 3, registration: { maxPlayers: 2, slug: "" } },
    });
    assert.equal(response.status, 400);
    const issues = (response.body as { details?: { issues?: Array<{ field: string }> } }).details?.issues ?? [];
    assert.ok(issues.some((issue) => issue.field === "registration"), JSON.stringify(response.body));
  });

  it("reads the rows of a draw and of a change of players that miss fields or are not objects", async () => {
    const wrong: string[] = [];
    for (const route of ["draw", "player-count"]) {
      for (const [body, message] of [
        [{ players: [null], tables: [] }, /Invalid player id/],
        [{ players: [{ id: 1 }], tables: [{ roundId: 1, tableId: 1 }] }, /Invalid table playerIds/],
        [{ players: [{ id: 1 }], tables: [null] }, /Invalid table playerIds/],
      ] as const) {
        const response = await server.request("POST", `/tournaments/${tournamentId}/${route}`, { token: admin.token, body });
        const text = String((response.body as { message?: string }).message);
        if (response.status !== 400 || !message.test(text)) wrong.push(`${route} ${JSON.stringify(body)}: ${response.status} ${text}`);
      }
    }
    // Without removed slots and waiting people, the change reaches the tournament, which has no players to change.
    const change = await server.request("POST", `/tournaments/${tournamentId}/player-count`, {
      token: admin.token, body: { players: [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }], tables: [{ roundId: 1, tableId: 1, playerIds: [1, 2, 3, 4] }] },
    });
    if (change.status >= 500 || change.status === 200) wrong.push(`player-count: ${change.status} ${JSON.stringify(change.body)}`);
    assert.deepEqual(wrong, []);
  });
});
