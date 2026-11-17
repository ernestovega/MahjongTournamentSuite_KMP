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
import { findTournament } from "./bots.mjs";

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

function sameHand(saved, server) {
  if (saved.isDraw) return text(server.playerWinnerId) === "";
  return text(server.playerWinnerId) === text(saved.playerWinnerId)
    && text(server.playerLooserId).replace(/^-$/, "") === text(saved.playerLooserId)
    && text(server.handScore) === text(saved.handScore);
}

/** Reads the tournament and checks it. [botReports] are the reports of bots.mjs, from every process. */
export async function checkTournament(api, tournamentId, botReports = []) {
  const reply = await api.call("GET", `/public/tournaments/${tournamentId}/tables-with-hands`, { label: "GET all tables" });
  if (reply.status !== 200) throw new Error(`Reading the tables failed: ${reply.status} ${reply.json?.message ?? ""}`);
  const tables = reply.json.tables;
  const byKey = new Map(tables.map((item) => [`${item.table.roundId}_${item.table.tableId}`, item]));
  const scoreProblems = [];
  const openTables = [];
  const ranking = new Map();
  let doneHands = 0;

  for (const [key, { table, hands }] of byKey) {
    if (!table.isCompleted) openTables.push(key);
    const seats = SEATS.map((name) => text(table[`player${name}Id`]));
    doneHands += hands.filter((hand) => hand.isDone).length;
    if (seats.some((seat) => seat === "") || table.useTotalsOnly) continue;
    const changes = hands.filter((hand) => hand.isDone || table.isCompleted).map((hand) => handChange(hand, seats)).filter(Boolean);
    if (changes.length === 0) continue;
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
  for (const report of botReports) {
    for (const saved of report.hands ?? []) {
      const list = savedByTable.get(saved.table) ?? [];
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
  };
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
  if (integrity != null) add(integrity.counts?.errors === 0, `Integrity check: ${integrity.counts?.errors ?? "?"} errors, ${integrity.counts?.warnings ?? "?"} warnings.`);
  return lines;
}

export function checkMarkdown(title, check, lines) {
  const out = [`### ${title}`, "", ...lines.map((line) => `- ${line}`), ""];
  out.push(`Tables: ${check.tables}. Hands on the server: ${check.doneHands}. Saved by the bots of these reports: ${check.handsSavedByBots}.`);
  for (const [name, list] of [["Score differences", check.scoreProblems], ["Lost hands", check.lost], ["Changed hands", check.changed], ["Extra hands", check.extra]]) {
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
  const lines = verdicts(check, botReports, null);
  writeCheck(args.report ?? "out/check.json", args.title ?? "Rehearsal check", check, lines);
  if (lines.some((line) => line.startsWith("❌"))) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
