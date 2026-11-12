#!/usr/bin/env node
// Checks the results of the benchmark against the budgets and against the previous run, and writes a report.
//
//   node scripts/benchmark/compare.mjs --current backend-results.json --current screens-results.json \
//     [--previous previous/backend-results.json --previous previous/screens-results.json] [--summary report.md]
//
// It fails (exit code 1) when a time is over its budget, or clearly slower than in the previous run.
// The budgets are in benchmarks/budgets.json. See docs/benchmark.md.
import { appendFile, readFile, writeFile } from "node:fs/promises";
import { parseArgs } from "node:util";

const { values: args } = parseArgs({
  options: {
    current: { type: "string", multiple: true, default: [] },
    previous: { type: "string", multiple: true, default: [] },
    budgets: { type: "string", default: new URL("../../benchmarks/budgets.json", import.meta.url).pathname },
    summary: { type: "string", default: process.env.GITHUB_STEP_SUMMARY ?? "" },
  },
});

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
const previous = (await Promise.all(args.previous.map(readJson))).filter(Boolean);
const previousOf = (kind) => previous.find((report) => report.kind === kind);

const problems = [];
const lines = ["# Benchmark", ""];

function verdict(value, budget, before, rule) {
  if (value == null) return "–";
  const over = budget != null && value > budget;
  const slower = before != null && value > before * rule.factor && value - before > rule.minMs;
  return over ? "❌ over budget" : slower ? "⚠️ slower" : "✅";
}

for (const report of current) {
  const before = previousOf(report.kind);
  if (report.kind === "backend") {
    const rule = budgets.backend.regression;
    lines.push(`## Backend (${report.base}, ${report.runs} calls each)`, "");
    lines.push(`Tournament: ${report.tournament.name || report.tournament.id}`, "");
    lines.push("| Endpoint | Mode | p50 | p95 | Budget p95 | Before p50 | Size | |", "|---|---|---:|---:|---:|---:|---:|---|");
    for (const row of report.results) {
      const budget = budgets.backend.p95Ms[row.name]?.[row.mode] ?? budgets.backend.p95Ms.default[row.mode];
      const old = before?.results.find((r) => r.name === row.name && r.mode === row.mode);
      const over = row.p95 > budget;
      const slower = old != null && row.p50 > old.p50 * rule.factor && row.p50 - old.p50 > rule.minMs;
      const mark = row.errors > 0 ? "❌ errors" : over ? "❌ over budget" : slower ? "⚠️ slower" : "✅";
      if (mark.startsWith("❌")) problems.push(`${row.name} (${row.mode}): p95 ${row.p95} ms, budget ${budget} ms${row.errors ? `, ${row.errors} errors` : ""}`);
      else if (mark.startsWith("⚠️")) problems.push(`${row.name} (${row.mode}): p50 ${row.p50} ms, before ${old.p50} ms`);
      lines.push(`| ${row.name} | ${row.mode} | ${row.p50} ms | ${row.p95} ms | ${budget} ms | ${old ? `${old.p50} ms` : "–"} | ${row.kb} kB | ${mark} |`);
    }
    lines.push("");
  }
  if (report.kind === "screens") {
    const rule = budgets.screens.regression;
    lines.push(`## Screens of the player app (${report.url}, median of ${report.samples})`, "");
    lines.push("The screen time counts from the opening of the screen to its content. The navigation time counts from the start of the page, so it includes the download and start of the app.", "");
    lines.push("| Screen | Profile | Screen | Since navigation | Budget | Before | |", "|---|---|---:|---:|---:|---:|---|");
    for (const row of report.results) {
      const metric = row.name.endsWith("(cold)") ? "sinceNavigationMs" : "appMs";
      const budget = budgets.screens.budgetMs[row.name]?.[row.profile] ?? budgets.screens.budgetMs[metric === "appMs" ? "default" : "coldStart"][row.profile];
      const value = row[metric];
      const old = before?.results.find((r) => r.name === row.name && r.profile === row.profile)?.[metric];
      const mark = verdict(value, budget, old, rule);
      if (mark.startsWith("❌")) problems.push(`${row.name} on ${row.profile}: ${value} ms, budget ${budget} ms`);
      else if (mark.startsWith("⚠️")) problems.push(`${row.name} on ${row.profile}: ${value} ms, before ${old} ms`);
      lines.push(`| ${row.name} | ${row.profile} | ${row.appMs} ms | ${row.sinceNavigationMs} ms | ${budget} ms | ${old != null ? `${old} ms` : "–"} | ${mark} |`);
    }
    if (report.errors > 0) problems.push(`${report.errors} screen samples failed`);
    lines.push("");
  }
}

lines.push(problems.length === 0 ? "**No regression.**" : `**${problems.length} problem(s):**\n\n${problems.map((p) => `- ${p}`).join("\n")}`);
const text = lines.join("\n") + "\n";
console.log(text);
if (args.summary) await appendFile(args.summary, text);
await writeFile("benchmark-report.md", text);
if (problems.length > 0) process.exitCode = 1;
