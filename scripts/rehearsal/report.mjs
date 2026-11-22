#!/usr/bin/env node
// The checks of a rehearsal after the last round: it reads the tournament as a spectator, scores every table again
// with its own code, and compares that with what the server stored and with the hands that the bots saved.
//
//   node scripts/rehearsal/report.mjs --tournament="Ensayo General MTS 2026" out/bots-*.json --report=out/check.json
//
// It needs no account. See docs/rehearsal.md for the targets.
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

import { Api, Metrics, STAGING_API, parseArgs } from "./api.mjs";
import { findTournament, sameHand } from "./bots.mjs";

const SEATS = ["East", "South", "West", "North"];
const PLACE_POINTS = [4, 2, 1, 0];

const text = (value) => String(value ?? "").trim();
const sumPenalty = (value) => text(value).split(";").reduce((sum, part) => sum + (/^-?\d+$/.test(part.trim()) ? Number(part.trim()) : 0), 0);
const pointsText = (value) => text(value).replace(",", ".");

/** The change of each seat for one hand, written from the rules (Green Book): 8 points from each loser plus the hand. */
function handChange(hand, seats) {
  const winner = text(hand.playerWinnerId);
  const loser = text(hand.playerLooserId) === "-" ? "" : text(hand.playerLooserId);
  const score = Number(text(hand.handScore) || 0);
  // A hand without a result does not count, not even its penalties (a draw has the score 0).
  if (winner === "" && loser === "" && text(hand.handScore) === "") return null;
  let change = [0, 0, 0, 0];
  if (winner !== "") {
    change = seats.map((seat) => {
      if (loser === "") return seat === winner ? 3 * (score + 8) : -(score + 8);
      if (seat === winner) return score + 24;
      return seat === loser ? -(score + 8) : -8;
    });
  }
  const penalties = SEATS.map((name) => sumPenalty(hand[`player${name}Penalty`]));
  return change.map((value, index) => value + penalties[index]);
}

/** Table points: 4, 2, 1 and 0 by place, and players who tie share the points of the places they take. */
export function tablePoints(scores) {
  const order = scores.map((score, seat) => ({ score, seat })).sort((a, b) => b.score - a.score);
  const points = [0, 0, 0, 0];
  for (let start = 0; start < 4;) {
    let end = start;
    while (end + 1 < 4 && order[end + 1].score === order[start].score) end += 1;
    const shared = PLACE_POINTS.slice(start, end + 1).reduce((a, b) => a + b, 0) / (end - start + 1);
    for (let index = start; index <= end; index += 1) points[order[index].seat] = Math.round(shared * 100) / 100;
    start = end + 1;
  }
  return points;
}


const beats = (a, b) => a.points > b.points || (a.points === b.points && a.score > b.score);
const round6 = (value) => Math.round(value * 1e6) / 1e6;

