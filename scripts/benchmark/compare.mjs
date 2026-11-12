#!/usr/bin/env node
// Checks the results of the benchmark against the budgets and against the recent runs, and writes a report.
//
//   node scripts/benchmark/compare.mjs --current backend-results.json --current screens-results.json \
//     [--history previous] [--summary report.md]
//
// --history is a folder with the results of the recent runs, newest first: previous/1/, previous/2/, ... each with
// backend-results.json and screens-results.json.
//
// A time has a problem when it is over its budget, or clearly slower than the median of the recent runs. The times
// of production vary a lot from run to run (shared runners, cold Cloud Functions right after a deploy), so a problem
// fails the run (exit code 1) only when the previous run had a problem on the same line too. A problem seen once is
// a warning. Errors fail at once. The budgets are in benchmarks/budgets.json. See docs/benchmark.md.
import { appendFile, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { parseArgs } from "node:util";

const { values: args } = parseArgs({
  options: {
    current: { type: "string", multiple: true, default: [] },
    history: { type: "string" },
    budgets: { type: "string", default: new URL("../../benchmarks/budgets.json", import.meta.url).pathname },
    summary: { type: "string", default: process.env.GITHUB_STEP_SUMMARY ?? "" },
  },
});

/** How many recent runs make the baseline. */
const BASELINE_RUNS = 5;

async function readJson(path) {
  try {
    return JSON.parse(await readFile(path, "utf8"));
  } catch {
    return null;
  }
}

const budgets = await readJson(args.budgets);
if (!budgets) throw new Error(`No budgets in ${args.budgets}`);
const current = (await Promise.all(args.current.map(readJson))).filter(Boolean);

// history[0] is the previous run, history[1] the one before, and so on.
const history = [];
if (args.history) {
  for (let index = 1; index <= BASELINE_RUNS + 1; index += 1) {
    const folder = join(args.history, String(index));
    const reports = [await readJson(join(folder, "backend-results.json")), await readJson(join(folder, "screens-results.json"))];
    if (reports.some(Boolean)) history.push(reports.filter(Boolean));
  }
}

/** The same report of an older run, if it measured the same app and tournament. */
function sameReport(run, report) {
  const old = run.find((r) => r.kind === report.kind);
  if (!old) return null;
  const tournamentOf = (r) => (typeof r.tournament === "object" ? r.tournament?.id : r.tournament);
  const sameTarget = old.base === report.base && old.url === report.url;
  return sameTarget && tournamentOf(old) === tournamentOf(report) ? old : null;
}

function median(values) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : Math.round((sorted[middle - 1] + sorted[middle]) / 2);
}

/** What to measure of each kind of report: the lines, how to match a line, its time, its budget. */
const kinds = {
  backend: {
    rule: budgets.backend.regression,
    sameLine: (a, b) => a.name === b.name && a.mode === b.mode,
    time: (row) => row.p50,
    budgetValue: (row) => row.p95,
    budget: (row) => budgets.backend.p95Ms[row.name]?.[row.mode] ?? budgets.backend.p95Ms.default[row.mode],
  },
  screens: {
    rule: budgets.screens.regression,
    sameLine: (a, b) => a.name === b.name && a.profile === b.profile,
    metric: (row) => (row.name.endsWith("(cold)") ? "sinceNavigationMs" : "appMs"),
    time(row) {
      return row[this.metric(row)];
    },
    budgetValue(row) {
      return this.time(row);
    },
    budget(row) {
      const fallback = budgets.screens.budgetMs[this.metric(row) === "appMs" ? "default" : "coldStart"][row.profile];
      return budgets.screens.budgetMs[row.name]?.[row.profile] ?? fallback;
    },
  },
};

/**
 * The problem of a line, or null. `runs` are the older runs, newest first: the median of the first ones is the
 * baseline.
 */
