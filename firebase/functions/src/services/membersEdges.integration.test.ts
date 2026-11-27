import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { before, describe, it } from "node:test";

import { auth, db } from "../firebase";
import { createAccount, requireAuthEmulator } from "../testing/authFixtures";
import { seedOrganization, seedTournament } from "../testing/firestoreFixtures";
import { listTournamentMembers, upsertTournamentMember } from "./membersService";
import { setGlobalUserRole } from "./usersService";

/** The members of a tournament when accounts have no email, are gone, are no editors or are disabled. */
describe("tournament members at the edges", () => {
  before(requireAuthEmulator);

  it("lists admins and editors without an email, and leaves out members that are gone or no editors", async () => {
    const organizationId = `org-members-${randomUUID()}`;
    await seedOrganization({ id: organizationId });
    const tournamentId = await seedTournament({ tables: [], organizerIds: [organizationId] });
    const clubAdmin = await auth.createUser({});
    await setGlobalUserRole(clubAdmin.uid, "CLUB_ADMIN", [organizationId]);
    const editor = await auth.createUser({});
    await setGlobalUserRole(editor.uid, "EDITOR");
    await upsertTournamentMember({ tournamentId, uid: editor.uid });
    const player = await createAccount({ role: "PLAYER" });
    await db.doc(`tournaments/${tournamentId}/members/${player.uid}`).set({ uid: player.uid });
    await db.doc(`tournaments/${tournamentId}/members/gone-${randomUUID()}`).set({ uid: "gone" });

    const members = await listTournamentMembers(tournamentId);
    const byUid = new Map(members.map((member) => [member.uid, member]));
    assert.deepEqual([byUid.get(clubAdmin.uid)?.email, byUid.get(clubAdmin.uid)?.role], ["", "CLUB_ADMIN"]);
    assert.deepEqual([byUid.get(editor.uid)?.email, byUid.get(editor.uid)?.role], ["", "EDITOR"]);
    assert.equal(byUid.has(player.uid), false);
    assert.equal(members.filter((member) => member.uid.startsWith("gone")).length, 0);
  });

  it("refuses to assign a disabled account", async () => {
    const tournamentId = await seedTournament({ tables: [] });
    const account = await createAccount({ role: "PLAYER", disabled: true });
    await assert.rejects(upsertTournamentMember({ tournamentId, uid: account.uid }), /A disabled account cannot be assigned/);
  });
});
