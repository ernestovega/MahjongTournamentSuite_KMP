#!/usr/bin/env node
// The cost of a game day in staging, and of a real tournament, in euros. See docs/benchmark.md ("Cost").
//
// It reads from Cloud Monitoring of staging what the project used in the window of the game day: Firestore reads,
// writes and deletes, requests, CPU and memory of the API (Cloud Run functions), bytes sent by the API and by Storage.
// Then it projects them to a real tournament (60 players, rounds of 2 hours by default): the reads of the screens grow
// with the time on screen, and the saves of hands with the players and the rounds. The bots tell which is which: the
// API answers each of their calls with its Firestore operations (header X-MTS-Firestore).
//
//   node scripts/rehearsal/cost.mjs --project=mts---staging --bots=out/bots.json --plan=out/scenario/plan.json \
//     [--since=ISO --until=ISO] [--target-players=60 --target-rounds=7 --round-minutes=120 --break-minutes=15]
//     [--days=2 --tournaments-per-month=1] [--report=out/cost.json]
//
// The token of Cloud Monitoring comes from GOOGLE_OAUTH_ACCESS_TOKEN or from `gcloud auth print-access-token`. It only
// reads. It never fails the run: without the permission (roles/monitoring.viewer) it says how to give it.
import { execFileSync } from "node:child_process";
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

import { parseArgs } from "./api.mjs";

const HERE = dirname(fileURLToPath(import.meta.url));
export const DEFAULT_PRICES = join(HERE, "../../benchmarks/prices.json");

/** The metrics of Cloud Monitoring that the bill counts, all DELTA: their sum over the window is the use. */
export const METRICS = {
  firestoreReads: "firestore.googleapis.com/document/read_count",
  firestoreWrites: "firestore.googleapis.com/document/write_count",
  firestoreDeletes: "firestore.googleapis.com/document/delete_count",
  requests: "run.googleapis.com/request_count",
  cpuSeconds: "run.googleapis.com/container/cpu/allocation_time",
  memoryGibSeconds: "run.googleapis.com/container/memory/allocation_time",
  instanceSeconds: "run.googleapis.com/container/billable_instance_time",
  apiSentBytes: "run.googleapis.com/container/network/sent_bytes_count",
  storageSentBytes: "storage.googleapis.com/network/sent_bytes_count",
  storageRequests: "storage.googleapis.com/api/request_count",
};

/** The sum of the points of a timeSeries answer. */
export function sumOfSeries(answer) {
  let total = 0;
  for (const series of answer?.timeSeries ?? []) {
    for (const point of series.points ?? []) {
      const value = point.value ?? {};
      total += Number(value.int64Value ?? value.doubleValue ?? 0);
    }
  }
  return total;
}

/**
 * Reads every metric of [METRICS] in the window. Returns { values, errors }: a metric that fails is left out with its
 * error, so one missing permission or metric does not hide the others.
 */
export async function readMonitoring({ project, since, until, token, fetchImpl = fetch }) {
  const values = {};
  const errors = {};
  for (const [key, type] of Object.entries(METRICS)) {
    const query = new URLSearchParams({
      filter: `metric.type="${type}"`,
      "interval.startTime": new Date(since).toISOString(),
      "interval.endTime": new Date(until).toISOString(),
      "aggregation.alignmentPeriod": "3600s",
      "aggregation.perSeriesAligner": "ALIGN_SUM",
      "aggregation.crossSeriesReducer": "REDUCE_SUM",
    });
    try {
      const response = await fetchImpl(`https://monitoring.googleapis.com/v3/projects/${project}/timeSeries?${query}`, {
        headers: { authorization: `Bearer ${token}` },
      });
      const json = await response.json().catch(() => ({}));
      if (!response.ok) {
        errors[key] = `${response.status} ${json?.error?.message ?? ""}`.trim();
        continue;
      }
      values[key] = sumOfSeries(json);
    } catch (error) {
      errors[key] = String(error?.message ?? error);
    }
  }
  return { values, errors };
}

/**
 * How much of the use comes from the screens (GET: it grows with the time on screen) and how much from the actions
 * (saves, sign-ins: they grow with the hands). From the labels of the bots report. Each share is null without data.
 */
export function sharesOfScreens(labels = {}) {
  const sum = (pick, screensOnly) => Object.entries(labels)
    .filter(([label]) => screensOnly === null || label.startsWith("GET") === screensOnly)
    .reduce((total, [, entry]) => total + pick(entry), 0);
  const share = (pick) => {
    const all = sum(pick, null);
    return all > 0 ? sum(pick, true) / all : null;
  };
  return {
    reads: share((entry) => entry.usage?.reads ?? 0),
    requests: share((entry) => entry.count ?? 0),
    bytes: share((entry) => entry.usage?.bytes ?? 0),
  };
}

