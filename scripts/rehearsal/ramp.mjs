#!/usr/bin/env node
// Load ramp: how far the API goes beyond a tournament of 60 players. Each stage multiplies the phones of a real day
// (60 players on the table screen, a room screen and spectators) and measures the answers, so the report says how many
// times the load of the tournament the server takes before it slows down or refuses calls. See docs/rehearsal.md.
//
//   node scripts/rehearsal/ramp.mjs --api=http://127.0.0.1:5055/api --tournament=test-live-ongoing \
//     [--players=60] [--stages=1,2,4,8] [--stage-seconds=60] [--p95-limit=1500] [--round=N] [--report=out/ramp.json]
//
// Without --api it uses the local test backend (npm run e2e:backend). Point it at staging only with a tournament in
// play, and never at production. Every phone reads as the app does: the manifest every 2 seconds and its table when the
// table changes (the table screen), and every 15 seconds the tables of the round (the ranking). With
// --writer-email, --writer-password and --writer-table (its table in the round) one scorer also checks a hand every 10 seconds per stage multiplier, with the
// preview call that the app makes before it saves, so the stage writes nothing.
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { performance } from "node:perf_hooks";

import { Api, Metrics, Session, parseArgs, sleep } from "./api.mjs";

export const LOCAL_API = "http://127.0.0.1:5055/api";

/** The phones of one stage: [multiplier] times the players, plus one room screen and 10 % spectators per multiplier. */
export function stagePhones(players, multiplier) {
  return { players: players * multiplier, spectators: Math.ceil(players * 0.1) * multiplier + multiplier };
}

/**
 * The verdict of one stage: it holds when the p95 of every label stays under [p95Limit] ms and less than 1 % of the
 * calls fail (no answer, or an answer of 400 and up: a refused call is a failure too).
 */
export function stageVerdict(summary, p95Limit) {
  let calls = 0;
  let failed = 0;
  let worstP95 = 0;
  let worstLabel = null;
  for (const [label, entry] of Object.entries(summary.labels)) {
    calls += entry.count;
    for (const [status, count] of Object.entries(entry.statuses)) {
      const code = Number(status);
      if (code === 0 || code >= 400) failed += count;
    }
    if ((entry.p95 ?? 0) > worstP95) {
      worstP95 = entry.p95 ?? 0;
      worstLabel = label;
    }
  }
  const failedShare = calls === 0 ? 1 : failed / calls;
  return { calls, failed, failedShare, worstP95, worstLabel, holds: calls > 0 && failedShare < 0.01 && worstP95 <= p95Limit };
}

/** The margin: the highest multiplier whose stage holds, with every lower stage holding too. 0 when the first fails. */
export function margin(stages) {
  let best = 0;
  for (const stage of stages) {
    if (!stage.verdict.holds) break;
    best = stage.multiplier;
  }
  return best;
}

async function tableScreen(api, tournamentId, tables, index, until) {
  await sleep(Math.random() * 2_000);
  let lastVersion = null;
  while (performance.now() < until) {
    const reply = await api.call("GET", `/public/tournaments/${tournamentId}/sync/manifest`, { label: "GET manifest" });
    const table = tables.length === 0 ? null : tables[index % tables.length];
    const version = table == null ? null : reply.json?.tableVersions?.[`${table.roundId}_${table.tableId}`];
    if (reply.status === 200 && table != null && version !== lastVersion) {
      lastVersion = version;
      await api.call("GET", `/public/tournaments/${tournamentId}/tables/${table.roundId}/${table.tableId}`, { label: "GET table" });
    }
    await sleep(2_000);
  }
}

async function rankingScreen(api, tournamentId, roundId, until) {
  await sleep(Math.random() * 15_000);
  while (performance.now() < until) {
    await api.call("GET", `/public/tournaments/${tournamentId}/rounds/${roundId}/tables-with-hands`, { label: "GET round tables" });
    await sleep(15_000);
  }
}

async function scorer(api, session, table, tournamentId, multiplier, until) {
  const path = `/player/tournaments/${tournamentId}/tables/${table.roundId}/${table.tableId}/hands/preview`;
  const seats = (await api.call("GET", `/public/tournaments/${tournamentId}/tables/${table.roundId}/${table.tableId}`, { label: "GET table" })).json;
  const seat = (name) => String((seats?.table ?? seats)?.[`player${name}Id`] ?? "");
  const hand = { playerWinnerId: seat("East"), playerLooserId: seat("South"), handScore: "8" };
  while (performance.now() < until) {
    await api.call("POST", path, { token: await session.bearer(), body: hand, label: "POST hand preview" });
    await sleep(10_000 / multiplier);
  }
}

