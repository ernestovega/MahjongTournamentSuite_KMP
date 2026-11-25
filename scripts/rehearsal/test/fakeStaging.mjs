// A staging API in memory for the tests of the rehearsal scripts: the routes that the bots and the report call, with
// the rules of the server that the corner cases of a game day try (versions, seats, penalties, the extra time, the clock),
// an organizer that starts the rounds, closes an abandoned table and plays the clock in Round control (pause, Set time,
// Stop, a table opened again), and an editor that deletes and corrects hands.
// It is not the server: it is just enough of it to run the bots against it in a few seconds.
const SEATS = ["East", "South", "West", "North"];

const emptyHand = (handId) => ({
  handId, playerWinnerId: "", playerLooserId: "", handScore: "", isChickenHand: false, isDone: false,
  playerEastPenalty: "", playerSouthPenalty: "", playerWestPenalty: "", playerNorthPenalty: "",
});

const sum = (text) => String(text ?? "").split(";").reduce((total, part) => total + (/^-?\d+$/.test(part.trim()) ? Number(part) : 0), 0);

/** The totals of the four seats, from the done hands (Green Book), and the table points 4, 2, 1 and 0 with ties shared. */
function totals(table) {
  const scores = table.manualScores != null ? table.manualScores.map(Number) : [0, 0, 0, 0];
  for (const hand of table.hands.filter((each) => each.isDone && table.manualScores == null)) {
    const points = Number(hand.handScore || 0);
    table.seats.forEach((seat, index) => {
      if (hand.playerWinnerId !== "") {
        if (hand.playerLooserId === "") scores[index] += seat === hand.playerWinnerId ? 3 * (points + 8) : -(points + 8);
        else if (seat === hand.playerWinnerId) scores[index] += points + 24;
        else scores[index] += seat === hand.playerLooserId ? -(points + 8) : -8;
      }
      scores[index] += sum(hand[`player${SEATS[index]}Penalty`]);
    });
  }
  const places = [4, 2, 1, 0];
  const tablePoints = scores.map((score) => {
    const above = scores.filter((other) => other > score).length;
    const same = scores.filter((other) => other === score).length;
    return places.slice(above, above + same).reduce((a, b) => a + b, 0) / same;
  });
  return { scores, tablePoints };
}

const PAUSE_MS = 300;
const STEP_MS = 150;
const STOP_HANDS = 5;

export class FakeStaging {
  /**
   * [draws] has the tables of each round: draws[round - 1][table - 1] is the list of four slots. [minutes] is the
   * match time of each round. [plan] gives the corner cases that the organizer and the editor play.
   */
  constructor({ metrics, draws, minutes, plan, editorDelayMs = 300 }) {
    this.metrics = metrics;
    this.plan = plan;
    this.minutes = minutes;
    this.editorDelayMs = editorDelayMs;
    this.clocks = [];
    this.tables = new Map();
    draws.forEach((tables, roundIndex) => tables.forEach((playerIds, tableIndex) => {
      const key = `${roundIndex + 1}_${tableIndex + 1}`;
      this.tables.set(key, {
        roundId: roundIndex + 1, tableId: tableIndex + 1, playerIds, seats: null, version: 0, isCompleted: false,
        hands: Array.from({ length: 16 }, (_, index) => emptyHand(index + 1)),
      });
    }));
    this.rounds = draws.length;
    this.players = [...new Set(draws.flat(2))].sort((a, b) => a - b).map((id) => ({ id, assignedEmaId: String(10000000 + id), team: 0 }));
    this.editorDone = new Set();
    this.editorDue = new Map();
    this.edits = [];
    this.clockSteps = [];
    this.startRound(1);
    // The organizer substitutes the player of a late table in Players (the corner case "substitution").
    for (const item of plan.cornerCases.filter((each) => each.kind === "substitution")) {
      const slot = Math.max(...this.tables.get(`${item.round}_${item.table}`).playerIds);
      this.players.find((player) => player.id === slot).substitution = { reason: item.reason, fromRound: item.round };
    }
  }

  /** Play: as the server, a round starts only once the round before is over, so its clock stops first. */
  startRound(roundId) {
    const previous = this.clockOf(roundId - 1);
    if (previous != null && !previous.stopped) this.stop(roundId - 1);
    this.clocks.push({ roundId, startedAtMs: Date.now(), pausedMs: 0, adjustMs: 0, pausedAtMs: null });
  }

