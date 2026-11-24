#!/usr/bin/env node
// Measures how long the screens of the player web app take to show their content, as a guest.
// The app itself measures each load (see ReportScreenLoading in the ui module) and keeps it in window.mtsScreenLoads.
// This script opens the screens in Chromium, on a desktop and on a throttled phone, and reads those times.
//
//   npm install --no-save playwright@1.63.0 && npx playwright install chromium
//   node scripts/benchmark/screens.mjs [--url https://mts.mahjongmadrid.com] [--samples 3] [--out screens-results.json]
//
// The page gets a stub of telemetry.js, so the benchmark sends nothing to Analytics and needs no consent.
// See docs/benchmark.md.
import { writeFile } from "node:fs/promises";
import { parseArgs } from "node:util";
import { chromium, devices } from "playwright";

const { values: args } = parseArgs({
  options: {
    url: { type: "string", default: process.env.BENCH_PLAYER_URL ?? "https://mts.mahjongmadrid.com" },
    api: { type: "string", default: process.env.BENCH_API_BASE ?? "" },
    samples: { type: "string", default: "3" },
    out: { type: "string", default: "screens-results.json" },
    tournament: { type: "string" },
    timeout: { type: "string", default: "60000" },
  },
});
const appUrl = args.url.replace(/\/$/, "");
const apiBase = (args.api || `${appUrl}/api`).replace(/\/$/, "");
const samples = Math.max(1, Number(args.samples));
const timeout = Number(args.timeout);

const TELEMETRY_STUB = `
  window.mtsTelemetry = function (kind, name, json) {
    (window.mtsTelemetryLog = window.mtsTelemetryLog || []).push({ kind: kind, name: name, json: json, at: performance.now() });
  };
  window.mtsTelemetryConfigured = function () { return false; };
  window.mtsTelemetryHasConsent = function () { return false; };
`;

/** The profiles. The phone has the CPU four times slower and a fast 4G network, like a mid-range phone at a venue. */
const profiles = [
  { name: "desktop", context: { viewport: { width: 1280, height: 800 } }, cpu: 1, network: null },
  {
    name: "phone",
    context: { ...devices["Pixel 7"] },
    cpu: 4,
    network: { offline: false, latency: 150, downloadThroughput: (9 * 1024 * 1024) / 8, uploadThroughput: (1.5 * 1024 * 1024) / 8 },
  },
];

async function pickTournament() {
  if (args.tournament) return args.tournament;
  const response = await fetch(`${apiBase}/public/tournaments`, { headers: { accept: "application/json" } });
  const { tournaments = [] } = await response.json();
  const ranked = [...tournaments].sort((a, b) =>
    Number(b.isCompleted === true) - Number(a.isCompleted === true) || (b.numPlayers ?? 0) - (a.numPlayers ?? 0));
  if (ranked.length === 0) throw new Error("No public tournament to measure");
  return ranked[0].id;
}

async function newPage(browser, profile) {
  const context = await browser.newContext(profile.context);
  const page = await context.newPage();
  const cdp = await context.newCDPSession(page);
  // The stub goes in through the Fetch domain of Chrome and only for telemetry.js. A route of Playwright would turn off
  // the HTTP cache of the whole context, and then a "warm" visit would download every file of the app again.
  await cdp.send("Fetch.enable", { patterns: [{ urlPattern: "*telemetry.js*" }] });
  cdp.on("Fetch.requestPaused", (event) => {
    cdp.send("Fetch.fulfillRequest", {
      requestId: event.requestId,
      responseCode: 200,
      responseHeaders: [{ name: "Content-Type", value: "application/javascript" }],
      body: Buffer.from(TELEMETRY_STUB).toString("base64"),
    }).catch(() => {});
  });
  if (profile.cpu > 1) await cdp.send("Emulation.setCPUThrottlingRate", { rate: profile.cpu });
  if (profile.network) {
    await cdp.send("Network.enable");
    await cdp.send("Network.emulateNetworkConditions", profile.network);
  }
  page.on("pageerror", (error) => console.warn(`  page error: ${error.message}`));
  return { context, page };
}

/** Waits for the load of [screen] (and [tab], if given) and gives the times of the app and of the page. */
async function waitForLoad(page, screen, tab) {
  const handle = await page.waitForFunction(
    ([s, t]) => (window.mtsScreenLoads || []).find((load) => load.screen === s && (!t || load.tab === t)),
    [screen, tab ?? null],
    { timeout, polling: 50 },
  );
  const load = await handle.jsonValue();
  return { appMs: Math.round(load.ms), sinceNavigationMs: Math.round(load.at), kind: load.kind, tab: load.tab };
}

