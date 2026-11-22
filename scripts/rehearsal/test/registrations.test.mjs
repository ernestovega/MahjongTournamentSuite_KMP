// The registrations of a game day (registrations.mjs): the roles of the people, the draw, and the whole process against
// a server in memory. Run with: node --test scripts/rehearsal/test/*.test.mjs
import { test } from "node:test";
import assert from "node:assert/strict";

import {
  drawAssignments, formCsv, hasEmail, makeSchedule, manageToken, PLACES, REGISTER_BUDGET, registrationRoles, runRegistrations, TOKEN_HEADER,
} from "../registrations.mjs";

/** A random generator with a seed, so a failing test fails every time. */
function seeded(seed) {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 2 ** 32;
  };
}

function slots(count, returningEvery = 3) {
  return Array.from({ length: count }, (_, index) => ({
    slot: index + 1,
    emaId: String(10990001 + index),
    name: `Name${index + 1} Surname${index + 1}`,
    email: `box+gd-${index + 1}@example.test`,
    returning: index % returningEvery === 0,
  }));
}

/** The rules of the server for a draw (tournamentDraw.ts). */
function drawProblem(players, tables, numRounds) {
  const teamOf = new Map(players.map((player) => [player.id, player.team]));
  const pairs = new Set();
  for (let round = 1; round <= numRounds; round += 1) {
    const seen = tables.filter((table) => table.roundId === round).flatMap((table) => table.playerIds);
    if (seen.length !== players.length || new Set(seen).size !== players.length) return `round ${round} does not seat everybody once`;
  }
  for (const table of tables) {
    if (table.playerIds.length !== 4) return "a table without four players";
    const teams = table.playerIds.map((id) => teamOf.get(id)).filter((team) => team > 0);
    if (new Set(teams).size !== teams.length) return "two players of a team at one table";
    const sorted = [...table.playerIds].sort((a, b) => a - b);
    for (let i = 0; i < 4; i += 1) {
      for (let j = i + 1; j < 4; j += 1) {
        const pair = `${sorted[i]}-${sorted[j]}`;
        if (pairs.has(pair)) return `${pair} meet twice`;
        pairs.add(pair);
      }
    }
  }
  return null;
}

test("the people of the plan register within the calls that one address has", () => {
  const roles = registrationRoles(slots(40, 3));
  const calls = roles.self.reduce((sum, slot) => sum + (slot.returning ? 1 : 2), 0) + 1;
  assert.ok(calls <= REGISTER_BUDGET, `${calls} calls`);
  assert.equal(roles.imported.length + roles.self.length + roles.added.length, PLACES + 2);
  // Nobody has two roles.
  const all = [...roles.imported, ...roles.self, ...roles.added, roles.pendingForever, roles.noEma].map((slot) => slot.emaId);
  assert.equal(new Set(all).size, all.length);
  // Without players of earlier game days, the organizer adds more people.
  const fresh = registrationRoles(slots(40, 1000).map((slot) => ({ ...slot, returning: false })));
  assert.ok(fresh.added.length > roles.added.length);
  assert.throws(() => registrationRoles(slots(10)), /need 20/);
});

test("the draw seats everybody once a round, nobody meets twice, and a table has four teams", () => {
  for (const seed of [1, 2, 3, 4, 5, 6, 7, 8]) {
    for (const isTeams of [false, true]) {
      const { players, tables } = makeSchedule(16, 3, isTeams, seeded(seed));
      assert.equal(drawProblem(players, tables, 3), null, `seed ${seed}, teams ${isTeams}`);
      assert.equal(tables.length, 12);
    }
  }
  const big = makeSchedule(64, 4, false, seeded(9));
  assert.equal(drawProblem(big.players, big.tables, 4), null);
});

test("a registration team takes the four slots of one team of the draw", () => {
  const teams = [
    { id: "a", members: ["1", "2", "3", "4"] },
    { id: "b", members: ["5", "6", "7", "8"] },
  ];
  const assignments = drawAssignments([], teams, true, seeded(3));
  const slotTeam = (playerId) => Math.floor((playerId - 1) / 4);
  for (const team of teams) {
    const of = new Set(assignments.filter((each) => team.members.includes(each.emaId)).map((each) => slotTeam(each.playerId)));
    assert.equal(of.size, 1);
  }
  const single = drawAssignments([{ emaId: "1" }, { emaId: "2" }], [], false, seeded(3));
  assert.deepEqual(single.map((each) => each.playerId).sort(), [1, 2]);
});

test("the token comes from the link of the registration email of that tournament", () => {
  const email = { links: ["https://mts.example.test/", "https://mts.example.test/registration?t=t1&k=abc123"] };
  assert.equal(manageToken(email, "t1"), "abc123");
  assert.equal(manageToken(email, "t2"), null);
  assert.equal(manageToken({ links: ["https://mts.example.test/inscripcion/gestionar?t=t1&k=old"] }, "t1"), "old");
  assert.equal(hasEmail({ name: "Ana" }), false);
  assert.equal(hasEmail({ rows: [{ email: "ana@example.test" }] }), true);
  assert.match(formCsv(slots(2), true).split("\n")[0], /Team/);
});

