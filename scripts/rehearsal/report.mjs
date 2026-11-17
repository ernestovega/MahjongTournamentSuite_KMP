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
    if (seats.some((seat) => seat === "") || table.useTotalsOnly) continue;
    const counted = hands.filter((hand) => hand.isDone || table.isCompleted);
    const changes = counted.map((hand) => handChange(hand, seats)).filter(Boolean);
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
  return {
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
  const cases = plan?.cornerCases ?? [];
  const planned = new Set(cases.filter((item) => item.kind === "delete" || item.kind === "edit").map((item) => `${item.round}_${item.table}`));
  for (const item of cases) {
    const key = `${item.round}_${item.table}`;
    const at = (kind) => events.filter((event) => event.kind === kind && event.table === key);
    const where = item.which === "last" ? "the last hand" : "an older hand";
    const position = (event) => (item.which === "last" ? event.handId === event.newest : event.handId < event.newest);
    if (item.kind === "delete") {
      const scorer = at("hand-deleted-seen").filter((event) => !event.watcher);
      const watchers = at("hand-deleted-seen").filter((event) => event.watcher);
      const again = at("hand-reentered");
      add(scorer.some(position) && watchers.length > 0 && again.length > 0,
        `Table ${key}: the editor deleted ${where}, the phones saw it and the player wrote it again (${scorer.length} seen by the scorer, ${watchers.length} by the others, ${again.length} written again${scorer[0] ? `, hand ${scorer[0].handId} of ${scorer[0].newest}` : ""}).`);
    } else if (item.kind === "late") {
      const late = events.filter((event) => event.kind === "late-table" && event.table === key);
      const done = events.filter((event) => event.kind === "table-completed" && event.table === key);
      add(late.length > 0 && done.length > 0, `Table ${key} started ${item.minutes} minutes late and still played to the end (${done[0]?.hands ?? 0} hands).`);
    } else if (item.kind === "substitution") {
      const found = Object.entries(check?.substitutions ?? {}).filter(([, value]) => value.reason === item.reason && Number(value.fromRound) === item.round);
      const ranked = found.filter(([slot]) => check.ranking.some((entry) => entry.playerId === slot));
      add(found.length > 0 && ranked.length === 0,
        `A player of table ${key} was substituted (${item.reason.toLowerCase()}, from round ${item.round}) and has no result in the ranking (${found.map(([slot]) => `slot ${slot}`).join(", ") || "none"}).`);
    } else if (item.kind === "edit") {
      const scorer = at("hand-corrected-seen").filter((event) => !event.watcher);
      const watchers = at("hand-corrected-seen").filter((event) => event.watcher);
      add(scorer.some(position) && watchers.length > 0,
        `Table ${key}: the editor corrected ${where} and the phones show the correction (${scorer.length} seen by the scorer, ${watchers.length} by the others${scorer[0] ? `, hand ${scorer[0].handId} of ${scorer[0].newest}, score ${scorer[0].from?.handScore} to ${scorer[0].to?.handScore}` : ""}).`);
    }
  }
  if (cases.length > 0) {
    const elsewhere = events.filter((event) => (event.kind === "hand-deleted-seen" || event.kind === "hand-corrected-seen") && !planned.has(event.table));
    add(elsewhere.length === 0, `No hand changed at the other tables (${elsewhere.length}${elsewhere.length ? `: ${[...new Set(elsewhere.map((event) => event.table))].join(", ")}` : ""}).`);
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
  if (delays.length > 0) add(Math.max(...delays) < 20_000, `A hand reaches the other phones of the table in p95 ${Math.max(...delays)} ms (target under 20 s).`);
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
