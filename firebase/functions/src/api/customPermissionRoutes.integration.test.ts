import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { seedOrganization, seedTournament } from "../testing/firestoreFixtures";
import { upsertTournamentMember } from "../services/membersService";
import { GRANTABLE, REQUIRED, ROLE_PERMISSIONS, SUPER_ADMIN_ONLY, type CustomizableRole, type Permission } from "../models/permissions";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { concrete, MATRIX } from "../testing/routeMatrix";

/**
 * The route table of `rolePermissions.integration.test.ts` again, with custom permissions instead of the standard
 * roles. For each customizable level (editor, club admin) one account holds only what a role always keeps and another
 * holds everything that the level may get. So every permission is tried both taken away and given, on every route
 * that checks it, inside and outside the scope of the account. Permissions that only a super admin has stay closed.
 */
const denied = (status: number) => status === 401 || status === 403;
type Body = Record<string, any>;

const GUARDED = MATRIX.filter(([, , access]) => !["PUBLIC", "SIGNED_IN", "PLAYER"].includes(access));

describe("custom permissions open and close exactly their routes", () => {
  let server: TestServer;
  let superAdmin: TestAccount;
  const accounts = {} as Record<CustomizableRole, { fewest: TestAccount; most: TestAccount }>;

  const setPermissions = async (account: TestAccount, level: CustomizableRole, permissions: Permission[] | null) => {
    const response = await server.request("PUT", `/admin/users/${account.uid}/permissions`, {
      token: superAdmin.token, body: { levels: { [level]: { permissions } } },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return response.body as Body;
  };

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    for (const id of ["org-es", "org-fr"]) await seedOrganization({ id });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    for (const level of ["EDITOR", "CLUB_ADMIN"] as const) {
      const create = () => level === "EDITOR" ? createAccount({ role: "EDITOR" }) : createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
      accounts[level] = { fewest: await create(), most: await create() };
      // The view of the tournaments is the one permission that a role always keeps.
      await setPermissions(accounts[level].fewest, level, []);
      await setPermissions(accounts[level].most, level, [...GRANTABLE[level]]);
    }
  });
  after(async () => { await server.close(); });

  it("the accounts hold what the level allows and nothing more", async () => {
    for (const level of ["EDITOR", "CLUB_ADMIN"] as const) {
      const whoami = async (account: TestAccount) =>
        ((await server.request("GET", "/admin/whoami", { token: account.token })).body as Body).permissions as Permission[];
      assert.deepEqual(await whoami(accounts[level].fewest), [...REQUIRED], level);
      const most = await whoami(accounts[level].most);
      assert.deepEqual([...most].sort(), [...GRANTABLE[level]].sort(), level);
      assert.ok(most.every((permission) => !SUPER_ADMIN_ONLY.has(permission)), level);
    }
  });

  for (const [method, path, access] of GUARDED) {
    const inTournament = access.startsWith("@");
    const permission = (inTournament ? access.slice(1) : access) as Permission;
    // The activity route checks its tournament inside the route.
    const scoped = inTournament || permission === "tournament.activity";

    it(`${method} ${path} (${access})`, async () => {
      for (const level of ["EDITOR", "CLUB_ADMIN"] as const) {
        for (const [label, account, holds] of [
          ["fewest", accounts[level].fewest, REQUIRED.includes(permission)],
          ["most", accounts[level].most, GRANTABLE[level].includes(permission)],
        ] as const) {
          // Fresh tournaments for each caller, because a call that passes may change or delete them: one in the scope
          // of the account (org-es, assigned to the editor) and one outside it (org-fr, assigned to nobody).
          const own = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
          const foreign = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-fr"] });
          if (level === "EDITOR") await upsertTournamentMember({ tournamentId: own, uid: account.uid });
          const inside = await server.request(method, concrete(path, own), { token: account.token, body: {} });
          if (holds) assert.ok(!denied(inside.status), `${level} ${label} in its scope: status ${inside.status}`);
          else assert.equal(inside.status, 403, `${level} ${label} in its scope: status ${inside.status}`);
          if (scoped) {
            const outside = await server.request(method, concrete(path, foreign), { token: account.token, body: {} });
            assert.equal(outside.status, 403, `${level} ${label} outside its scope: status ${outside.status}`);
          }
        }
      }
    });
  }
});

