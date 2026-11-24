import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, beforeEach, describe, it } from "node:test";

import { db } from "../firebase";
import { setMailTransportForTests, type OutgoingEmail } from "../services/mailService";
import { writePlayerExportXlsx } from "../services/playerImport";
import { runRegistrationsHour } from "../services/registrationJobs";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { validDrawTables } from "../testing/draw";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";

type Settings = { status: string; maxPlayers: number | null; closesOn: string | null; slug: string; confirmed: number; waiting: number };
type PublicList = {
  settings: Settings;
  isTeams: boolean;
  registrations: Array<{ emaId: string; name: string; country: string; status: string; position: number; teamId: string | null }>;
  teams: Array<{ id: string; name: string; captainEmaId: string; members: string[] }>;
};
type AdminList = Omit<PublicList, "registrations"> & {
  drawPending: boolean;
  registrations: Array<PublicList["registrations"][number] & { email: string; paid: boolean; foodRestrictions: string; sharedEmail: boolean; hasAccount: boolean; source: string }>;
};
type Own = { emaId: string; status: string; position: number; email: string; foodRestrictions: string; teamId: string | null; manageToken: string };

let emaCounter = 0;
const tag = randomUUID().slice(0, 4).replace(/[^0-9]/g, "7").padEnd(4, "1");
/** EMA numbers of these tests: 7xxxx... with 8 digits, written in the registry. */
async function newEma(firstName = "Ana", country = "ES"): Promise<string> {
  emaCounter++;
  const emaId = `7${tag.slice(0, 3)}${String(emaCounter).padStart(4, "0")}`;
  await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName, lastName: `Player ${emaCounter}`, country });
  return emaId;
}

const tokenOf = (link: string) => new URL(link.match(/https?:\/\/\S+/)?.[0] ?? link).searchParams.get("k") ?? "";
const linkIn = (email: OutgoingEmail) => email.text.match(/https?:\/\/\S+\/registration\?\S+/)?.[0] ?? "";

