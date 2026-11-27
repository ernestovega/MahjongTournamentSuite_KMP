import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, beforeEach, describe, it } from "node:test";

import { db } from "../firebase";
import { setMailTransportForTests, type OutgoingEmail } from "../services/mailService";
import { createAccount, giveEmaIdToAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";

type Refusal = { details?: { code?: string } };
const codeOf = (body: unknown) => (body as Refusal).details?.code;

let emaCounter = 0;
const tag = randomUUID().slice(0, 4).replace(/[^0-9]/g, "3").padEnd(4, "1");
async function newEma(): Promise<string> {
  emaCounter++;
  const emaId = `6${tag.slice(0, 3)}${String(emaCounter).padStart(4, "0")}`;
  await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Eva", lastName: `Player ${emaCounter}`, country: "ES" });
  return emaId;
}

const tokenOf = (email: OutgoingEmail) => {
  const link = email.text.match(/https?:\/\/\S+\/registration\?\S+/)?.[0] ?? "";
  return link === "" ? "" : new URL(link).searchParams.get("k") ?? "";
};

/** Every refusal of the registrations that the screens show: a wrong email, a draw made, a team rule, a missing person. */
describe("registration refusals", () => {
  let server: TestServer;
  let admin: TestAccount;
  const sent: OutgoingEmail[] = [];
  let mailFails = false;

  async function createTournament(options: { isTeams?: boolean } = {}): Promise<string> {
    const response = await server.request("POST", "/tournaments", {
      token: admin.token,
      body: {
        name: "Refusals", shortName: "REF", organizerIds: ["org-ref"], eventStartDate: "2027-02-20", eventEndDate: "2027-02-20",
        hostCountry: "ES", hostCity: "Madrid", venueName: "Club", venueAddress: "Calle 1", contactEmail: "org@example.test",
        isTeams: options.isTeams ?? false, numRounds: 1, roundSchedules: fullRoundSchedules(1, "2027-02-20"), timeZone: "Europe/Madrid",
        registration: { maxPlayers: null, closesOn: null, slug: `ref-${randomUUID().slice(0, 8)}` },
      },
    });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return (response.body as { id: string }).id;
  }

  async function signedIn(tournamentId: string): Promise<{ account: TestAccount; emaId: string }> {
    const account = await createAccount({ role: "PLAYER" });
    const emaId = await newEma();
    await giveEmaIdToAccount(account.uid, emaId);
    const response = await server.request("POST", `/registrations/${tournamentId}`, { token: account.token, body: {} });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return { account, emaId };
  }

  /** A registration without an account, still waiting for the link of its email. */
  async function pending(tournamentId: string): Promise<{ emaId: string; token: string }> {
    const emaId = await newEma();
    sent.length = 0;
    const response = await server.request("POST", `/registrations/${tournamentId}`, { body: { emaId, email: `${emaId}@example.test` } });
    assert.equal(response.status, 200, JSON.stringify(response.body));
    return { emaId, token: tokenOf(sent[0]) };
  }

  const asAdmin = (method: string, path: string, body?: unknown) => server.request(method, path, { token: admin.token, body });

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    await seedOrganization({ id: "org-ref" });
    admin = await createAccount({ role: "SUPER_ADMIN" });
    setMailTransportForTests(async (email) => {
      if (mailFails) throw new Error("SMTP down");
      sent.push(email);
    });
  });
  beforeEach(() => {
    sent.length = 0;
    mailFails = false;
  });
  after(async () => {
    setMailTransportForTests(null);
    await server.close();
  });

  it("refuses a wrong email, and a registration once the draw is made", async () => {
    const id = await createTournament();
    const wrong = await server.request("POST", `/registrations/${id}`, { body: { emaId: await newEma(), email: "not an email" } });
    assert.equal(wrong.status, 400);
    assert.equal(codeOf(wrong.body), "INVALID_EMAIL");

    await db.doc(`tournaments/${id}`).update({ drawPending: false });
    const late = await server.request("POST", `/registrations/${id}`, { body: { emaId: await newEma(), email: "late@example.test" } });
    assert.equal(late.status, 409);
    assert.equal(codeOf(late.body), "DRAW_MADE");
  });

  it("says when the email of the link could not be sent", async () => {
    const id = await createTournament();
    mailFails = true;
    const response = await server.request("POST", `/registrations/${id}`, { body: { emaId: await newEma(), email: "guest@example.test" } });
    assert.equal(response.status, 502);
  });

  it("refuses an old link, and changes before the link is opened", async () => {
    const id = await createTournament();
    const person = await pending(id);
    const headers = { "x-registration-token": person.token };

    const change = await server.request("PUT", `/registrations/${id}/me`, { headers, body: { foodRestrictions: "" } });
    assert.equal(codeOf(change.body), "PENDING");
    assert.equal(codeOf((await server.request("GET", `/registrations/${id}/me`, { token: (await createAccount({ role: "PLAYER" })).token })).body), "REGISTRATION_NOT_FOUND");

    await db.doc(`tournaments/${id}/registrations/${person.emaId}`).update({ pendingUntil: Date.now() - 1000 });
    const old = await server.request("POST", `/registrations/${id}/confirm`, { body: { token: person.token } });
    assert.equal(old.status, 404);
    assert.equal(codeOf(old.body), "LINK_EXPIRED");
  });

  it("keeps the rules of the teams", async () => {
    const id = await createTournament({ isTeams: true });
    const captain = await signedIn(id);
    const waiting = await pending(id);
    const other = await signedIn(id);
    const team = (path = "") => `/registrations/${id}/me/team${path}`;

    assert.equal(codeOf((await server.request("PUT", team(), { token: captain.account.token, body: { name: "Wind" } })).body), "NO_TEAM");
    assert.equal(codeOf((await server.request("POST", team(), { headers: { "x-registration-token": waiting.token }, body: { name: "Wind" } })).body), "PENDING");
    assert.equal((await server.request("POST", team(), { token: captain.account.token, body: { name: "Wind" } })).status, 200);
    assert.equal(codeOf((await server.request("POST", team(), { token: captain.account.token, body: { name: "Fire" } })).body), "ALREADY_IN_TEAM");

    const add = (emaId: string) => server.request("POST", team("/members"), { token: captain.account.token, body: { emaId } });
    assert.equal(codeOf((await add(await newEma())).body), "REGISTRATION_NOT_FOUND");
    assert.equal(codeOf((await add(waiting.emaId)).body), "PENDING");
    assert.equal((await server.request("POST", team(), { token: other.account.token, body: { name: "Earth" } })).status, 200);
    assert.equal(codeOf((await add(other.emaId)).body), "ALREADY_IN_TEAM");

    const removeSelf = await server.request("DELETE", team(`/members/${captain.emaId}`), { token: captain.account.token });
    assert.equal(codeOf(removeSelf.body), "USE_LEAVE");
  });

  it("asks for a name and a contact email of the tournament to register without an EMA number", async () => {
    const id = await createTournament();
    const noName = await server.request("POST", `/registrations/${id}/no-ema`, { body: { name: " a ", email: "new@example.test" } });
    assert.equal(codeOf(noName.body), "INVALID_NAME");

    // Older tournaments were saved without a contact email.
    const noContact = await createTournament();
    await db.doc(`tournaments/${noContact}`).update({ contactEmail: "" });
    const refused = await server.request("POST", `/registrations/${noContact}/no-ema`, { body: { name: "New Player", email: "new@example.test" } });
    assert.equal(codeOf(refused.body), "NO_CONTACT");

    mailFails = true;
    const failed = await server.request("POST", `/registrations/${id}/no-ema`, { body: { name: "New Player", email: "new@example.test" } });
    assert.equal(failed.status, 502);
  });

  it("refuses the organizer changes of someone who is not registered", async () => {
    const id = await createTournament();
    const missingEma = await newEma();
    const registered = await signedIn(id);
    const other = await signedIn(id);
    const path = (emaId: string) => `/tournaments/${id}/registrations/${emaId}`;

    assert.equal(codeOf((await asAdmin("POST", `/tournaments/${id}/registrations`, { emaId: registered.emaId, email: "x@example.test", notify: false })).body), "ALREADY_REGISTERED");
    assert.equal(codeOf((await asAdmin("PUT", path(missingEma), { paid: true })).body), "REGISTRATION_NOT_FOUND");
    assert.equal((await asAdmin("PUT", path(registered.emaId), { status: "PENDING" })).status, 400);
    assert.equal(codeOf((await asAdmin("PUT", path(missingEma), { newEmaId: await newEma() })).body), "REGISTRATION_NOT_FOUND");
    assert.equal(codeOf((await asAdmin("PUT", path(registered.emaId), { newEmaId: other.emaId })).body), "ALREADY_REGISTERED");
    assert.equal(codeOf((await asAdmin("DELETE", path(missingEma))).body), "REGISTRATION_NOT_FOUND");
  });

  it("refuses an empty file of answers", async () => {
    const id = await createTournament();
    const response = await asAdmin("POST", `/tournaments/${id}/registrations/import`, { fileName: "answers.xlsx", dataBase64: "=" });
    assert.equal(response.status, 400);
    assert.equal(codeOf(response.body), "EMPTY_FILE");
  });

  it("does not find a link name that nobody uses", async () => {
    assert.equal((await server.request("GET", `/public/registration-links/nobody-${tag}`)).status, 404);
  });

  it("asks an account without an EMA number for one", async () => {
    const id = await createTournament();
    const account = await createAccount({ role: "PLAYER" });
    const response = await server.request("POST", `/registrations/${id}`, { token: account.token, body: {} });
    assert.equal(response.status, 400);
    assert.equal(codeOf(response.body), "NO_EMA_ID");
  });

  it("lets a registered person change the food restrictions, and a captain rename the team", async () => {
    const id = await createTournament({ isTeams: true });
    const person = await signedIn(id);

    const food = await server.request("PUT", `/registrations/${id}/me`, { token: person.account.token, body: { foodRestrictions: "Vegan", consent: true } });
    assert.equal(food.status, 200, JSON.stringify(food.body));

    const team = `/registrations/${id}/me/team`;
    assert.equal((await server.request("POST", team, { token: person.account.token, body: { name: "Wind" } })).status, 200);
    const renamed = await server.request("PUT", team, { token: person.account.token, body: { name: "Water" } });
    assert.equal(renamed.status, 200, JSON.stringify(renamed.body));
    const teams = await db.collection(`tournaments/${id}/registrationTeams`).get();
    assert.ok(teams.docs.some((doc) => doc.get("name") === "Water"), "the team has its new name");
  });

  it("gives the team of a captain to the new EMA number of the captain", async () => {
    const id = await createTournament({ isTeams: true });
    const captain = await signedIn(id);
    assert.equal((await server.request("POST", `/registrations/${id}/me/team`, { token: captain.account.token, body: { name: "Wind" } })).status, 200);
    const newEmaId = await newEma();

    const moved = await asAdmin("PUT", `/tournaments/${id}/registrations/${captain.emaId}`, { newEmaId });
    assert.equal(moved.status, 200, JSON.stringify(moved.body));
    const stored = await db.doc(`tournaments/${id}/registrations/${newEmaId}`).get();
    assert.ok(stored.exists);
  });

  it("refuses an import without a file", async () => {
    const id = await createTournament();
    const response = await asAdmin("POST", `/tournaments/${id}/registrations/import`, { fileName: "answers.xlsx" });
    assert.equal(response.status, 400);
    assert.equal(codeOf(response.body), "EMPTY_FILE");
  });
});
