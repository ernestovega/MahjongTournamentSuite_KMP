import assert from "node:assert/strict";
import test from "node:test";

import { getGlobalUserRole, getGlobalUserRoles, hasAdminClaim, hasEditorAccess, parseGlobalUserRole } from "./globalRole";

test("gives editor access only to admins and editors", () => {
  assert.equal(hasEditorAccess(undefined), false);
  assert.equal(hasEditorAccess({}), false);
  assert.equal(hasEditorAccess({ admin: true }), true);
  assert.equal(hasEditorAccess({ editor: true }), true);
  assert.equal(hasEditorAccess({ player: true }), false);
  assert.equal(hasEditorAccess({ player: true, editor: true }), true);
  assert.equal(hasEditorAccess({ player: true, admin: true }), true);
  assert.equal(hasAdminClaim(undefined), false);
});

test("lists every capability of an account", () => {
  assert.deepEqual(getGlobalUserRoles({ customClaims: undefined }), []);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { admin: true } }), ["ADMIN"]);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { player: true } }), ["PLAYER"]);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { editor: true, player: true } }), ["EDITOR", "PLAYER"]);
  assert.deepEqual(getGlobalUserRoles({ customClaims: { admin: true, player: true } }), ["ADMIN", "PLAYER"]);
  assert.equal(getGlobalUserRole({ customClaims: { player: true } }), "PLAYER");
  assert.equal(getGlobalUserRole({ customClaims: { editor: true, player: true } }), "EDITOR");
});

test("recognizes only the new admin claim and role names", () => {
  assert.equal(getGlobalUserRole({ customClaims: { admin: true } }), "ADMIN");
  assert.equal(hasAdminClaim({ admin: true }), true);
  assert.equal(parseGlobalUserRole("EDITOR"), "EDITOR");
  assert.equal(parseGlobalUserRole("ADMIN"), "ADMIN");
  assert.equal(parseGlobalUserRole("SUPERADMIN"), null);
  assert.equal(parseGlobalUserRole("READER"), null);
});