/** Firestore operations by label, most reads first, with the reads per call. */
export function readsByLabel(labels = {}) {
  return Object.entries(labels)
    .filter(([, entry]) => (entry.usage?.measured ?? 0) > 0)
    .map(([label, entry]) => ({
      label,
      calls: entry.count,
      reads: entry.usage.reads,
      writes: entry.usage.writes,
      deletes: entry.usage.deletes,
      readsPerCall: entry.usage.reads / entry.usage.measured,
      kbPerCall: entry.count > 0 ? entry.usage.bytes / entry.count / 1024 : 0,
    }))
    .sort((a, b) => b.reads - a.reads);
}

/**
 * The scale from the game day to the real tournament. The screens: players × minutes of play (the bots poll as the
 * app does). The actions: players × rounds (the same hands per table).
 */
export function scales({ scenario, target, windowMinutes }) {
  const realMinutes = target.rounds * (target.roundMinutes + target.breakMinutes);
  return {
    screens: (target.players / scenario.players) * (realMinutes / Math.max(1, windowMinutes)),
    actions: (target.players * target.rounds) / (scenario.players * scenario.rounds),
    realMinutes,
  };
}

const SHARE_OF = {
  firestoreReads: "reads",
  requests: "requests",
  cpuSeconds: "requests",
  memoryGibSeconds: "requests",
  instanceSeconds: "requests",
  apiSentBytes: "bytes",
  storageSentBytes: null,
  storageRequests: null,
  firestoreWrites: null,
  firestoreDeletes: null,
};

/** The use of the real tournament. Writes, deletes and Storage scale with the actions; the rest by its share. */
export function projectUse(measured, shares, scale) {
  const projected = {};
  for (const [key, value] of Object.entries(measured)) {
    const shareKey = SHARE_OF[key];
    // Without bots data, all of a read metric counts as screens: the screens make most of the reads.
    const share = shareKey == null ? 0 : (shares[shareKey] ?? 1);
    projected[key] = value * (share * scale.screens + (1 - share) * scale.actions);
  }
  return projected;
}

/** The web app that each player and spectator downloads: [loads] cold loads of [mb] each. Hosting only. */
export function webDownloadBytes({ clients, loads, mb }) {
  return clients * loads * mb * 1024 * 1024;
}

const GB = 1024 ** 3;

/**
 * The price in USD of a use, line by line. [days]: the days it spreads over (the daily free tier of Firestore), and
 * [free]: true to take off the free tiers (the monthly ones whole, as if the project used nothing else that month).
 */
export function priceOf(use, prices, { days = 1, free = false } = {}) {
  const f = prices.firestore;
  const r = prices.cloudRun;
  const h = prices.hosting;
  const s = prices.storage;
  const overDaily = (total, perDay) => (free ? Math.max(0, total / days - perDay) * days : total);
  const overMonthly = (total, perMonth) => (free ? Math.max(0, total - perMonth) : total);
  const hostingBytes = (use.apiSentBytes ?? 0) + (use.webBytes ?? 0);
  const lines = {
    firestoreReads: (overDaily(use.firestoreReads ?? 0, f.freeReadsPerDay) / 100_000) * f.readsPer100k,
    firestoreWrites: (overDaily(use.firestoreWrites ?? 0, f.freeWritesPerDay) / 100_000) * f.writesPer100k,
    firestoreDeletes: (overDaily(use.firestoreDeletes ?? 0, f.freeDeletesPerDay) / 100_000) * f.deletesPer100k,
    requests: (overMonthly(use.requests ?? 0, r.freeRequestsPerMonth) / 1_000_000) * r.perMillionRequests,
    cpu: overMonthly(use.cpuSeconds ?? 0, r.freeVcpuSecondsPerMonth) * r.vcpuSecond,
    memory: overMonthly(use.memoryGibSeconds ?? 0, r.freeGibSecondsPerMonth) * r.gibSecond,
    hosting: overMonthly(hostingBytes / GB, h.freeGbPerMonth) * h.perGb,
    storage: ((use.storageSentBytes ?? 0) / GB) * s.egressPerGb + ((use.storageRequests ?? 0) / 10_000) * s.operationsPer10k,
  };
  const total = Object.values(lines).reduce((sum, value) => sum + value, 0);
  return { lines, total };
}

export const euros = (usd, prices) => usd * prices.eurPerUsd;

function multiply(use, factor) {
  return Object.fromEntries(Object.entries(use).map(([key, value]) => [key, value * factor]));
}