  clockOf(roundId) {
    return this.clocks.find((item) => item.roundId === roundId) ?? null;
  }

  /** Stop, as the server: the match time becomes what was played minus the extra time, so both are over now. */
  stop(roundId) {
    const clock = this.clockOf(roundId);
    const extraMs = (this.plan.matchExtraMinutes ?? 0) * 60_000;
    if (clock.pausedAtMs != null) {
      clock.pausedMs += Date.now() - clock.pausedAtMs;
      clock.pausedAtMs = null;
    }
    const played = Math.max(0, Date.now() - clock.startedAtMs - clock.pausedMs);
    clock.startedAtMs -= Math.max(0, extraMs - played);
    clock.adjustMs = Math.max(played, extraMs) - extraMs - this.minutes[roundId - 1] * 60_000;
    clock.stopped = true;
  }

  matchEnd(roundId) {
    const clock = this.clockOf(roundId);
    if (clock == null || clock.pausedAtMs != null) return null;
    return clock.startedAtMs + clock.pausedMs + this.minutes[roundId - 1] * 60_000 + clock.adjustMs;
  }

  roundTables(roundId) {
    return [...this.tables.values()].filter((table) => table.roundId === roundId);
  }

  get allDone() {
    return [...this.tables.values()].every((table) => table.isCompleted);
  }

  tableJson(table) {
    const { scores, tablePoints } = totals({ ...table, seats: table.seats ?? ["", "", "", ""] });
    const json = { roundId: table.roundId, tableId: table.tableId, version: table.version, isCompleted: table.isCompleted, useTotalsOnly: Boolean(table.manualScores) };
    SEATS.forEach((name, index) => {
      json[`player${name}Id`] = table.seats?.[index] ?? "";
      json[`player${name}Score`] = String(scores[index]);
      json[`player${name}Points`] = String(tablePoints[index]);
    });
    return { table: json, hands: table.hands.map((hand) => ({ ...hand })) };
  }

  /** The API: { status, json, ms }, recorded in the metrics as the real client does. */
  async call(method, path, { token, body, label } = {}) {
    const [status, json] = this.route(method, path, token, body ?? {});
    this.metrics.record(label ?? `${method} ${path}`, status, 1, status >= 400 ? json?.message : null);
    this.organizer();
    return { status, json, ms: 1 };
  }

  route(method, path, token, body) {
    const [route, query] = path.split("?");
    let match;
    if (method === "POST" && route === "/auth/signIn") {
      return [200, { idToken: `token-${body.email}`, refreshToken: "refresh" }];
    }
    if (method === "GET" && route === "/public/tournaments") return [200, { tournaments: [{ id: "t1", name: this.plan.name }] }];
    if (method === "GET" && route === "/me") {
      const slot = Number(/s(\d+)@/.exec(token ?? "")?.[1]);
      return [200, { tournaments: [{ tournamentId: "t1", playerId: slot }] }];
    }
    if (method === "GET" && route === "/public/tournaments/t1") return [200, { id: "t1", isCompleted: this.allDone }];
    if (method === "GET" && route === "/public/tournaments/t1/round-clocks") {
      return [200, { roundClocks: this.clocks.map(({ stopped, ...clock }) => ({ ...clock })) }];
    }
    if (method === "GET" && route === "/public/tournaments/t1/players") return [200, { players: this.players.map((player) => ({ ...player })) }];
    if (method === "GET" && route === "/public/tournaments/t1/sync/manifest") {
      return [200, { tableVersions: Object.fromEntries([...this.tables].map(([key, table]) => [key, table.version])) }];
    }
    if (method === "GET" && route === "/public/tournaments/t1/tables") {
      const roundId = Number(new URLSearchParams(query).get("roundId"));
      return [200, { tables: this.roundTables(roundId).map((table) => ({ tableId: table.tableId, playerIds: table.playerIds })) }];
    }
    if (method === "GET" && route === "/public/tournaments/t1/tables-with-hands") {
      return [200, { tables: [...this.tables.values()].map((table) => this.tableJson(table)) }];
    }
    if ((match = /^\/public\/tournaments\/t1\/tables\/(\d+)\/(\d+)$/.exec(route)) && method === "GET") {
      const table = this.tables.get(`${match[1]}_${match[2]}`);
      return table == null ? [404, { message: "Table not found" }] : [200, this.tableJson(table)];
    }
    if ((match = /^\/player\/tournaments\/t1\/tables\/(\d+)\/(\d+)\/(seats|penalties|hands|hands\/preview)$/.exec(route)) && method === "POST") {
      const table = this.tables.get(`${match[1]}_${match[2]}`);
      return this.playerWrite(match[3], table, body);
    }
    return [404, { message: `No route ${method} ${path}` }];
  }

