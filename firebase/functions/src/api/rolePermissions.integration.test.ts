import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth } from "../firebase";
import { requireAdmin } from "./middleware/requireAdmin";
import { requireEditor } from "./middleware/requireEditor";
import { requireRegisteredPlayer } from "./middleware/requireRegisteredPlayer";
import { requireTournamentReader } from "./middleware/requireTournamentReader";
import { requireAuth } from "./middleware/requireAuth";
import { requireTournamentEditor } from "./middleware/requireTournamentEditor";
import { buildApp } from "./app";
import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, signInTestAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";

/**
 * Who may call what. The table is written by hand, so it states the intended rules.
 * A test compares it with the guards of the real routes. A new route fails that test until it is listed here.
 */
type Access = "PUBLIC" | "SIGNED_IN" | "EDITOR" | "READER" | "PLAYER" | "MEMBER" | "ADMIN";

const MATRIX: Array<[string, string, Access]> = [
  ["GET", "/health", "PUBLIC"],
  ["GET", "/version", "PUBLIC"],
  ["GET", "/config/", "PUBLIC"],
  ["POST", "/auth/signIn", "PUBLIC"],
  ["POST", "/auth/passwordReset", "PUBLIC"],
  ["POST", "/auth/refresh", "PUBLIC"],
  ["POST", "/admin/bootstrapAdmin", "PUBLIC"],
  ["POST", "/player/invitations/accept", "PUBLIC"],
  ["GET", "/auth/me", "SIGNED_IN"],
  ["GET", "/admin/whoami", "SIGNED_IN"],
  ["GET", "/countries/", "SIGNED_IN"],
  ["GET", "/ema-player-registry/", "SIGNED_IN"],
  ["GET", "/admin/users", "EDITOR"],
  ["POST", "/admin/users", "EDITOR"],
  ["PUT", "/admin/users/:uid", "EDITOR"],
  ["GET", "/admin/users/lookup", "EDITOR"],
  ["GET", "/sync/manifest", "SIGNED_IN"],
  ["GET", "/tournaments/", "SIGNED_IN"],
  ["POST", "/tournaments/id-card-proof", "EDITOR"],
  ["POST", "/tournaments/id-card-preview", "EDITOR"],
  ["GET", "/tournaments/statistics/overview", "ADMIN"],
  ["GET", "/tournaments/statistics/me", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/me/standings", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/persons/:emaId", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/persons/:emaId/standings", "SIGNED_IN"],
  ["POST", "/ema-player-registry/", "ADMIN"],
  ["PUT", "/ema-player-registry/:emaId", "ADMIN"],
  ["PUT", "/ema-player-registry/:emaId/photo", "ADMIN"],
  ["PUT", "/admin/users/:uid/disabled", "ADMIN"],
  ["GET", "/admin/usage", "ADMIN"],
  ["POST", "/tournaments/", "ADMIN"],
  ["PUT", "/tournaments/:tournamentId/settings", "ADMIN"],
  ["DELETE", "/tournaments/:tournamentId", "ADMIN"],
  ["PUT", "/tournaments/:tournamentId", "ADMIN"],
  ["POST", "/tournaments/:tournamentId/tables/:roundId/:tableId/reset", "ADMIN"],
  ["GET", "/tournaments/:tournamentId/sync/manifest", "READER"],
  ["GET", "/tournaments/:tournamentId/id-cards", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/id-list", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/integrity", "MEMBER"],
  ["POST", "/tournaments/:tournamentId/ema-report", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/users/lookup", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/members", "MEMBER"],
  ["PUT", "/tournaments/:tournamentId/members/:uid", "MEMBER"],
  ["DELETE", "/tournaments/:tournamentId/members/:uid", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/players", "READER"],
  ["PUT", "/tournaments/:tournamentId/players/:playerId", "MEMBER"],
  ["PUT", "/tournaments/:tournamentId/players/:playerId/substitution", "MEMBER"],
  ["DELETE", "/tournaments/:tournamentId/players/:playerId/substitution", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/player-invitations", "MEMBER"],
  ["POST", "/tournaments/:tournamentId/player-accounts/send", "MEMBER"],
  ["POST", "/tournaments/:tournamentId/player-accounts/resend", "MEMBER"],
  ["POST", "/tournaments/:tournamentId/player-accounts/revoke", "MEMBER"],
  ["PUT", "/tournaments/:tournamentId/player-accounts/emails", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/teams", "READER"],
  ["PUT", "/tournaments/:tournamentId/teams/:teamId", "MEMBER"],
  ["GET", "/tournaments/:tournamentId/rounds", "READER"],
  ["GET", "/tournaments/:tournamentId/tables", "READER"],
  ["GET", "/tournaments/:tournamentId/tables-with-hands", "READER"],
  ["GET", "/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", "READER"],
  ["GET", "/tournaments/:tournamentId/tables/:roundId/:tableId", "READER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/hands/preview", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/penalties", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/hands", "PLAYER"],
  ["PUT", "/tournaments/:tournamentId/tables/:roundId/:tableId/state", "MEMBER"],
  ["GET", "/tournaments/:tournamentId", "MEMBER"],
];

function actualGuards(): Map<string, Access> {
  const app = buildApp() as unknown as { _router: { stack: any[] } };
  const result = new Map<string, Access>();
  const walk = (layer: any, prefix: string) => {
    if (layer.route) {
      const handlers: unknown[] = layer.route.stack.map((item: any) => item.handle);
      const access: Access = handlers.includes(requireAdmin) ? "ADMIN"
        : handlers.includes(requireRegisteredPlayer) ? "PLAYER"
        : handlers.includes(requireTournamentReader) ? "READER"
        : handlers.includes(requireTournamentEditor) ? "MEMBER"
        : handlers.includes(requireEditor) ? "EDITOR"
        : handlers.includes(requireAuth) ? "SIGNED_IN"
        : "PUBLIC";
      for (const method of Object.keys(layer.route.methods)) result.set(`${method.toUpperCase()} ${prefix}${layer.route.path}`, access);
    } else if (layer.name === "router") {
      const match = layer.regexp.source.match(/^\^\\\/(\w[\w-]*)/);
      layer.handle.stack.forEach((inner: any) => walk(inner, prefix + (match ? `/${match[1]}` : "")));
    }
  };
  app._router.stack.forEach((layer) => walk(layer, ""));
  return result;
}

const denied = (status: number) => status === 401 || status === 403;

describe("who may call which route", () => {
  let server: TestServer;
  let t1 = "";
  let t2 = "";
  let admin: TestAccount;
  let member: TestAccount;
  let outsider: TestAccount;
  let player: TestAccount;
  let registeredPlayer: TestAccount;

  const concrete = (path: string, tournamentId: string) => path
    .replace(":tournamentId", tournamentId)
    .replace(":roundId", "1").replace(":tableId", "1").replace(":playerId", "1").replace(":teamId", "1")
    .replace(":uid", "unknown-user").replace(":invitationId", "unknown-invitation").replace(":submissionId", "unknown-submission").replace(":emaId", "unknown-ema");

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    t1 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    t2 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    admin = await createAccount({ role: "ADMIN" });
    member = await createAccount({ role: "EDITOR", memberOf: [t1] });
    outsider = await createAccount({ role: "EDITOR" });
    player = await createAccount({ role: "PLAYER" });
    registeredPlayer = await createAccount({ role: "PLAYER", playerOf: [t1] });
  });
  after(async () => { await server.close(); });

  it("lists every route of the app with its intended access", () => {
    const actual = actualGuards();
    const expected = new Map(MATRIX.map(([method, path, access]) => [`${method} ${path}`, access]));

    assert.deepEqual([...actual.entries()].sort(), [...expected.entries()].sort());
  });

  for (const [method, path, access] of MATRIX.filter(([, , a]) => a !== "PUBLIC")) {
    describe(`${method} ${path} (${access})`, () => {
      it("refuses a request without a token", async () => {
        assert.equal((await server.request(method, concrete(path, t1))).status, 401);
      });

      it("refuses a token that is not valid", async () => {
        assert.equal((await server.request(method, concrete(path, t1), { token: "not-a-token" })).status, 401);
      });

      if (access === "SIGNED_IN") {
        it("lets any signed-in account through, also a player", async () => {
          for (const account of [player, outsider, member, admin]) {
            const { status } = await server.request(method, concrete(path, t1), { token: account.token });
            assert.ok(!denied(status), `${status} for ${account.email}`);
          }
        });
      }

      if (access === "EDITOR") {
        it("lets any signed-in editor and admin through", async () => {
          for (const account of [outsider, member, admin]) {
            const { status } = await server.request(method, concrete(path, t1), { token: account.token });
            assert.ok(!denied(status), `${status} for ${account.email}`);
          }
        });

        it("refuses an account that is only a player", async () => {
          assert.equal((await server.request(method, concrete(path, t1), { token: player.token })).status, 403);
        });
      }

      if (access === "PLAYER") {
        it("lets a player who is registered in the tournament pass the guard", async () => {
          const { status } = await server.request(method, concrete(path, t1), { token: registeredPlayer.token, body: {} });
          assert.ok(!denied(status), `status ${status}`);
        });

        it("refuses editors, admins and players who are not registered here", async () => {
          for (const account of [member, admin, player, outsider]) {
            assert.equal((await server.request(method, concrete(path, t1), { token: account.token, body: {} })).status, 403);
          }
          assert.equal((await server.request(method, concrete(path, t2), { token: registeredPlayer.token, body: {} })).status, 403);
        });
      }

      if (access === "READER") {
        it("refuses an editor who is not assigned to the tournament", async () => {
          assert.equal((await server.request(method, concrete(path, t1), { token: outsider.token })).status, 403);
        });

        it("lets an assigned editor and a registered player read the tournament", async () => {
          for (const account of [member, registeredPlayer]) {
            const { status } = await server.request(method, concrete(path, t1), { token: account.token });
            assert.ok(!denied(status), `status ${status} for ${account.email}`);
          }
        });

        it("refuses a player who is registered in another tournament", async () => {
          assert.equal((await server.request(method, concrete(path, t2), { token: registeredPlayer.token })).status, 403);
        });

        it("refuses a player who is not registered anywhere", async () => {
          assert.equal((await server.request(method, concrete(path, t1), { token: player.token })).status, 403);
        });

        it("lets an admin into any tournament without an assignment", async () => {
          const { status } = await server.request(method, concrete(path, "probe-tournament"), { token: admin.token });
          assert.ok(!denied(status), `status ${status}`);
        });
      }

      if (access === "MEMBER") {
        it("refuses an editor who is not assigned to the tournament", async () => {
          assert.equal((await server.request(method, concrete(path, t1), { token: outsider.token })).status, 403);
        });

        it("lets an assigned editor into the assigned tournament", async () => {
          const { status } = await server.request(method, concrete(path, t1), { token: member.token });
          assert.ok(!denied(status), `status ${status}`);
        });

        it("refuses an account that is only a player", async () => {
          assert.equal((await server.request(method, concrete(path, t1), { token: player.token })).status, 403);
        });

        it("refuses an assigned editor in another tournament", async () => {
          assert.equal((await server.request(method, concrete(path, t2), { token: member.token })).status, 403);
        });

        it("lets an admin into any tournament without an assignment", async () => {
          const { status } = await server.request(method, concrete(path, "probe-tournament"), { token: admin.token });
          assert.ok(!denied(status), `status ${status}`);
        });
      }

      if (access === "ADMIN") {
        it("refuses an editor, also an assigned one, and a player", async () => {
          for (const account of [player, outsider, member]) {
            assert.equal((await server.request(method, concrete(path, t1), { token: account.token })).status, 403);
          }
        });

        it("lets an admin through the guard", async () => {
          const { status } = await server.request(method, concrete(path, "probe-tournament"), { token: admin.token });
          assert.ok(!denied(status), `status ${status}`);
        });
      }
    });
  }
});

describe("account rules", () => {
  let server: TestServer;
  let admin: TestAccount;
  let editor: TestAccount;
  let otherEditor: TestAccount;
  let player: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "ADMIN" });
    editor = await createAccount({ role: "EDITOR" });
    otherEditor = await createAccount({ role: "EDITOR" });
    player = await createAccount({ role: "PLAYER" });
  });
  after(async () => { await server.close(); });

  const update = (token: string, uid: string, email: string, role: string) =>
    server.request("PUT", `/admin/users/${uid}`, { token, body: { email, alias: "x", role, tournamentAssignments: [] } });

  it("whoami tells the role from the token claim", async () => {
    assert.deepEqual((await server.request("GET", "/admin/whoami", { token: admin.token })).body, { uid: admin.uid, role: "ADMIN", roles: ["ADMIN"] });
    assert.deepEqual((await server.request("GET", "/admin/whoami", { token: editor.token })).body, { uid: editor.uid, role: "EDITOR", roles: ["EDITOR"] });
    assert.deepEqual((await server.request("GET", "/admin/whoami", { token: player.token })).body, { uid: player.uid, role: "PLAYER", roles: ["PLAYER"] });
  });

  it("an editor cannot create an admin account", async () => {
    const { status } = await server.request("POST", "/admin/users", {
      token: editor.token, body: { email: "new-admin@example.test", role: "ADMIN", alias: "x" },
    });

    assert.equal(status, 403);
  });

  it("an editor cannot change an admin account", async () => {
    assert.equal((await update(editor.token, admin.uid, admin.email, "ADMIN")).status, 403);
  });

  it("an editor cannot promote an editor to admin", async () => {
    assert.equal((await update(editor.token, otherEditor.uid, otherEditor.email, "ADMIN")).status, 403);
  });

  it("an editor can edit another editor account", async () => {
    assert.equal((await update(editor.token, otherEditor.uid, otherEditor.email, "EDITOR")).status, 200);
  });

  it("an admin can promote an editor to admin", async () => {
    const promoted = await createAccount({ role: "EDITOR" });

    assert.equal((await update(admin.token, promoted.uid, promoted.email, "ADMIN")).status, 200);
    assert.deepEqual((await auth.getUser(promoted.uid)).customClaims, { admin: true });
  });

  it("nobody can change their own role", async () => {
    const extraAdmin = await createAccount({ role: "ADMIN" });

    assert.equal((await update(extraAdmin.token, extraAdmin.uid, extraAdmin.email, "EDITOR")).status, 400);
  });

  it("an editor cannot disable an account", async () => {
    assert.equal((await server.request("PUT", `/admin/users/${otherEditor.uid}/disabled`, { token: editor.token, body: { disabled: true } })).status, 403);
  });

  it("an admin must send a boolean to disable an account", async () => {
    assert.equal((await server.request("PUT", `/admin/users/${otherEditor.uid}/disabled`, { token: admin.token, body: { disabled: "yes" } })).status, 400);
  });

  it("an admin cannot disable their own account", async () => {
    const extraAdmin = await createAccount({ role: "ADMIN" });

    assert.equal((await server.request("PUT", `/admin/users/${extraAdmin.uid}/disabled`, { token: extraAdmin.token, body: { disabled: true } })).status, 400);
  });

  it("an admin can disable an editor, and that editor can no longer sign in to the API", async () => {
    const victim = await createAccount({ role: "EDITOR" });

    const { status } = await server.request("PUT", `/admin/users/${victim.uid}/disabled`, { token: admin.token, body: { disabled: true } });

    assert.equal(status, 200);
    assert.equal((await server.request("GET", "/auth/me", { token: victim.token })).status, 401);
  });

  it("a disabled account is refused on its first call", async () => {
    const disabled = await createAccount({ role: "EDITOR", disabled: true });

    assert.equal((await server.request("GET", "/admin/whoami", { token: disabled.token })).status, 401);
  });

  it("the last enabled admin cannot be demoted or disabled", async () => {
    const others = (await auth.listUsers(1000)).users.filter((user) => user.customClaims?.admin === true && !user.disabled && user.uid !== admin.uid);
    try {
      for (const user of others) await auth.updateUser(user.uid, { disabled: true });
      const demote = await update(admin.token, admin.uid, admin.email, "ADMIN");
      assert.equal(demote.status, 200, "keeping the role is fine");

      // A second admin tries to disable the only other enabled admin. Only the first remains after the others are off.
      const helper = await createAccount({ role: "ADMIN" });
      const disableHelper = await server.request("PUT", `/admin/users/${admin.uid}/disabled`, { token: helper.token, body: { disabled: true } });
      assert.equal(disableHelper.status, 200, "two enabled admins: one may be disabled");
      await auth.updateUser(admin.uid, { disabled: false });
      await auth.updateUser(helper.uid, { disabled: true });
      // Disabling revoked the old tokens of the admin, so the admin signs in again.
      const adminToken = await signInTestAccount(admin.email);
      const last = await server.request("PUT", `/admin/users/${admin.uid}/disabled`, { token: adminToken, body: { disabled: true } });
      assert.equal(last.status, 400);
    } finally {
      for (const user of others) await auth.updateUser(user.uid, { disabled: false });
    }
  });

  it("the bootstrap route needs the right key and works only without an enabled admin", async () => {
    process.env.MTS_BOOTSTRAP_KEY = "secret-key";
    assert.equal((await server.request("POST", "/admin/bootstrapAdmin", { body: { uid: editor.uid } })).status, 403);
    assert.equal((await server.request("POST", "/admin/bootstrapAdmin", { body: { uid: editor.uid }, headers: { "x-bootstrap-key": "wrong" } })).status, 403);
    // The right key is still refused, because an enabled admin exists.
    assert.equal((await server.request("POST", "/admin/bootstrapAdmin", { body: { uid: editor.uid }, headers: { "x-bootstrap-key": "secret-key" } })).status, 403);
  });
});

