import assert from "node:assert/strict";
import test from "node:test";

import {
  accountOrganizations,
  canManageStaffAccount,
  canSeeAccount,
  hasGrantsOutside,
  isStaffAccount,
  viewOfAccount,
  type AccountScope,
} from "./accountScope";
import { parseAccountRequestKind } from "./accountRequests";
import type { ManagedUser } from "./usersService";

/**
 * Which accounts a Club Admin or an editor sees and changes in Accounts (club-admin-role, 19-nov): the scope rules
 * without Firestore. The routes are covered by rolePermissions.integration.test.
 */

function user(uid: string, fields: Partial<ManagedUser> = {}): ManagedUser {
  const roles = fields.roles ?? ["PLAYER"];
  return {
    uid,
    email: `${uid}@example.test`,
    alias: uid,
    emaId: null,
    photoUrl: null,
    hasOwnPhoto: false,
    role: roles[0],
    roles,
    disabled: false,
    disabledWithoutRole: false,
    tournamentAssignments: [],
    adminOrganizations: [],
    permissionSettings: { customRoles: {}, permissionChanges: {} },
    rolePermissions: {} as ManagedUser["rolePermissions"],
    ...fields,
  } as ManagedUser;
}

const superAdmin: AccountScope = {
  superAdmin: true, uid: "root", tournamentIds: new Set(), organizations: new Set(), manageOrganizations: new Set(),
  manageTournamentIds: new Set(), emaIdsWithEmail: null,
};

/** A club admin of the Madrid club, which organizes tournament t-mad. It also edits t-bcn of the Barcelona club. */
const madridAdmin: AccountScope = {
  superAdmin: false,
  uid: "clubadmin",
  tournamentIds: new Set(["t-mad", "t-bcn"]),
  organizations: new Set(["mad", "bcn"]),
  manageOrganizations: new Set(["mad"]),
  manageTournamentIds: new Set(["t-mad"]),
  emaIdsWithEmail: new Set(["10990001"]),
};

/** An editor of t-bcn only: it manages no accounts. */
const barcelonaEditor: AccountScope = {
  superAdmin: false,
  uid: "editor",
  tournamentIds: new Set(["t-bcn"]),
  organizations: new Set(["bcn"]),
  manageOrganizations: new Set(),
  manageTournamentIds: new Set(),
  emaIdsWithEmail: new Set(),
};

const madridEditor = user("ed-mad", { roles: ["EDITOR"], tournamentAssignments: [{ tournamentId: "t-mad", tournamentName: "Madrid", organizerIds: ["mad"] }] });
const valenciaEditor = user("ed-val", { roles: ["EDITOR"], tournamentAssignments: [{ tournamentId: "t-val", tournamentName: "Valencia", organizerIds: ["val"] }] });
const bothEditor = user("ed-both", {
  roles: ["EDITOR"],
  tournamentAssignments: [
    { tournamentId: "t-mad", tournamentName: "Madrid", organizerIds: ["mad"] },
    { tournamentId: "t-val", tournamentName: "Valencia", organizerIds: ["val"] },
  ],
});
const root = user("root2", { roles: ["SUPER_ADMIN"] });

test("the organizations of an account are its clubs and the organizers of its tournaments", () => {
  const account = user("u", {
    adminOrganizations: ["mad"],
    tournamentAssignments: [
      { tournamentId: "t1", tournamentName: "T1", organizerIds: ["bcn", "mad"] },
      { tournamentId: "t2", tournamentName: "T2" },
    ],
  });
  assert.deepEqual([...accountOrganizations(account)].sort(), ["bcn", "mad"]);
  assert.equal(isStaffAccount(user("p")), false);
  assert.equal(isStaffAccount(user("e", { roles: ["PLAYER", "EDITOR"] })), true);
});

