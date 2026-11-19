import assert from "node:assert/strict";
import test from "node:test";

import { ROLE_PERMISSIONS, type Permission } from "../models/permissions";
import { canChangeOrganizers, type Access } from "./accessService";

function access(overrides: Partial<Access> = {}, clubAdminPermissions: readonly Permission[] = ROLE_PERMISSIONS.CLUB_ADMIN): Access {
  return {
    uid: "u1",
    disabled: false,
    superAdmin: false,
    adminOrganizations: new Set<string>(),
    editor: false,
    player: false,
    rolePermissions: { EDITOR: new Set(ROLE_PERMISSIONS.EDITOR), CLUB_ADMIN: new Set(clubAdminPermissions) },
    ...overrides,
  };
}

const madridAdmin = access({ adminOrganizations: new Set(["madrid"]) });

test("a super admin changes the organizers always", () => {
  assert.equal(canChangeOrganizers(access({ superAdmin: true }), ["madrid"], ["paris"]), true);
  assert.equal(canChangeOrganizers(access({ superAdmin: true }), [], ["paris"]), true);
});

test("a club admin of the main organizer changes them while it keeps one of its organizations", () => {
  assert.equal(canChangeOrganizers(madridAdmin, ["madrid"], ["madrid", "paris"]), true, "adds a co-organizer");
  assert.equal(canChangeOrganizers(madridAdmin, ["madrid", "paris"], ["madrid"]), true, "removes a co-organizer");
  assert.equal(canChangeOrganizers(madridAdmin, ["madrid", "paris"], ["paris", "madrid"]), true, "hands the main role to a co-organizer");
  assert.equal(canChangeOrganizers(madridAdmin, ["madrid"], ["paris"]), false, "gives the tournament away");
});

test("the club admin of a co-organizer does not change them", () => {
  assert.equal(canChangeOrganizers(madridAdmin, ["paris", "madrid"], ["paris", "madrid", "lisbon"]), false);
  assert.equal(canChangeOrganizers(madridAdmin, ["paris", "madrid"], ["madrid", "paris"]), false);
});

test("an account without the permission or without organizations does not change them", () => {
  const withoutPermission = access(
    { adminOrganizations: new Set(["madrid"]) },
    ROLE_PERMISSIONS.CLUB_ADMIN.filter((permission) => permission !== "tournaments.changeOrganizers"),
  );
  assert.equal(canChangeOrganizers(withoutPermission, ["madrid"], ["madrid", "paris"]), false);
  assert.equal(canChangeOrganizers(access({ editor: true }), ["madrid"], ["madrid", "paris"]), false);
  assert.equal(canChangeOrganizers(madridAdmin, [], ["madrid"]), false, "a tournament without organizers is for a super admin");
});