describe("tournament rules for assigned editors", () => {
  let server: TestServer;
  let t1 = "";
  let member: TestAccount;
  let admin: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    t1 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    member = await createAccount({ role: "EDITOR", memberOf: [t1] });
    admin = await createAccount({ role: "ADMIN" });
  });
  after(async () => { await server.close(); });

  it("an assigned editor can add another editor to the tournament", async () => {
    const colleague = await createAccount({ role: "EDITOR" });

    const { status } = await server.request("PUT", `/tournaments/${t1}/members/${colleague.uid}`, { token: member.token });

    assert.equal(status, 200);
    assert.equal((await server.request("GET", `/tournaments/${t1}/players`, { token: colleague.token })).status, 200);
  });

  it("an admin account cannot be added as a member, because admins already see everything", async () => {
    const { status } = await server.request("PUT", `/tournaments/${t1}/members/${admin.uid}`, { token: member.token });

    assert.equal(status, 400);
  });

  it("an assigned editor can save a table, an outsider cannot", async () => {
    const outsider = await createAccount({ role: "EDITOR" });
    const path = `/tournaments/${t1}/tables/1/1/state`;
    const body = { expectedVersion: 0, tablePatch: { playerEastId: "1" }, handPatches: [] };

    assert.equal((await server.request("PUT", path, { token: outsider.token, body })).status, 403);
    assert.equal((await server.request("PUT", path, { token: member.token, body })).status, 200);
  });

  it("only an admin can reset the table that an editor saved", async () => {
    const path = `/tournaments/${t1}/tables/1/1/reset`;

    assert.equal((await server.request("POST", path, { token: member.token })).status, 403);
    assert.equal((await server.request("POST", path, { token: admin.token })).status, 200);
  });

  it("the change log names the editor who saved", async () => {
    const t = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const editor = await createAccount({ role: "EDITOR", memberOf: [t] });

    await server.request("PUT", `/tournaments/${t}/tables/1/1/state`, {
      token: editor.token, body: { expectedVersion: 0, tablePatch: { playerEastId: "1" }, handPatches: [] },
    });

    const { db } = await import("../firebase");
    const log = await db.collection("tournaments").doc(t).collection("changes").get();
    assert.equal(log.docs[0].get("uid"), editor.uid);
  });

  it("a request body without a version is refused before anything is written", async () => {
    const t = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const editor = await createAccount({ role: "EDITOR", memberOf: [t] });

    const { status } = await server.request("PUT", `/tournaments/${t}/tables/1/1/state`, {
      token: editor.token, body: { expectedVersion: null, tablePatch: { playerEastId: "1" } },
    });

    assert.equal(status, 400);
  });
});
