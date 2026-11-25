// Web E2E of the registrations (docs/registrations.md): a person without an account registers from the public link of
// a tournament, confirms with the link of the email, the next one waits, and the person who cancels frees the place,
// and the organization gives it to the first on the waiting list (the waiting list moves only by hand). Each run creates its own tournament with open registrations, as the admin app does.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { startSite } from "../server.mjs";
import { API, PASSWORD, ROOT, launch, openApp, seeText, tapButton, typeIn } from "../support.mjs";

const PLACES = 4;

async function adminToken() {
  const response = await fetch(`${API}/api/auth/signIn`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ email: "admin@e2e.test", password: PASSWORD }),
  });
  assert.equal(response.status, 200, "admin sign-in");
  return (await response.json()).idToken;
}

async function call(method, path, token, body) {
  const response = await fetch(`${API}/api${path}`, {
    method,
    headers: { "content-type": "application/json", ...(token ? { authorization: `Bearer ${token}` } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  assert.ok(response.ok, `${method} ${path}: HTTP ${response.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

/** A tournament with [PLACES] places and open registrations, at the public link [slug]. */
async function createRegistrationTournament(token, slug) {
  const roundSchedules = [1, 2].map((roundId) => ({ roundId, date: "2027-02-20", startTime: `1${roundId}:00` }));
  const created = await call("POST", "/tournaments", token, {
    name: "Test - Registrations E2E", shortName: "REG E2E", organizerIds: ["seed-org-test-club"],
    eventStartDate: "2027-02-20", eventEndDate: "2027-02-21", hostCountry: "ES", hostCity: "Madrid",
    venueName: "Club", venueAddress: "Calle 1", contactEmail: "org@example.test", isTeams: false, numRounds: 2,
    roundSchedules, timeZone: "Europe/Madrid", registration: { maxPlayers: PLACES, closesOn: null, slug },
  });
  return created.id;
}

/** The path of the link of the last email to [to], to open on the local site. */
async function linkOfMailTo(to) {
  const { emails } = await (await fetch(`${API}/__e2e/mail`)).json();
  const email = emails.filter((it) => it.to === to).at(-1);
  assert.ok(email, `no email to ${to}`);
  const link = new URL(email.text.match(/https:\/\/\S+/)[0]);
  return link.pathname + link.search;
}

/** Registers [player]  from the public link, without an account, and returns the page. */
async function registerWithoutAccount(browser, siteUrl, slug, player, email) {
  const page = await openApp(browser, siteUrl, `/${slug}`);
  await tapButton(page, "Register");
  await typeIn(page, 0, player.emaId);
  await tapButton(page, new RegExp(player.emaId));
  // With the person chosen, the search leaves the form: the email is the first field.
  await typeIn(page, 0, email);
  await tapButton(page, "Register");
  await seeText(page, "We sent a link");
  return page;
}

describe("Registrations web", () => {
  let site;
  let browser;
  let token;
  let tournamentId;
  let people;
  const slug = `e2e-${randomUUID().slice(0, 8)}`;

  before(async () => {
    site = await startSite({ root: ROOT, api: API });
    browser = await launch();
    token = await adminToken();
    tournamentId = await createRegistrationTournament(token, slug);
    const { players } = await call("GET", "/public/players");
    // Other people on each run: the backend allows a few registration emails per person and hour.
    const visible = players.filter((it) => !it.hiddenFromPublic && !it.disabled);
    const start = Math.floor(Math.random() * (visible.length - PLACES - 1));
    people = visible.slice(start, start + PLACES + 1);
    assert.equal(people.length, PLACES + 1);
    await fetch(`${API}/__e2e/mail`, { method: "DELETE" });
  });
  after(async () => {
    if (tournamentId) await fetch(`${API}/api/tournaments/${tournamentId}`, { method: "DELETE", headers: { authorization: `Bearer ${token}` } });
    await browser?.close();
    await site?.close();
  });

  it("registers without an account, waits when it is full, cancels, and the place goes to the waiting list by hand", async () => {
    // 1. The first person registers and confirms with the link of the email.
    const first = people[0];
    const firstEmail = `first-${slug}@e2e.test`;
    let page = await registerWithoutAccount(browser, site.url, slug, first, firstEmail);
    await page.context().close();
    const firstLink = await linkOfMailTo(firstEmail);
    page = await openApp(browser, site.url, firstLink);
    await seeText(page, "You have place 1");
    await seeText(page, `1 of ${PLACES} places taken`);
    await page.context().close();

    // 2. The organization fills the other places.
    for (const person of people.slice(1, PLACES)) {
      await call("POST", `/tournaments/${tournamentId}/registrations`, token, { emaId: person.emaId, email: `${person.emaId}@e2e.test`, notify: false });
    }

    // 3. The next person goes to the waiting list.
    const next = people[PLACES];
    const nextEmail = `next-${slug}@e2e.test`;
    page = await registerWithoutAccount(browser, site.url, slug, next, nextEmail);
    await page.context().close();
    const nextLink = await linkOfMailTo(nextEmail);
    page = await openApp(browser, site.url, nextLink);
    await seeText(page, "waiting list");
    await page.context().close();

    // 4. The first person cancels from the link of the email. The place stays free: the waiting list moves by hand.
    page = await openApp(browser, site.url, firstLink);
    await tapButton(page, "My registration");
    await tapButton(page, "Cancel my registration");
    await tapButton(page, "Cancel registration");
    await page.context().close();
    let list = await call("GET", `/public/tournaments/${tournamentId}/registrations`);
    assert.deepEqual([list.settings.confirmed, list.settings.waiting], [PLACES - 1, 1]);
    assert.ok(!list.registrations.some((it) => it.emaId === first.emaId), "the cancelled person left the list");

    // 5. The organization gives the free place to the person who waited, who sees it with the same link.
    await call("PUT", `/tournaments/${tournamentId}/registrations/${next.emaId}`, token, { status: "CONFIRMED" });
    page = await openApp(browser, site.url, nextLink);
    await seeText(page, `You have place ${PLACES}`);
    await page.context().close();
  });
});
