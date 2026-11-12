#!/usr/bin/env node
// Measures the response times of the public API, the same reads that the player app makes.
// Every endpoint runs twice: "cdn" as the apps call it (Firebase Hosting can answer from its cache), and "origin"
// with a unique query parameter, so the cache misses and the Cloud Function answers.
//
//   node scripts/benchmark/backend.mjs [--base https://mtsplayer.mahjongmadrid.com/api] [--runs 15] [--out results.json]
//
// It needs no packages: Node 22 has fetch. See docs/benchmark.md.
import { writeFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";
import { parseArgs } from "node:util";

const { values: args } = parseArgs({
  options: {
    base: { type: "string", default: process.env.BENCH_API_BASE ?? "https://mtsplayer.mahjongmadrid.com/api" },
    runs: { type: "string", default: "15" },
    out: { type: "string", default: "backend-results.json" },
    tournament: { type: "string" },
  },
});
const base = args.base.replace(/\/$/, "");
const runs = Math.max(3, Number(args.runs));
const headers = { "accept": "application/json", "accept-encoding": "gzip", "user-agent": "mts-benchmark" };
// With App Check in "enforce" mode, a debug token lets the benchmark through.
if (process.env.BENCH_APP_CHECK_TOKEN) headers["x-firebase-appcheck"] = process.env.BENCH_APP_CHECK_TOKEN;

async function getJson(path) {
  const response = await fetch(base + path, { headers });
  if (!response.ok) throw new Error(`${path}: HTTP ${response.status}`);
  return response.json();
}

/** The tournament with the most data: the finished one with the most players, or the one given with --tournament. */
async function pickTournament() {
  const { tournaments = [] } = await getJson("/public/tournaments");
  if (tournaments.length === 0) throw new Error("No public tournament to measure");
  if (args.tournament) return tournaments.find((t) => t.id === args.tournament) ?? { id: args.tournament };
  const ranked = [...tournaments].sort((a, b) =>
    Number(b.isCompleted === true) - Number(a.isCompleted === true) || (b.numPlayers ?? 0) - (a.numPlayers ?? 0));
  return ranked[0];
}

async function pickPerson(tournamentId) {
  const { players = [] } = await getJson(`/public/tournaments/${tournamentId}/players`);
  return players.map((p) => p.assignedEmaId).find((id) => typeof id === "string" && id.length > 0) ?? null;
}

function percentile(sorted, p) {
  if (sorted.length === 0) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, index)];
}

async function measure(path, mode) {
  const times = [];
  let bytes = 0;
  let cache = "";
  let errors = 0;
  // One call first, so a cold start of the function does not count in the times. Cold starts are measured apart.
  await fetch(base + path + (mode === "origin" ? `${path.includes("?") ? "&" : "?"}bench=warm${Date.now()}` : ""), { headers }).catch(() => null);
  for (let i = 0; i < runs; i += 1) {
    const url = base + path + (mode === "origin" ? `${path.includes("?") ? "&" : "?"}bench=${Date.now()}-${i}` : "");
    const start = performance.now();
    try {
      const response = await fetch(url, { headers });
      const body = await response.arrayBuffer();
      const ms = performance.now() - start;
      if (!response.ok) {
        errors += 1;
        continue;
      }
      times.push(ms);
      bytes = body.byteLength;
      cache = response.headers.get("x-cache") ?? response.headers.get("cache-control") ?? "";
    } catch {
      errors += 1;
    }
  }
  const sorted = [...times].sort((a, b) => a - b);
  const round = (value) => Math.round(value);
  return {
    p50: round(percentile(sorted, 50)),
    p95: round(percentile(sorted, 95)),
    max: round(sorted.at(-1) ?? 0),
    runs: times.length,
    errors,
    kb: Math.round(bytes / 102.4) / 10,
    cache,
  };
}

const tournament = await pickTournament();
const id = encodeURIComponent(tournament.id);
const emaId = await pickPerson(tournament.id);
const endpoints = [
  ["config", "/config"],
  ["sync manifest", "/public/sync/manifest"],
  ["tournament list", "/public/tournaments"],
  ["countries", "/public/countries"],
  ["EMA registry", "/public/players"],
  ["tournament", `/public/tournaments/${id}`],
  ["tournament manifest", `/public/tournaments/${id}/sync/manifest`],
  ["tournament players", `/public/tournaments/${id}/players`],
  ["tournament rounds", `/public/tournaments/${id}/rounds`],
  ["tournament tables", `/public/tournaments/${id}/tables`],
  ["tables with hands (all)", `/public/tournaments/${id}/tables-with-hands`],
  ["tables with hands (round 1)", `/public/tournaments/${id}/rounds/1/tables-with-hands`],
  ["statistics overview", "/public/statistics/overview"],
];
if (tournament.isTeams) endpoints.push(["tournament teams", `/public/tournaments/${id}/teams`]);
if (emaId) {
  endpoints.push(["person statistics", `/public/statistics/persons/${encodeURIComponent(emaId)}`]);
  endpoints.push(["person standings", `/public/statistics/persons/${encodeURIComponent(emaId)}/standings?tournaments=${id}`]);
}

const results = [];
for (const [name, path] of endpoints) {
  for (const mode of ["cdn", "origin"]) {
    const result = await measure(path, mode);
    results.push({ name, mode, path: path.replace(id, ":tournament").replace(emaId ?? "\u0000", ":emaId"), ...result });
    console.log(`${name.padEnd(30)} ${mode.padEnd(6)} p50 ${String(result.p50).padStart(5)} ms  p95 ${String(result.p95).padStart(5)} ms  ${result.kb} kB${result.errors ? `  errors ${result.errors}` : ""}`);
  }
}

const report = {
  kind: "backend",
  at: new Date().toISOString(),
  base,
  runs,
  tournament: { id: tournament.id, name: tournament.name ?? "", players: tournament.numPlayers ?? null },
  results,
};
await writeFile(args.out, JSON.stringify(report, null, 2));
console.log(`\nSaved ${args.out}`);