test("a club admin sees players, itself, super admins and the staff that shares an organization", () => {
  assert.ok(canSeeAccount(madridAdmin, user("player")));
  assert.ok(canSeeAccount(madridAdmin, user("clubadmin", { roles: ["CLUB_ADMIN"] })));
  assert.ok(canSeeAccount(madridAdmin, root));
  assert.ok(canSeeAccount(madridAdmin, madridEditor));
  assert.ok(!canSeeAccount(madridAdmin, valenciaEditor), "an editor of another club stays hidden");
  assert.ok(canSeeAccount(superAdmin, valenciaEditor));
  // An editor sees the staff of the organizers of its tournaments.
  assert.ok(canSeeAccount(barcelonaEditor, user("ca-bcn", { roles: ["CLUB_ADMIN"], adminOrganizations: ["bcn"] })));
  assert.ok(!canSeeAccount(barcelonaEditor, madridEditor));
});

test("a club admin changes the staff of its clubs only, never itself nor a super admin; an editor changes nobody", () => {
  assert.ok(canManageStaffAccount(madridAdmin, madridEditor));
  assert.ok(canManageStaffAccount(madridAdmin, bothEditor), "an editor with one tournament of its club");
  assert.ok(canManageStaffAccount(madridAdmin, user("player")), "a player can be made an editor of its club");
  assert.ok(!canManageStaffAccount(madridAdmin, valenciaEditor));
  assert.ok(!canManageStaffAccount(madridAdmin, root));
  assert.ok(!canManageStaffAccount(madridAdmin, user("clubadmin", { roles: ["CLUB_ADMIN"], adminOrganizations: ["mad"] })));
  // Barcelona is an organization of the club admin (it edits t-bcn) but not one where it manages accounts.
  assert.ok(!canManageStaffAccount(madridAdmin, user("ca-bcn", { roles: ["CLUB_ADMIN"], adminOrganizations: ["bcn"] })));
  assert.ok(!canManageStaffAccount(barcelonaEditor, user("player")));
  assert.ok(canManageStaffAccount(superAdmin, valenciaEditor));
});

test("grants outside the clubs of the viewer keep the account from being demoted by it", () => {
  assert.equal(hasGrantsOutside(madridAdmin, madridEditor), false);
  assert.equal(hasGrantsOutside(madridAdmin, bothEditor), true);
  assert.equal(hasGrantsOutside(madridAdmin, user("ca", { roles: ["CLUB_ADMIN"], adminOrganizations: ["mad", "val"] })), true);
  assert.equal(hasGrantsOutside(superAdmin, bothEditor), false);
});

test("the view of an account hides the tournaments outside the scope and the email of players of other tournaments", () => {
  const view = viewOfAccount(madridAdmin, bothEditor);
  assert.deepEqual(view.tournamentAssignments.map((assignment) => assignment.tournamentId), ["t-mad"]);
  assert.equal(view.email, "ed-both@example.test", "the email of staff always shows");
  assert.equal(view.canManage, true);
  assert.equal(view.hasGrantsOutside, true);

  const seated = viewOfAccount(madridAdmin, user("p1", { emaId: "10990001" }));
  assert.equal(seated.email, "p1@example.test", "a player of a tournament of the viewer shows its email");
  assert.equal(seated.emailHidden, false);

  const stranger = viewOfAccount(madridAdmin, user("p2", { emaId: "10990002" }));
  assert.equal(stranger.email, "");
  assert.equal(stranger.emailHidden, true);

  const noEma = viewOfAccount(madridAdmin, user("p3"));
  assert.equal(noEma.emailHidden, true, "a player without EMA number plays in no tournament of the viewer");

  const own = viewOfAccount(barcelonaEditor, user("editor", { roles: ["EDITOR"] }));
  assert.equal(own.emailHidden, false, "the own email always shows");
  assert.equal(own.canManage, false);
});

test("a super admin sees every email and manages every account but its own", () => {
  const view = viewOfAccount(superAdmin, user("p2", { emaId: "10990002" }));
  assert.equal(view.email, "p2@example.test");
  assert.equal(view.canManage, true);
  assert.equal(viewOfAccount(superAdmin, user("root", { roles: ["SUPER_ADMIN"] })).canManage, false);
});

test("an account request is to disable or to delete, nothing else", () => {
  assert.equal(parseAccountRequestKind("DISABLE"), "DISABLE");
  assert.equal(parseAccountRequestKind("DELETE"), "DELETE");
  assert.equal(parseAccountRequestKind("delete"), null);
  assert.equal(parseAccountRequestKind(undefined), null);
  assert.equal(parseAccountRequestKind(1), null);
});