function problemOf(kind, report, row, runs) {
  const k = kinds[kind];
  if (row.errors > 0) return { error: true, text: `${row.errors} errors` };
  const budget = k.budget(row);
  const value = k.budgetValue(row);
  if (value != null && budget != null && value > budget) return { text: `${value} ms, budget ${budget} ms` };
  const baseline = baselineOf(kind, report, row, runs);
  const time = k.time(row);
  if (time != null && baseline != null && time > baseline * k.rule.factor && time - baseline > k.rule.minMs) {
    return { text: `${time} ms, median of the recent runs ${baseline} ms` };
  }
  return null;
}

function baselineOf(kind, report, row, runs) {
  const k = kinds[kind];
  const times = runs
    .slice(0, BASELINE_RUNS)
    .map((run) => sameReport(run, report)?.results.find((old) => k.sameLine(old, row)))
    .filter(Boolean)
    .map((old) => k.time(old))
    .filter((time) => time != null);
  return median(times);
}

const failures = [];
const warnings = [];
const lines = ["# Benchmark", ""];

for (const report of current) {
  const kind = report.kind;
  if (!kinds[kind]) continue;
  const k = kinds[kind];
  const previousReport = history[0] ? sameReport(history[0], report) : null;
  if (kind === "backend") {
    lines.push(`## Backend (${report.base}, ${report.runs} calls each)`, "");
    lines.push(`Tournament: ${report.tournament.name || report.tournament.id}`, "");
    lines.push("| Endpoint | Mode | p50 | p95 | Budget p95 | Recent p50 | Size | |", "|---|---|---:|---:|---:|---:|---:|---|");
  } else {
    lines.push(`## Screens of the player app (${report.url}, median of ${report.samples})`, "");
    lines.push("The screen time counts from the opening of the screen to its content. The navigation time counts from the start of the page, so it includes the download and start of the app.", "");
    lines.push("| Screen | Profile | Screen | Since navigation | Budget | Recent | |", "|---|---|---:|---:|---:|---:|---|");
  }
  for (const row of report.results) {
    const label = kind === "backend" ? `${row.name} (${row.mode})` : `${row.name} on ${row.profile}`;
    const problem = problemOf(kind, report, row, history);
    // The previous run, judged against the runs before it.
    const previousRow = previousReport?.results.find((old) => k.sameLine(old, row));
    const previousProblem = previousRow ? problemOf(kind, previousReport, previousRow, history.slice(1)) : null;
    let mark = "✅";
    if (problem?.error) {
      mark = "❌ errors";
      failures.push(`${label}: ${problem.text}`);
    } else if (problem && previousProblem) {
      mark = "❌ twice in a row";
      failures.push(`${label}: ${problem.text}; the previous run too (${previousProblem.text})`);
    } else if (problem) {
      mark = "⚠️ once";
      warnings.push(`${label}: ${problem.text}`);
    }
    const baseline = baselineOf(kind, report, row, history);
    const recent = baseline != null ? `${baseline} ms` : "–";
    if (kind === "backend") {
      lines.push(`| ${row.name} | ${row.mode} | ${row.p50} ms | ${row.p95} ms | ${k.budget(row)} ms | ${recent} | ${row.kb} kB | ${mark} |`);
    } else {
      lines.push(`| ${row.name} | ${row.profile} | ${row.appMs} ms | ${row.sinceNavigationMs} ms | ${k.budget(row)} ms | ${recent} | ${mark} |`);
    }
  }
  if (kind === "screens" && report.errors > 0) failures.push(`${report.errors} screen samples failed`);
  lines.push("");
}

lines.push(`Recent runs compared: ${Math.min(history.length, BASELINE_RUNS)}.`, "");
if (failures.length > 0) lines.push(`**${failures.length} problem(s):**`, "", ...failures.map((p) => `- ${p}`), "");
if (warnings.length > 0) {
  lines.push(`**${warnings.length} warning(s)**, seen only in this run (it fails if the next run sees them too):`, "");
  lines.push(...warnings.map((p) => `- ${p}`), "");
}
if (failures.length === 0 && warnings.length === 0) lines.push("**No regression.**", "");
const text = lines.join("\n");
console.log(text);
for (const warning of warnings) console.log(`::warning title=Benchmark::${warning}`);
if (args.summary) await appendFile(args.summary, text);
await writeFile("benchmark-report.md", text);
if (failures.length > 0) process.exitCode = 1;
