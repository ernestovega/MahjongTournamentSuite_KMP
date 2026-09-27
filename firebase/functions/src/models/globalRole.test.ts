import assert from "node:assert/strict";
import test from "node:test";

import { getGlobalUserRole, hasAdminClaim, parseGlobalUserRole } from "./globalRole";

test("treats accounts without the admin claim as editors", () => {
  assert.equal(getGlobalUserRole({ customClaims: undefined }), "EDITOR");
  assert.equal(getGlobalUserRole({ customClaims: { admin: false } }), "EDITOR");
  assert.equal(hasAdminClaim(undefined), false);
});

test("recognizes only the new admin claim and role names", () => {
  assert.equal(getGlobalUserRole({ customClaims: { admin: true } }), "ADMIN");
  assert.equal(hasAdminClaim({ admin: true }), true);
  assert.equal(parseGlobalUserRole("EDITOR"), "EDITOR");
  assert.equal(parseGlobalUserRole("ADMIN"), "ADMIN");
  assert.equal(parseGlobalUserRole("SUPERADMIN"), null);
  assert.equal(parseGlobalUserRole("READER"), null);
});