/** Reads the tournament and checks it. [botReports] are the reports of bots.mjs, from every process. */
export async function checkTournament(api, tournamentId, botReports = []) {
  const reply = await api.call("GET", `/public/tournaments/${tournamentId}/tables-with-hands`, { label: "GET all tables" });
  if (reply.status !== 200) throw new Error(`Reading the tables failed: ${reply.status} ${reply.json?.message ?? ""}`);
  const tables = reply.json.tables;
  // A substitute plays for a slot from a round on: the results of the slot in those rounds do not count for it
  // (playerSubstitution.ts). A disqualified slot counts in no round.
  const players = await api.call("GET", `/public/tournaments/${tournamentId}/players`, { label: "GET players" });
  const substitutionOf = new Map((players.json?.players ?? []).filter((player) => player.substitution != null)
    .map((player) => [String(player.id), player.substitution]));
  const ignored = (slot, roundId) => {
    const substitution = substitutionOf.get(String(slot));
    return substitution != null && (substitution.reason === "DISQUALIFIED" || roundId >= Number(substitution.fromRound));
  };
  const byKey = new Map(tables.map((item) => [`${item.table.roundId}_${item.table.tableId}`, item]));
  const scoreProblems = [];
  const openTables = [];
  const ranking = new Map();
  // The chicken hands of each slot, and the best hand of each slot at each table (the winning hand with most points).
  const chickenBySlot = new Map();
  const bestBySlotTable = [];
  let doneHands = 0;

  for (const [key, { table, hands }] of byKey) {
    if (!table.isCompleted) openTables.push(key);
    const seats = SEATS.map((name) => text(table[`player${name}Id`]));
    doneHands += hands.filter((hand) => hand.isDone).length;
    if (seats.some((seat) => seat === "")) continue;
    // A table of manual totals has no hands that count: its totals are the ones that the editor typed.
    const counted = table.useTotalsOnly ? [] : hands.filter((hand) => hand.isDone || table.isCompleted);
    const changes = table.useTotalsOnly
      ? [SEATS.map((name) => Number(text(table[`manualPlayer${name}Score`]) || text(table[`player${name}Score`]) || 0))]
      : counted.map((hand) => handChange(hand, seats)).filter(Boolean);
    if (changes.length === 0) continue;
    const bestHere = new Map();
    for (const hand of counted) {
      const winner = text(hand.playerWinnerId);
      if (winner === "" || !/^\d+$/.test(text(hand.handScore)) || ignored(winner, table.roundId)) continue;
      bestHere.set(winner, Math.max(bestHere.get(winner) ?? 0, Number(text(hand.handScore))));
      if (hand.isChickenHand) chickenBySlot.set(winner, (chickenBySlot.get(winner) ?? 0) + 1);
    }
    bestHere.forEach((best, slot) => bestBySlotTable.push({ slot, best }));
    const scores = changes.reduce((totals, change) => change.map((value, index) => totals[index] + value), [0, 0, 0, 0]);
    const points = tablePoints(scores);
    SEATS.forEach((name, index) => {
      const storedScore = text(table[`player${name}Score`]);
      const storedPoints = pointsText(table[`player${name}Points`]);
      if (storedScore !== String(scores[index])) {
        scoreProblems.push(`${key} ${name}: score ${storedScore} on the server, ${scores[index]} here`);
      }
      if (Number(storedPoints) !== points[index]) {
        scoreProblems.push(`${key} ${name}: table points ${storedPoints} on the server, ${points[index]} here`);
      }
      if (ignored(seats[index], table.roundId)) return;
      const entry = ranking.get(seats[index]) ?? { playerId: seats[index], points: 0, score: 0 };
      entry.points += points[index];
      entry.score += scores[index];
      ranking.set(seats[index], entry);
    });
  }

  // Every hand that a bot saved must be on the server as it was sent, and its table must have no other hand.
  const lost = [];
  const changed = [];
  const extra = [];
  const savedByTable = new Map();
  // The hands that an editor deleted are not counted, and a hand written twice in the same slot counts as the last one.
  for (const report of botReports) {
    for (const saved of report.hands ?? []) {
      if (saved.deleted) continue;
      const list = (savedByTable.get(saved.table) ?? []).filter((item) => Number(item.handId) !== Number(saved.handId));
      list.push(saved);
      savedByTable.set(saved.table, list);
    }
  }
  for (const [key, savedHands] of savedByTable) {
    const serverHands = (byKey.get(key)?.hands ?? []).filter((hand) => hand.isDone);
    for (const saved of savedHands) {
      const server = serverHands.find((hand) => Number(hand.handId) === Number(saved.handId));
      if (server == null) lost.push(`${key}#${saved.handId}`);
      else if (!sameHand(saved.hand, server)) changed.push(`${key}#${saved.handId}`);
    }
    if (serverHands.length > savedHands.length) extra.push(`${key}: ${serverHands.length} hands on the server, ${savedHands.length} saved by the bot`);
  }
  const races = botReports.flatMap((report) => (report.events ?? []).filter((event) => event.kind === "race"));
  const raceDuplicates = races.filter((event) => event.statuses.filter((status) => status === 200).length > 1);

  const sorted = [...ranking.values()].sort((a, b) => b.points - a.points || b.score - a.score);
  const result = {
    tournamentId,
    tables: tables.length,
    openTables,
    doneHands,
    handsSavedByBots: [...savedByTable.values()].reduce((sum, list) => sum + list.length, 0),
    scoreProblems,
    lost,
    changed,
    extra,
    races: races.length,
    raceDuplicates: raceDuplicates.length,
    ranking: sorted,
    substitutions: Object.fromEntries(substitutionOf),
    chickenBySlot: Object.fromEntries(chickenBySlot),
    bestBySlotTable,
  };
  // The tables as the server has them, for the corner cases. Not in the JSON of the check: it would be too large.
  Object.defineProperty(result, "tablesByKey", { value: byKey, enumerable: false });
  return result;
}

