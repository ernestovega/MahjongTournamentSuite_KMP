import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { auth, db } from "../firebase";
import {
  getManagedUser,
  getUserProfile,
  listManagedUsers,
  listUserTournamentAssignments,
  sendPasswordResets,
  setGlobalUserRole,
  syncRoleClaims,
  syncUserTournamentAssignments,
} from "./usersService";

const codeOf = (error: unknown) => (error as { details?: { code?: string } }).details?.code;

/**
 * Accounts whose profile documents miss fields, accounts without an email, and memberships in places that are not a
 * tournament: each one falls back to its default, so the lists of accounts still load.
 */
describe("accounts with malformed documents", () => {
  it("reads a profile and an account without email or alias", async () => {
    const user = await auth.createUser({});
    await db.doc(`users/${user.uid}`).set({ emaId: "07700001" });
    await db.doc("emaPlayerRegistry/07700001").set({ firstName: "Ema", photoUrl: "https://example.test/ema.png" });

    assert.deepEqual(await getUserProfile(user.uid), { uid: user.uid, email: "", alias: "", emaId: "07700001" });
    const managed = await getManagedUser(user.uid);
    assert.equal(managed.email, "");
    assert.equal(managed.photoUrl, "https://example.test/ema.png");
    const listed = (await listManagedUsers()).find((item) => item.uid === user.uid);
    assert.equal(listed?.email, "");
    assert.equal(listed?.alias, "");
    assert.equal(listed?.photoUrl, "https://example.test/ema.png");
  });

  it("leaves out memberships outside a tournament, of a missing tournament, or not visible", async () => {
    const user = await auth.createUser({ email: `members-${Date.now()}@example.test` });
    const visible = `users-malformed-visible-${Date.now()}`;
    const hidden = `users-malformed-hidden-${Date.now()}`;
    await db.doc(`tournaments/${visible}`).set({});
    await db.doc(`tournaments/${hidden}`).set({ name: "Hidden" });
    await db.doc(`tournaments/${visible}/members/${user.uid}`).set({ uid: user.uid });
    await db.doc(`tournaments/${hidden}/members/${user.uid}`).set({ uid: user.uid });
    await db.doc(`tournaments/users-malformed-gone/members/${user.uid}`).set({ uid: user.uid });
    await db.doc(`members/${user.uid}`).set({ uid: user.uid });
    try {
      const only = new Set([visible]);
      assert.deepEqual(await listUserTournamentAssignments(user.uid, only), [{ tournamentId: visible, tournamentName: "", organizerIds: [] }]);
      const listed = (await listManagedUsers(only)).find((item) => item.uid === user.uid);
      assert.deepEqual(listed?.tournamentAssignments.map((assignment) => assignment.tournamentId), [visible]);
    } finally {
      await db.doc(`members/${user.uid}`).delete();
    }
  });

  it("refuses repeated, foreign or missing tournaments for an account", async () => {
    const user = await auth.createUser({});
    const assignment = (tournamentId: string) => ({ tournamentId, tournamentName: "", organizerIds: [] });
    await assert.rejects(
      syncUserTournamentAssignments(user.uid, [assignment("a"), assignment("a")]),
      (error) => codeOf(error) === "DUPLICATE_TOURNAMENT",
    );
    await assert.rejects(syncUserTournamentAssignments(user.uid, [assignment("a")], new Set(["b"])), /only to the tournaments that you manage/);
    await assert.rejects(syncUserTournamentAssignments(user.uid, [assignment("users-malformed-none")]), /Tournament not found/);
  });

  it("refuses a club admin without organizations and keeps an account that does not exist", async () => {
    const user = await auth.createUser({});
    await assert.rejects(setGlobalUserRole(user.uid, "CLUB_ADMIN", [" "]), /at least one organization/);
    assert.equal(await syncRoleClaims("users-malformed-nobody"), "UNCHANGED");
    await assert.rejects(sendPasswordResets([]), /at least one account/);
    const [result] = await sendPasswordResets([user.uid]);
    assert.deepEqual(result, { uid: user.uid, outcome: "FAILED", reason: "NO_EMAIL" });
  });
});