/** The whole report: the measured use, the projection and the prices. Pure, for the tests. */
export function costReport({ measured, errors = {}, labels = {}, scenario, target, windowMinutes, prices, web }) {
  const shares = sharesOfScreens(labels);
  const scale = scales({ scenario, target, windowMinutes });
  const tournament = projectUse(measured, shares, scale);
  tournament.webBytes = webDownloadBytes({ clients: target.players + target.spectators, loads: web.loadsPerClient * target.days, mb: web.mb });
  const perDay = multiply(tournament, 1 / target.days);
  const month = multiply(tournament, target.tournamentsPerMonth);
  return {
    windowMinutes,
    scenario,
    target,
    web,
    shares,
    scale,
    measured,
    errors,
    tournament,
    readsByLabel: readsByLabel(labels),
    cost: {
      gameDay: priceOf(measured, prices),
      tournament: priceOf(tournament, prices, { days: target.days }),
      day: priceOf(perDay, prices),
      month: priceOf(month, prices, { days: target.days * target.tournamentsPerMonth }),
      monthWithFreeTier: priceOf(month, prices, { days: target.days * target.tournamentsPerMonth, free: true }),
    },
    eurPerUsd: prices.eurPerUsd,
    pricesUpdated: prices.updated,
  };
}

const LINE_NAMES = {
  firestoreReads: "Firestore reads",
  firestoreWrites: "Firestore writes",
  firestoreDeletes: "Firestore deletes",
  requests: "API requests",
  cpu: "API CPU",
  memory: "API memory",
  hosting: "Hosting (web app and API answers)",
  storage: "Storage (photos, logos)",
};

const eur = (usd, rate) => `${(usd * rate).toFixed(usd * rate < 1 ? 3 : 2)} €`;
const count = (value) => Math.round(value ?? 0).toLocaleString("en-US");
const mb = (bytes) => `${((bytes ?? 0) / 1024 ** 2).toFixed(1)} MB`;

export function markdownCost(report, title = "Cost") {
  const rate = report.eurPerUsd;
  const t = report.target;
  const lines = [`### ${title}`, ""];
  const failed = Object.entries(report.errors);
  if (failed.length > 0) {
    lines.push(`Cloud Monitoring did not answer for ${failed.map(([key]) => key).join(", ")}: ${failed[0][1]}`, "",
      "Without the metrics the cost is incomplete. The service account of the workflow needs the role `roles/monitoring.viewer` in staging (docs/benchmark.md, \"Cost\").", "");
  }
  lines.push(
    `Measured in staging over ${Math.round(report.windowMinutes)} minutes: ${report.scenario.players} players, ${report.scenario.rounds} rounds. ` +
      `Projected to ${t.players} players and ${t.spectators} spectators, ${t.rounds} rounds of ${t.roundMinutes} min (+${t.breakMinutes} min between rounds) over ${t.days} day(s), ` +
      `${t.tournamentsPerMonth} tournament(s) a month. List prices of ${report.pricesUpdated}, 1 USD = ${rate} EUR.`,
    "",
    "| | Game day (measured) | Tournament (projected) |",
    "| --- | ---: | ---: |",
    `| Firestore reads | ${count(report.measured.firestoreReads)} | ${count(report.tournament.firestoreReads)} |`,
    `| Firestore writes | ${count(report.measured.firestoreWrites)} | ${count(report.tournament.firestoreWrites)} |`,
    `| Firestore deletes | ${count(report.measured.firestoreDeletes)} | ${count(report.tournament.firestoreDeletes)} |`,
    `| API requests | ${count(report.measured.requests)} | ${count(report.tournament.requests)} |`,
    `| API CPU (vCPU·s) | ${count(report.measured.cpuSeconds)} | ${count(report.tournament.cpuSeconds)} |`,
    `| API memory (GiB·s) | ${count(report.measured.memoryGibSeconds)} | ${count(report.tournament.memoryGibSeconds)} |`,
    `| API answers sent | ${mb(report.measured.apiSentBytes)} | ${mb(report.tournament.apiSentBytes)} |`,
    `| Web app downloads | - | ${mb(report.tournament.webBytes)} |`,
    `| Storage sent | ${mb(report.measured.storageSentBytes)} | ${mb(report.tournament.storageSentBytes)} |`,
    "",
    "| Cost | Game day | Tournament day | Whole tournament | Month | Month, after the free tier |",
    "| --- | ---: | ---: | ---: | ---: | ---: |",
  );
  const c = report.cost;
  for (const key of Object.keys(LINE_NAMES)) {
    lines.push(`| ${LINE_NAMES[key]} | ${eur(c.gameDay.lines[key], rate)} | ${eur(c.day.lines[key], rate)} | ${eur(c.tournament.lines[key], rate)} | ${eur(c.month.lines[key], rate)} | ${eur(c.monthWithFreeTier.lines[key], rate)} |`);
  }
  lines.push(`| **Total** | **${eur(c.gameDay.total, rate)}** | **${eur(c.day.total, rate)}** | **${eur(c.tournament.total, rate)}** | **${eur(c.month.total, rate)}** | **${eur(c.monthWithFreeTier.total, rate)}** |`, "");
  const pct = (value) => (value == null ? "-" : `${Math.round(value * 100)} %`);
  if (report.shares.reads == null) {
    lines.push(`Without the reads of each call of the bots (a backend before 1.0.27, or no bots), all the reads, calls and bytes count as screens and scale ×${report.scale.screens.toFixed(2)} (players and minutes): an upper bound. Writes scale ×${report.scale.actions.toFixed(2)} (players and rounds).`);
  } else lines.push(`Screens (GET) made ${pct(report.shares.reads)} of the reads the bots saw, ${pct(report.shares.requests)} of their calls and ${pct(report.shares.bytes)} of the bytes. They scale ×${report.scale.screens.toFixed(2)} (players and minutes), the actions ×${report.scale.actions.toFixed(2)} (players and rounds).`);
  if (report.readsByLabel.length > 0) {
    lines.push("", "Firestore by call of the bots (the most reads first):", "", "| Call | Calls | Reads | Reads per call | Writes | kB per call |", "| --- | ---: | ---: | ---: | ---: | ---: |");
    for (const row of report.readsByLabel.slice(0, 15)) {
      lines.push(`| ${row.label} | ${count(row.calls)} | ${count(row.reads)} | ${row.readsPerCall.toFixed(1)} | ${count(row.writes)} | ${row.kbPerCall.toFixed(1)} |`);
    }
  }
  lines.push("", "The real bill is in the Cloud Console, Billing > Reports. The projection counts the web app download as an estimate.");
  return lines.join("\n");
}