/**
 * The places that the server gives each person (the standings of the list of tournaments: podium, team, chicken
 * hands and best hand), compared with the same places counted here from the hands. Returns the differences.
 */
export async function checkStandings(api, tournamentId, check) {
  const reply = await api.call("GET", `/public/tournaments/${tournamentId}/players`, { label: "GET players" });
  if (reply.status !== 200) throw new Error(`Reading the players failed: ${reply.status}`);
  const players = reply.json.players.filter((player) => player.assignedEmaId);
  const rows = check.ranking.map((entry) => ({ ...entry, points: round6(entry.points) }));
  const teamOf = new Map(players.map((player) => [String(player.id), player.team]));
  const teamRows = [...new Set(rows.map((row) => teamOf.get(row.playerId)).filter((team) => team > 0))].map((team) => {
    const members = rows.filter((row) => teamOf.get(row.playerId) === team);
    return { team, points: round6(members.reduce((sum, row) => sum + row.points, 0)), score: members.reduce((sum, row) => sum + row.score, 0) };
  });
  const chicken = (slot) => check.chickenBySlot[slot] ?? 0;
  const best = (slot) => check.bestBySlotTable.filter((entry) => entry.slot === slot).reduce((max, entry) => Math.max(max, entry.best), 0);
  const problems = [];
  let compared = 0;
  for (const player of players) {
    const slot = String(player.id);
    const mine = rows.find((row) => row.playerId === slot);
    if (mine == null) continue;
    const answer = await api.call("GET", `/public/statistics/persons/${player.assignedEmaId}/standings?tournaments=${tournamentId}`, { label: "GET standings" });
    // A person who asked to stay hidden has no public standings.
    if (answer.status === 404) continue;
    const standing = answer.json?.standings?.[0];
    if (answer.status !== 200 || standing == null) {
      problems.push(`slot ${slot}: no standing (${answer.status})`);
      continue;
    }
    compared += 1;
    const myTeam = teamRows.find((row) => row.team === teamOf.get(slot));
    const expected = {
      rank: rows.filter((row) => beats(row, mine)).length + 1,
      points: mine.points,
      score: mine.score,
      teamRank: myTeam == null ? null : teamRows.filter((row) => beats(row, myTeam)).length + 1,
      teamPoints: myTeam == null ? null : myTeam.points,
      chickenHands: chicken(slot),
      chickenRank: chicken(slot) === 0
        ? null
        : rows.filter((row) => chicken(row.playerId) > chicken(slot) || (chicken(row.playerId) === chicken(slot) && beats(row, mine))).length + 1,
      bestHand: best(slot),
      bestHandRank: best(slot) === 0 ? null : check.bestBySlotTable.filter((entry) => entry.best > best(slot)).length + 1,
    };
    for (const [field, value] of Object.entries(expected)) {
      const stored = typeof value === "number" && standing[field] != null ? round6(Number(standing[field])) : standing[field] ?? null;
      if (stored !== value) problems.push(`slot ${slot} ${field}: ${standing[field]} on the server, ${value} here`);
    }
  }
  return {
    compared,
    problems,
    teams: teamRows.length,
    chickenHands: Object.values(check.chickenBySlot).reduce((sum, count) => sum + count, 0),
    bestHand: check.bestBySlotTable.reduce((max, entry) => Math.max(max, entry.best), 0),
  };
}

/**
 * The corner cases of the plan of a game day (plan.cornerCases): each correction of an editor must be seen by the
 * phones of its table, a deleted hand must be written again, and no other table may have a correction.
 */