/**
 * Just enough of the server for the registrations: places and waiting list, pending people with a link, teams, the
 * import, the close and the draw. It answers as staging does, with { status, json }.
 */
class FakeServer {
  constructor({ accounts }) {
    this.accounts = accounts;
    this.registrations = new Map();
    this.teams = new Map();
    this.tournament = null;
    this.outbox = [];
    this.registerCalls = 0;
    this.slots = [];
  }

  place(registration) {
    const confirmed = [...this.registrations.values()].filter((each) => each.status === "CONFIRMED").length;
    const waiting = [...this.registrations.values()].filter((each) => each.status === "WAITING").length;
    if (confirmed < this.tournament.registration.maxPlayers) Object.assign(registration, { status: "CONFIRMED", position: confirmed + 1 });
    else Object.assign(registration, { status: "WAITING", position: waiting + 1 });
  }

  renumber() {
    for (const status of ["CONFIRMED", "WAITING"]) {
      [...this.registrations.values()].filter((each) => each.status === status).sort((a, b) => a.position - b.position)
        .forEach((each, index) => { each.position = index + 1; });
    }
  }

  own(options) {
    const token = options.headers?.[TOKEN_HEADER];
    if (token) return [...this.registrations.values()].find((each) => each.token === token);
    const emaId = this.accounts.get(options.token);
    return emaId == null ? null : this.registrations.get(emaId);
  }

  teamList() {
    return [...this.teams.values()].map((team) => ({
      ...team, members: [...this.registrations.values()].filter((each) => each.teamId === team.id).map((each) => each.emaId),
    }));
  }

  async call(method, path, options = {}) {
    const body = options.body ?? {};
    const ok = (json) => ({ status: 200, json });
    const route = `${method} ${path.replace(/^\/(tournaments|registrations|public\/tournaments)\/t1/, "/$1/:id")}`;
    if (route === "GET /admin/organizations") return ok({ organizations: [{ id: "org" }] });
    if (route === "POST /tournaments") {
      this.tournament = { id: "t1", ...body, drawPending: true };
      return ok(this.tournament);
    }
    if (path === `/public/registration-links/${this.tournament?.registration.slug}`) return ok({ tournamentId: "t1" });
    if (route === "POST /tournaments/:id/registrations/import") {
      const lines = body.csv.split("\n").slice(1);
      if (body.apply) {
        lines.forEach((line) => {
          const emaId = line.split(",")[4];
          const registration = { emaId, name: emaId, email: "x", teamId: null };
          this.place(registration);
          this.registrations.set(emaId, registration);
        });
      }
      return ok({ rows: lines.map((line, index) => ({ line: index + 2, problems: [] })), imported: body.apply ? lines.length : 0 });
    }
    if (route === "POST /registrations/:id") {
      this.registerCalls += 1;
      const emaId = this.accounts.get(options.token) ?? body.emaId;
      const registration = { emaId, name: emaId, email: body.email, teamId: null, token: `tok-${emaId}` };
      if (options.token) this.place(registration);
      else {
        Object.assign(registration, { status: "PENDING", position: 0 });
        this.outbox.push({ to: body.email, links: [`https://mts.example.test/registration?t=t1&k=${registration.token}`] });
      }
      this.registrations.set(emaId, registration);
      return ok({ status: registration.status, position: registration.position, emailHint: "x***", manageToken: options.token ? registration.token : null });
    }
    if (route === "POST /registrations/:id/confirm") {
      this.registerCalls += 1;
      const registration = [...this.registrations.values()].find((each) => each.token === body.token);
      if (registration == null) return { status: 404, json: {} };
      this.place(registration);
      return ok(registration);
    }
    if (route === "POST /registrations/:id/no-ema") {
      this.outbox.push({ to: this.tournament.contactEmail, links: [] });
      return ok({ ok: true });
    }
    if (route === "GET /public/tournaments/:id/registrations") {
      const registrations = [...this.registrations.values()].filter((each) => each.status !== "PENDING")
        .map(({ emaId, name, status, position, teamId }) => ({ emaId, name, status, position, teamId }));
      return ok({ registrations });
    }
    if (route === "DELETE /registrations/:id/me") {
      const registration = this.own(options);
      if (registration == null) return { status: 404, json: {} };
      this.registrations.delete(registration.emaId);
      this.renumber();
      this.outbox.push({ to: this.tournament.contactEmail, links: [] });
      return ok({ ok: true });
    }
    let match = /^PUT \/tournaments\/:id\/registrations\/(\d+)$/.exec(route);
    if (match) {
      const registration = this.registrations.get(match[1]);
      if (body.status === "CONFIRMED") {
        const confirmed = [...this.registrations.values()].filter((each) => each.status === "CONFIRMED").length;
        Object.assign(registration, { status: "CONFIRMED", position: confirmed + 1 });
        this.renumber();
      }
      if (body.teamName) {
        const id = `team-${body.teamName}`;
        if (!this.teams.has(id)) this.teams.set(id, { id, name: body.teamName, captainEmaId: registration.emaId });
        registration.teamId = id;
      }
      return ok(registration);
    }
    if (route === "POST /tournaments/:id/registrations") {
      const registration = { emaId: body.emaId, name: body.emaId, email: body.email, teamId: null };
      this.place(registration);
      this.registrations.set(body.emaId, registration);
      return ok(registration);
    }
    if (route === "GET /tournaments/:id/registrations") return ok({ registrations: [...this.registrations.values()], teams: this.teamList() });
    if (route === "POST /registrations/:id/me/team") {
      const registration = this.own(options);
      const id = `team-${body.name}`;
      this.teams.set(id, { id, name: body.name, captainEmaId: registration.emaId });
      registration.teamId = id;
      return ok(registration);
    }
    if (route === "POST /registrations/:id/me/team/members") {
      const captain = this.own(options);
      const member = this.registrations.get(body.emaId);
      if (member == null || member.teamId != null || member.status === "PENDING") return { status: 409, json: { details: { code: "ALREADY_IN_TEAM" } } };
      member.teamId = captain.teamId;
      return ok(captain);
    }
    if (route === "PUT /tournaments/:id/registration-settings") {
      this.tournament.registration.status = body.status;
      return ok({ ...this.tournament.registration });
    }
    if (route === "POST /tournaments/:id/draw") {
      const confirmed = [...this.registrations.values()].filter((each) => each.status === "CONFIRMED");
      const problem = this.tournament.registration.status !== "CLOSED" ? "REGISTRATION_OPEN"
        : drawProblem(body.players, body.tables, this.tournament.numRounds)
          ?? (body.assignments.length !== confirmed.length ? "BAD_ASSIGNMENTS" : null);
      if (problem) return { status: 409, json: { details: { code: problem } } };
      this.slots = body.assignments.map((each) => ({ id: each.playerId, assignedEmaId: each.emaId }));
      this.tournament.drawPending = false;
      return ok(this.tournament);
    }
    if (route === "GET /public/tournaments/:id/players") return ok({ players: this.slots });
    return { status: 404, json: { message: `No route ${route}` } };
  }
}