function accessToken() {
  if (process.env.GOOGLE_OAUTH_ACCESS_TOKEN) return process.env.GOOGLE_OAUTH_ACCESS_TOKEN;
  return execFileSync("gcloud", ["auth", "print-access-token"], { encoding: "utf8" }).trim();
}

/** The players, rounds and round length of the tournament of the game day: from the plan, or from the public API. */
async function scenarioOf(args) {
  if (args.plan) {
    const plan = JSON.parse(readFileSync(args.plan, "utf8"));
    return { name: plan.name, players: plan.players, rounds: plan.rounds };
  }
  if (args.api && args.tournament) {
    const base = String(args.api).replace(/\/$/, "");
    const { tournaments = [] } = await (await fetch(`${base}/public/tournaments`)).json();
    const found = tournaments.find((t) => t.name === args.tournament || t.id === args.tournament);
    if (found) return { name: found.name, players: found.numPlayers, rounds: found.numRounds };
  }
  return { name: "", players: Number(args["scenario-players"] ?? 64), rounds: Number(args["scenario-rounds"] ?? 7) };
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const bots = args.bots ? JSON.parse(readFileSync(args.bots, "utf8")) : {};
  const since = args.since || bots.startedAt;
  const until = args.until || bots.finishedAt || new Date().toISOString();
  if (!args.project || !since) {
    console.error("Usage: cost.mjs --project=<staging> (--bots=bots.json | --since=ISO) [--until=ISO]");
    process.exit(2);
  }
  const prices = JSON.parse(readFileSync(args.prices ?? DEFAULT_PRICES, "utf8"));
  const scenario = await scenarioOf(args);
  const target = {
    players: Number(args["target-players"] ?? 60),
    spectators: Number(args["target-spectators"] ?? 20),
    rounds: Number(args["target-rounds"] ?? 7),
    roundMinutes: Number(args["round-minutes"] ?? 120),
    breakMinutes: Number(args["break-minutes"] ?? 15),
    days: Number(args.days ?? 2),
    tournamentsPerMonth: Number(args["tournaments-per-month"] ?? 1),
  };
  const web = { mb: Number(args["web-mb"] ?? 15), loadsPerClient: Number(args["web-loads"] ?? 2) };
  let measured = {};
  let errors = {};
  try {
    ({ values: measured, errors } = await readMonitoring({ project: args.project, since, until, token: accessToken() }));
  } catch (error) {
    errors = { token: String(error?.message ?? error) };
  }
  const windowMinutes = (new Date(until) - new Date(since)) / 60_000;
  const report = costReport({ measured, errors, labels: bots.labels, scenario, target, windowMinutes, prices, web });
  report.since = since;
  report.until = until;
  const markdown = markdownCost(report, args.title ?? `Cost of ${scenario.name || "the game day"}`);
  console.log(markdown);
  if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${markdown}\n\n`);
  if (args.report) {
    mkdirSync(dirname(args.report), { recursive: true });
    writeFileSync(args.report, JSON.stringify(report, null, 2));
  }
  if (Object.keys(errors).length > 0) console.log(`::warning::The cost is incomplete: ${Object.keys(errors).join(", ")} without data.`);
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) await main();
