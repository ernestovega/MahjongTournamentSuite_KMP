import assert from "node:assert/strict";
import test from "node:test";

import {
  GRANTABLE,
  PERMISSIONS,
  REQUIRED,
  ROLE_PERMISSIONS,
  SUPER_ADMIN_ONLY,
  type Permission,
} from "../models/permissions";
import {
  effectivePermissions,
  parseCustomizableRole,
  parseGrantablePermissions,
  readPermissionSettings,
  rolePermissionsOf,
  type CustomRole,
  type PermissionSettings,
} from "./customPermissions";

const referee: CustomRole = {
  id: "referee",
  name: "Head referee",
  base: "EDITOR",
  permissions: ["tournament.view", "tournament.edit"],
};
const roles = new Map<string, CustomRole>([[referee.id, referee]]);
const none: PermissionSettings = { customRoles: {}, permissionChanges: {} };

test("no permission only for super admins can be given to another role", () => {
  for (const level of ["EDITOR", "CLUB_ADMIN"] as const) {
    GRANTABLE[level].forEach((permission) => assert.ok(!SUPER_ADMIN_ONLY.has(permission), `${level} ${permission}`));
    REQUIRED.forEach((permission) => assert.ok(GRANTABLE[level].includes(permission)));
    ROLE_PERMISSIONS[level].forEach((permission) => assert.ok(GRANTABLE[level].includes(permission), `${level} ${permission}`));
  }
  assert.ok(SUPER_ADMIN_ONLY.has("permissions.manage"));
  assert.ok(ROLE_PERMISSIONS.SUPER_ADMIN.includes("permissions.manage"));
  GRANTABLE.EDITOR.forEach((permission) => assert.ok(GRANTABLE.CLUB_ADMIN.includes(permission), permission));
});

test("without a custom role or changes a person keeps the standard role", () => {
  for (const level of ["EDITOR", "CLUB_ADMIN"] as const) {
    const effective = effectivePermissions(level, none, roles);
    assert.deepEqual([...effective].sort(), [...new Set([...ROLE_PERMISSIONS[level], ...REQUIRED])].sort());
  }
});

test("a custom role replaces the standard one only at its own level", () => {
  assert.deepEqual(rolePermissionsOf("EDITOR", "referee", roles), referee.permissions);
  assert.deepEqual(rolePermissionsOf("CLUB_ADMIN", "referee", roles), [...ROLE_PERMISSIONS.CLUB_ADMIN]);
  assert.deepEqual(rolePermissionsOf("EDITOR", "deleted-role", roles), [...ROLE_PERMISSIONS.EDITOR]);
});

test("changes add and take away permissions, cut to what the level allows, and keep the required ones", () => {
  const extra = GRANTABLE.EDITOR.find((permission) => !referee.permissions.includes(permission))!;
  const settings: PermissionSettings = {
    customRoles: { EDITOR: "referee" },
    permissionChanges: { EDITOR: { added: [extra, "accounts.delete"], removed: ["tournament.view", ...referee.permissions] } },
  };
  const effective = effectivePermissions("EDITOR", settings, roles);
  assert.ok(effective.has(extra));
  assert.ok(!effective.has("accounts.delete"), "a super admin permission never comes in");
  assert.ok(effective.has("tournament.view"), "the required permission stays");
  referee.permissions.filter((p) => !REQUIRED.includes(p)).forEach((permission) => assert.ok(!effective.has(permission)));
});

test("reads only known roles and permissions from a profile", () => {
  const settings = readPermissionSettings({
    customRoles: { EDITOR: "referee", CLUB_ADMIN: "", PLAYER: "x" },
    permissionChanges: {
      EDITOR: { added: ["tournament.view", "not.a.permission"], removed: "nope" },
      CLUB_ADMIN: { added: [], removed: [] },
    },
  });
  assert.deepEqual(settings, {
    customRoles: { EDITOR: "referee" },
    permissionChanges: { EDITOR: { added: ["tournament.view"], removed: [] } },
  });
  assert.deepEqual(readPermissionSettings(undefined), none);
});

test("refuses lists that an app should never send", () => {
  assert.deepEqual(parseGrantablePermissions(undefined, "EDITOR", "permissions"), []);
  assert.throws(() => parseGrantablePermissions("tournament.view", "EDITOR", "permissions"), /must be an array/);
  assert.throws(() => parseGrantablePermissions(["nope"], "EDITOR", "permissions"), /unknown permission/);
  assert.throws(() => parseGrantablePermissions(["accounts.delete"], "CLUB_ADMIN", "permissions"), /cannot have/);
  const notForEditors = GRANTABLE.CLUB_ADMIN.find((permission) => !GRANTABLE.EDITOR.includes(permission))!;
  assert.throws(() => parseGrantablePermissions([notForEditors], "EDITOR", "permissions"), /cannot have/);
  const ordered = parseGrantablePermissions([...GRANTABLE.EDITOR].reverse(), "EDITOR", "permissions");
  assert.deepEqual(ordered, PERMISSIONS.filter((permission) => GRANTABLE.EDITOR.includes(permission)));
  assert.equal(parseCustomizableRole("EDITOR"), "EDITOR");
  assert.throws(() => parseCustomizableRole("SUPER_ADMIN"), /EDITOR or CLUB_ADMIN/);
});
