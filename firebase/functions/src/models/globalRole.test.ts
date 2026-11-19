import assert from "node:assert/strict";
import test from "node:test";

import { getGlobalUserRole, getGlobalUserRoles, hasStaffClaim, hasSuperAdminClaim, parseGlobalUserRole, ranksAbove } from "./globalRole";
import { PERMISSIONS, ROLE_PERMISSIONS } from "./permissions";

test("gives staff access only to super admins, club admins and editors", () => {
  assert.equal(hasStaffClaim(undefined), false);
  assert.equal(hasStaffClaim({}), false);
  assert.equal(hasStaffClaim({ superAdmin: true }), true);
  assert.equal(hasStaffClaim({ clubAdmin: true }), true);
  assert.equal(hasStaffClaim({ editor: true }), true);
  assert.equal(hasStaffClaim({ player: true }), false);
  assert.equal(hasSuperAdminClaim(undefined), false);
  assert.equal(hasSuperAdminClaim({ superAdmin: true }), true);
});

test("lists every role of an account in rank order", () => {
  assert.deepEqual(getGlobalUserRoles({ customClaims: undefined }), []);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { superAdmin: true } }), ["SUPER_ADMIN"]);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { player: true } }), ["PLAYER"]);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { editor: true, player: true } }), ["EDITOR", "PLAYER"]);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { clubAdmin: true, editor: true } }), ["CLUB_ADMIN", "EDITOR"]);
  assert.equal(getGlobalUserRole({ customClaims: { player: true } }), "PLAYER");
  assert.equal(getGlobalUserRole({ customClaims: { clubAdmin: true, editor: true, player: true } }), "CLUB_ADMIN");
});

test("knows only the new role names, not the old admin claim", () => {
  assert.equal(getGlobalUserRole({ customClaims: { admin: true } }), "PLAYER");
  assert.equal(parseGlobalUserRole("EDITOR"), "EDITOR");
  assert.equal(parseGlobalUserRole("CLUB_ADMIN"), "CLUB_ADMIN");
  assert.equal(parseGlobalUserRole("SUPER_ADMIN"), "SUPER_ADMIN");
  assert.equal(parseGlobalUserRole("ADMIN"), null);
  assert.equal(parseGlobalUserRole("PLAYER"), null);
  assert.ok(ranksAbove("SUPER_ADMIN", "CLUB_ADMIN"));
  assert.ok(ranksAbove("CLUB_ADMIN", "EDITOR"));
  assert.ok(!ranksAbove("EDITOR", "CLUB_ADMIN"));
});

test("each role has the permissions that Ernesto chose", () => {
  const editor = new Set(ROLE_PERMISSIONS.EDITOR);
  const clubAdmin = new Set(ROLE_PERMISSIONS.CLUB_ADMIN);
  const superAdmin = new Set(ROLE_PERMISSIONS.SUPER_ADMIN);
  // A higher role can do everything that a lower role can do, except asking a super admin.
  for (const permission of editor) assert.ok(clubAdmin.has(permission), permission);
  for (const permission of clubAdmin) if (permission !== "accounts.requestRemoval") assert.ok(superAdmin.has(permission), permission);
  assert.deepEqual(ROLE_PERMISSIONS.PLAYER, []);
  // Editors run the round control and create players, and nothing of the admins.
  for (const permission of ["tournament.roundControl", "accounts.createPlayer"] as const) assert.ok(editor.has(permission), permission);
  for (const permission of ["tournament.delete", "tournament.resetTable", "tournament.members", "accounts.manageStaff", "ema.create"] as const) {
    assert.ok(!editor.has(permission), permission);
  }
  // Club admins: destructive actions in their tournaments, new EMA players, no DEV menu, no global account actions.
  for (const permission of ["tournament.delete", "tournament.resetTable", "tournaments.create", "tournaments.changeOrganizers", "ema.create", "accounts.manageStaff", "organizations.edit"] as const) {
    assert.ok(clubAdmin.has(permission), permission);
  }
  for (const permission of [
    "dev.activity", "dev.errors", "ema.edit", "ema.delete", "accounts.disable", "accounts.delete", "accounts.changeEmail",
    "accounts.changeEmaId", "accounts.manageSuperAdmins", "organizations.create", "organizations.delete", "tournaments.deleteFinished",
  ] as const) {
    assert.ok(!clubAdmin.has(permission), permission);
  }
  assert.equal(new Set(PERMISSIONS).size, PERMISSIONS.length);
});
