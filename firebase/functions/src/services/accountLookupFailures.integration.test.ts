import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { describe, it, mock } from "node:test";

import { auth, db, storage } from "../firebase";
import { deleteAccountChecked } from "./accountDeletes";
import * as usersService from "./usersService";

/** Account work when Firebase Auth fails for a reason other than a missing account. */
describe("account lookups that fail", () => {
  it("passes on a failed lookup when deleting an account", async () => {
    const failing = mock.method(usersService, "getManagedUser", async () => {
      throw Object.assign(new Error("Auth is down"), { code: "auth/internal-error" });
    });
    try {
      await assert.rejects(deleteAccountChecked("actor", "someone"), /Auth is down/);
    } finally {
      failing.mock.restore();
    }
  });

  it("counts a password reset whose account cannot be read as a failed email", async () => {
    const quiet = mock.method(console, "error", () => {});
    const failing = mock.method(auth, "getUser", async () => {
      throw Object.assign(new Error("Auth is down"), { code: "auth/internal-error" });
    });
    try {
      assert.deepEqual(await usersService.sendPasswordResets(["someone"]), [{ uid: "someone", outcome: "FAILED", reason: "EMAIL_FAILED" }]);
    } finally {
      failing.mock.restore();
      quiet.mock.restore();
    }
  });
});

/** Accounts with missing data: no custom claims, no email, memberships of tournaments that are gone, an own photo. */
describe("accounts with missing data", () => {
  it("syncs and takes away the roles of an account that never had claims", async () => {
    const user = await auth.createUser({ email: `${randomUUID()}@example.test` });
    await usersService.createUserProfile(user.uid, user.email!);
    await usersService.syncRoleClaims(user.uid);
    await usersService.revokePlayerCapability((await auth.createUser({})).uid);
    await usersService.setAdminOrganizations(user.uid, []);
    assert.equal((await db.doc(`users/${user.uid}`).get()).get("adminOrganizations"), undefined);
  });

  it("leaves out the memberships of tournaments that are gone", async () => {
    const uid = `member-${randomUUID()}`;
    const gone = `gone-${randomUUID()}`;
    await db.doc(`tournaments/${gone}/members/${uid}`).set({ uid });
    assert.deepEqual(await usersService.listUserTournamentAssignments(uid, new Set([gone])), []);
    // The list of accounts reads every membership at once, and shows none of a tournament that is gone.
    for (const visible of [new Set([gone]), new Set<string>()]) {
      const users = await usersService.listManagedUsers(visible);
      assert.ok(users.every((user) => user.tournamentAssignments.every((assignment) => assignment.tournamentId !== gone)));
    }
  });

  it("deletes the own photo with the account, and lists a super admin without an email", async () => {
    const user = await auth.createUser({ email: `${randomUUID()}@example.test` });
    await usersService.createUserProfile(user.uid, user.email!);
    await storage.file(`userPhotos/${user.uid}.jpg`).save(Buffer.from("jpeg"), { contentType: "image/jpeg" });
    await db.doc(`users/${user.uid}`).update({ photoUrl: "https://example.test/photo.jpg" });
    await usersService.deleteManagedUser(user.uid);
    assert.equal((await storage.file(`userPhotos/${user.uid}.jpg`).exists())[0], false);

    const admin = await auth.createUser({});
    await usersService.setGlobalUserRole(admin.uid, "SUPER_ADMIN");
    try {
      const listed = (await usersService.listEnabledSuperAdmins()).find((entry) => entry.uid === admin.uid);
      assert.equal(listed?.email, "");
    } finally {
      await auth.deleteUser(admin.uid);
    }
  });
});
