#!/usr/bin/env node
// The registrations of a game day (docs/registrations.md, docs/scenarios.md): before the tournament of the scenario,
// the bot admin opens a second tournament with registrations, and the people of the plan go through the whole process
// as they would in the apps.
//
// - The organizer creates the tournament with 16 places, imports the old Google Form and adds people by hand.
// - People register: with their account (players of earlier game days), or with an email and the link that comes in
//   it. One person never opens the link and stays pending, out of the public list. One person writes "I have no EMA
//   number". The last ones go to the waiting list.
// - A person with a place cancels: the first of the waiting list gets the place by itself (docs/registrations.md,
//   "Waiting list"), and both that person and the organization get an email.
// - With teams, four captains create their teams and add three people each.
// - The organizer closes the registrations and makes the draw, as the admin app does.
//
//   node scripts/rehearsal/registrations.mjs --plan=out/scenario/plan.json [--minutes=20] --report=out/registrations.json
//
// Environment: REHEARSAL_PASSWORD, STAGING_ADMIN_EMAIL, REHEARSAL_IMAP_USER and REHEARSAL_IMAP_PASSWORD.
// The output never prints an email.
import fs from "node:fs";
import { Api, Metrics, parseArgs, rehearsalEmail, Session, sleep, STAGING_API } from "./api.mjs";
import { Mailbox, waitForEmail } from "./mail.mjs";

export const PLACES = 16;
export const TEAM_SIZE = 4;
export const DRAW_ROUNDS = 3;
/** The registrations and confirmations that one address can send in 15 minutes (registrations.routes.ts). */
export const REGISTER_BUDGET = 19;
export const TOKEN_HEADER = "x-registration-token";

