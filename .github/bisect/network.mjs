// TEMPORARY (bisect of the web slowdown): what the browser downloads to open the start screen, cold and warm.
// node network.mjs <url> [label]
import { chromium } from "playwright";

const [url, label = url] = process.argv.slice(2);
const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1280, height: 800 } });
await context.route("**/telemetry.js", (route) => route.fulfill({ contentType: "application/javascript", body: "window.mtsTelemetry=function(){};window.mtsTelemetryConfigured=function(){return false};window.mtsTelemetryHasConsent=function(){return false};" }));
const page = await context.newPage();
const cdp = await context.newCDPSession(page);
await cdp.send("Network.enable");
let rows = [];
const started = new Map();
cdp.on("Network.requestWillBeSent", (e) => started.set(e.requestId, { url: e.request.url, t0: e.timestamp }));
cdp.on("Network.responseReceived", (e) => { const r = started.get(e.requestId); if (r) { r.status = e.response.status; r.fromCache = e.response.fromDiskCache || e.response.fromMemoryCache; r.cc = e.response.headers["cache-control"] ?? e.response.headers["Cache-Control"]; r.xcache = e.response.headers["x-cache"]; r.timing = e.response.timing; } });
cdp.on("Network.loadingFinished", (e) => { const r = started.get(e.requestId); if (r) { r.t1 = e.timestamp; r.bytes = e.encodedDataLength; rows.push(r); } });

async function load(kind) {
  rows = []; started.clear();
  const t = Date.now();
  await page.goto(url, { waitUntil: "load" });
  await page.waitForFunction(() => (window.mtsScreenLoads || []).length > 0, null, { timeout: 60000 }).catch(() => {});
  const ms = Date.now() - t;
  await page.waitForTimeout(1500);
  const kinds = {};
  for (const r of rows) {
    const path = new URL(r.url).pathname;
    const k = path.startsWith("/api/") ? "api" : path.includes("composeResources") ? "composeResources" : path.split(".").pop();
    const g = (kinds[k] ??= { n: 0, bytes: 0, ms: 0, s304: 0, cache: 0, maxMs: 0 });
    const d = Math.round(((r.t1 ?? r.t0) - r.t0) * 1000);
    g.n++; g.bytes += r.bytes ?? 0; g.ms += d; g.maxMs = Math.max(g.maxMs, d);
    if (r.status === 304) g.s304++;
    if (r.fromCache) g.cache++;
  }
  console.log(`\n### ${label} ${kind}: first screen ${ms} ms, ${rows.length} requests`);
  console.log("| kind | n | KB | sum ms | max ms | 304 | from cache |\n|---|---:|---:|---:|---:|---:|---:|");
  for (const [k, g] of Object.entries(kinds).sort((a, b) => b[1].ms - a[1].ms)) console.log(`| ${k} | ${g.n} | ${Math.round(g.bytes / 1024)} | ${g.ms} | ${g.maxMs} | ${g.s304} | ${g.cache} |`);
  const slow = [...rows].map((r) => ({ p: new URL(r.url).pathname, d: Math.round(((r.t1 ?? r.t0) - r.t0) * 1000), s: r.status, cc: r.cc, x: r.xcache })).sort((a, b) => b.d - a.d).slice(0, 12);
  console.log("slowest:"); for (const s of slow) console.log(`  ${s.d} ms ${s.s} ${s.p} cc=${s.cc} x-cache=${s.x}`);
}
await load("cold");
await load("warm");
await load("warm2");
await browser.close();
