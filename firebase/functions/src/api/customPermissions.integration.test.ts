import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { seedTournament } from "../testing/firestoreFixtures";
import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { GRANTABLE, ROLE_PERMISSIONS } from "../models/permissions";

type Body = Record<string, any>;

describe("custom roles and the permissions of one person", () => {
  let server: TestServer;
  let es = "";
  let fr = "";
  let superAdmin: TestAccount;
  let clubAdmin: TestAccount;
  let editor: TestAccount;
  let frenchEditor: TestAccount;
  let otherClubAdmin: TestAccount;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    es = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "ES" });
    fr = await seedTournament({ tables: [{ roundId: 1, tableId: 1 }], hostCountry: "FR" });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    clubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    otherClubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: ["org-es"] });
    editor = await createAccount({ role: "EDITOR", memberOf: [es] });
    frenchEditor = await createAccount({ role: "EDITOR", memberOf: [es, fr] });
  });
  after(async () => { await server.close(); });

  const createRole = async (body: Body, token = superAdmin.token) =>
    server.request("POST", "/admin/roles", { token, body });
  const setPermissions = (token: string, uid: string, levels: Body) =>
    server.request("PUT", `/admin/users/${uid}/permissions`, { token, body: { levels } });
  const whoami = async (token: string) =>
    ((await server.request("GET", "/admin/whoami", { token })).body as Body).permissions as string[];
  const tournamentPermissions = async (token: string, tournamentId: string) => {
    const list = (await server.request("GET", "/tournaments/", { token })).body as Body;
    const items = (Array.isArray(list) ? list : list.tournaments) as Body[];
    return items.find((item) => item.id === tournamentId)?.permissions as string[] | undefined;
  };

  it("lists the standard roles, the custom ones and what each base may hold", async () => {
    const { status, body } = await server.request("GET", "/admin/roles", { token: editor.token });
    assert.equal(status, 200);
    assert.deepEqual((body as Body).standard.map((role: Body) => role.id), ["EDITOR", "CLUB_ADMIN", "SUPER_ADMIN"]);
    assert.deepEqual((body as Body).grantable.EDITOR, GRANTABLE.EDITOR);
    assert.ok(!(body as Body).grantable.CLUB_ADMIN.includes("permissions.manage"));
  });

  it("only a super admin creates roles, and never with a permission that the base cannot hold", async () => {
    assert.equal((await createRole({ name: "Árbitro", base: "EDITOR", permissions: ["tournament.view"] }, clubAdmin.token)).status, 403);
    assert.equal((await createRole({ name: "Bad", base: "EDITOR", permissions: ["tournaments.create"] })).status, 400);
    assert.equal((await createRole({ name: "Bad", base: "CLUB_ADMIN", permissions: ["accounts.delete"] })).status, 400);
    assert.equal((await createRole({ name: "Bad", base: "SUPER_ADMIN", permissions: [] })).status, 400);
    assert.equal((await createRole({ name: "Editor", base: "EDITOR", permissions: [] })).status, 400);
    const { status, body } = await createRole({ name: "Read only", base: "EDITOR", permissions: [] });
    assert.equal(status, 201);
    // A role always keeps the view of its tournaments.
    assert.deepEqual((body as Body).permissions, ["tournament.view"]);
    assert.equal((await createRole({ name: "read ONLY", base: "EDITOR", permissions: [] })).status, 400);
  });

  it("a custom role changes what an editor may do at its next request", async () => {
    const role = (await createRole({ name: "Configurator", base: "EDITOR", permissions: [...ROLE_PERMISSIONS.EDITOR, "tournament.configure"] })).body as Body;
    const account = await createAccount({ role: "EDITOR", memberOf: [es] });
    assert.ok(!(await tournamentPermissions(account.token, es))?.includes("tournament.configure"));
    assert.equal((await setPermissions(superAdmin.token, account.uid, { EDITOR: { roleId: role.id } })).status, 200);
    assert.ok((await tournamentPermissions(account.token, es))?.includes("tournament.configure"));
    // Editing the role reaches the account too.
    assert.equal((await server.request("PUT", `/admin/roles/${role.id}`, {
      token: superAdmin.token, body: { name: "Configurator", permissions: ["tournament.view", "tournament.edit"] },
    })).status, 200);
    const now = await tournamentPermissions(account.token, es);
    assert.ok(!now?.includes("tournament.configure"));
    assert.ok(!now?.includes("tournament.roundControl"));
    // Deleting it puts the account back on the standard role.
    const removed = await server.request("DELETE", `/admin/roles/${role.id}`, { token: superAdmin.token });
    assert.deepEqual([removed.status, (removed.body as Body).accounts], [200, 1]);
    assert.ok((await tournamentPermissions(account.token, es))?.includes("tournament.roundControl"));
  });

  it("a super admin adds or takes away single permissions, and only the difference is kept", async () => {
    const account = await createAccount({ role: "EDITOR", memberOf: [es] });
    const wanted = [...ROLE_PERMISSIONS.EDITOR.filter((permission) => permission !== "tools.idCards"), "ema.create"];
    const { status, body } = await setPermissions(superAdmin.token, account.uid, { EDITOR: { roleId: null, permissions: wanted } });
    assert.equal(status, 200);
    assert.deepEqual((body as Body).permissionSettings.permissionChanges.EDITOR, { added: ["ema.create"], removed: ["tools.idCards"] });
    const now = await whoami(account.token);
    assert.ok(now.includes("ema.create"));
    assert.ok(!now.includes("tools.idCards"));
    // Never more than the base allows, and never without the view.
    assert.equal((await setPermissions(superAdmin.token, account.uid, { EDITOR: { permissions: ["tournaments.create"] } })).status, 400);
    const viewless = await setPermissions(superAdmin.token, account.uid, { EDITOR: { permissions: [] } });
    assert.deepEqual((viewless.body as Body).rolePermissions.EDITOR, ["tournament.view"]);
    // null goes back to the role as it is.
    const reset = await setPermissions(superAdmin.token, account.uid, { EDITOR: { permissions: null } });
    assert.equal((reset.body as Body).permissionSettings.permissionChanges.EDITOR, undefined);
  });

  it("a club admin chooses roles in its organizations, but never single permissions or a role above its own", async () => {
    const small = (await createRole({ name: "Small", base: "EDITOR", permissions: ["tournament.view", "tournament.edit"] })).body as Body;
    const big = (await createRole({ name: "Big", base: "EDITOR", permissions: ["tournament.view", "ema.edit"] })).body as Body;
    assert.equal((await setPermissions(clubAdmin.token, editor.uid, { EDITOR: { roleId: small.id } })).status, 200);
    assert.equal((await setPermissions(clubAdmin.token, editor.uid, { EDITOR: { roleId: big.id } })).status, 403);
    assert.equal((await setPermissions(clubAdmin.token, editor.uid, { EDITOR: { permissions: ["tournament.view"] } })).status, 403);
    // An editor with a tournament in France keeps its role unless a super admin changes it.
    assert.equal((await setPermissions(clubAdmin.token, frenchEditor.uid, { EDITOR: { roleId: small.id } })).status, 403);
    // Another club admin of the same organization may get a smaller club admin role.
    const smallAdmin = (await createRole({ name: "Small admin", base: "CLUB_ADMIN", permissions: ["tournament.view", "tournament.edit"] })).body as Body;
    assert.equal((await setPermissions(clubAdmin.token, otherClubAdmin.uid, { CLUB_ADMIN: { roleId: smallAdmin.id } })).status, 200);
    assert.ok(!(await whoami(otherClubAdmin.token)).includes("tournaments.create"));
  });

  it("refuses the own account, super admins and editors", async () => {
    assert.equal((await setPermissions(clubAdmin.token, clubAdmin.uid, { EDITOR: { roleId: null } })).status, 400);
    assert.equal((await setPermissions(superAdmin.token, superAdmin.uid, { EDITOR: { roleId: null } })).status, 400);
    const other = await createAccount({ role: "SUPER_ADMIN" });
    assert.equal((await setPermissions(superAdmin.token, other.uid, { EDITOR: { roleId: null } })).status, 400);
    assert.equal((await setPermissions(editor.token, frenchEditor.uid, { EDITOR: { roleId: null } })).status, 403);
  });
});
