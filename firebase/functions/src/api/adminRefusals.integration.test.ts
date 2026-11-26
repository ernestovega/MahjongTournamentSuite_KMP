import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization } from "../testing/firestoreFixtures";

/** What the accounts routes refuse: a body that is not valid, an account of another club, the own account. */
describe("admin refusals", () => {
  let server: TestServer;
  let superAdmin: TestAccount;
  let clubAdmin: TestAccount;
  let otherClubAdmin: TestAccount;
  const clubA = `club-a-${randomUUID().slice(0, 6)}`;
  const clubB = `club-b-${randomUUID().slice(0, 6)}`;

  const asSuper = (method: string, path: string, body?: unknown) => server.request(method, path, { token: superAdmin.token, body });
  const asClub = (method: string, path: string, body?: unknown) => server.request(method, path, { token: clubAdmin.token, body });
  const newUser = (body: Record<string, unknown>) => asSuper("POST", "/admin/users", { email: `${randomUUID()}@example.test`, alias: "New", ...body });

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    await seedOrganization({ id: clubA });
    await seedOrganization({ id: clubB });
    superAdmin = await createAccount({ role: "SUPER_ADMIN" });
    clubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: [clubA] });
    otherClubAdmin = await createAccount({ role: "CLUB_ADMIN", organizations: [clubB] });
  });
  after(async () => {
    await server.close();
  });

  it("refuses a new account with a role, tournaments or organizations that are not valid", async () => {
    assert.equal((await newUser({ role: "WIZARD" })).status, 400);
    assert.equal((await newUser({ role: "EDITOR", tournamentAssignments: "t1" })).status, 400);
    assert.equal((await newUser({ role: "EDITOR", tournamentAssignments: [{ tournamentId: " " }] })).status, 400);
    assert.equal((await newUser({ role: "CLUB_ADMIN", adminOrganizations: clubA })).status, 400);
    const noClub = await newUser({ role: "CLUB_ADMIN", adminOrganizations: [] });
    assert.equal(noClub.status, 400);
    assert.match(JSON.stringify(noClub.body), /at least one organization/);
  });

  it("refuses a change to a role that does not exist", async () => {
    const response = await asSuper("PUT", `/admin/users/${clubAdmin.uid}`, { role: "WIZARD", alias: "Club" });
    assert.equal(response.status, 400);
  });

  it("does not let a club admin change the accounts of another club", async () => {
    assert.equal((await asClub("PUT", `/admin/users/${otherClubAdmin.uid}`, { role: "CLUB_ADMIN", alias: "Other" })).status, 403);
    assert.equal((await asClub("PUT", `/admin/users/${otherClubAdmin.uid}/permissions`, { levels: {} })).status, 403);
    assert.equal((await asClub("POST", `/admin/users/${otherClubAdmin.uid}/removal`, { reason: "left" })).status, 403);
  });

  it("refuses to remove the own account, a super admin, or with an unknown kind", async () => {
    assert.equal((await asClub("POST", `/admin/users/${clubAdmin.uid}/removal`, { reason: "me" })).status, 400);
    assert.equal((await asClub("POST", `/admin/users/${superAdmin.uid}/removal`, { reason: "boss" })).status, 403);
    const player = await createAccount({ role: "PLAYER" });
    const unknownKind = await asClub("POST", `/admin/users/${player.uid}/removal`, { reason: "x", kind: "VANISH" });
    assert.equal(unknownKind.status, 400);
  });

  it("needs the levels of the permissions as an object", async () => {
    const editor = await createAccount({ role: "EDITOR" });
    assert.equal((await asSuper("PUT", `/admin/users/${editor.uid}/permissions`, { levels: "all" })).status, 400);
  });

  it("needs something to look for", async () => {
    assert.equal((await asSuper("GET", "/admin/users/lookup?identifier=%20")).status, 400);
  });
});