/** Runs the stages one after the other and returns the report. */
export async function ramp(options) {
  const { base, tournamentId, players, stages, stageSeconds, p95Limit, writer, log = console.log } = options;
  const probe = new Api(base, new Metrics());
  const round = await probe.call("GET", `/public/tournaments/${tournamentId}/rounds`, { label: "GET rounds" });
  if (round.status !== 200) throw new Error(`The tournament ${tournamentId} has no rounds: HTTP ${round.status}`);
  // The round in play: the last round that has a done hand, or the first round before the tournament starts.
  const roundIds = (round.json.rounds ?? []).map((it) => Number(it.roundId)).sort((a, b) => b - a);
  let roundId = options.roundId ?? null;
  let tables = [];
  for (const candidate of roundId != null ? [roundId] : roundIds) {
    const listed = await probe.call("GET", `/public/tournaments/${tournamentId}/rounds/${candidate}/tables-with-hands`, { label: "GET round tables" });
    const items = listed.json?.tables ?? [];
    if (roundId != null || items.some((it) => (it.hands ?? []).some((hand) => hand.isDone))) {
      roundId = candidate;
      tables = items.map((it) => ({ roundId: candidate, tableId: it.table?.tableId ?? it.tableId })).filter((it) => it.tableId != null);
      break;
    }
  }
  if (roundId == null) {
    roundId = roundIds.at(-1) ?? 1;
    const listed = await probe.call("GET", `/public/tournaments/${tournamentId}/rounds/${roundId}/tables-with-hands`, { label: "GET round tables" });
    tables = (listed.json?.tables ?? []).map((it) => ({ roundId, tableId: it.table?.tableId ?? it.tableId })).filter((it) => it.tableId != null);
  }
  let session = null;
  if (writer) {
    session = new Session(probe, writer.email, writer.password);
    if (!(await session.signIn())) throw new Error(`${writer.email} cannot sign in`);
  }

  const results = [];
  for (const multiplier of stages) {
    const metrics = new Metrics();
    const api = new Api(base, metrics);
    if (session) session.api = api;
    const phones = stagePhones(players, multiplier);
    log(`Stage x${multiplier}: ${phones.players} table screens and ${phones.spectators} ranking screens for ${stageSeconds} s.`);
    const until = performance.now() + stageSeconds * 1_000;
    await Promise.all([
      ...Array.from({ length: phones.players }, (_, index) => tableScreen(api, tournamentId, tables, index, until)),
      ...Array.from({ length: phones.spectators }, () => rankingScreen(api, tournamentId, roundId, until)),
      ...(session && tables.length > 0 ? [scorer(api, session, writer.tableId ? { roundId, tableId: writer.tableId } : tables[0], tournamentId, multiplier, until)] : []),
    ]);
    const summary = metrics.summary();
    const verdict = stageVerdict(summary, p95Limit);
    log(`  ${verdict.calls} calls, ${verdict.failed} failed, worst p95 ${verdict.worstP95} ms (${verdict.worstLabel}): ${verdict.holds ? "holds" : "does not hold"}.`);
    results.push({ multiplier, phones, verdict, labels: summary.labels, refusals: summary.refusals });
    if (!verdict.holds) break;
  }
  return { base, tournamentId, roundId, tables: tables.length, players, p95Limit, stages: results, margin: margin(results) };
}

const isMain = import.meta.url === `file://${process.argv[1]}`;
if (isMain) {
  const args = parseArgs(process.argv.slice(2));
  if (!args.tournament) {
    console.error("Pass --tournament=<id>.");
    process.exit(2);
  }
  const base = String(args.api ?? LOCAL_API);
  if (/mahjongmadrid\.com|mahjong-tournament-suite\b(?!.*staging)/i.test(base)) {
    console.error("The ramp never runs against production.");
    process.exit(2);
  }
  const report = await ramp({
    base,
    tournamentId: String(args.tournament),
    players: Number(args.players ?? 60),
    stages: String(args.stages ?? "1,2,4,8").split(",").map(Number).filter((it) => it > 0),
    stageSeconds: Number(args["stage-seconds"] ?? 60),
    p95Limit: Number(args["p95-limit"] ?? 1500),
    roundId: args.round ? Number(args.round) : undefined,
    writer: args["writer-email"]
      ? {
        email: String(args["writer-email"]),
        password: String(args["writer-password"] ?? process.env.REHEARSAL_PASSWORD ?? ""),
        tableId: args["writer-table"] ? Number(args["writer-table"]) : null,
      }
      : null,
  });
  console.log(`Margin: x${report.margin} (${report.margin * report.players} players' phones).`);
  if (args.report) {
    mkdirSync(dirname(String(args.report)), { recursive: true });
    writeFileSync(String(args.report), JSON.stringify(report, null, 2));
  }
}