function shuffle(list, random) {
  const copy = [...list];
  for (let i = copy.length - 1; i > 0; i -= 1) {
    const j = Math.floor(random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

/**
 * Who does what. The people come from the plan, in its order: the players of earlier game days register with their
 * account (one call each), the new ones with an email (two calls: the registration and the link). The address of the
 * bots has [budget] calls, so the rest come from the import of the form and from the organizer.
 */
export function registrationRoles(slots, { places = PLACES, budget = REGISTER_BUDGET } = {}) {
  const total = places + 2;
  const people = slots.slice(0, total + 2);
  if (people.length < total + 2) throw new Error(`The plan has ${slots.length} players; the registrations need ${total + 2}.`);
  const imported = people.slice(0, 4);
  // The last two: one never opens the link of its email, and one writes that it has no EMA number (no registration).
  const pendingForever = people[total];
  const noEma = people[total + 1];
  let left = budget - 1;
  const self = [];
  const added = [];
  for (const slot of people.slice(4, total)) {
    const cost = slot.returning ? 1 : 2;
    if (cost <= left) {
      self.push(slot);
      left -= cost;
    } else {
      added.push(slot);
    }
  }
  return { imported, self, added, pendingForever, noEma };
}

/** The CSV of the old Google Form, as the organizer downloads it, with the people of [slots]. */
export function formCsv(slots, isTeams) {
  const header = ["Marca temporal", "Dirección de correo electrónico", "First name", "Last name", "EMA number", "Country", ...(isTeams ? ["Team"] : []), "Food restrictions"];
  const rows = slots.map((slot, index) => {
    const [first, ...rest] = slot.name.split(" ");
    return [`0${index + 1}/11/2026 10:00:00`, slot.email, first, rest.join(" ") || "-", slot.emaId, "Spain", ...(isTeams ? [""] : []), index === 0 ? "Vegetarian" : "no"];
  });
  return [header, ...rows].map((row) => row.map((cell) => (/[",\n]/.test(cell) ? `"${cell.replace(/"/g, '""')}"` : cell)).join(",")).join("\n");
}

/**
 * The rounds and tables of a draw, as the admin app computes them: tables of four, nobody meets a person twice, and with
 * teams the four players of a table come from four teams. Slots 1-4 are team 1, 5-8 team 2... Random tries.
 */
export function makeSchedule(numPlayers, numRounds, isTeams, random = Math.random) {
  const players = Array.from({ length: numPlayers }, (_, index) => ({ id: index + 1, team: isTeams ? Math.floor(index / TEAM_SIZE) + 1 : 0 }));
  const teamOf = (id) => players[id - 1].team;
  for (let attempt = 0; attempt < 2_000; attempt += 1) {
    const met = new Set();
    const key = (a, b) => (a < b ? `${a}-${b}` : `${b}-${a}`);
    const tables = [];
    let failed = false;
    for (let roundId = 1; roundId <= numRounds && !failed; roundId += 1) {
      let round = null;
      for (let roundTry = 0; roundTry < 200 && round == null; roundTry += 1) {
        const free = shuffle(players.map((player) => player.id), random);
        const built = [];
        while (free.length > 0) {
          const table = [free.shift()];
          for (let i = 0; i < free.length && table.length < 4; i += 1) {
            const id = free[i];
            const fits = table.every((other) => !met.has(key(id, other)) && (!isTeams || teamOf(id) !== teamOf(other)));
            if (fits) {
              table.push(id);
              free.splice(i, 1);
              i -= 1;
            }
          }
          if (table.length < 4) break;
          built.push(table);
        }
        if (built.length * 4 === numPlayers) round = built;
      }
      if (round == null) {
        failed = true;
      } else {
        round.forEach((table, index) => {
          tables.push({ roundId, tableId: index + 1, playerIds: table });
          for (let i = 0; i < 4; i += 1) for (let j = i + 1; j < 4; j += 1) met.add(key(table[i], table[j]));
        });
      }
    }
    if (!failed) return { players, tables, tries: attempt + 1 };
  }
  throw new Error(`No draw for ${numPlayers} players and ${numRounds} rounds`);
}

/** Who sits in each slot: a random order, and with teams one registration team in the four slots of one draw team. */
export function drawAssignments(confirmed, teams, isTeams, random = Math.random) {
  if (!isTeams) return shuffle(confirmed, random).map((person, index) => ({ playerId: index + 1, emaId: person.emaId }));
  return shuffle(teams, random).flatMap((team, teamIndex) => shuffle(team.members, random)
    .map((emaId, index) => ({ playerId: teamIndex * TEAM_SIZE + index + 1, emaId })));
}

/** The token of the link "/registration?t=&k=" (or the old "/inscripcion/gestionar?t=&k=") of a registration email, or null. */
export function manageToken(email, tournamentId) {
  for (const link of email.links ?? []) {
    const url = new URL(link);
    if (url.pathname !== "/registration" && url.pathname !== "/inscripcion/gestionar") continue;
    if (url.searchParams.get("t") === tournamentId) return url.searchParams.get("k");
  }
  return null;
}

function madridDate(date) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Madrid", year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

function addDays(isoDate, days) {
  const date = new Date(`${isoDate}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** The text of a public answer: it must never hold an email. */
export function hasEmail(json) {
  return /[^\s"@]+@[^\s"@]+\.[a-z]{2,}/i.test(JSON.stringify(json ?? null));
}

/** Waits until an address has [count] emails since [sinceDate], up to [waitMs]. Returns how many it has. */
async function waitForCount(mailbox, to, sinceDate, count, pause, waitMs) {
  for (let waited = 0; ; waited += 15_000) {
    const found = (await mailbox.search(to, sinceDate)).length;
    if (found >= count || waited + 15_000 > waitMs) return found;
    await pause(15_000);
  }
}

/**
 * Runs the registrations. [signIn] gives a token for an email, or null. [openMailbox] opens the mailbox of the people.
 * Returns the report: the tournament, the steps with their outcome, and the counts.
 */
export async function runRegistrations({
  api, plan, adminToken, signIn, openMailbox, organizerEmail, minutes = 20, pause = sleep, random = Math.random, now = new Date(),
  mailWaitMs = 300_000,
}) {
  const steps = [];
  const step = (name, ok, detail = "") => {
    steps.push({ name, ok, detail });
    console.log(`${ok ? "✓" : "✗"} ${name}${detail ? `: ${detail}` : ""}`);
    return ok;
  };
  const must = async (promise, name) => {
    const reply = await promise;
    if (reply.status < 200 || reply.status >= 300) {
      step(name, false, `${reply.status} ${JSON.stringify(reply.json).slice(0, 200)}`);
      throw new Error(`${name} failed`);
    }
    return reply;
  };
  const admin = async (method, path, body, label) => api.call(method, path, { token: await adminToken(), body, label: `registrations admin ${label}` });
  const person = (method, path, { token, registration, body, label }) => api.call(method, path, {
    token, body, label: `registrations person ${label}`, headers: registration ? { [TOKEN_HEADER]: registration } : undefined,
  });

  const isTeams = Boolean(plan.isTeams);
  const roles = registrationRoles(plan.slots);
  const today = madridDate(now);
  const report = { tournamentId: null, steps, counts: {} };
  try {
    // 1. The organizer creates the tournament with registrations.
    const organizations = await must(admin("GET", "/admin/organizations", undefined, "GET organizations"), "Read the organizations");
    const organization = (organizations.json.organizations ?? [])[0];
    if (organization == null) throw new Error("Staging has no organization");
    const slug = `${plan.tag}-reg`.toLowerCase().replace(/[^a-z0-9-]/g, "").slice(0, 40);
    const created = await must(admin("POST", "/tournaments", {
      name: `${plan.name} · inscripciones`,
      shortName: `R${String(plan.shortName ?? "GD").slice(0, 9)}`,
      organizerIds: [organization.id],
      eventStartDate: addDays(today, 30),
      eventEndDate: addDays(today, 30),
      hostCountry: "ES",
      hostCity: "Madrid",
      venueName: "Game day",
      venueAddress: "Calle Mayor 1, Madrid",
      contactEmail: organizerEmail,
      isTeams,
      isPublic: true,
      numRounds: DRAW_ROUNDS,
      timeZone: "Europe/Madrid",
      roundSchedules: Array.from({ length: DRAW_ROUNDS }, (_, index) => ({ roundId: index + 1, date: addDays(today, 30), startTime: `${10 + 2 * index}:00` })),
      registration: { maxPlayers: PLACES, closesOn: addDays(today, 14), slug },
    }, "POST tournament"), "Create the tournament with registrations");
    const tournamentId = created.json.id;
    report.tournamentId = tournamentId;
    step("Create the tournament with registrations", created.json.drawPending === true, `${PLACES} places, link /${slug}`);
    const link = await api.call("GET", `/public/registration-links/${slug}`, { label: "registrations public GET link" });
    step("The public link opens the tournament", link.status === 200 && link.json?.tournamentId === tournamentId);

    // 2. The organizer imports the old Google Form: first the preview, then the import.
    const csv = formCsv(roles.imported, isTeams);
    const preview = await must(admin("POST", `/tournaments/${tournamentId}/registrations/import`, { csv }, "POST import preview"), "Preview the import");
    step("Preview the import of the form", preview.json.imported === 0 && preview.json.rows.length === roles.imported.length
      && preview.json.rows.every((row) => row.problems.length === 0), `${preview.json.rows.length} rows`);
    const applied = await must(admin("POST", `/tournaments/${tournamentId}/registrations/import`, { csv, apply: true }, "POST import"), "Import the form");
    step("Import the form", applied.json.imported === roles.imported.length, `${applied.json.imported} people`);

    // 3. People register: with their account, or with an email and the link of the email.
    const access = new Map();
    const startedAt = new Date(now.getTime() - 120_000);
    const waitingForLink = [];
    // A player of an earlier game day whose password does not work registers with its email, which costs one call more.
    // Who does not fit in the calls of the address is added by the organizer.
    let budget = REGISTER_BUDGET - 1;
    const registering = [];
    for (const slot of roles.self) {
      const token = slot.returning ? await signIn(slot.email) : null;
      const cost = token ? 1 : 2;
      if (cost > budget) {
        roles.added.push(slot);
      } else {
        budget -= cost;
        registering.push({ slot, token });
      }
    }
    roles.self = registering.map((each) => each.slot);
    for (const { slot, token } of [...registering, { slot: roles.pendingForever, token: null }]) {
      const body = { emaId: slot.emaId, email: token ? "" : slot.email, foodRestrictions: slot === roles.self[0] ? "Celiac" : "", consent: slot === roles.self[0] };
      const reply = await person("POST", `/registrations/${tournamentId}`, { token, body, label: "POST register" });
      if (reply.status !== 200) {
        step(`Register ${slot.emaId}`, false, `${reply.status} ${reply.json?.details?.code ?? reply.json?.message ?? ""}`);
      } else if (token != null) {
        access.set(slot.emaId, { token, registration: reply.json.manageToken ?? null });
      } else if (slot !== roles.pendingForever) {
        waitingForLink.push(slot);
      }
      await pause(1_000);
    }
    step("People register in the app", access.size + waitingForLink.length === roles.self.length,
      `${access.size} with their account, ${waitingForLink.length} with an email`);

    // The people with an email open the link of their email, which confirms it.
    const mailbox = waitingForLink.length > 0 ? await openMailbox() : null;
    try {
      const deadline = Date.now() + minutes * 60_000;
      const pending = new Map(waitingForLink.map((slot) => [slot.email, slot]));
      while (pending.size > 0 && Date.now() < deadline) {
        for (const [email, slot] of [...pending]) {
          for (const number of (await mailbox.search(email, startedAt)).reverse()) {
            const found = manageToken(await mailbox.read(number), tournamentId);
            if (found == null) continue;
            const confirmed = await person("POST", `/registrations/${tournamentId}/confirm`, { body: { token: found }, label: "POST confirm" });
            if (confirmed.status === 200) access.set(slot.emaId, { token: null, registration: found });
            else step(`Confirm ${slot.emaId}`, false, String(confirmed.status));
            pending.delete(email);
            break;
          }
        }
        if (pending.size > 0) await pause(15_000);
      }
      step("People open the link of their email", pending.size === 0, `${waitingForLink.length - pending.size} of ${waitingForLink.length}`);
    } finally {
      mailbox?.close();
    }

    // The organizer adds the people who registered elsewhere, without an email to them.
    for (const slot of roles.added) {
      await must(admin("POST", `/tournaments/${tournamentId}/registrations`, { emaId: slot.emaId, email: slot.email, notify: false }, "POST add"), `Add ${slot.emaId}`);
    }
    step("The organizer adds people by hand", true, `${roles.added.length} people`);

    // One person has no EMA number: a message to the organization.
    const [first, ...rest] = roles.noEma.name.split(" ");
    const message = await person("POST", `/registrations/${tournamentId}/no-ema`, {
      body: { name: `${first} ${rest.join(" ")}`, country: "Spain", email: roles.noEma.email, message: "Game day" }, label: "POST no EMA",
    });
    step("\"I have no EMA number\" writes to the organization", message.status === 200);

    // 4. The public list: places, waiting list, the pending person out of it, and no email at all.
    const list = await api.call("GET", `/public/tournaments/${tournamentId}/registrations`, { label: "registrations public GET list" });
    const listed = list.json?.registrations ?? [];
    const confirmedCount = listed.filter((row) => row.status === "CONFIRMED").length;
    const waitingCount = listed.filter((row) => row.status === "WAITING").length;
    step("The public list has the places and the waiting list", list.status === 200 && confirmedCount === PLACES && waitingCount >= 1,
      `${confirmedCount} with a place, ${waitingCount} waiting`);
    step("The pending person is not in the public list", !listed.some((row) => row.emaId === roles.pendingForever.emaId));
    step("The public list has no email", !hasEmail(list.json));

    // 5. A person with a place cancels, and the first of the waiting list gets the place by itself, with an email to it
    //    and one to the organization that says who took the place.
    const firstWaiting = listed.filter((row) => row.status === "WAITING").sort((a, b) => a.position - b.position)[0];
    const promotedEmail = plan.slots.find((slot) => slot.emaId === firstWaiting?.emaId)?.email ?? "";
    const mails = await openMailbox();
    try {
      const before = promotedEmail === "" ? 0 : (await mails.search(promotedEmail, startedAt)).length;
      const leaving = listed.find((row) => row.status === "CONFIRMED" && access.has(row.emaId));
      if (leaving != null) {
        const who = access.get(leaving.emaId);
        const cancelled = await person("DELETE", `/registrations/${tournamentId}/me`, { token: who.token, registration: who.registration, label: "DELETE me" });
        step("A person cancels its registration", cancelled.status === 200);
        access.delete(leaving.emaId);
      } else {
        step("A person cancels its registration", false, "nobody with a place can sign in");
      }
      if (firstWaiting != null) {
        const after = (await api.call("GET", `/public/tournaments/${tournamentId}/registrations`, { label: "registrations public GET list" })).json?.registrations ?? [];
        const promoted = after.find((row) => row.emaId === firstWaiting.emaId);
        const placed = after.filter((row) => row.status === "CONFIRMED").length;
        step("The first of the waiting list gets the place by itself", promoted?.status === "CONFIRMED" && placed === PLACES,
          `${promoted?.status ?? "gone"}, ${placed} with a place`);
        const toOrganization = await waitForEmail(mails, organizerEmail, {
          sinceDate: startedAt, timeoutMs: mailWaitMs, matches: (email) => String(email.text ?? "").includes(`(${firstWaiting.emaId})`),
        });
        step("The organization reads who took the place", toOrganization != null);
        const toPerson = promotedEmail === "" ? 0 : await waitForCount(mails, promotedEmail, startedAt, before + 1, pause, mailWaitMs);
        step("The person who gets the place gets an email", toPerson > before, `${toPerson - before} new`);
        const paid = await admin("PUT", `/tournaments/${tournamentId}/registrations/${firstWaiting.emaId}`, { paid: true }, "PUT paid");
        step("The organizer marks the new place as paid", paid.status === 200 && paid.json.paid === true);
      }
    } finally {
      mails?.close();
    }

    // 6. Teams: four captains create their teams and add three people each.
    let adminList = (await must(admin("GET", `/tournaments/${tournamentId}/registrations`, undefined, "GET list"), "Read the list")).json;
    const confirmed = adminList.registrations.filter((row) => row.status === "CONFIRMED");
    step("16 people with a place", confirmed.length === PLACES, `${confirmed.length}`);
    if (isTeams) {
      const captains = confirmed.filter((row) => access.has(row.emaId)).slice(0, PLACES / TEAM_SIZE);
      const others = confirmed.filter((row) => !captains.includes(row));
      for (const [index, captain] of captains.entries()) {
        const who = access.get(captain.emaId);
        const name = `Equipo ${String.fromCharCode(65 + index)}`;
        const team = await person("POST", `/registrations/${tournamentId}/me/team`, { token: who.token, registration: who.registration, body: { name }, label: "POST team" });
        if (team.status !== 200) {
          step(`Captain ${captain.emaId} creates a team`, false, String(team.status));
          continue;
        }
        for (const member of others.splice(0, TEAM_SIZE - 1)) {
          const added = await person("POST", `/registrations/${tournamentId}/me/team/members`, {
            token: who.token, registration: who.registration, body: { emaId: member.emaId }, label: "POST team member",
          });
          if (added.status !== 200) step(`Add ${member.emaId} to ${name}`, false, String(added.status));
        }
      }
      if (captains.length < PLACES / TEAM_SIZE) {
        // Not enough captains with access: the organizer puts the rest in teams, as when people do not form theirs.
        for (const [index, member] of others.entries()) {
          await admin("PUT", `/tournaments/${tournamentId}/registrations/${member.emaId}`, { teamName: `Organización ${Math.floor(index / TEAM_SIZE) + 1}` }, "PUT team");
        }
      }
      adminList = (await must(admin("GET", `/tournaments/${tournamentId}/registrations`, undefined, "GET list"), "Read the list")).json;
      const full = adminList.teams.filter((team) => team.members.length === TEAM_SIZE).length;
      step("The teams are complete", full === PLACES / TEAM_SIZE, `${full} teams of ${TEAM_SIZE}`);
    }

    // 7. The organizer closes the registrations and makes the draw with the people with a place.
    const closed = await must(admin("PUT", `/tournaments/${tournamentId}/registration-settings`, {
      status: "CLOSED", maxPlayers: PLACES, closesOn: addDays(today, 14), slug,
    }, "PUT close"), "Close the registrations");
    step("Close the registrations", closed.json.status === "CLOSED");
    const people = adminList.registrations.filter((row) => row.status === "CONFIRMED");
    const schedule = makeSchedule(people.length, DRAW_ROUNDS, isTeams, random);
    const assignments = drawAssignments(people, adminList.teams.filter((team) => team.members.length === TEAM_SIZE), isTeams, random);
    const draw = await admin("POST", `/tournaments/${tournamentId}/draw`, {
      numTries: schedule.tries, players: schedule.players, tables: schedule.tables, assignments,
    }, "POST draw");
    step("Make the draw", draw.status === 200 && draw.json?.drawPending !== true,
      draw.status === 200 ? `${people.length} players, ${DRAW_ROUNDS} rounds` : `${draw.status} ${draw.json?.details?.code ?? ""}`);
    const slots = await api.call("GET", `/public/tournaments/${tournamentId}/players`, { label: "registrations public GET players" });
    // A person who asked to stay hidden shows only as its number.
    const assigned = (slots.json?.players ?? []).filter((player) => player.assignedEmaId || String(player.name ?? "").startsWith("#")).length;
    step("Every person with a place has a slot", assigned === people.length, `${assigned} of ${people.length}`);
    report.counts = { confirmed: people.length, imported: roles.imported.length, self: roles.self.length, added: roles.added.length };
  } catch (error) {
    if (!steps.some((each) => !each.ok)) step("Registrations", false, String(error.message ?? error));
  }
  report.ok = steps.every((each) => each.ok);
  return report;
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const plan = JSON.parse(fs.readFileSync(String(args.plan ?? "out/scenario/plan.json"), "utf8"));
  const password = process.env.REHEARSAL_PASSWORD ?? "";
  const baseEmail = process.env.STAGING_ADMIN_EMAIL ?? "";
  const mailboxUser = process.env.REHEARSAL_IMAP_USER ?? "";
  if (!password || !baseEmail || !mailboxUser) throw new Error("Set REHEARSAL_PASSWORD, STAGING_ADMIN_EMAIL and REHEARSAL_IMAP_USER.");
  const api = new Api(String(args.api ?? STAGING_API), new Metrics());
  const admin = new Session(api, rehearsalEmail(baseEmail, "botadmin"), password);
  if (!(await admin.signIn())) throw new Error("The bot admin could not sign in.");
  const report = await runRegistrations({
    api,
    plan,
    adminToken: () => admin.bearer(),
    signIn: async (email) => {
      const session = new Session(api, email, password);
      return (await session.signIn()) ? session.token : null;
    },
    openMailbox: () => new Mailbox().open(),
    organizerEmail: rehearsalEmail(mailboxUser, `${plan.tag}-org`),
    minutes: Number(args.minutes ?? 20),
  });
  const failed = report.steps.filter((each) => !each.ok);
  console.log(`Registrations: ${report.steps.length - failed.length} of ${report.steps.length} steps right.`);
  if (args.report) fs.writeFileSync(String(args.report), `${JSON.stringify(report, null, 2)}\n`);
  if (!report.ok) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
