import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { requireRegisteredPlayer } from "./middleware/requireRegisteredPlayer";
import { requireAuth } from "./middleware/requireAuth";
import type { PermissionGuard } from "./middleware/requirePermission";
import { buildApp, MOUNTED_ROUTERS } from "./app";
import { seedOrganization, seedTournament } from "../testing/firestoreFixtures";
import { getGlobalDataVersions } from "../services/dataVersionsService";
import { setGlobalUserRole } from "../services/usersService";
import { upsertTournamentMember } from "../services/membersService";
import { ROLE_PERMISSIONS, type Permission } from "../models/permissions";
import type { GlobalUserRole } from "../models/globalRole";
import { createAccount, signInTestAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { validDrawTables } from "../testing/draw";
import { fullRoundSchedules } from "../testing/schedule";
import { randomUUID } from "node:crypto";

const randomTag = () => randomUUID().slice(0, 12);

/**
 * Who may call what. The table is written by hand, so it states the intended rules: each route names the permission
 * that it checks. "@" marks a permission that the route checks inside the tournament of its path.
 * A test compares it with the guards of the real routes. A new route fails that test until it is listed here.
 * ROLE_PERMISSIONS (models/permissions.ts) says which role has which permission, and the tests below call every
 * route as each role, inside and outside its scope.
 */
type Access = "PUBLIC" | "SIGNED_IN" | "PLAYER" | Permission | `@${Permission}`;

const MATRIX: Array<[string, string, Access]> = [
  ["GET", "/health", "PUBLIC"],
  ["GET", "/version", "PUBLIC"],
  ["GET", "/config/", "PUBLIC"],
  ["POST", "/telemetry/errors", "PUBLIC"],
  ["GET", "/admin/activity", "tournament.activity"],
  ["GET", "/admin/error-groups", "dev.errors"],
  ["GET", "/admin/error-groups/:id", "dev.errors"],
  ["POST", "/auth/signIn", "PUBLIC"],
  ["POST", "/auth/passwordReset", "PUBLIC"],
  ["POST", "/support/message", "PUBLIC"],
  ["POST", "/auth/refresh", "PUBLIC"],
  ["GET", "/auth/me", "SIGNED_IN"],
  ["PUT", "/auth/me", "SIGNED_IN"],
  ["PUT", "/auth/me/photo", "SIGNED_IN"],
  ["DELETE", "/auth/me/photo", "SIGNED_IN"],
  ["DELETE", "/auth/me", "SIGNED_IN"],
  ["GET", "/admin/whoami", "SIGNED_IN"],
  ["GET", "/countries/", "SIGNED_IN"],
  ["GET", "/ema-player-registry/", "SIGNED_IN"],
  ["GET", "/admin/users", "accounts.view"],
  ["POST", "/admin/users", "accounts.createPlayer"],
  ["PUT", "/admin/users/:uid", "accounts.view"],
  ["GET", "/admin/users/lookup", "accounts.view"],
  ["GET", "/sync/manifest", "SIGNED_IN"],
  ["GET", "/tournaments/", "SIGNED_IN"],
  ["POST", "/venues/resolve-link", "tools.venues"],
  ["POST", "/tournaments/id-card-proof", "tools.idCards"],
  ["POST", "/tournaments/id-card-preview", "tools.idCards"],
  ["GET", "/tournaments/statistics/overview", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/groups", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/me", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/me/standings", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/persons/:emaId", "SIGNED_IN"],
  ["GET", "/tournaments/statistics/persons/:emaId/standings", "SIGNED_IN"],
  ["POST", "/ema-player-registry/", "ema.create"],
  ["PUT", "/ema-player-registry/:emaId", "ema.edit"],
  ["POST", "/ema-player-registry/delete/preview", "ema.delete"],
  ["POST", "/ema-player-registry/delete", "ema.delete"],
  ["PUT", "/ema-player-registry/:emaId/photo", "ema.edit"],
  ["PUT", "/admin/users/:uid/disabled", "accounts.disable"],
  ["DELETE", "/admin/users/:uid", "accounts.delete"],
  ["POST", "/admin/users/password-resets", "accounts.passwordReset"],
  ["POST", "/admin/users/delete", "accounts.delete"],
  ["PUT", "/admin/users/:uid/photo", "accounts.photo"],
  ["PUT", "/admin/users/:uid/permissions", "accounts.manageStaff"],
  ["GET", "/admin/roles", "accounts.view"],
  ["POST", "/admin/roles", "permissions.manage"],
  ["PUT", "/admin/roles/:roleId", "permissions.manage"],
  ["DELETE", "/admin/roles/:roleId", "permissions.manage"],
  ["POST", "/admin/users/:uid/removal", "accounts.requestRemoval"],
  ["GET", "/admin/account-requests", "accounts.reviewRequests"],
  ["GET", "/admin/organizations", "tournament.view"],
  ["POST", "/admin/organizations", "organizations.create"],
  ["PUT", "/admin/organizations/:organizationId", "organizations.edit"],
  ["DELETE", "/admin/organizations/:organizationId", "organizations.delete"],
  ["POST", "/admin/account-requests/:requestId", "accounts.reviewRequests"],
  ["DELETE", "/admin/users/:uid/photo", "accounts.photo"],
  ["POST", "/tournaments/", "tournaments.create"],
  ["PUT", "/tournaments/:tournamentId/settings", "@tournament.configure"],
  ["DELETE", "/tournaments/:tournamentId", "@tournament.delete"],
  ["PUT", "/tournaments/:tournamentId", "@tournament.configure"],
  ["POST", "/tournaments/:tournamentId/tables/:roundId/:tableId/reset", "@tournament.resetTable"],
  ["GET", "/tournaments/:tournamentId/sync/manifest", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/id-cards", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/id-list", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/integrity", "@tournament.view"],
  ["POST", "/tournaments/:tournamentId/ema-report", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/users/lookup", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/members", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/members/:uid", "@tournament.members"],
  ["DELETE", "/tournaments/:tournamentId/members/:uid", "@tournament.members"],
  ["GET", "/tournaments/:tournamentId/players", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/players/:playerId", "@tournament.edit"],
  ["PUT", "/tournaments/:tournamentId/players/:playerId/substitution", "@tournament.edit"],
  ["DELETE", "/tournaments/:tournamentId/players/:playerId/substitution", "@tournament.edit"],
  ["POST", "/tournaments/:tournamentId/player-accounts/send", "@tournament.edit"],
  ["PUT", "/tournaments/:tournamentId/player-emails", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId/player-import/export", "@tournament.view"],
  ["POST", "/tournaments/:tournamentId/player-import/preview", "@tournament.edit"],
  ["POST", "/tournaments/:tournamentId/player-import/apply", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId/teams", "@tournament.view"],
  ["PUT", "/tournaments/:tournamentId/teams/:teamId", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId/rounds", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/round-clocks", "@tournament.view"],
  ["POST", "/tournaments/:tournamentId/rounds/:roundId/clock", "@tournament.edit"],
  ["POST", "/tournaments/:tournamentId/rounds/:roundId/control", "@tournament.roundControl"],
  ["POST", "/tournaments/:tournamentId/complete-open-tables", "@tournament.roundControl"],
  ["GET", "/tournaments/:tournamentId/tables", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/tables-with-hands", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", "@tournament.view"],
  ["GET", "/tournaments/:tournamentId/tables/:roundId/:tableId", "@tournament.view"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/hands/preview", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/penalties", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/hands", "PLAYER"],
  ["POST", "/player/tournaments/:tournamentId/tables/:roundId/:tableId/seats", "PLAYER"],
  ["PUT", "/tournaments/:tournamentId/tables/:roundId/:tableId/state", "@tournament.edit"],
  ["GET", "/tournaments/:tournamentId", "@tournament.view"],
  ["GET", "/public/sync/manifest", "PUBLIC"],
  ["GET", "/public/tournaments", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/sync/manifest", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/players", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/teams", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/rounds", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/round-clocks", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/tables", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/tables-with-hands", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/rounds/:roundId/tables-with-hands", "PUBLIC"],
  ["GET", "/public/tournaments/:tournamentId/tables/:roundId/:tableId", "PUBLIC"],
  ["GET", "/public/statistics/overview", "PUBLIC"],
  ["GET", "/public/statistics/groups", "PUBLIC"],
  ["GET", "/public/statistics/persons/:emaId", "PUBLIC"],
  ["GET", "/public/statistics/persons/:emaId/standings", "PUBLIC"],
  ["GET", "/public/players", "PUBLIC"],
  ["GET", "/public/countries", "PUBLIC"],
  ["GET", "/me/", "SIGNED_IN"],
];

function actualGuards(): Map<string, Access> {
  const app = buildApp() as unknown as { router: { stack: any[] } };
  const result = new Map<string, Access>();
  const walk = (layer: any, prefix: string) => {
    if (layer.route) {
      const handlers: unknown[] = layer.route.stack.map((item: any) => item.handle);
      const guard = handlers.find((handler) => typeof (handler as Partial<PermissionGuard>).permission === "string") as PermissionGuard | undefined;
      const access: Access = guard ? (guard.scope === "tournament" ? `@${guard.permission}` : guard.permission)
        : handlers.includes(requireRegisteredPlayer) ? "PLAYER"
        : handlers.includes(requireAuth) ? "SIGNED_IN"
        : "PUBLIC";
      for (const method of Object.keys(layer.route.methods)) result.set(`${method.toUpperCase()} ${prefix}${layer.route.path}`, access);
    } else if (layer.name === "router") {
      layer.handle.stack.forEach((inner: any) => walk(inner, prefix));
    }
  };
  // The mounted routers come in the order of MOUNTED_ROUTERS, so the order gives each one its prefix.
  const routerLayers = app.router.stack.filter((layer) => layer.name === "router");
  assert.equal(routerLayers.length, MOUNTED_ROUTERS.length);
  routerLayers.forEach((layer, index) => walk(layer, MOUNTED_ROUTERS[index][0]));
  app.router.stack.filter((layer) => layer.route).forEach((layer) => walk(layer, ""));
  return result;
}

const denied = (status: number) => status === 401 || status === 403;
const has = (role: GlobalUserRole, permission: Permission) => ROLE_PERMISSIONS[role].includes(permission);

describe("who may call which route", () => {
  let server: TestServer;
  let t1 = "";
  let t2 = "";
  let superAdmin: TestAccount;
  let clubAdmin: TestAccount;
  let foreignClubAdmin: TestAccount;
  let member: TestAccount;
  let outsider: TestAccount;
  let player: TestAccount;
  let registeredPlayer: TestAccount;

  const concrete = (path: string, tournamentId: string) => {
    const filled = path
      .replace(":tournamentId", tournamentId)
      .replace(":roundId", "1").replace(":tableId", "1").replace(":playerId", "1").replace(":teamId", "1")
      .replace(":uid", "unknown-user").replace(":invitationId", "unknown-invitation").replace(":submissionId", "unknown-submission")
      .replace(":emaId", "unknown-ema").replace(":requestId", "unknown-request").replace(":organizationId", "unknown-organization").replace(":roleId", "unknown-role");
    // A club admin reads the activity of one tournament of its organizations.
    return filled === "/admin/activity" ? `${filled}?tournamentId=${tournamentId}` : filled;
  };

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    for (const id of ["org-es", "org-fr", "org-se"]) await seedOrganization({ id });
    t1 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
    t2 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-fr"] });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    clubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    foreignClubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-se"] });
    member = await createAccount({ role: "EDITOR", memberOf: [t1] });
    outsider = await createAccount({ role: "EDITOR", memberOf: [t2] });
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
          // Deleting the own account would delete the shared accounts of these tests, so that route gets fresh ones.
          const ownDeletion = method === "DELETE" && path === "/auth/me";
          const accounts = ownDeletion
            ? [await createAccount({ role: "PLAYER" }), await createAccount({ role: "EDITOR" }), await createAccount({ role: "SUPER_ADMIN" })]
            : [player, outsider, member, clubAdmin, superAdmin];
          for (const account of accounts) {
            const { status } = await server.request(method, concrete(path, t1), { token: account.token });
            assert.ok(!denied(status), `${status} for ${account.email}`);
          }
        });
        return;
      }

      if (access === "PLAYER") {
        it("lets a player who is registered in the tournament pass the guard", async () => {
          const { status } = await server.request(method, concrete(path, t1), { token: registeredPlayer.token, body: {} });
          assert.ok(!denied(status), `status ${status}`);
        });

        it("refuses editors, admins and players who are not registered here", async () => {
          for (const account of [member, clubAdmin, superAdmin, player, outsider]) {
            assert.equal((await server.request(method, concrete(path, t1), { token: account.token, body: {} })).status, 403);
          }
          assert.equal((await server.request(method, concrete(path, t2), { token: registeredPlayer.token, body: {} })).status, 403);
        });
        return;
      }

      const inTournament = access.startsWith("@");
      const permission = (inTournament ? access.slice(1) : access) as Permission;
      // Who passes the guard in t1, a tournament of org-es: [account, role, whether the role holds t1 in its scope].
      // The super admin goes last, because a call that passes may delete the tournament.
      const callers = (): Array<[string, TestAccount, GlobalUserRole, boolean]> => [
        ["club admin of another organization", foreignClubAdmin, "CLUB_ADMIN", false],
        ["editor of another tournament", outsider, "EDITOR", false],
        ["assigned editor", member, "EDITOR", true],
        ["club admin of the organizer", clubAdmin, "CLUB_ADMIN", true],
        ["super admin", superAdmin, "SUPER_ADMIN", true],
      ];
      // The activity route checks its tournament inside the route.
      const scoped = inTournament || permission === "tournament.activity";

      it("lets through exactly the roles that hold the permission, in their scope", async () => {
        // A fresh tournament of org-es, because a call that passes can change or delete it.
        const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
        await upsertTournamentMember({ tournamentId, uid: member.uid });
        for (const [label, account, role, inScope] of callers()) {
          const allowed = has(role, permission) && (!scoped || inScope);
          const { status } = await server.request(method, concrete(path, tournamentId), { token: account.token, body: {} });
          if (allowed) assert.ok(!denied(status), `${label}: status ${status}`);
          else assert.equal(status, 403, `${label}: status ${status}`);
        }
      });

      it("refuses an account that is only a player, even one registered here", async () => {
        for (const account of [player, registeredPlayer]) {
          assert.equal((await server.request(method, concrete(path, t1), { token: account.token, body: {} })).status, 403);
        }
      });

      if (inTournament) {
        it("lets a super admin into any tournament without an assignment", async () => {
          const { status } = await server.request(method, concrete(path, "probe-tournament"), { token: superAdmin.token, body: {} });
          assert.ok(!denied(status), `status ${status}`);
        });
      }
    });
  }
});

