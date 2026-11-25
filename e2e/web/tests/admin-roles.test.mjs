// Web E2E of the Club Admin role and of the permissions of one person (docs/user-roles-matrix.md). club@e2e.test is the Club Admin
// of the club that owns test-last-table: the admin app lists only that tournament, the server refuses the others, and
// a super admin who takes away "Create tournaments" from this person removes the button and the call.
import assert from "node:assert/strict";
import { after, afterEach, before, describe, it } from "node:test";

import { startSite } from "../server.mjs";
import { ADMIN_ROOT, API, PASSWORD, ROOT, button, launch, openAdmin, saveOpenPages, seeText, notSeeText } from "../support.mjs";

const CLUB = { uid: "e2e-club-admin", email: "club@e2e.test" };
const OWN = { id: "test-last-table", name: "Test - One table left, broken logo, chicken hands off" };
const OTHER = { id: "test-live-ongoing", name: "Test - Live ongoing tournament" };

async function token(email) {
  const response = await fetch(`${API}/api/auth/signIn`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email, password: PASSWORD }),
  });
  assert.equal(response.status, 200, `${email} sign-in`);
  return (await response.json()).idToken;
}

async function call(method, path, idToken, body) {
  const response = await fetch(`${API}/api${path}`, {
    method,
    headers: { "content-type": "application/json", authorization: `Bearer ${idToken}` },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  return { status: response.status, body: text ? JSON.parse(text) : null };
}

const tournamentList = (body) => (Array.isArray(body) ? body : body.tournaments);

describe("Club Admin and custom permissions", () => {
  let site;
  let browser;
  let club;
  let admin;

  /** Sets the permissions of the Club Admin role of club@. Null goes back to the role as it is. */
  const setClubPermissions = (permissions) =>
    call("PUT", `/admin/users/${CLUB.uid}/permissions`, admin, { levels: { CLUB_ADMIN: { permissions } } });

  before(async () => {
    site = await startSite({ root: ROOT, admin: ADMIN_ROOT, api: API });
    browser = await launch();
    club = await token(CLUB.email);
    admin = await token("admin@e2e.test");
  });
  afterEach(async (t) => saveOpenPages(t.name));
  after(async () => {
    if (admin) await setClubPermissions(null);
    await browser?.close();
    await site?.close();
  });

  it("the server gives a Club Admin only the tournaments of the club and refuses the others", async () => {
    const list = await call("GET", "/tournaments", club);
    assert.equal(list.status, 200);
    assert.deepEqual(tournamentList(list.body).map((it) => it.id), [OWN.id]);
    assert.ok(tournamentList(list.body)[0].permissions.includes("tournament.configure"), "the club configures its tournament");

    const foreign = await call("GET", `/tournaments/${OTHER.id}/members`, club);
    assert.ok([403, 404].includes(foreign.status), `members of another club: HTTP ${foreign.status}`);
    const roles = await call("POST", "/admin/roles", club, { name: "Referee", base: "EDITOR", permissions: ["tournament.view"] });
    assert.equal(roles.status, 403, "only a super admin creates roles");
  });

  it("the admin app lists only the tournament of the club", { timeout: 120_000 }, async () => {
    const page = await openAdmin(browser, site.url, CLUB.email, ["TournamentsRoute {}"]);
    await seeText(page, OWN.name);
    await notSeeText(page, OTHER.name);
    await button(page, "New tournament").waitFor();
  });

  it("without Create tournaments the button goes and the server refuses", { timeout: 120_000 }, async () => {
    const whoami = await call("GET", "/admin/whoami", club);
    const fewer = whoami.body.permissions.filter((it) => it !== "tournaments.create");
    const changed = await setClubPermissions(fewer);
    assert.equal(changed.status, 200, JSON.stringify(changed.body));

    const after = await call("GET", "/admin/whoami", await token(CLUB.email));
    assert.ok(!after.body.permissions.includes("tournaments.create"));
    const create = await call("POST", "/tournaments", club, { name: "Not allowed" });
    assert.equal(create.status, 403, "the server refuses to create");

    const page = await openAdmin(browser, site.url, CLUB.email, ["TournamentsRoute {}"]);
    await seeText(page, OWN.name);
    assert.equal(await button(page, "New tournament").count(), 0, "the New tournament button is gone");
  });
});