export function cornerCaseVerdicts(plan, botReports, check = null) {
  const events = botReports.flatMap((report) => report.events ?? []);
  const lines = [];
  const add = (ok, textLine) => lines.push(`${ok ? "✅" : "❌"} ${textLine}`);
  // A corner case that fails shows what the bots of its table saved and saw, to find the cause from the job log.
  const detail = (key) => {
    const saved = botReports.flatMap((report) => report.hands ?? []).filter((hand) => hand.table === key)
      .map((hand) => `#${hand.handId} ${hand.at?.slice(11, 19)} ${hand.hand?.handScore ?? ""}${hand.reentry ? " again" : ""}`);
    const seen = events.filter((event) => event.table === key && /hand-(deleted|corrected)-seen|hand-reentered/.test(event.kind))
      .map((event) => `${event.at?.slice(11, 19)} ${event.kind} #${event.handId} slot ${event.slot}${event.watcher ? " (watcher)" : ""}${event.version != null ? ` v${event.version}` : ""}${event.to ? ` to ${event.to.playerWinnerId ?? "-"}/${event.to.handScore ?? ""}` : ""}`);
    lines.push(`  Saved at ${key}: ${saved.join(", ") || "none"}.`);
    lines.push(`  Seen at ${key}: ${seen.join(", ") || "nothing"}.`);
  };
  const cases = plan?.cornerCases ?? [];
  const planned = new Set(cases.filter((item) => item.kind === "delete" || item.kind === "edit").map((item) => `${item.round}_${item.table}`));
  for (const item of cases) {
    const key = `${item.round}_${item.table}`;
    const at = (kind) => events.filter((event) => event.kind === kind && event.table === key);
    const where = item.which === "last" ? "the last hand" : "an older hand";
    const position = (event) => (item.which === "last" ? event.handId === event.newest : event.handId < event.newest);
    if (item.kind === "delete") {
      const scorer = at("hand-deleted-seen").filter((event) => !event.watcher);
      // The scorer writes the hand again at once: a phone whose next look comes after that sees the hand change, not
      // go. Both are a phone that saw the deletion.
      const watchers = [...at("hand-deleted-seen"), ...at("hand-corrected-seen")].filter((event) => event.watcher);
      const again = at("hand-reentered");
      const okDelete = scorer.some(position) && watchers.length > 0 && again.length > 0;
      add(okDelete,
        `Table ${key}: the editor deleted ${where}, the phones saw it and the player wrote it again (${scorer.length} seen by the scorer, ${watchers.length} by the others, ${again.length} written again${scorer[0] ? `, hand ${scorer[0].handId} of ${scorer[0].newest}` : ""}).`);
      if (!okDelete) detail(key);
    } else if (item.kind === "late") {
      const late = events.filter((event) => event.kind === "late-table" && event.table === key);
      const done = events.filter((event) => event.kind === "table-completed" && event.table === key);
      add(late.length > 0 && done.length > 0, `Table ${key} started ${item.minutes} minutes late and still played to the end (${done[0]?.hands ?? 0} hands).`);
    } else if (item.kind === "substitution") {
      const found = Object.entries(check?.substitutions ?? {}).filter(([, value]) => value.reason === item.reason && Number(value.fromRound) === item.round);
      const ranked = found.filter(([slot]) => check.ranking.some((entry) => entry.playerId === slot));
      add(found.length > 0 && ranked.length === 0,
        `A player of table ${key} was substituted (${item.reason.toLowerCase()}, from round ${item.round}) and has no result in the ranking (${found.map(([slot]) => `slot ${slot}`).join(", ") || "none"}).`);
    } else if (item.kind === "race") {
      const race = events.find((event) => event.kind === "race" && event.table === key);
      const statuses = race?.statuses ?? [];
      const saved = statuses.filter((status) => status === 200).length;
      const refused = statuses.filter((status) => status === 409).length;
      const extraHere = (check?.extra ?? []).filter((line) => line.startsWith(`${key}:`));
      add(saved === 1 && refused === 1 && extraHere.length === 0,
        `Table ${key}: two phones saved the same hand at the same time, one was saved and the other was told the table changed (answers ${statuses.join(" and ") || "none"}).`);
      if (!(saved === 1 && refused === 1)) detail(key);
    } else if (item.kind === "penalty-draw") {
      const penalties = at("penalty").filter((event) => event.planned);
      const draw = at("planned-draw")[0];
      const hand = draw == null ? null : (check?.tablesByKey?.get(key)?.hands ?? []).find((each) => Number(each.handId) === Number(draw.handId));
      const values = hand == null ? [] : SEATS.map((name) => text(hand[`player${name}Penalty`]));
      const expected = item.penalties.map((value, seat) => SEATS.map((_, index) => (index === seat ? value : "")));
      // Each call adds one row: east has "-10;", south ";-5" (the empty parts keep the rows in line).
      const want = SEATS.map((_, index) => expected.map((row) => row[index]).join(";").replace(/^;+$/, ""));
      const isDraw = hand != null && hand.isDone && text(hand.playerWinnerId) === "";
      const sameScores = !(check?.scoreProblems ?? []).some((line) => line.startsWith(`${key} `));
      const ok = penalties.length === item.penalties.length && penalties.every((event) => event.status === 200)
        && isDraw && values.every((value, index) => value === want[index]) && sameScores;
      add(ok,
        `Table ${key}: the scorer gave ${item.penalties.join(" and ")} as penalties and wrote a draw, and the penalties count in the totals (hand ${draw?.handId ?? "-"}, penalties by seat: ${hand == null ? "not found" : values.map(sumPenalty).join(" / ")}).`);
      if (!ok) detail(key);
    } else if (item.kind === "seat-change") {
      const before = at("seat-change").find((event) => event.when === "before");
      const after = at("seat-change").find((event) => event.when === "after");
      const table = check?.tablesByKey?.get(key)?.table;
      const now = table == null ? [] : SEATS.map((name) => text(table[`player${name}Id`]));
      const kept = before != null && now.join(",") === (before.seats ?? []).map(String).join(",");
      const ok = before?.status === 200 && after?.status === 409 && kept;
      add(ok,
        `Table ${key}: the players changed seats before the first hand (answer ${before?.status ?? "none"}) and could not after it (answer ${after?.status ?? "none"}), and the table keeps the new seats (${now.join(", ") || "not found"}).`);
      if (!ok) detail(key);
    } else if (item.kind === "extra-time") {
      const hand = at("extra-time-hand")[0];
      const data = check?.tablesByKey?.get(key);
      const done = (data?.hands ?? []).filter((each) => each.isDone);
      const inExtra = hand != null && hand.matchEnd != null && hand.savedAt > hand.matchEnd;
      const last = hand != null && done.length > 0 && Math.max(...done.map((each) => Number(each.handId))) === Number(hand.handId);
      const ok = inExtra && last && data?.table?.isCompleted === true;
      const seconds = hand?.matchEnd == null ? "-" : Math.round((hand.savedAt - hand.matchEnd) / 1000);
      add(ok,
        `Table ${key}: a slow table saved a hand ${seconds} s after the gong, in the extra time, and that hand closed the table (hand ${hand?.handId ?? "-"} of ${done.length}, ${data?.table?.isCompleted ? "completed" : "open"}).`);
      if (!ok) detail(key);
    } else if (item.kind === "abandoned") {
      const left = at("table-abandoned")[0];
      const data = check?.tablesByKey?.get(key);
      const done = (data?.hands ?? []).filter((each) => each.isDone).length;
      const ok = left != null && data?.table?.isCompleted === true && done === left.hands;
      add(ok,
        `Table ${key}: the players left after ${left?.hands ?? "-"} hands and the organizer closed the table (${data?.table?.isCompleted ? "completed" : "open"} with ${done} hands).`);
      if (!ok) detail(key);
    } else if (item.kind === "manual-totals") {
      const left = at("table-left-for-totals")[0];
      const table = check?.tablesByKey?.get(key)?.table;
      const scores = table == null ? [] : SEATS.map((name) => text(table[`player${name}Score`]));
      const points = table == null ? [] : SEATS.map((name) => pointsText(table[`player${name}Points`]));
      const sumZero = scores.length === 4 && scores.reduce((total, score) => total + Number(score), 0) === 0 && scores.every((score) => /^-?\d+$/.test(score));
      const sameScores = !(check?.scoreProblems ?? []).some((line) => line.startsWith(`${key} `));
      const ok = left != null && table?.useTotalsOnly === true && table?.isCompleted === true && sumZero && sameScores;
      add(ok,
        `Table ${key}: the table played on paper and the editor typed its totals with Manual scores (${table?.useTotalsOnly ? "manual" : "not manual"}, ${table?.isCompleted ? "completed" : "open"}, totals ${scores.join(" / ") || "-"}, table points ${points.join(" / ") || "-"}).`);
      if (!ok) detail(key);
    } else if (item.kind === "offline") {
      const lost = at("offline-lost-reply")[0];
      const back = at("offline-back")[0];
      const away = at("background")[0];
      const caughtUp = at("background-back")[0];
      const extraHere = (check?.extra ?? []).filter((line) => line.startsWith(`${key}:`));
      const data = check?.tablesByKey?.get(key);
      const once = lost != null && (data?.hands ?? []).filter((hand) => hand.isDone && Number(hand.handId) === Number(lost.handId)).length === 1;
      const ok = lost != null && back?.status === 409 && back?.shown === 1 && once && extraHere.length === 0
        && away != null && caughtUp != null && caughtUp.after >= caughtUp.before;
      add(ok,
        `Table ${key}: a phone lost the network as it saved hand ${lost?.handId ?? "-"}, sent it again after ${lost?.seconds ?? "-"} s and was told the table changed (answer ${back?.status ?? "none"}), the hand is saved once, and a phone back from ${away?.seconds ?? "-"} s in the background shows the hands (${caughtUp == null ? "-" : `${caughtUp.before} before, ${caughtUp.after} after`}).`);
      if (!ok) detail(key);
    } else if (item.kind === "tie") {
      const tied = (check?.tablesByKey?.get(key)?.table == null) ? [] : SEATS.map((name) => text(check.tablesByKey.get(key).table[`player${name}Id`]));
      const rows = (check?.ranking ?? []).filter((entry) => tied.includes(entry.playerId));
      const level = rows.length === tied.length && rows.length > 1
        && rows.every((row) => round6(row.points) === round6(rows[0].points) && row.score === rows[0].score);
      const place = rows.length === 0 ? "-" : (check.ranking.filter((entry) => beats(entry, rows[0])).length + 1);
      add(level,
        `The ${tied.length} players of table ${key} finish level in the ranking (${rows.map((row) => `${round6(row.points)} points, ${row.score}`).join(" / ") || "not ranked"}) and share place ${place}; the places of the server are compared in the check of the places.`);
      if (!level) detail(key);
    } else if (item.kind === "player-count") {
      // The draw of the server after the organizer took a table out: every round has to/4 tables, and every player of
      // the plan plays once a round. No slot above the new number is left in a table.
      const rounds = new Map();
      for (const data of check?.tablesByKey?.values() ?? []) {
        const seats = SEATS.map((name) => Number(text(data.table[`player${name}Id`])));
        const round = rounds.get(data.table.roundId) ?? [];
        round.push(seats);
        rounds.set(data.table.roundId, round);
      }
      const expected = Array.from({ length: item.to }, (_, index) => index + 1).join(",");
      const wrong = [...rounds].filter(([, tables]) => tables.length !== item.to / 4
        || tables.flat().sort((a, b) => a - b).join(",") !== expected).map(([round]) => round);
      const ok = rounds.size === plan.rounds && wrong.length === 0;
      add(ok,
        `The tournament was created with ${item.from} players and the organizer took a table out before the first hand: the new draw has ${item.to / 4} tables in each of its ${rounds.size} rounds, with the ${item.to} players once each${wrong.length ? ` (wrong in round ${wrong.join(", ")})` : ""}.`);
    } else if (item.kind === "edit") {
      const scorer = at("hand-corrected-seen").filter((event) => !event.watcher);
      const watchers = at("hand-corrected-seen").filter((event) => event.watcher);
      const okEdit = scorer.some(position) && watchers.length > 0;
      add(okEdit,
        `Table ${key}: the editor corrected ${where} and the phones show the correction (${scorer.length} seen by the scorer, ${watchers.length} by the others${scorer[0] ? `, hand ${scorer[0].handId} of ${scorer[0].newest}, score ${scorer[0].from?.handScore} to ${scorer[0].to?.handScore}` : ""}).`);
      if (!okEdit) detail(key);
    }
  }
  if (cases.length > 0) {
    const elsewhere = events.filter((event) => (event.kind === "hand-deleted-seen" || event.kind === "hand-corrected-seen") && !planned.has(event.table));
    add(elsewhere.length === 0, `No hand changed at the other tables (${elsewhere.length}${elsewhere.length ? `: ${[...new Set(elsewhere.map((event) => event.table))].join(", ")}` : ""}).`);
    for (const key of new Set(elsewhere.map((event) => event.table))) detail(key);
  }
  return lines;
}