describe("account rules", () => {
  let server: TestServer;
  let superAdmin: TestAccount;
  let editor: TestAccount;
  let otherEditor: TestAccount;
  let player: TestAccount;
  let t = "";

  before(async () => {
    requireAuthEmulator();
    // A new account gets the password email through the Auth emulator.
    process.env.MTS_ID_TOOLKIT_API_KEY ??= "test-key";
    server = await startServer();
    await seedOrganization({ id: "org-es" });
    t = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    editor = await createAccount({ role: "EDITOR", memberOf: [t] });
    otherEditor = await createAccount({ role: "EDITOR", memberOf: [t] });
    player = await createAccount({ role: "PLAYER" });
  });
  after(async () => { await server.close(); });

  const update = (token: string, uid: string, email: string, role: string, extra: Record<string, unknown> = {}) =>
    server.request("PUT", `/admin/users/${uid}`, { token, body: { email, alias: "x", role, tournamentAssignments: [{ tournamentId: t }], ...extra } });

  it("whoami tells the roles, the organizations and the permissions of the account", async () => {
    const sa = (await server.request("GET", "/admin/whoami", { token: superAdmin.token })).body as Record<string, unknown>;
    assert.deepEqual([sa.role, sa.roles, sa.adminOrganizations], ["SUPER_ADMIN", ["SUPER_ADMIN"], []]);
    assert.ok((sa.permissions as string[]).includes("dev.errors"));
    const ed = (await server.request("GET", "/admin/whoami", { token: editor.token })).body as Record<string, unknown>;
    assert.deepEqual([ed.role, ed.roles], ["EDITOR", ["EDITOR"]]);
    assert.ok(!(ed.permissions as string[]).includes("dev.errors"));
    assert.ok((ed.permissions as string[]).includes("tournament.roundControl"));
    const pl = (await server.request("GET", "/admin/whoami", { token: player.token })).body as Record<string, unknown>;
    assert.deepEqual([pl.role, pl.roles, pl.permissions], ["PLAYER", ["PLAYER"], []]);
  });

  it("an editor cannot create a super admin, a club admin or an editor", async () => {
    for (const [role, extra] of [["SUPER_ADMIN", {}], ["CLUB_ADMIN", { adminOrganizations: ["org-es"] }], ["EDITOR", { tournamentAssignments: [{ tournamentId: t }] }]] as const) {
      const { status } = await server.request("POST", "/admin/users", {
        token: editor.token, body: { email: `new-${role.toLowerCase()}@example.test`, role, alias: "x", ...extra },
      });
      assert.equal(status, 403, role);
    }
  });

  it("an editor creates a player account with an EMA id", async () => {
    await db.doc("emaPlayerRegistry/90000010").set({ firstName: "Edith", lastName: "Clarke", country: "USA" });
    const { status, body } = await server.request("POST", "/admin/users", {
      token: editor.token, body: { email: "new-player-by-editor@example.test", role: "PLAYER", alias: "x", emaId: "90000010" },
    });

    assert.equal(status, 201);
    assert.equal((body as { role: string }).role, "PLAYER");
  });

  it("an editor cannot change a super admin account or another editor account", async () => {
    assert.equal((await update(editor.token, superAdmin.uid, superAdmin.email, "SUPER_ADMIN")).status, 403);
    assert.equal((await update(editor.token, otherEditor.uid, otherEditor.email, "SUPER_ADMIN")).status, 403);
    assert.equal((await update(editor.token, otherEditor.uid, otherEditor.email, "EDITOR")).status, 403);
  });

  it("an editor can edit the own alias, not the own role", async () => {
    assert.equal((await update(editor.token, editor.uid, editor.email, "EDITOR")).status, 200);
    assert.equal((await update(editor.token, editor.uid, editor.email, "CLUB_ADMIN", { adminOrganizations: ["org-es"] })).status, 400);
  });

  it("only a super admin changes the email of an account", async () => {
    assert.equal((await update(editor.token, editor.uid, "taken-over@example.test", "EDITOR")).status, 403);
    assert.equal((await auth.getUser(editor.uid)).email, editor.email);

    const target = await createAccount({ role: "EDITOR", memberOf: [t] });
    assert.equal((await update(superAdmin.token, target.uid, `new-${target.email}`, "EDITOR")).status, 200);
  });

  it("a super admin can promote an editor to super admin, and its assignments end", async () => {
    const promoted = await createAccount({ role: "EDITOR", memberOf: [t] });

    assert.equal((await update(superAdmin.token, promoted.uid, promoted.email, "SUPER_ADMIN")).status, 200);
    assert.deepEqual((await auth.getUser(promoted.uid)).customClaims, { superAdmin: true });
    assert.equal((await db.doc(`tournaments/${t}/members/${promoted.uid}`).get()).exists, false);
  });

  it("an editor needs at least one tournament", async () => {
    const target = await createAccount({ role: "EDITOR", memberOf: [t] });

    assert.equal((await update(superAdmin.token, target.uid, target.email, "EDITOR", { tournamentAssignments: [] })).status, 400);
    const created = await server.request("POST", "/admin/users", {
      token: superAdmin.token, body: { email: "editor-without-tournaments@example.test", role: "EDITOR", alias: "x", tournamentAssignments: [] },
    });
    assert.equal(created.status, 400);
  });

  const updateWithEma = (token: string, uid: string, email: string, role: string, emaId?: string) =>
    server.request("PUT", `/admin/users/${uid}`, { token, body: { email, alias: "x", role, emaId, tournamentAssignments: [] } });

  it("a super admin can make an editor a player, and the editor loses the editor role", async () => {
    const demoted = await createAccount({ role: "EDITOR", memberOf: [t] });
    await db.doc("emaPlayerRegistry/90000001").set({ firstName: "Ada", lastName: "Lovelace", country: "GBR" });

    assert.equal((await updateWithEma(superAdmin.token, demoted.uid, demoted.email, "PLAYER", "90000001")).status, 200);
    assert.deepEqual((await auth.getUser(demoted.uid)).customClaims, { player: true });
    assert.equal((await db.doc(`tournaments/${t}/members/${demoted.uid}`).get()).exists, false);
  });

  it("a super admin can make a super admin a player", async () => {
    const demoted = await createAccount({ role: "SUPER_ADMIN" });
    await db.doc("emaPlayerRegistry/90000002").set({ firstName: "Grace", lastName: "Hopper", country: "USA" });

    assert.equal((await updateWithEma(superAdmin.token, demoted.uid, demoted.email, "PLAYER", "90000002")).status, 200);
    assert.deepEqual((await auth.getUser(demoted.uid)).customClaims, { player: true });
  });

  it("making an account a player needs an EMA id and changes nothing without one", async () => {
    const target = await createAccount({ role: "EDITOR", memberOf: [t] });

    assert.equal((await updateWithEma(superAdmin.token, target.uid, target.email, "PLAYER")).status, 400);
    assert.deepEqual((await auth.getUser(target.uid)).customClaims, { editor: true });
  });

  it("an editor cannot make an account a player", async () => {
    assert.equal((await updateWithEma(editor.token, editor.uid, editor.email, "PLAYER", "90000001")).status, 400);
    assert.equal((await updateWithEma(editor.token, otherEditor.uid, otherEditor.email, "PLAYER", "90000003")).status, 403);
    assert.deepEqual((await auth.getUser(otherEditor.uid)).customClaims, { editor: true });
  });

  it("a super admin cannot make the own account a player", async () => {
    const extra = await createAccount({ role: "SUPER_ADMIN" });

    assert.equal((await updateWithEma(extra.token, extra.uid, extra.email, "PLAYER", "90000001")).status, 400);
  });

  it("nobody can change their own role", async () => {
    const extra = await createAccount({ role: "SUPER_ADMIN" });

    assert.equal((await update(extra.token, extra.uid, extra.email, "EDITOR")).status, 400);
  });

  it("only a super admin changes the EMA id of an existing account", async () => {
    const target = await createAccount({ role: "PLAYER", playerOf: [t] });
    await db.doc("emaPlayerRegistry/90000011").set({ firstName: "Hedy", lastName: "Lamarr", country: "AUT" });

    assert.equal((await updateWithEma(editor.token, target.uid, target.email, "PLAYER", "90000011")).status, 403);
    assert.equal((await updateWithEma(superAdmin.token, target.uid, target.email, "PLAYER", "90000011")).status, 200);
  });

  it("an editor cannot disable an account", async () => {
    assert.equal((await server.request("PUT", `/admin/users/${otherEditor.uid}/disabled`, { token: editor.token, body: { disabled: true } })).status, 403);
  });

  it("a super admin must send a boolean to disable an account", async () => {
    assert.equal((await server.request("PUT", `/admin/users/${otherEditor.uid}/disabled`, { token: superAdmin.token, body: { disabled: "yes" } })).status, 400);
  });

  it("a super admin cannot disable the own account", async () => {
    const extra = await createAccount({ role: "SUPER_ADMIN" });

    assert.equal((await server.request("PUT", `/admin/users/${extra.uid}/disabled`, { token: extra.token, body: { disabled: true } })).status, 400);
  });

  it("a super admin can disable an editor, and that editor can no longer sign in to the API", async () => {
    const victim = await createAccount({ role: "EDITOR", memberOf: [t] });

    const { status } = await server.request("PUT", `/admin/users/${victim.uid}/disabled`, { token: superAdmin.token, body: { disabled: true } });

    assert.equal(status, 200);
    assert.equal((await server.request("GET", "/auth/me", { token: victim.token })).status, 401);
  });

  it("a disabled account is refused on its first call", async () => {
    const disabled = await createAccount({ role: "EDITOR", disabled: true });

    assert.equal((await server.request("GET", "/admin/whoami", { token: disabled.token })).status, 401);
  });

  it("an editor cannot delete an account", async () => {
    const victim = await createAccount({ role: "EDITOR" });

    assert.equal((await server.request("DELETE", `/admin/users/${victim.uid}`, { token: editor.token })).status, 403);
    assert.ok(await auth.getUser(victim.uid));
  });

  it("a super admin cannot delete the own account", async () => {
    const extra = await createAccount({ role: "SUPER_ADMIN" });

    assert.equal((await server.request("DELETE", `/admin/users/${extra.uid}`, { token: extra.token })).status, 400);
  });

  it("a super admin deletes an editor: the sign-in, the profile and the assignments go", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const victim = await createAccount({ role: "EDITOR", memberOf: [tournamentId] });

    const { status } = await server.request("DELETE", `/admin/users/${victim.uid}`, { token: superAdmin.token });

    assert.equal(status, 200);
    await assert.rejects(auth.getUser(victim.uid));
    assert.equal((await db.doc(`users/${victim.uid}`).get()).exists, false);
    assert.equal((await db.doc(`tournaments/${tournamentId}/members/${victim.uid}`).get()).exists, false);
  });

  it("a player deletes the own account: the sign-in goes, the EMA id is free again", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const own = await createAccount({ role: "PLAYER", playerOf: [tournamentId] });
    const emaId = String((await db.doc(`users/${own.uid}`).get()).get("emaId"));

    assert.equal((await server.request("DELETE", "/auth/me", { token: own.token })).status, 200);

    await assert.rejects(auth.getUser(own.uid));
    assert.equal((await db.doc(`users/${own.uid}`).get()).exists, false);
    assert.equal((await db.doc(`emaIdIndex/${emaId}`).get()).exists, false);
  });

  it("a super admin deletes a player account: the EMA id is free again", async () => {
    const tournamentId = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }] });
    const victim = await createAccount({ role: "PLAYER", playerOf: [tournamentId] });
    const emaId = String((await db.doc(`users/${victim.uid}`).get()).get("emaId"));

    assert.equal((await server.request("DELETE", `/admin/users/${victim.uid}`, { token: superAdmin.token })).status, 200);

    await assert.rejects(auth.getUser(victim.uid));
    assert.equal((await db.doc(`emaIdIndex/${emaId}`).get()).exists, false, "the EMA id still points to the deleted account");
  });

  it("the last enabled super admin cannot be demoted or disabled", async () => {
    const others = (await auth.listUsers(1000)).users.filter((user) => user.customClaims?.superAdmin === true && !user.disabled && user.uid !== superAdmin.uid);
    try {
      for (const user of others) await auth.updateUser(user.uid, { disabled: true });
      const keep = await update(superAdmin.token, superAdmin.uid, superAdmin.email, "SUPER_ADMIN");
      assert.equal(keep.status, 200, "keeping the role is fine");

      // A second super admin disables the first one. Then only the first remains after the others are off.
      const helper = await createAccount({ role: "SUPER_ADMIN" });
      const disableFirst = await server.request("PUT", `/admin/users/${superAdmin.uid}/disabled`, { token: helper.token, body: { disabled: true } });
      assert.equal(disableFirst.status, 200, "two enabled super admins: one may be disabled");
      await auth.updateUser(superAdmin.uid, { disabled: false });
      await auth.updateUser(helper.uid, { disabled: true });
      // Disabling revoked the old tokens, so the super admin signs in again.
      superAdmin = { ...superAdmin, token: await signInTestAccount(superAdmin.email) };
      const last = await server.request("PUT", `/admin/users/${superAdmin.uid}/disabled`, { token: superAdmin.token, body: { disabled: true } });
      assert.equal(last.status, 400);
    } finally {
      for (const user of others) await auth.updateUser(user.uid, { disabled: false });
    }
  });
});