  playerWrite(kind, table, body) {
    if (table.isCompleted) return [409, { message: "This table is already completed" }];
    const next = table.hands.find((hand) => !hand.isDone);
    if (kind === "seats") {
      const penalized = table.hands.some((hand) => SEATS.some((name) => hand[`player${name}Penalty`] !== ""));
      if (table.hands.some((hand) => hand.isDone) || penalized) return [409, { message: "The seats cannot change after the first hand or penalty" }];
      if (body.expectedVersion !== table.version) return [409, { message: "The table changed. Check the seats and save again" }];
      table.seats = SEATS.map((name) => String(body[`player${name}Id`]));
      table.version += 1;
      return [200, { tableVersion: table.version }];
    }
    if (table.seats == null) return [409, { message: "The seats of this table are not assigned yet" }];
    if (kind !== "seats" && this.clockOf(table.roundId)?.stopped) return [409, { message: "The time of this match is over" }];
    if (kind === "hands/preview") return [200, { tableVersion: table.version }];
    if (body.expectedVersion !== table.version) return [409, { message: "The table changed after the review. Check the new totals and confirm again" }];
    if (next == null) return [409, { message: "All hands of this table are done" }];
    if (kind === "penalties") {
      // Each call adds a row of penalties, as the server does: the empty parts keep the rows of the seats in line.
      const rows = Math.max(...SEATS.map((name) => (next[`player${name}Penalty`] === "" ? 0 : next[`player${name}Penalty`].split(";").length)));
      SEATS.forEach((name) => {
        const parts = next[`player${name}Penalty`] === "" ? [] : next[`player${name}Penalty`].split(";");
        const merged = [...Array.from({ length: rows }, (_, row) => parts[row] ?? ""), body[`player${name}Penalty`] ?? ""];
        next[`player${name}Penalty`] = merged.some((cell) => cell !== "") ? merged.join(";") : "";
      });
      table.version += 1;
      return [200, { handId: next.handId, tableVersion: table.version }];
    }
    Object.assign(next, body.isDraw
      ? { playerWinnerId: "", playerLooserId: "", handScore: "0", isChickenHand: false }
      : { playerWinnerId: body.playerWinnerId, playerLooserId: body.playerLooserId ?? "", handScore: body.handScore, isChickenHand: Boolean(body.isChickenHand) });
    next.isDone = true;
    table.version += 1;
    // The sixteenth hand closes the table, and so does a hand in the extra time after the gong.
    const end = this.matchEnd(table.roundId);
    if (table.hands.every((hand) => hand.isDone) || (end != null && Date.now() > end)) table.isCompleted = true;
    return [200, { handId: next.handId, tableVersion: table.version }];
  }

  /** The organizer and the editor, after each call: they start the next round, close a table that its players left,
   * and delete or correct the hands of the plan a little after the table reaches them, as a person would. */
  organizer() {
    for (const event of this.metrics.events.filter((each) => each.kind === "table-abandoned")) {
      const table = this.tables.get(event.table);
      if (!table.isCompleted) {
        table.isCompleted = true;
        table.version += 1;
      }
    }
    // The editor types the totals of a table that played on paper (Manual scores), and completes it. The totals of a
    // table with a player of the tie are 0, so the tie stays.
    for (const event of this.metrics.events.filter((each) => each.kind === "table-left-for-totals")) {
      const table = this.tables.get(event.table);
      if (table.isCompleted) continue;
      const item = this.plan.cornerCases.find((each) => each.kind === "manual-totals" && `${each.round}_${each.table}` === event.table);
      const tie = this.plan.cornerCases.find((each) => each.kind === "tie");
      const tied = tie == null ? [] : this.tables.get(`${tie.round}_${tie.table}`).playerIds;
      table.manualScores = table.playerIds.some((id) => tied.includes(id)) ? ["0", "0", "0", "0"] : item.scores;
      table.isCompleted = true;
      table.version += 1;
    }
    for (const item of this.plan.cornerCases.filter((each) => each.kind === "delete" || each.kind === "edit")) {
      const key = `${item.round}_${item.table}`;
      const table = this.tables.get(key);
      if (this.editorDone.has(key) || table == null || table.isCompleted) continue;
      const done = table.hands.filter((hand) => hand.isDone);
      if (done.length < item.minHands) continue;
      if (!this.editorDue.has(key)) this.editorDue.set(key, Date.now() + this.editorDelayMs);
      if (Date.now() < this.editorDue.get(key)) continue;
      const newest = Math.max(...done.map((hand) => hand.handId));
      const target = table.hands.find((hand) => hand.handId === (item.which === "last" ? newest : newest - item.back));
      if (item.kind === "delete") Object.assign(target, emptyHand(target.handId));
      // A correction changes the score, or gives a draw a winner.
      else if (target.playerWinnerId === "") Object.assign(target, { playerWinnerId: table.seats[0], playerLooserId: "", handScore: "8" });
      else target.handScore = target.handScore === "88" ? "64" : "88";
      table.version += 1;
      this.editorDone.add(key);
      this.edits.push({ kind: item.kind, table: key, handId: target.handId, newest });
    }
    const current = Math.max(...this.clocks.map((clock) => clock.roundId));
    if (this.playTheClock(current)) return;
    if (current < this.rounds && this.roundTables(current).every((table) => table.isCompleted)) this.startRound(current + 1);
  }