/** The targets of docs/rehearsal.md that the bots can measure, as lines with ✅ or ❌. */
export function verdicts(check, botReports, integrity) {
  const lines = [];
  const add = (ok, textLine) => lines.push(`${ok ? "✅" : "❌"} ${textLine}`);
  const statuses = {};
  const handMs = [];
  for (const report of botReports) {
    for (const entry of Object.values(report.labels ?? {})) {
      for (const [status, count] of Object.entries(entry.statuses)) statuses[status] = (statuses[status] ?? 0) + count;
    }
    handMs.push(...(report.hands ?? []).map((hand) => hand.ms));
  }
  const server5xx = Object.entries(statuses).filter(([status]) => Number(status) >= 500).reduce((sum, [, count]) => sum + count, 0);
  const network = statuses[0] ?? 0;
  const tooMany = statuses[429] ?? 0;
  handMs.sort((a, b) => a - b);
  const p95 = handMs.length === 0 ? null : handMs[Math.min(handMs.length - 1, Math.ceil(0.95 * handMs.length) - 1)];
  const delays = botReports.map((report) => report.handDelayMs?.p95).filter((value) => value != null);
  add(check.scoreProblems.length === 0, `Scores and table points match an independent count (${check.scoreProblems.length} differences).`);
  add(check.lost.length === 0 && check.changed.length === 0, `No hand lost or changed (${check.lost.length} lost, ${check.changed.length} changed of ${check.handsSavedByBots}).`);
  add(check.extra.length === 0 && check.raceDuplicates === 0, `No duplicate hand (${check.extra.length} tables with extra hands, ${check.raceDuplicates} of ${check.races} races saved twice).`);
  add(server5xx === 0 && network === 0, `No server error (${server5xx} answers 5xx, ${network} network failures).`);
  add(tooMany === 0, `No request refused for too many calls (${tooMany} answers 429).`);
  add(p95 != null && p95 < 1_500, `Saving a hand takes p95 ${p95 ?? "-"} ms (target under 1500 ms).`);
  const slowest = delays.reduce((max, delay) => Math.max(max, delay), 0);
  if (delays.length > 0) add(slowest < 20_000, `A hand reaches the other phones of the table in p95 ${slowest} ms (target under 20 s).`);
  add(check.openTables.length === 0, `Every table is closed (${check.openTables.length} open${check.openTables.length ? `: ${check.openTables.join(", ")}` : ""}).`);
  if (check.standings != null) {
    const s = check.standings;
    add(s.problems.length === 0 && s.compared > 0,
      `Places of every player (ranking, team, chicken hands and best hand) match an independent count (${s.problems.length} differences in ${s.compared} players; ${s.teams} teams, ${s.chickenHands} chicken hands, best hand ${s.bestHand}).`);
  }
  if (integrity != null) add(integrity.counts?.errors === 0, `Integrity check: ${integrity.counts?.errors ?? "?"} errors, ${integrity.counts?.warnings ?? "?"} warnings.`);
  return lines;
}