/** Sets a saved value of the open screen and reloads, so the app opens that tab like after a page refresh. */
async function reloadWith(page, suffix, value, isText) {
  await page.evaluate(([name, v, text]) => {
    const key = text ? "playerApp.texts" : "playerApp.scrollPositions";
    const lines = (sessionStorage.getItem(key) || "").split("\n").filter((line) => line.length > 0);
    const index = lines.findIndex((line) => (text ? line.split("\t")[0] : line.split(",").slice(2).join(",")).endsWith(name));
    if (index < 0) throw new Error(`No saved value ends with ${name}. Saved: ${lines.join(" | ")}`);
    if (text) lines[index] = `${lines[index].split("\t")[0]}\t+${v}`;
    else lines[index] = `${v},0,${lines[index].split(",").slice(2).join(",")}`;
    sessionStorage.setItem(key, lines.join("\n"));
  }, [suffix, value, isText]);
  await page.reload({ waitUntil: "commit" });
}

async function measureProfile(browser, profile, tournamentId) {
  const rows = [];
  for (let sample = 0; sample < samples; sample += 1) {
    // A new context has an empty cache: the first visit of a new user.
    const { context, page } = await newPage(browser, profile);
    try {
      await page.goto(appUrl + "/", { waitUntil: "commit" });
      const first = await waitForLoad(page, "Tournaments");
      rows.push({ screen: "Tournaments", cache: "cold", ...first });

      // The same browser comes back: the files of the app are in the cache.
      await page.reload({ waitUntil: "commit" });
      rows.push({ screen: "Tournaments", cache: "warm", ...(await waitForLoad(page, "Tournaments")) });

      // The tabs of the start: In play (only while a tournament is in play), All and Statistics (StartTab).
      await reloadWith(page, "/value:tournaments-tab-v2", 2, false);
      rows.push({ screen: "Tournaments", cache: "warm", ...(await waitForLoad(page, "Tournaments")), view: "General statistics" });

      await page.goto(`${appUrl}/t/${encodeURIComponent(tournamentId)}`, { waitUntil: "commit" });
      rows.push({ screen: "Tournament", cache: "warm", ...(await waitForLoad(page, "Tournament")), view: "Tables" });
      // The tabs of a tournament by their ids (MainTab): Ranking, Players, Statistics and Info.
      for (const [tab, view] of [[1, "Ranking"], [2, "Players"], [3, "Statistics"], [4, "Info"]]) {
        await reloadWith(page, "/value:tournamentTab", tab, false);
        rows.push({ screen: "Tournament", cache: "warm", ...(await waitForLoad(page, "Tournament")), view });
      }
    } catch (error) {
      console.warn(`  ${profile.name} sample ${sample + 1} stopped: ${error.message}`);
      rows.push({ screen: "error", error: error.message });
    } finally {
      await context.close();
    }
  }
  return rows;
}

function median(values) {
  const sorted = [...values].sort((a, b) => a - b);
  return sorted.length === 0 ? null : sorted[Math.floor((sorted.length - 1) / 2)];
}

/**
 * The API runs in a Cloud Function that sleeps after a while without requests, and its first answer then takes a few
 * seconds. The benchmark runs at night, when it always sleeps, so it is woken first and the time is kept apart.
 */
async function wakeApi() {
  const started = Date.now();
  await fetch(`${apiBase}/health`).catch(() => null);
  return Date.now() - started;
}

const apiWakeMs = await wakeApi();
console.log(`API woken in ${apiWakeMs} ms`);
const tournamentId = await pickTournament();
const browser = await chromium.launch(process.env.BENCH_CHROMIUM ? { executablePath: process.env.BENCH_CHROMIUM } : {});
const results = [];
let errors = 0;
for (const profile of profiles) {
  console.log(`Profile ${profile.name}`);
  const rows = await measureProfile(browser, profile, tournamentId);
  errors += rows.filter((row) => row.screen === "error").length;
  const groups = new Map();
  for (const row of rows.filter((r) => r.screen !== "error")) {
    const name = `${row.screen}${row.view ? ` › ${row.view}` : ""} (${row.cache})`;
    groups.set(name, [...(groups.get(name) ?? []), row]);
  }
  for (const [name, group] of groups) {
    const result = {
      name,
      profile: profile.name,
      appMs: median(group.map((row) => row.appMs)),
      sinceNavigationMs: median(group.map((row) => row.sinceNavigationMs)),
      samples: group.length,
    };
    results.push(result);
    console.log(`  ${name.padEnd(48)} screen ${String(result.appMs).padStart(6)} ms   since navigation ${String(result.sinceNavigationMs).padStart(6)} ms`);
  }
}
await browser.close();

await writeFile(args.out, JSON.stringify({ kind: "screens", at: new Date().toISOString(), url: appUrl, tournament: tournamentId, samples, errors, apiWakeMs, results }, null, 2));
console.log(`\nSaved ${args.out}`);
if (errors > 0) process.exitCode = 1;