for (const isTeams of [false, true]) {
  test(`the whole registration of a game day ${isTeams ? "with" : "without"} teams, as the apps do it`, async () => {
    const plan = { tag: "gd1119abcd", name: "Game day small", shortName: "GDS19", isTeams, slots: slots(24, 3) };
    const accounts = new Map(plan.slots.filter((slot) => slot.returning).map((slot) => [`id-${slot.email}`, slot.emaId]));
    const server = new FakeServer({ accounts });
    const report = await runRegistrations({
      api: server,
      plan,
      adminToken: async () => "admin",
      signIn: async (email) => `id-${email}`,
      openMailbox: async () => ({
        search: async (to) => server.outbox.map((email, index) => (email.to === to ? index : -1)).filter((index) => index >= 0),
        read: async (index) => server.outbox[index],
        close: () => {},
      }),
      organizerEmail: "box+gd-org@example.test",
      pause: async () => {},
      random: seeded(isTeams ? 7 : 8),
    });
    const failed = report.steps.filter((step) => !step.ok);
    assert.deepEqual(failed, []);
    assert.ok(report.ok);
    assert.equal(server.tournament.drawPending, false);
    assert.equal(server.slots.length, PLACES);
    assert.ok(server.registerCalls <= REGISTER_BUDGET, `${server.registerCalls} register calls`);
    // The person who never opened the link is still pending, and the organization got the no-EMA message and the cancellation.
    assert.equal(server.registrations.get(registrationRoles(plan.slots).pendingForever.emaId).status, "PENDING");
    assert.equal(server.outbox.filter((email) => email.to === "box+gd-org@example.test").length, 2);
    if (isTeams) assert.ok(server.teamList().every((team) => team.members.length === 4));
  });
}

test("a refused draw fails the registrations and says why", async () => {
  const plan = { tag: "gd", name: "Game day", shortName: "GD", isTeams: false, slots: slots(24, 3) };
  const server = new FakeServer({ accounts: new Map() });
  const original = server.call.bind(server);
  server.call = async (method, path, options) => (path.endsWith("/draw") ? { status: 409, json: { details: { code: "BAD_DRAW" } } } : original(method, path, options));
  const report = await runRegistrations({
    api: server,
    plan,
    adminToken: async () => "admin",
    signIn: async () => null,
    openMailbox: async () => ({
      search: async (to) => server.outbox.map((email, index) => (email.to === to ? index : -1)).filter((index) => index >= 0),
      read: async (index) => server.outbox[index],
      close: () => {},
    }),
    organizerEmail: "org@example.test",
    pause: async () => {},
    random: seeded(1),
  });
  assert.equal(report.ok, false);
  assert.match(report.steps.find((step) => !step.ok).detail, /BAD_DRAW/);
});