describe("registrations", () => {
  let server: TestServer;
  let admin: TestAccount;
  let editor: TestAccount;
  const sent: OutgoingEmail[] = [];

  async function createTournament(options: { isTeams?: boolean; maxPlayers?: number | null; closesOn?: string | null; slug?: string; numRounds?: number; token?: string } = {}): Promise<string> {
    const numRounds = options.numRounds ?? 2;
    const response = await server.request("POST", "/tournaments", {
      token: options.token ?? admin.token,
      body: {
        name: "Registration open",
        shortName: "REG",
        organizerIds: ["org-es"],
        eventStartDate: "2027-02-20",
        eventEndDate: "2027-02-21",
        hostCountry: "ES",
        hostCity: "Madrid",
        venueName: "Club",
        venueAddress: "Calle 1",
        contactEmail: "org@example.test",
        isTeams: options.isTeams ?? false,
        numRounds,
        roundSchedules: fullRoundSchedules(numRounds, "2027-02-20"),
        timeZone: "Europe/Madrid",
        registration: {
          maxPlayers: options.maxPlayers ?? null,
          closesOn: options.closesOn ?? null,
          slug: options.slug ?? `reg-${randomUUID().slice(0, 8)}`,
        },
      },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return (response.body as { id: string }).id;
  }

  async function registerSignedIn(tournamentId: string, body: Record<string, unknown> = {}): Promise<{ account: TestAccount; emaId: string; status: number; body: unknown }> {
    const account = await createAccount({ role: "PLAYER" });
    const emaId = await newEma();
    await giveEmaIdToAccount(account.uid, emaId);
    const response = await server.request("POST", `/registrations/${tournamentId}`, { token: account.token, body });
    return { account, emaId, ...response };
  }

  async function adminAdd(tournamentId: string, emaId: string, body: Record<string, unknown> = {}) {
    const response = await server.request("POST", `/tournaments/${tournamentId}/registrations`, {
      token: admin.token, body: { emaId, email: `${emaId}@example.test`, notify: false, ...body },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return response.body as AdminList["registrations"][number];
  }

  const publicList = async (tournamentId: string) => (await server.request("GET", `/public/tournaments/${tournamentId}/registrations`)).body as PublicList;
  const adminList = async (tournamentId: string) => (await server.request("GET", `/tournaments/${tournamentId}/registrations`, { token: admin.token })).body as AdminList;

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    await seedOrganization({ id: "org-es" });
    admin = await createAccount({ role: "SUPER_ADMIN" });
    setMailTransportForTests(async (email) => {
      sent.push(email);
    });
  });
  beforeEach(() => {
    sent.length = 0;
  });
  after(async () => {
    setMailTransportForTests(null);
    await server.close();
  });

  it("creates a tournament with open registrations, no players and no draw", async () => {
    const id = await createTournament({ maxPlayers: 64, closesOn: "2027-01-31", slug: `mmc-${tag}` });
    const tournament = (await server.request("GET", `/public/tournaments/${id}`)).body as { drawPending: boolean; numPlayers: number; registration: Settings };
    assert.equal(tournament.drawPending, true);
    assert.equal(tournament.numPlayers, 0);
    assert.deepEqual(tournament.registration, { status: "OPEN", maxPlayers: 64, closesOn: "2027-01-31", slug: `mmc-${tag}`, confirmed: 0, waiting: 0 });
    assert.equal((await db.collection(`tournaments/${id}/players`).get()).size, 0);
    assert.equal((await db.collection(`tournaments/${id}/tables`).get()).size, 0);
    const link = await server.request("GET", `/public/registration-links/mmc-${tag}`);
    assert.deepEqual(link.body, { tournamentId: id });
    const integrity = await server.request("GET", `/tournaments/${id}/integrity`, { token: admin.token });
    assert.equal((integrity.body as { findings: Array<{ code: string }> }).findings.some((finding) => finding.code === "info.numPlayers"), false);
  });

  it("gives a free link name from the short name when the app sends an empty one", async () => {
    // The form of a new tournament has no link name: the app sends "" and the API makes one.
    const shortName = `E${randomUUID().slice(0, 6)}`;
    const create = () => server.request("POST", "/tournaments", {
      token: admin.token,
      body: {
        name: "Empty link", shortName, organizerIds: ["org-es"], eventStartDate: "2027-03-01", eventEndDate: "2027-03-01",
        hostCountry: "ES", hostCity: "Madrid", venueName: "Club", venueAddress: "Calle 1", contactEmail: "org@example.test",
        isTeams: false, numRounds: 1, roundSchedules: fullRoundSchedules(1, "2027-03-01"), registration: { status: "OPEN", maxPlayers: 16, slug: "" },
      },
    });
    const first = await create();
    assert.equal(first.status, 200, JSON.stringify(first.body));
    const second = await create();
    assert.equal(second.status, 200, JSON.stringify(second.body));
    const third = await create();
    assert.equal(third.status, 200, JSON.stringify(third.body));
    // The shortest link first (the short name without spaces), then with the year, then with a number.
    const base = shortName.toLowerCase();
    const slugOf = async (id: string) => ((await server.request("GET", `/public/tournaments/${id}`)).body as { registration: Settings }).registration.slug;
    assert.equal(await slugOf((first.body as { id: string }).id), base);
    assert.equal(await slugOf((second.body as { id: string }).id), `${base}2027`);
    assert.equal(await slugOf((third.body as { id: string }).id), `${base}-2`);
    // The public link is the short one, at the root of the site.
    const list = await server.request("GET", `/tournaments/${(first.body as { id: string }).id}/registrations`, { token: admin.token });
    assert.match((list.body as { publicLink: string }).publicLink, new RegExp(`^https?://[^/]+/${base}$`));
  });

  it("refuses a link name that the site uses for its own pages", async () => {
    const id = await createTournament();
    for (const slug of ["admin", "api-docs", "green-book", "inscripcion"]) {
      const response = await server.request("PUT", `/tournaments/${id}/registration-settings`, { token: admin.token, body: { status: "OPEN", slug } });
      assert.equal(response.status, 409, slug);
    }
  });

  it("refuses a link name that another tournament uses", async () => {
    const slug = `same-${randomUUID().slice(0, 6)}`;
    await createTournament({ slug });
    const before = (await db.collection("tournaments").count().get()).data().count;
    const response = await server.request("POST", "/tournaments", {
      token: admin.token,
      body: {
        name: "Other", shortName: "OTH", organizerIds: ["org-es"], eventStartDate: "2027-03-01", eventEndDate: "2027-03-01",
        hostCountry: "ES", hostCity: "Madrid", venueName: "Club", venueAddress: "Calle 1", contactEmail: "org@example.test",
        isTeams: false, numRounds: 1, roundSchedules: fullRoundSchedules(1, "2027-03-01"), registration: { slug },
      },
    });
    assert.equal(response.status, 409);
    assert.equal((await db.collection("tournaments").count().get()).data().count, before);
  });

  it("confirms a signed-in player at once and lists it publicly without email", async () => {
    const id = await createTournament();
    const result = await registerSignedIn(id, { foodRestrictions: "Celiac", consent: true });
    assert.equal(result.status, 200, JSON.stringify(result.body));
    assert.equal((result.body as { status: string }).status, "CONFIRMED");
    const me = (await server.request("GET", "/me", { token: result.account.token })).body as { registrations: Array<{ tournamentId: string; status: string }> };
    assert.deepEqual(me.registrations.filter((item) => item.tournamentId === id), [{ tournamentId: id, status: "CONFIRMED" }]);
    const list = await publicList(id);
    assert.equal(list.registrations.length, 1);
    assert.equal(list.registrations[0].emaId, result.emaId);
    assert.equal(list.registrations[0].position, 1);
    assert.equal(JSON.stringify(list).includes("@"), false);
    assert.equal(JSON.stringify(list).includes("Celiac"), false);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].to, result.account.email.toLowerCase());
    const own = (await server.request("GET", `/registrations/${id}/me`, { token: result.account.token })).body as Own;
    assert.equal(own.foodRestrictions, "Celiac");
    const admins = await adminList(id);
    assert.equal(admins.registrations[0].foodRestrictions, "Celiac");
    assert.equal(admins.registrations[0].hasAccount, true);
    assert.equal(admins.settings.confirmed, 1);
  });

  it("needs the consent for food restrictions", async () => {
    const id = await createTournament();
    const result = await registerSignedIn(id, { foodRestrictions: "Nuts" });
    assert.equal(result.status, 400);
    assert.equal((result.body as { details: { code: string } }).details.code, "CONSENT_REQUIRED");
  });

  it("registers without an account after the link of the email, and refuses a second registration", async () => {
    const id = await createTournament();
    const emaId = await newEma();
    const response = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "Guest@Example.test", foodRestrictions: "Vegan", consent: true, language: "es" } });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    assert.equal((response.body as { status: string }).status, "PENDING");
    assert.equal((await publicList(id)).registrations.length, 0);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].to, "guest@example.test");
    assert.match(sent[0].subject, /Confirma tu inscripción/);
    const token = tokenOf(linkIn(sent[0]));
    const confirmed = await server.request("POST", `/registrations/${id}/confirm`, { body: { token } });
    assert.equal(confirmed.status, 200, JSON.stringify(confirmed.body));
    assert.equal((confirmed.body as Own).status, "CONFIRMED");
    assert.equal((confirmed.body as Own).foodRestrictions, "Vegan");
    assert.equal((await publicList(id)).registrations.length, 1);
    // Opening the link again changes nothing.
    assert.equal(((await server.request("POST", `/registrations/${id}/confirm`, { body: { token } })).body as Own).status, "CONFIRMED");
    const again = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "other@example.test" } });
    assert.equal(again.status, 409);
    assert.equal((again.body as { details: { code: string } }).details.code, "ALREADY_REGISTERED");
  });

  it("sends the link to the account of the EMA number, not to a typed email", async () => {
    const id = await createTournament();
    const account = await createAccount({ role: "PLAYER" });
    const emaId = await newEma();
    await giveEmaIdToAccount(account.uid, emaId);
    const response = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "intruder@example.test" } });
    assert.equal(response.status, 200);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].to, account.email.toLowerCase());
  });

  it("refuses an unknown EMA number and a closed registration", async () => {
    const id = await createTournament();
    const unknown = await server.request("POST", `/registrations/${id}`, { body: { emaId: "99999998", email: "a@example.test" } });
    assert.equal(unknown.status, 404);
    await server.request("PUT", `/tournaments/${id}/registration-settings`, {
      token: admin.token, body: { status: "CLOSED", maxPlayers: null, closesOn: null, slug: `closed-${randomUUID().slice(0, 6)}` },
    });
    const closed = await registerSignedIn(id);
    assert.equal(closed.status, 409);
    assert.equal((closed.body as { details: { code: string } }).details.code, "REGISTRATION_CLOSED");
  });

  it("puts people on the waiting list when the places are full, and the organizer promotes them", async () => {
    const id = await createTournament({ maxPlayers: 4 });
    for (let index = 0; index < 4; index++) await adminAdd(id, await newEma());
    const fifth = await registerSignedIn(id);
    assert.equal((fifth.body as { status: string; position: number }).status, "WAITING");
    assert.equal((fifth.body as { position: number }).position, 1);
    // The account finds the tournament among its own, on the waiting list.
    const me = (await server.request("GET", "/me", { token: fifth.account.token })).body as { registrations: Array<{ tournamentId: string; status: string }> };
    assert.deepEqual(me.registrations.filter((item) => item.tournamentId === id), [{ tournamentId: id, status: "WAITING" }]);
    assert.match(sent[0].subject, /waiting list/);
    let list = await publicList(id);
    assert.equal(list.settings.confirmed, 4);
    assert.equal(list.settings.waiting, 1);
    sent.length = 0;
    const promoted = await server.request("PUT", `/tournaments/${id}/registrations/${fifth.emaId}`, { token: admin.token, body: { status: "CONFIRMED" } });
    assert.equal(promoted.status, 200);
    assert.equal(sent.length, 1);
    assert.match(sent[0].subject, /You have a place/);
    list = await publicList(id);
    assert.equal(list.settings.confirmed, 5);
    assert.equal(list.registrations.find((item) => item.emaId === fifth.emaId)?.position, 5);
  });

  it("lets a person cancel and tells the organization", async () => {
    const id = await createTournament();
    const first = await registerSignedIn(id);
    await registerSignedIn(id);
    sent.length = 0;
    const cancel = await server.request("DELETE", `/registrations/${id}/me`, { token: first.account.token });
    assert.equal(cancel.status, 200);
    const me = (await server.request("GET", "/me", { token: first.account.token })).body as { registrations: Array<{ tournamentId: string }> };
    assert.equal(me.registrations.some((item) => item.tournamentId === id), false);
    assert.deepEqual(sent.map((email) => email.to).sort(), [first.account.email.toLowerCase(), "org@example.test"].sort());
    const organization = sent.find((email) => email.to === "org@example.test");
    assert.match(organization?.subject ?? "", /Baja en Registration open/);
    assert.match(organization?.text ?? "", /1 con plaza/);
    assert.equal((await publicList(id)).registrations.length, 1);
    // Without a token or an account nobody cancels anything.
    assert.equal((await server.request("DELETE", `/registrations/${id}/me`)).status, 403);
  });

  it("lets the person with the link of the email cancel without an account", async () => {
    const id = await createTournament();
    const emaId = await newEma();
    await adminAdd(id, emaId, { notify: true });
    const token = tokenOf(linkIn(sent[0]));
    const cancel = await server.request("DELETE", `/registrations/${id}/me`, { headers: { "x-registration-token": token } });
    assert.equal(cancel.status, 200);
    assert.equal((await publicList(id)).registrations.length, 0);
    assert.equal((await server.request("GET", `/registrations/${id}/me`, { headers: { "x-registration-token": "wrong" } })).status, 404);
  });

  it("lets the organizer edit, mark as paid and remove, and tells the person", async () => {
    const id = await createTournament();
    const emaId = await newEma();
    await adminAdd(id, emaId);
    const edited = await server.request("PUT", `/tournaments/${id}/registrations/${emaId}`, {
      token: admin.token, body: { paid: true, email: "New@Example.test", foodRestrictions: "No pork" },
    });
    assert.equal(edited.status, 200);
    const row = (await adminList(id)).registrations[0];
    assert.equal(row.paid, true);
    assert.equal(row.email, "new@example.test");
    assert.equal(row.foodRestrictions, "No pork");
    assert.equal(JSON.stringify(await publicList(id)).includes("paid"), false);
    sent.length = 0;
    assert.equal((await server.request("DELETE", `/tournaments/${id}/registrations/${emaId}`, { token: admin.token })).status, 200);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].to, "new@example.test");
    assert.match(sent[0].text, /org@example.test/);
  });

  it("flags shared emails for the organizer", async () => {
    const id = await createTournament();
    await adminAdd(id, await newEma(), { email: "family@example.test" });
    await adminAdd(id, await newEma(), { email: "family@example.test" });
    assert.deepEqual((await adminList(id)).registrations.map((item) => item.sharedEmail), [true, true]);
  });

  it("lets a person register with the email of another registration, and tells that one email has one account", async () => {
    const id = await createTournament();
    await adminAdd(id, await newEma(), { email: "family@example.test" });
    sent.length = 0;
    const response = await server.request("POST", `/registrations/${id}`, { body: { emaId: await newEma(), email: "family@example.test", language: "en" } });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    assert.equal(sent.length, 1);
    assert.match(sent[0].text, /one email can have only one account/);
    const confirmed = await server.request("POST", `/registrations/${id}/confirm`, { body: { token: tokenOf(linkIn(sent[0])) } });
    assert.equal((confirmed.body as Own & { sharedEmail: boolean }).sharedEmail, true);
    // An email that nobody else uses has no note.
    sent.length = 0;
    await server.request("POST", `/registrations/${id}`, { body: { emaId: await newEma(), email: "alone@example.test", language: "en" } });
    assert.doesNotMatch(sent[0].text, /only one account/);
  });

  it("lets editors of the tournament manage the list, but not other editors or players", async () => {
    const id = await createTournament();
    editor = await createAccount({ role: "EDITOR", memberOf: [id] });
    const outsider = await createAccount({ role: "EDITOR" });
    const player = await createAccount({ role: "PLAYER" });
    assert.equal((await server.request("GET", `/tournaments/${id}/registrations`, { token: editor.token })).status, 200);
    assert.equal((await server.request("GET", `/tournaments/${id}/registrations`, { token: outsider.token })).status, 403);
    assert.equal((await server.request("GET", `/tournaments/${id}/registrations`, { token: player.token })).status, 403);
    // Only who configures the tournament changes the settings and makes the draw.
    assert.equal((await server.request("PUT", `/tournaments/${id}/registration-settings`, { token: editor.token, body: {} })).status, 403);
  });

  describe("teams", () => {
    it("lets a captain make a team, add people, and pass the team on when leaving", async () => {
      const id = await createTournament({ isTeams: true });
      const captain = await registerSignedIn(id);
      const others = [await newEma(), await newEma(), await newEma(), await newEma()];
      for (const emaId of others) await adminAdd(id, emaId, { notify: true });
      const tokens = new Map(sent.filter((email) => email.to.endsWith("@example.test")).map((email) => [email.to.split("@")[0], tokenOf(linkIn(email))]));
      sent.length = 0;
      const made = await server.request("POST", `/registrations/${id}/me/team`, { token: captain.account.token, body: { name: "Red Dragons" } });
      assert.equal(made.status, 200, JSON.stringify(made.body));
      for (const emaId of others.slice(0, 3)) {
        const added = await server.request("POST", `/registrations/${id}/me/team/members`, { token: captain.account.token, body: { emaId } });
        assert.equal(added.status, 200, JSON.stringify(added.body));
      }
      assert.equal(sent.length, 3);
      assert.match(sent[0].subject, /Red Dragons/);
      const full = await server.request("POST", `/registrations/${id}/me/team/members`, { token: captain.account.token, body: { emaId: others[3] } });
      assert.equal((full.body as { details: { code: string } }).details.code, "TEAM_FULL");
      // Another team cannot take the same name.
      const sameName = await server.request("POST", `/registrations/${id}/me/team`, {
        headers: { "x-registration-token": tokens.get(others[3]) ?? "" }, body: { name: "red dragons" },
      });
      assert.equal((sameName.body as { details: { code: string } }).details.code, "TEAM_NAME_TAKEN");
      let teams = (await publicList(id)).teams;
      assert.equal(teams.length, 1);
      assert.deepEqual(teams[0].members.sort(), [captain.emaId, ...others.slice(0, 3)].sort());
      // A member leaves with the link of its email.
      const left = await server.request("DELETE", `/registrations/${id}/me/team`, { headers: { "x-registration-token": tokens.get(others[0]) ?? "" } });
      assert.equal(left.status, 200);
      assert.equal((left.body as Own).teamId, null);
      // The captain leaves: the next member becomes the captain.
      await server.request("DELETE", `/registrations/${id}/me/team`, { token: captain.account.token });
      teams = (await publicList(id)).teams;
      assert.equal(teams[0].captainEmaId, others[1]);
      assert.equal(teams[0].members.length, 2);
      // Only the captain adds people.
      const notCaptain = await server.request("POST", `/registrations/${id}/me/team/members`, {
        headers: { "x-registration-token": tokens.get(others[2]) ?? "" }, body: { emaId: others[3] },
      });
      assert.equal(notCaptain.status, 403);
    });

    it("lets the organizer set the team by name, and an empty name takes the person out", async () => {
      const id = await createTournament({ isTeams: true });
      const emaId = await newEma();
      await adminAdd(id, emaId, { teamName: "Blue Winds" });
      let list = await adminList(id);
      assert.equal(list.teams[0].name, "Blue Winds");
      assert.equal(list.registrations[0].teamId, list.teams[0].id);
      await server.request("PUT", `/tournaments/${id}/registrations/${emaId}`, { token: admin.token, body: { teamName: "" } });
      list = await adminList(id);
      assert.equal(list.teams.length, 0);
      assert.equal(list.registrations[0].teamId, null);
    });

    it("refuses teams in a tournament without teams", async () => {
      const id = await createTournament();
      const person = await registerSignedIn(id);
      const response = await server.request("POST", `/registrations/${id}/me/team`, { token: person.account.token, body: { name: "X" } });
      assert.equal((response.body as { details: { code: string } }).details.code, "NO_TEAMS");
    });
  });

  it("asks the organization to register someone without an EMA number", async () => {
    const id = await createTournament();
    const response = await server.request("POST", `/registrations/${id}/no-ema`, {
      body: { name: "New Player", country: "fr", email: "new@example.test", message: "First tournament!" },
    });
    assert.equal(response.status, 200);
    assert.equal(sent.length, 1);
    assert.equal(sent[0].to, "org@example.test");
    assert.equal(sent[0].replyTo, "new@example.test");
    assert.match(sent[0].text, /First tournament!/);
    assert.match(sent[0].subject, /sin número EMA/);
  });

  it("imports the answers of the old Google Form in their order, with places and waiting list", async () => {
    const id = await createTournament({ maxPlayers: 4, isTeams: true });
    const ids = [await newEma(), await newEma(), await newEma(), await newEma(), await newEma()];
    const csv = [
      "Marca temporal,Dirección de correo electrónico,First name,Last name,EMA number,Country,Team,Food restrictions",
      `16/09/2026 10:00:00,b@example.test,B,B,${ids[1]},Spain,#masti,no`,
      `15/09/2026 10:00:00,a@example.test,A,A,${ids[0]}.0,Spain,#masti,Celiac`,
      `17/09/2026 10:00:00,c@example.test,C,C,${ids[2]},Spain,TBD,`,
      `18/09/2026 10:00:00,d@example.test,D,D,${ids[3]},Spain,,`,
      `19/09/2026 10:00:00,e@example.test,E,E,${ids[4]},Spain,,`,
      "20/09/2026 10:00:00,x@example.test,X,X,99999997,Spain,,",
    ].join("\n");
    const preview = await server.request("POST", `/tournaments/${id}/registrations/import`, { token: admin.token, body: { csv } });
    assert.equal(preview.status, 200, JSON.stringify(preview.body));
    const rows = (preview.body as { rows: Array<{ emaId: string; problems: string[] }>; imported: number }).rows;
    assert.deepEqual(rows.map((row) => row.problems), [[], [], [], [], [], ["EMA_NOT_FOUND"]]);
    assert.equal((await adminList(id)).registrations.length, 0);
    assert.equal(sent.length, 0);
    const applied = await server.request("POST", `/tournaments/${id}/registrations/import`, { token: admin.token, body: { csv, apply: true } });
    // The row with problems is imported too, marked, for the organizer to fix it in the list.
    assert.equal((applied.body as { imported: number }).imported, 6);
    const list = await adminList(id);
    assert.deepEqual(list.registrations.map((item) => [item.emaId, item.status]), [
      [ids[0], "CONFIRMED"], [ids[1], "CONFIRMED"], [ids[2], "CONFIRMED"], [ids[3], "CONFIRMED"], [ids[4], "WAITING"], ["99999997", "WAITING"],
    ]);
    assert.deepEqual((list.registrations[5] as unknown as { problems: string[] }).problems, ["EMA_NOT_FOUND"]);
    assert.equal(list.registrations[0].foodRestrictions, "Celiac");
    assert.equal(list.teams.length, 1);
    assert.deepEqual(list.teams[0].members.sort(), [ids[0], ids[1]].sort());
    assert.equal(sent.length, 0);
    // The file replaces the list: the preview says how many registrations go away, the rows have no problem.
    const again = await server.request("POST", `/tournaments/${id}/registrations/import`, { token: admin.token, body: { csv } });
    assert.equal((again.body as { replaced: number }).replaced, 6);
    assert.deepEqual((again.body as { rows: Array<{ problems: string[] }> }).rows[0].problems, []);
    // The same answers as the Excel file that Google Sheets downloads.
    const excel = await writePlayerExportXlsx(csv.split("\n").map((line) => line.split(",")));
    const fromExcel = await server.request("POST", `/tournaments/${id}/registrations/import`, {
      token: admin.token, body: { fileName: "form.xlsx", dataBase64: excel.toString("base64") },
    });
    assert.equal(fromExcel.status, 200, JSON.stringify(fromExcel.body));
    const excelRows = (fromExcel.body as { rows: Array<{ emaId: string; problems: string[] }> }).rows;
    assert.equal(excelRows.length, 6);
    assert.equal(excelRows[1].emaId, ids[0]);
    assert.deepEqual(excelRows[0].problems, []);
    // Applied again with only two people, the list is those two, without teams left over and without emails.
    const shorter = [csv.split("\n")[0], csv.split("\n")[3], csv.split("\n")[4]].join("\n");
    const replacedList = await server.request("POST", `/tournaments/${id}/registrations/import`, { token: admin.token, body: { csv: shorter, apply: true } });
    assert.equal(replacedList.status, 200, JSON.stringify(replacedList.body));
    assert.deepEqual([(replacedList.body as { imported: number }).imported, (replacedList.body as { replaced: number }).replaced], [2, 6]);
    const after = await adminList(id);
    assert.deepEqual(after.registrations.map((item) => [item.emaId, item.status]), [[ids[2], "CONFIRMED"], [ids[3], "CONFIRMED"]]);
    assert.equal(after.teams.length, 0);
    assert.equal(sent.length, 0);
    // A wrong EMA number and a repeated one are imported with an id of their row; the organizer gives them the right
    // number, and the problems go away.
    const wrong = [
      csv.split("\n")[0],
      `21/09/2026 10:00:00,f@example.test,F,F,abc,Spain,,Vegan`,
      `22/09/2026 10:00:00,bad-email,G,G,${ids[2]},Spain,,`,
    ].join("\n");
    const marked = await server.request("POST", `/tournaments/${id}/registrations/import`, { token: admin.token, body: { csv: wrong, apply: true } });
    assert.equal((marked.body as { imported: number }).imported, 2);
    const withProblems = (await adminList(id)).registrations as unknown as Array<{ emaId: string; problems: string[]; fileEmaId: string | null; foodRestrictions: string }>;
    assert.deepEqual(withProblems.map((item) => [item.emaId, item.problems, item.fileEmaId]), [
      ["row-2", ["INVALID_EMA"], "abc"], [ids[2], ["INVALID_EMAIL"], null],
    ]);
    assert.equal(withProblems[0].foodRestrictions, "Vegan");
    const fixed = await server.request("PUT", `/tournaments/${id}/registrations/row-2`, { token: admin.token, body: { newEmaId: ids[0] } });
    assert.equal(fixed.status, 200, JSON.stringify(fixed.body));
    const fixedEmail = await server.request("PUT", `/tournaments/${id}/registrations/${ids[2]}`, { token: admin.token, body: { email: "g@example.test" } });
    assert.equal(fixedEmail.status, 200, JSON.stringify(fixedEmail.body));
    const clean = (await adminList(id)).registrations as unknown as Array<{ emaId: string; problems: string[]; foodRestrictions: string }>;
    assert.deepEqual(clean.map((item) => [item.emaId, item.problems]), [[ids[0], []], [ids[2], []]]);
    assert.equal(clean[0].foodRestrictions, "Vegan");
    assert.equal(sent.length, 0);
  });

  describe("hourly job", () => {
    it("closes after the last day, reminds the people without a full team once, and sends the weekly report", async () => {
      const id = await createTournament({ isTeams: true, closesOn: "2027-01-31" });
      const alone = await registerSignedIn(id);
      sent.length = 0;
      // Sunday 2027-01-24 at 21:30 in Madrid: one week before the close and the time of the weekly report.
      await runRegistrationsHour(new Date("2027-01-24T20:30:00Z"));
      const mine = sent.filter((email) => email.to === alone.account.email.toLowerCase() || email.to === "org@example.test");
      assert.equal(mine.filter((email) => /Your team for/.test(email.subject)).length, 1);
      const report = sent.find((email) => email.to === "org@example.test" && email.text.includes(`registrations=${id}`));
      assert.match(report?.subject ?? "", /Informe semanal/);
      assert.match(report?.text ?? "", /1 con plaza/);
      sent.length = 0;
      await runRegistrationsHour(new Date("2027-01-24T21:30:00Z"));
      assert.equal(sent.filter((email) => email.to === alone.account.email.toLowerCase() || email.text.includes(`registrations=${id}`)).length, 0);
      await runRegistrationsHour(new Date("2027-02-01T00:30:00Z"));
      assert.equal((await publicList(id)).settings.status, "CLOSED");
    });

    it("removes the pending registrations whose link is too old", async () => {
      const id = await createTournament();
      const emaId = await newEma();
      await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "late@example.test" } });
      const token = tokenOf(linkIn(sent[0]));
      await runRegistrationsHour(new Date(Date.now() + 49 * 3_600_000));
      const confirm = await server.request("POST", `/registrations/${id}/confirm`, { body: { token } });
      assert.equal(confirm.status, 404);
    });
  });

  describe("draw", () => {
    async function closeAndList(id: string) {
      const slug = (await publicList(id)).settings.slug;
      await server.request("PUT", `/tournaments/${id}/registration-settings`, { token: admin.token, body: { status: "CLOSED", slug } });
      return adminList(id);
    }

    it("needs closed registrations and a multiple of 4", async () => {
      const id = await createTournament();
      for (let index = 0; index < 17; index++) await adminAdd(id, await newEma());
      const open = await server.request("POST", `/tournaments/${id}/draw`, { token: admin.token, body: { players: [], tables: [], assignments: [] } });
      assert.equal((open.body as { details: { code: string } }).details.code, "REGISTRATION_OPEN");
      await closeAndList(id);
      const count = await server.request("POST", `/tournaments/${id}/draw`, { token: admin.token, body: { players: [], tables: [], assignments: [] } });
      assert.equal((count.body as { details: { code: string; confirmed: number } }).details.code, "BAD_PLAYER_COUNT");
      assert.equal((count.body as { details: { confirmed: number } }).details.confirmed, 17);
    });

    it("refuses while a person with a place has an EMA problem of the import, and goes on once it is fixed", async () => {
      const id = await createTournament();
      const people: string[] = [];
      for (let index = 0; index < 16; index++) {
        const emaId = await newEma();
        people.push(emaId);
        await adminAdd(id, emaId);
      }
      // As an import leaves a row whose EMA number is not in the registry; an email problem does not stop the draw.
      await db.doc(`tournaments/${id}/registrations/${people[3]}`).update({ problems: ["EMA_NOT_FOUND"] });
      await db.doc(`tournaments/${id}/registrations/${people[4]}`).update({ problems: ["INVALID_EMAIL"] });
      await closeAndList(id);
      const players = people.map((_, index) => ({ id: index + 1, team: 0 }));
      const body = { numTries: 1, players, tables: validDrawTables(16, 2), assignments: people.map((emaId, index) => ({ playerId: index + 1, emaId })) };
      const refused = await server.request("POST", `/tournaments/${id}/draw`, { token: admin.token, body });
      assert.equal(refused.status, 409, JSON.stringify(refused.body));
      const details = (refused.body as { details: { code: string; emaIds: string[] } }).details;
      assert.deepEqual([details.code, details.emaIds], ["IMPORT_PROBLEMS", [people[3]]]);
      assert.equal((await db.doc(`tournaments/${id}`).get()).get("drawPending"), true);
      await db.doc(`tournaments/${id}/registrations/${people[3]}`).update({ problems: [] });
      const draw = await server.request("POST", `/tournaments/${id}/draw`, { token: admin.token, body });
      assert.equal(draw.status, 200, JSON.stringify(draw.body));
    });

    it("makes the tournament from the confirmed people: slots with EMA numbers and emails, tables and hands", async () => {
      const id = await createTournament({ maxPlayers: 16 });
      const people: string[] = [];
      for (let index = 0; index < 16; index++) {
        const emaId = await newEma();
        people.push(emaId);
        await adminAdd(id, emaId);
      }
      const waiting = await newEma();
      await adminAdd(id, waiting);
      const list = await closeAndList(id);
      assert.equal(list.settings.waiting, 1);
      const players = people.map((_, index) => ({ id: index + 1, team: 0 }));
      const draw = await server.request("POST", `/tournaments/${id}/draw`, {
        token: admin.token,
        body: { numTries: 3, players, tables: validDrawTables(16, 2), assignments: people.map((emaId, index) => ({ playerId: index + 1, emaId })) },
      });
      assert.equal(draw.status, 200, JSON.stringify(draw.body));
      const tournament = draw.body as { drawPending: boolean; numPlayers: number; numTries: number };
      assert.equal(tournament.drawPending, false);
      assert.equal(tournament.numPlayers, 16);
      assert.equal(tournament.numTries, 3);
      const slots = await db.collection(`tournaments/${id}/players`).get();
      assert.equal(slots.size, 16);
      const slot1 = slots.docs.find((doc) => doc.id === "1");
      assert.equal(slot1?.get("assignedEmaId"), people[0]);
      assert.equal(slot1?.get("email"), `${people[0]}@example.test`);
      assert.equal((await db.collection(`tournaments/${id}/tables`).get()).size, 8);
      const publicPlayers = (await server.request("GET", `/public/tournaments/${id}/players`)).body as { players: Array<{ assignedEmaId: string }> };
      assert.equal(publicPlayers.players.length, 16);
      // The registrations stay for the record, and nobody changes them after the draw.
      const late = await server.request("POST", `/tournaments/${id}/registrations`, { token: admin.token, body: { emaId: await newEma() } });
      assert.equal((late.body as { details: { code: string } }).details.code, "DRAW_MADE");
      const again = await server.request("POST", `/tournaments/${id}/draw`, { token: admin.token, body: { players: [], tables: [], assignments: [] } });
      assert.equal((again.body as { details: { code: string } }).details.code, "DRAW_MADE");
    });
  });

  // The admin of this file writes a lot, and the write limit of one user counts a minute: these use another admin.
  it("never sends the link of a pending registration to a second email", async () => {
    const id = await createTournament({ token: (await createAccount({ role: "SUPER_ADMIN" })).token });
    const emaId = await newEma();
    const first = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "owner@example.test" }, headers: { "x-forwarded-for": "10.7.0.1" } });
    assert.equal(first.status, 200, JSON.stringify(first.body));
    const ownerToken = tokenOf(linkIn(sent[0]));
    // The same email again (the person did not find the first email) gets the same link.
    await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "owner@example.test" }, headers: { "x-forwarded-for": "10.7.0.1" } });
    assert.equal(tokenOf(linkIn(sent[1])), ownerToken);
    // Another email gets a new link, and the old link stops working.
    const other = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "intruder@example.test" }, headers: { "x-forwarded-for": "10.7.0.1" } });
    assert.equal(other.status, 200, JSON.stringify(other.body));
    assert.equal(sent[2].to, "intruder@example.test");
    const intruderToken = tokenOf(linkIn(sent[2]));
    assert.notEqual(intruderToken, ownerToken);
    const old = await server.request("POST", `/registrations/${id}/confirm`, { body: { token: ownerToken }, headers: { "x-forwarded-for": "10.7.0.1" } });
    assert.notEqual(old.status, 200);
  });

  it("sends at most 5 emails an hour about the same person", async () => {
    const id = await createTournament({ token: (await createAccount({ role: "SUPER_ADMIN" })).token });
    const emaId = await newEma();
    for (let attempt = 0; attempt < 5; attempt++) {
      const response = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: `try${attempt}@example.test` }, headers: { "x-forwarded-for": "10.7.0.2" } });
      assert.equal(response.status, 200, JSON.stringify(response.body));
    }
    const sixth = await server.request("POST", `/registrations/${id}`, { body: { emaId, email: "try5@example.test" }, headers: { "x-forwarded-for": "10.7.0.2" } });
    assert.equal(sixth.status, 429);
    assert.equal(sent.length, 5);
  });
});