describe("custom permissions of an account with two levels stay at their own level", () => {
  let server: TestServer;
  let superAdmin: TestAccount;
  let both: TestAccount;
  let clubTournament = "";
  let editedTournament = "";

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    for (const id of ["org-es", "org-fr"]) await seedOrganization({ id });
    clubTournament = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
    editedTournament = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-fr"] });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    // Club admin of org-es and editor of a tournament of org-fr.
    both = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"], memberOf: [editedTournament] });
  });
  after(async () => { await server.close(); });

  const setLevels = async (levels: Body) => {
    const response = await server.request("PUT", `/admin/users/${both.uid}/permissions`, { token: superAdmin.token, body: { levels } });
    assert.equal(response.status, 200, JSON.stringify(response.body));
  };
  const permissionsIn = async (tournamentId: string) => {
    const list = (await server.request("GET", "/tournaments/", { token: both.token })).body as Body;
    const items = (Array.isArray(list) ? list : list.tournaments) as Body[];
    return items.find((item) => item.id === tournamentId)?.permissions as Permission[] | undefined;
  };
  const configure = (tournamentId: string) =>
    server.request("PUT", `/tournaments/${tournamentId}/settings`, { token: both.token, body: {} });
  const saveTable = (tournamentId: string) =>
    server.request("PUT", `/tournaments/${tournamentId}/tables/1/1/state`, { token: both.token, body: {} });

  it("a smaller club admin role does not take anything from the editor part, nor the other way round", async () => {
    await setLevels({ CLUB_ADMIN: { permissions: ["tournament.view"] }, EDITOR: { permissions: null } });
    assert.deepEqual(await permissionsIn(clubTournament), ["tournament.view"]);
    assert.ok((await permissionsIn(editedTournament))?.includes("tournament.edit"));
    assert.equal((await configure(clubTournament)).status, 403);
    assert.equal((await saveTable(clubTournament)).status, 403);
    assert.ok(!denied((await saveTable(editedTournament)).status));
  });

  it("a permission added to the editor part works only in the assigned tournament", async () => {
    await setLevels({ CLUB_ADMIN: { permissions: ["tournament.view"] }, EDITOR: { permissions: [...ROLE_PERMISSIONS.EDITOR, "tournament.configure"] } });
    assert.ok(!denied((await configure(editedTournament)).status));
    assert.equal((await configure(clubTournament)).status, 403);
  });

  it("a permission taken from the editor part stays with the club admin part", async () => {
    await setLevels({ CLUB_ADMIN: { permissions: null }, EDITOR: { permissions: ["tournament.view"] } });
    assert.equal((await saveTable(editedTournament)).status, 403);
    assert.ok(!denied((await saveTable(clubTournament)).status));
    assert.ok(!denied((await configure(clubTournament)).status));
  });
});

describe("a club admin without the staff permission", () => {
  let server: TestServer;
  let superAdmin: TestAccount;
  let clubAdmin: TestAccount;
  let tournament = "";

  before(async () => {
    requireAuthEmulator();
    process.env.MTS_ID_TOOLKIT_API_KEY ??= "test-key";
    server = await startServer();
    await seedOrganization({ id: "org-es" });
    tournament = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], organizerIds: ["org-es"] });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    clubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    const fewer = ROLE_PERMISSIONS.CLUB_ADMIN.filter((permission) => permission !== "accounts.manageStaff");
    const response = await server.request("PUT", `/admin/users/${clubAdmin.uid}/permissions`, {
      token: superAdmin.token, body: { levels: { CLUB_ADMIN: { permissions: fewer } } },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
  });
  after(async () => { await server.close(); });

  const update = (account: TestAccount, body: Body) =>
    server.request("PUT", `/admin/users/${account.uid}`, { token: clubAdmin.token, body: { email: account.email, alias: "x", ...body } });

  it("creates no staff accounts", async () => {
    const { status } = await server.request("POST", "/admin/users", {
      token: clubAdmin.token,
      body: { email: "new-editor@example.test", role: "EDITOR", tournamentAssignments: [{ tournamentId: tournament }] },
    });
    assert.equal(status, 403);
  });

  it("changes neither the role, nor the organizations, nor the tournaments of the staff of its club", async () => {
    const editor = await createAccount({ role: "EDITOR", memberOf: [tournament] });
    const otherAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    assert.equal((await update(editor, { role: "CLUB_ADMIN", adminOrganizations: ["org-es"], tournamentAssignments: [] })).status, 403);
    assert.equal((await update(editor, { role: "EDITOR", tournamentAssignments: [] })).status, 403);
    assert.equal((await update(otherAdmin, { role: "EDITOR", adminOrganizations: [], tournamentAssignments: [{ tournamentId: tournament }] })).status, 403);
  });

  it("chooses no role for the staff of its club", async () => {
    const editor = await createAccount({ role: "EDITOR", memberOf: [tournament] });
    const { status } = await server.request("PUT", `/admin/users/${editor.uid}/permissions`, {
      token: clubAdmin.token, body: { levels: { EDITOR: { roleId: null } } },
    });
    assert.equal(status, 403);
  });

  it("still edits its own profile", async () => {
    assert.equal((await update(clubAdmin, { role: "CLUB_ADMIN", adminOrganizations: ["org-es"] })).status, 200);
  });
});