/** A tournament that the API accepts, for the create and settings routes. */
function tournamentBody(organizerIds: string[], hostCountry = "ES"): Record<string, unknown> {
  const numPlayers = 16;
  const players = Array.from({ length: numPlayers }, (_, index) => ({ id: index + 1, team: 0, name: `Player ${index + 1}`, country: "ES" }));
  return {
    name: "Club open", shortName: "CO", organizerIds, eventStartDate: "2030-10-01", eventEndDate: "2030-10-02",
    hostCountry, hostCity: "City", venueName: "Hall", venueAddress: "Street 1", contactEmail: "contact@example.test",
    isTeams: false, numPlayers, numRounds: 2, numTries: 1, players, tables: validDrawTables(numPlayers, 2),
    roundSchedules: fullRoundSchedules(2, "2030-10-01"),
  };
}

describe("club admin rules", () => {
  let server: TestServer;
  let spain = "";
  let spain2 = "";
  let sweden = "";
  let superAdmin: TestAccount;
  let spanishAdmin: TestAccount;
  let spanishEditor: TestAccount;

  before(async () => {
    requireAuthEmulator();
    process.env.MTS_ID_TOOLKIT_API_KEY ??= "test-key";
    server = await startServer();
    for (const id of ["org-es", "org-se"]) await seedOrganization({ id });
    spain = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
    spain2 = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
    sweden = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "SE", organizerIds: ["org-se"] });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    spanishAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    spanishEditor = await createAccount({ role: "EDITOR", memberOf: [spain] });
  });
  after(async () => { await server.close(); });

  const createUser = (token: string, body: Record<string, unknown>) =>
    server.request("POST", "/admin/users", { token, body: { email: `${randomTag()}@example.test`, alias: "x", ...body } });
  const updateUser = (token: string, account: TestAccount, body: Record<string, unknown>) =>
    server.request("PUT", `/admin/users/${account.uid}`, { token, body: { email: account.email, alias: "x", ...body } });

  it("lists the tournaments of its organizations with its permissions in each", async () => {
    const { body } = await server.request("GET", "/tournaments", { token: spanishAdmin.token });
    const tournaments = (body as { tournaments: Array<{ id: string; permissions: string[] }> }).tournaments;
    const ids = tournaments.map((tournament) => tournament.id);

    assert.ok(ids.includes(spain) && ids.includes(spain2));
    assert.ok(!ids.includes(sweden));
    assert.ok(tournaments.find((tournament) => tournament.id === spain)?.permissions.includes("tournament.delete"));
  });

  it("creates tournaments only for its organizations, and every tournament needs an organizer and a real country", async () => {
    assert.equal((await server.request("POST", "/tournaments", { token: spanishAdmin.token, body: tournamentBody(["org-se"]) })).status, 403);
    assert.equal((await server.request("POST", "/tournaments", { token: superAdmin.token, body: tournamentBody(["org-es"], "XX") })).status, 400);
    assert.equal((await server.request("POST", "/tournaments", { token: superAdmin.token, body: tournamentBody([]) })).status, 400);
    assert.equal((await server.request("POST", "/tournaments", { token: superAdmin.token, body: tournamentBody(["org-unknown"]) })).status, 400);
    const created = await server.request("POST", "/tournaments", { token: spanishAdmin.token, body: tournamentBody(["org-es", "org-se"]) });
    assert.equal(created.status, 200, JSON.stringify(created.body));
    const tournament = created.body as { organizerIds: string[]; organizers: Array<{ id: string }>; primaryColor: string };
    assert.deepEqual(tournament.organizerIds, ["org-es", "org-se"]);
    assert.deepEqual(tournament.organizers.map((organizer) => organizer.id), ["org-es", "org-se"]);
  });

  it("changes the country of its tournament, but only a super admin changes the organizers", async () => {
    const created = await server.request("POST", "/tournaments", { token: spanishAdmin.token, body: tournamentBody(["org-es"]) });
    const id = (created.body as { id: string }).id;

    assert.equal((await server.request("PUT", `/tournaments/${id}/settings`, { token: spanishAdmin.token, body: tournamentBody(["org-se"]) })).status, 403);
    assert.equal((await server.request("PUT", `/tournaments/${id}/settings`, { token: spanishAdmin.token, body: tournamentBody(["org-es"], "SE") })).status, 200);
    assert.equal((await server.request("PUT", `/tournaments/${id}/settings`, { token: superAdmin.token, body: tournamentBody(["org-se"]) })).status, 200);
    // The tournament belongs to the Swedish club now, so the Spanish admin no longer manages it.
    assert.equal((await server.request("GET", `/tournaments/${id}`, { token: spanishAdmin.token })).status, 403);
  });

  it("edits its own organization only, and only a super admin creates and deletes organizations", async () => {
    const body = { name: `Club ${randomTag()}`, type: "CLUB", country: "ES", primaryColor: "#123456" };
    assert.equal((await server.request("POST", "/admin/organizations", { token: spanishAdmin.token, body })).status, 403);
    const created = await server.request("POST", "/admin/organizations", { token: superAdmin.token, body });
    assert.equal(created.status, 201, JSON.stringify(created.body));
    const newId = (created.body as { id: string }).id;
    assert.equal((await server.request("POST", "/admin/organizations", { token: superAdmin.token, body })).status, 400, "names do not repeat");

    assert.equal((await server.request("PUT", "/admin/organizations/org-es", { token: spanishAdmin.token, body: { ...body, name: "Spanish club", primaryColor: "#AA0000" } })).status, 200);
    assert.equal((await server.request("PUT", `/admin/organizations/${newId}`, { token: spanishAdmin.token, body })).status, 403);
    const list = (await server.request("GET", "/tournaments", { token: spanishAdmin.token })).body as { tournaments: Array<{ id: string; primaryColor: string }> };
    assert.equal(list.tournaments.find((tournament) => tournament.id === spain)?.primaryColor, "#AA0000", "the tournament takes the color of its main organizer");

    assert.equal((await server.request("DELETE", "/admin/organizations/org-es", { token: superAdmin.token })).status, 400, "it still organizes tournaments");
    assert.equal((await server.request("DELETE", `/admin/organizations/${newId}`, { token: spanishAdmin.token })).status, 403);
    assert.equal((await server.request("DELETE", `/admin/organizations/${newId}`, { token: superAdmin.token })).status, 200);
  });

  it("deletes its tournaments, but a finished one with results only a super admin", async () => {
    const finished = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES", eventEndDate: "2020-01-02" });
    await db.doc(`tournaments/${finished}/tables/1_1`).update({ hasProgress: true });
    const fresh = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES", eventEndDate: "2020-01-02" });

    assert.equal((await server.request("DELETE", `/tournaments/${finished}`, { token: spanishAdmin.token })).status, 403);
    assert.equal((await server.request("DELETE", `/tournaments/${fresh}`, { token: spanishAdmin.token })).status, 200);
    assert.equal((await server.request("DELETE", `/tournaments/${finished}`, { token: superAdmin.token })).status, 200);
  });

  it("creates editors of its tournaments and club admins of its organizations, never super admins", async () => {
    assert.equal((await createUser(spanishAdmin.token, { role: "EDITOR", tournamentAssignments: [{ tournamentId: spain }] })).status, 201);
    assert.equal((await createUser(spanishAdmin.token, { role: "EDITOR", tournamentAssignments: [{ tournamentId: sweden }] })).status, 403);
    assert.equal((await createUser(spanishAdmin.token, { role: "CLUB_ADMIN", adminOrganizations: ["org-es"] })).status, 201);
    assert.equal((await createUser(spanishAdmin.token, { role: "CLUB_ADMIN", adminOrganizations: ["org-se"] })).status, 403);
    assert.equal((await createUser(spanishAdmin.token, { role: "CLUB_ADMIN", adminOrganizations: ["org-unknown"] })).status, 400);
    assert.equal((await createUser(spanishAdmin.token, { role: "SUPER_ADMIN" })).status, 403);
  });

  it("changes only the Spanish part of a Swedish club admin who also edits a Spanish tournament", async () => {
    const swede = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-se"], memberOf: [spain] });
    assert.deepEqual((await auth.getUser(swede.uid)).customClaims, { clubAdmin: true, editor: true });

    // The Spanish admin sees the Spanish assignment only, and cannot make the account an editor or a player.
    const list = (await server.request("GET", "/admin/users", { token: spanishAdmin.token })).body as { users: Array<{ uid: string; tournamentAssignments: Array<{ tournamentId: string }>; hasGrantsOutside: boolean; canManage: boolean }> };
    const seen = list.users.find((user) => user.uid === swede.uid);
    assert.deepEqual(seen?.tournamentAssignments.map((assignment) => assignment.tournamentId), [spain]);
    assert.equal(seen?.hasGrantsOutside, true);
    assert.equal(seen?.canManage, true);
    assert.equal((await updateUser(spanishAdmin.token, swede, { role: "EDITOR", tournamentAssignments: [{ tournamentId: spain }] })).status, 403);
    assert.equal((await updateUser(spanishAdmin.token, swede, { role: "CLUB_ADMIN", adminOrganizations: [], tournamentAssignments: [{ tournamentId: spain }, { tournamentId: spain2 }] })).status, 200);
    assert.equal((await db.doc(`tournaments/${spain2}/members/${swede.uid}`).get()).exists, true);
    // Taking the Spanish tournaments away leaves a club admin of the Swedish club.
    assert.equal((await updateUser(spanishAdmin.token, swede, { role: "CLUB_ADMIN", adminOrganizations: [], tournamentAssignments: [] })).status, 200);
    assert.deepEqual((await auth.getUser(swede.uid)).customClaims, { clubAdmin: true });
    assert.deepEqual((await db.doc(`users/${swede.uid}`).get()).get("adminOrganizations"), ["org-se"]);
  });

  it("takes its own organizations from another club admin, but cannot demote one with organizations of others", async () => {
    const both = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es", "org-se"] });

    assert.equal((await updateUser(spanishAdmin.token, both, { role: "EDITOR", adminOrganizations: [], tournamentAssignments: [{ tournamentId: spain }] })).status, 403);
    assert.equal((await updateUser(spanishAdmin.token, both, { role: "CLUB_ADMIN", adminOrganizations: [], tournamentAssignments: [] })).status, 200);
    assert.deepEqual((await db.doc(`users/${both.uid}`).get()).get("adminOrganizations"), ["org-se"]);
  });

  it("sees every player, the staff of its organizations, and not the staff of other organizations", async () => {
    const swedishEditor = await createAccount({ role: "EDITOR", memberOf: [sweden] });
    const someone = await createAccount({ role: "PLAYER", playerOf: [sweden] });
    const local = await createAccount({ role: "PLAYER", playerOf: [spain] });

    for (const viewer of [spanishAdmin, spanishEditor]) {
      const { users } = (await server.request("GET", "/admin/users", { token: viewer.token })).body as { users: Array<{ uid: string; email: string; emailHidden: boolean }> };
      const uids = users.map((user) => user.uid);
      assert.ok(!uids.includes(swedishEditor.uid), "a Swedish editor is not visible");
      assert.ok(uids.includes(spanishEditor.uid) && uids.includes(superAdmin.uid));
      const stranger = users.find((user) => user.uid === someone.uid);
      assert.equal(stranger?.emailHidden, true, "the email of a player of another tournament is hidden");
      assert.equal(stranger?.email, "");
      assert.equal(users.find((user) => user.uid === local.uid)?.email, local.email);
    }
    // Saving an account with a hidden email sends no email: the account keeps it.
    const saved = await server.request("PUT", `/admin/users/${someone.uid}`, {
      token: spanishEditor.token, body: { email: "", alias: "New alias", role: "PLAYER" },
    });
    assert.equal(saved.status, 200);
    assert.equal((await auth.getUser(someone.uid)).email, someone.email);
  });

  it("an editor that loses its last tournament becomes a player, or is disabled without an EMA id", async () => {
    const t = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES" });
    const withoutEma = await createAccount({ role: "EDITOR", memberOf: [t] });
    const withEma = await createAccount({ role: "EDITOR", memberOf: [t], playerOf: [spain] });

    const members = (await server.request("GET", `/tournaments/${t}/members`, { token: spanishAdmin.token })).body as { members: Array<{ uid: string; onlyTournament: boolean }> };
    assert.equal(members.members.find((member) => member.uid === withoutEma.uid)?.onlyTournament, true);
    const removed = await server.request("DELETE", `/tournaments/${t}/members/${withoutEma.uid}`, { token: spanishAdmin.token });
    assert.equal((removed.body as { result: string }).result, "DISABLED");
    assert.equal((await auth.getUser(withoutEma.uid)).disabled, true);

    // A country admin brings it back with a tournament of its countries.
    const listed = ((await server.request("GET", "/admin/users", { token: spanishAdmin.token })).body as { users: Array<{ uid: string; canManage: boolean; disabledWithoutRole: boolean }> })
      .users.find((user) => user.uid === withoutEma.uid);
    assert.equal(listed?.canManage, true);
    assert.equal(listed?.disabledWithoutRole, true);
    const back = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES" });
    const reassigned = await server.request("PUT", `/admin/users/${withoutEma.uid}`, {
      token: spanishAdmin.token, body: { email: "", alias: "Back", role: "EDITOR", tournamentAssignments: [{ tournamentId: back }] },
    });
    assert.equal(reassigned.status, 200);
    assert.equal((await auth.getUser(withoutEma.uid)).disabled, false);
    assert.equal((await auth.getUser(withoutEma.uid)).customClaims?.editor, true);
    assert.equal((reassigned.body as { disabledWithoutRole: boolean }).disabledWithoutRole, false);

    // An account that a super admin disabled stays disabled.
    const blocked = await createAccount({ role: "EDITOR", memberOf: [back] });
    assert.equal((await server.request("PUT", `/admin/users/${blocked.uid}/disabled`, { token: superAdmin.token, body: { disabled: true } })).status, 200);
    const other = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES" });
    assert.equal((await server.request("PUT", `/admin/users/${blocked.uid}`, {
      token: spanishAdmin.token, body: { email: "", alias: "", role: "EDITOR", tournamentAssignments: [{ tournamentId: back }, { tournamentId: other }] },
    })).status, 200);
    assert.equal((await auth.getUser(blocked.uid)).disabled, true);

    assert.equal((await server.request("DELETE", `/tournaments/${t}`, { token: spanishAdmin.token })).status, 200);
    assert.deepEqual((await auth.getUser(withEma.uid)).customClaims, { player: true });
    assert.equal((await auth.getUser(withEma.uid)).disabled, false);
  });

  it("asks a super admin to delete an account, after taking it out of its organizations", async () => {
    const t = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES" });
    const target = await createAccount({ role: "EDITOR", memberOf: [t, sweden], playerOf: [spain] });

    const removal = await server.request("POST", `/admin/users/${target.uid}/removal`, {
      token: spanishAdmin.token, body: { kind: "DELETE", reason: "Left the club" },
    });
    assert.equal(removal.status, 200);
    assert.equal((await db.doc(`tournaments/${t}/members/${target.uid}`).get()).exists, false);
    assert.equal((await db.doc(`tournaments/${sweden}/members/${target.uid}`).get()).exists, true, "the Swedish tournament stays");
    assert.equal((await server.request("GET", "/admin/account-requests", { token: spanishAdmin.token })).status, 403);

    const pending = (await server.request("GET", "/admin/account-requests", { token: superAdmin.token })).body as { requests: Array<{ id: string; targetUid: string; reason: string }> };
    const request = pending.requests.find((item) => item.targetUid === target.uid);
    assert.equal(request?.reason, "Left the club");
    const approve = await server.request("POST", `/admin/account-requests/${request?.id}`, { token: superAdmin.token, body: { approve: true } });
    assert.equal(approve.status, 200);
    await assert.rejects(auth.getUser(target.uid));
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
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => { await server.close(); });

  it("an account that just became an editor lists its tournaments with the token it already has", async () => {
    const promoted = await createAccount({ role: "PLAYER" });
    const before = (await getGlobalDataVersions()).tournaments.revision;
    await setGlobalUserRole(promoted.uid, "EDITOR");
    await upsertTournamentMember({ tournamentId: t1, uid: promoted.uid });
    assert.ok((await getGlobalDataVersions()).tournaments.revision > before, "cached tournament lists become old");

    // The token was issued while the account was a player, so it has no editor claim.
    const list = await server.request("GET", "/tournaments", { token: promoted.token });
    assert.equal(list.status, 200);
    const ids = (list.body as { tournaments: Array<{ id: string }> }).tournaments.map((tournament) => tournament.id);
    assert.deepEqual(ids, [t1]);
  });

  it("an assigned editor cannot add or remove editors, an admin can", async () => {
    const colleague = await createAccount({ role: "EDITOR" });
    const path = `/tournaments/${t1}/members/${colleague.uid}`;

    assert.equal((await server.request("PUT", path, { token: member.token })).status, 403);
    assert.equal((await server.request("PUT", path, { token: admin.token })).status, 200);
    assert.equal((await server.request("GET", `/tournaments/${t1}/players`, { token: colleague.token })).status, 200);
    assert.equal((await server.request("DELETE", path, { token: member.token })).status, 403);
    assert.equal((await server.request("DELETE", path, { token: admin.token })).status, 200);
  });

  it("an admin account cannot be added as a member, because admins already see everything", async () => {
    const { status } = await server.request("PUT", `/tournaments/${t1}/members/${admin.uid}`, { token: admin.token });

    assert.equal(status, 400);
  });

  it("an editor cannot save a table of a completed tournament, an admin can", async () => {
    const done = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], isCompleted: true });
    const editor = await createAccount({ role: "EDITOR", memberOf: [done] });
    const path = `/tournaments/${done}/tables/1/1/state`;
    const body = { expectedVersion: 0, tablePatch: { playerEastId: "1" }, handPatches: [] };

    assert.equal((await server.request("PUT", path, { token: editor.token, body })).status, 403);
    assert.equal((await server.request("PUT", path, { token: admin.token, body })).status, 200);
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
    const response = await server.request("POST", path, { token: admin.token });
    assert.equal(response.status, 200);
    // The answer carries the empty table, so the admin app shows it without another request.
    const body = response.body as { table: { playerEastId: string; version: number }; hands: unknown[] };
    assert.equal(body.table.playerEastId, "");
    assert.ok(body.table.version > 0);
    assert.ok(Array.isArray(body.hands));
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

describe("input checks on public and path values", () => {
  let server: TestServer;
  let admin: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
  });
  after(async () => { await server.close(); });

  it("refuses a tournament id that contains a slash", async () => {
    const { status } = await server.request("GET", "/tournaments/a%2Fb/sync/manifest", { token: admin.token });

    assert.equal(status, 400);
  });

  it("answers the same for a password reset of an unknown email", async () => {
    process.env.MTS_ID_TOOLKIT_API_KEY ??= "test-key";
    const { status } = await server.request("POST", "/auth/passwordReset", { body: { email: "nobody-here@example.test" } });

    assert.equal(status, 200);
  });

  it("refuses requests without an App Check token in enforce mode, except the health check", async () => {
    process.env.APP_CHECK_MODE = "enforce";
    try {
      assert.equal((await server.request("GET", "/health")).status, 200);
      const config = await server.request("GET", "/config");
      assert.equal(config.status, 401);
      assert.equal((config.body as { error?: string }).error, "app_check_failed");
    } finally {
      delete process.env.APP_CHECK_MODE;
    }
  });
});
