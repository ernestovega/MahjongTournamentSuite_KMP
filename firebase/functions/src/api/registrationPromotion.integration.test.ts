import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, beforeEach, describe, it } from "node:test";

import { db } from "../firebase";
import { setMailTransportForTests, type OutgoingEmail } from "../services/mailService";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";

type Settings = { status: string; maxPlayers: number | null; closesOn: string | null; slug: string; confirmed: number; waiting: number };
type PublicList = { settings: Settings; registrations: Array<{ emaId: string; status: string; position: number }> };

/**
 * A free place goes to the first of the waiting list at once: when a person with a place cancels, when an organizer
 * removes one, and when the organizer gives more places. The promoted person and the organization get an email.
 * In a file of its own: the writes of an address have a limit per minute that the other registration tests use up.
 */
describe("waiting list fills the free places", () => {
  let server: TestServer;
  let admin: TestAccount;
  const sent: OutgoingEmail[] = [];
  const tag = randomUUID().replace(/[^0-9]/g, "").padEnd(4, "5").slice(0, 3);
  let counter = 0;

  async function newEma(): Promise<string> {
    counter++;
    const emaId = `5${tag}${String(counter).padStart(4, "0")}`;
    await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Wait", lastName: `Player ${counter}`, country: "ES" });
    return emaId;
  }

  async function createTournament(maxPlayers: number | null): Promise<string> {
    const response = await server.request("POST", "/tournaments", {
      token: admin.token,
      body: {
        name: "Waiting list", shortName: "WAIT", organizerIds: ["org-es"], eventStartDate: "2027-02-20", eventEndDate: "2027-02-21",
        hostCountry: "ES", hostCity: "Madrid", venueName: "Club", venueAddress: "Calle 1", contactEmail: "org@example.test",
        isTeams: false, numRounds: 2, roundSchedules: fullRoundSchedules(2, "2027-02-20"), timeZone: "Europe/Madrid",
        registration: { maxPlayers, closesOn: null, slug: `wait-${randomUUID().slice(0, 8)}` },
      },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return (response.body as { id: string }).id;
  }

  async function adminAdd(tournamentId: string, body: Record<string, unknown> = {}): Promise<string> {
    const emaId = await newEma();
    const response = await server.request("POST", `/tournaments/${tournamentId}/registrations`, {
      token: admin.token, body: { emaId, email: `${emaId}@example.test`, notify: false, ...body },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return emaId;
  }

  /** Four people with a place and two on the waiting list. */
  async function fullTournament(): Promise<{ id: string; placed: string[]; waiting: string[] }> {
    const id = await createTournament(4);
    const placed = [await adminAdd(id), await adminAdd(id), await adminAdd(id), await adminAdd(id)];
    const waiting = [await adminAdd(id), await adminAdd(id)];
    sent.length = 0;
    return { id, placed, waiting };
  }

  const publicList = async (id: string) => (await server.request("GET", `/public/tournaments/${id}/registrations`)).body as PublicList;
  const statusOf = (list: PublicList, emaId: string) => list.registrations.find((item) => item.emaId === emaId);
  const organizationEmails = () => sent.filter((email) => email.to === "org@example.test");

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

  it("gives the place of a person who cancels to the first of the waiting list, and tells both in emails", async () => {
    const id = await createTournament(4);
    const account = await createAccount({ role: "PLAYER" });
    const leaving = await newEma();
    await giveEmaIdToAccount(account.uid, leaving);
    assert.equal((await server.request("POST", `/registrations/${id}`, { token: account.token, body: {} })).status, 200);
    for (let index = 0; index < 3; index++) await adminAdd(id);
    const first = await adminAdd(id);
    const second = await adminAdd(id);
    sent.length = 0;

    assert.equal((await server.request("DELETE", `/registrations/${id}/me`, { token: account.token })).status, 200);

    const list = await publicList(id);
    assert.equal(statusOf(list, first)?.status, "CONFIRMED");
    assert.deepEqual([statusOf(list, second)?.status, statusOf(list, second)?.position], ["WAITING", 1]);
    assert.deepEqual([list.settings.confirmed, list.settings.waiting], [4, 1]);
    const promoted = sent.filter((email) => email.to === `${first}@example.test`);
    assert.equal(promoted.length, 1);
    assert.match(promoted[0].subject, /You have a place in Waiting list/);
    // One email to the organization, with the cancellation and who took the place.
    const organization = organizationEmails();
    assert.equal(organization.length, 1);
    assert.match(organization[0].subject, /Baja en Waiting list/);
    assert.match(organization[0].text, new RegExp(`• Wait Player \\d+ \\(${first}\\)`));
    assert.match(organization[0].text, /4 con plaza y 1 en lista de espera/);
    assert.equal(sent.some((email) => email.to === `${second}@example.test`), false);
  });

  it("promotes nobody when the person who cancels was on the waiting list", async () => {
    const id = await createTournament(4);
    for (let index = 0; index < 4; index++) await adminAdd(id);
    const account = await createAccount({ role: "PLAYER" });
    const waiting = await newEma();
    await giveEmaIdToAccount(account.uid, waiting);
    await server.request("POST", `/registrations/${id}`, { token: account.token, body: {} });
    const other = await adminAdd(id);
    sent.length = 0;
    assert.equal((await server.request("DELETE", `/registrations/${id}/me`, { token: account.token })).status, 200);
    const list = await publicList(id);
    assert.deepEqual([statusOf(list, other)?.status, statusOf(list, other)?.position], ["WAITING", 1]);
    assert.doesNotMatch(organizationEmails()[0]?.text ?? "", /• /);
  });

  it("gives the place of a person the organizer removes to the first of the waiting list", async () => {
    const { id, placed, waiting } = await fullTournament();
    assert.equal((await server.request("DELETE", `/tournaments/${id}/registrations/${placed[1]}`, { token: admin.token })).status, 200);
    const list = await publicList(id);
    assert.equal(statusOf(list, waiting[0])?.status, "CONFIRMED");
    assert.equal(statusOf(list, waiting[1])?.status, "WAITING");
    assert.equal(sent.filter((email) => email.to === `${waiting[0]}@example.test`).length, 1);
    const organization = organizationEmails();
    assert.equal(organization.length, 1);
    assert.match(organization[0].subject, new RegExp(`Plaza desde la lista de espera en Waiting list: Wait Player \\d+ \\(${waiting[0]}\\)`));
    // The log feeds the weekly report.
    const log = await db.collection(`tournaments/${id}/registrationLog`).where("kind", "==", "promoted").get();
    assert.deepEqual(log.docs.map((doc) => doc.get("emaId")), [waiting[0]]);
  });

  it("fills the new places when the organizer gives more, in the order of the waiting list", async () => {
    const { id, waiting } = await fullTournament();
    const third = await adminAdd(id);
    sent.length = 0;
    const settings = (await publicList(id)).settings;
    const updated = await server.request("PUT", `/tournaments/${id}/registration-settings`, {
      token: admin.token, body: { status: settings.status, maxPlayers: 6, closesOn: null, slug: settings.slug },
    });
    assert.equal(updated.status, 200, JSON.stringify(updated.body));
    assert.deepEqual([(updated.body as Settings).confirmed, (updated.body as Settings).waiting], [6, 1]);
    const list = await publicList(id);
    assert.deepEqual([waiting[0], waiting[1], third].map((emaId) => statusOf(list, emaId)?.status), ["CONFIRMED", "CONFIRMED", "WAITING"]);
    assert.equal(organizationEmails().length, 1);
    assert.match(organizationEmails()[0].text, /• .*\n• /);
    // The same settings again change nothing and send nothing.
    sent.length = 0;
    await server.request("PUT", `/tournaments/${id}/registration-settings`, {
      token: admin.token, body: { status: settings.status, maxPlayers: 6, closesOn: null, slug: settings.slug },
    });
    assert.equal(sent.length, 0);
  });

  it("also fills the place while the registrations are closed, before the draw", async () => {
    const { id, placed, waiting } = await fullTournament();
    const settings = (await publicList(id)).settings;
    await server.request("PUT", `/tournaments/${id}/registration-settings`, {
      token: admin.token, body: { status: "CLOSED", maxPlayers: 4, closesOn: null, slug: settings.slug },
    });
    await server.request("DELETE", `/tournaments/${id}/registrations/${placed[0]}?notify=false`, { token: admin.token });
    assert.equal(statusOf(await publicList(id), waiting[0])?.status, "CONFIRMED");
  });

  it("leaves the waiting list alone in a tournament without a number of places", async () => {
    const id = await createTournament(null);
    const placed = await adminAdd(id);
    // Without a limit a person waits only because the organizer put it there.
    const waiting = await adminAdd(id, { status: "WAITING" });
    sent.length = 0;
    await server.request("DELETE", `/tournaments/${id}/registrations/${placed}`, { token: admin.token });
    assert.equal(statusOf(await publicList(id), waiting)?.status, "WAITING");
    assert.equal(organizationEmails().length, 0);
  });
});