  /**
   * The organizer in Round control, in the round of the clock cases of the plan: a pause, then Set time, then the Stop
   * when the table of the Stop has played [STOP_HANDS] hands, then a table opened again. The Play of the next round
   * closes that table first (the dialog "Close them and start"). The waits are short: a pause of [PAUSE_MS] counts as
   * the seconds of the plan. Returns true while the next round must wait.
   */
  playTheClock(roundId) {
    const cases = this.plan.cornerCases.filter((item) => item.round === roundId);
    const pause = cases.find((item) => item.kind === "pause");
    const stop = cases.find((item) => item.kind === "stop");
    if (pause == null && stop == null) return false;
    const clock = this.clockOf(roundId);
    const hands = this.roundTables(roundId).reduce((total, table) => total + table.hands.filter((hand) => hand.isDone).length, 0);
    if (pause != null && clock.pausedAtMs == null && clock.pausedMs === 0 && hands >= 4) {
      clock.pausedAtMs = Date.now();
      return true;
    }
    if (clock.pausedAtMs != null) {
      if (Date.now() - clock.pausedAtMs < PAUSE_MS) return true;
      // The pause counts as the seconds of the plan: the clock starts as much earlier, so the play time stays true.
      clock.startedAtMs -= pause.seconds * 1_000 - (Date.now() - clock.pausedAtMs);
      clock.pausedAtMs = null;
      clock.pausedMs = pause.seconds * 1_000;
      this.resumedAt = Date.now();
      return true;
    }
    const steps = cases.find((item) => item.kind === "set-time")?.steps ?? [];
    if (this.clockSteps.length < steps.length) {
      const due = Date.now() - (this.clockSteps.at(-1) ?? this.resumedAt ?? clock.startedAtMs) >= STEP_MS;
      if (due) {
        clock.adjustMs += steps[this.clockSteps.length].change * 60_000;
        this.clockSteps.push(Date.now());
      }
      return true;
    }
    if (stop == null) return false;
    // The phones see each change of the time before the next one.
    if (!clock.stopped && Date.now() - (this.clockSteps.at(-1) ?? 0) < STEP_MS) return true;
    if (!clock.stopped) {
      const table = this.tables.get(`${roundId}_${stop.table}`);
      if (!table.isCompleted && table.hands.filter((hand) => hand.isDone).length < STOP_HANDS) return true;
      this.stop(roundId);
      this.stoppedAt = Date.now();
      // The server closes the tables with hands at once.
      for (const each of this.roundTables(roundId).filter((item) => !item.isCompleted && item.hands.some((hand) => hand.isDone))) {
        each.isCompleted = true;
        each.version += 1;
      }
      const reopen = cases.find((item) => item.kind === "reopen");
      if (reopen != null) {
        const again = this.tables.get(`${roundId}_${reopen.table}`);
        again.isCompleted = false;
        again.version += 1;
      }
      return true;
    }
    if (Date.now() - this.stoppedAt < STEP_MS) return true;
    if (roundId < this.rounds && this.clockOf(roundId + 1) == null) {
      for (const each of this.roundTables(roundId).filter((item) => !item.isCompleted)) {
        each.isCompleted = true;
        each.version += 1;
      }
      this.startRound(roundId + 1);
    }
    return true;
  }
}