export function checkMarkdown(title, check, lines) {
  const out = [`### ${title}`, "", ...lines.map((line) => `- ${line}`), ""];
  out.push(`Tables: ${check.tables}. Hands on the server: ${check.doneHands}. Saved by the bots of these reports: ${check.handsSavedByBots}.`);
  for (const [name, list] of [["Place differences", check.standings?.problems ?? []], ["Score differences", check.scoreProblems], ["Lost hands", check.lost], ["Changed hands", check.changed], ["Extra hands", check.extra]]) {
    if (list.length > 0) out.push("", `${name}:`, ...list.slice(0, 30).map((item) => `- ${item}`));
  }
  out.push("", "Top of the ranking (independent count):", "", "| # | Player | Points | Score |", "| --- | --- | --- | --- |");
  check.ranking.slice(0, 10).forEach((entry, index) => out.push(`| ${index + 1} | ${entry.playerId} | ${entry.points} | ${entry.score} |`));
  return out.join("\n");
}

export function writeCheck(path, title, check, lines) {
  mkdirSync(dirname(path), { recursive: true });
  writeFileSync(path, JSON.stringify({ ...check, verdicts: lines }, null, 2));
  const markdown = checkMarkdown(title, check, lines);
  console.log(markdown);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n\n`);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const files = process.argv.slice(2).filter((arg) => !arg.startsWith("--"));
  const botReports = files.map((file) => JSON.parse(readFileSync(file, "utf8")));
  const api = new Api(args.api ?? STAGING_API, new Metrics());
  const tournamentId = await findTournament(api, args.tournament);
  const check = await checkTournament(api, tournamentId, botReports);
  if (args.standings !== "off") check.standings = await checkStandings(api, tournamentId, check);
  const plan = args.plan ? JSON.parse(readFileSync(String(args.plan), "utf8")) : null;
  const lines = [...verdicts(check, botReports, null), ...cornerCaseVerdicts(plan, botReports, check)];
  writeCheck(args.report ?? "out/check.json", args.title ?? "Rehearsal check", check, lines);
  if (lines.some((line) => line.startsWith("❌"))) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
