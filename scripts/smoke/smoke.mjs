// Smoke test of a deployed site: a few quick requests that say the deploy is alive, not that every feature works.
//
//   node scripts/smoke/smoke.mjs                                   # production, expects the versions of main
//   MTS_SITE_URL=https://mts---staging.web.app node scripts/smoke/smoke.mjs
//
// It checks that the Player web and the admin web load with their scripts, that /version.json and /api/health
// give the versions of gradle/libs.versions.toml, that the public list of tournaments answers, and that the Android
// App Links file is served. Hosting and the API can take a moment to serve the new version, so each check tries again
// for up to two minutes. It runs at the end of the deploys (.github/workflows/firebase.yml and staging.yml).
import { readFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

/** The versions that main declares, read from the version catalog. */
export async function expectedVersions(catalog = path.join(ROOT, "gradle/libs.versions.toml")) {
  const text = await readFile(catalog, "utf8");
  const read = (key) => text.match(new RegExp(`^${key}\\s*=\\s*"([^"]+)"`, "m"))?.[1];
  return { web: read("web-versionName"), backend: read("backend-versionName") };
}

async function get(fetchImpl, url, timeoutMs) {
  const response = await fetchImpl(url, { signal: AbortSignal.timeout(timeoutMs), headers: { "Cache-Control": "no-cache" } });
  const body = await response.text();
  return { status: response.status, type: response.headers.get("content-type") ?? "", body };
}

function json(answer, what) {
  try {
    return JSON.parse(answer.body);
  } catch {
    throw new Error(`${what} is not JSON`);
  }
}

/** The scripts of a page that the site serves itself (not those of another host). */
export function ownScripts(html, pageUrl) {
  const origin = new URL(pageUrl).origin;
  return [...html.matchAll(/<script[^>]*\ssrc=["']([^"']+)["']/gi)]
    .map((match) => new URL(match[1], pageUrl))
    .filter((url) => url.origin === origin)
    .map((url) => url.href);
}

/**
 * The checks, each a name and a function that throws when the site fails it. Without [appLinks] there is no check of
 * the App Links file: staging is served at its *.web.app address, where Firebase Hosting answers
 * /.well-known/assetlinks.json itself with the apps of the staging project (none, so `[]`), not with our file.
 */
export function checks(site, expected, { appLinks = true } = {}) {
  const page = (name, address) => ({
    name,
    run: async (request) => {
      const answer = await request(address);
      if (answer.status !== 200) throw new Error(`HTTP ${answer.status}`);
      if (!answer.type.includes("text/html")) throw new Error(`content type ${answer.type}`);
      const scripts = ownScripts(answer.body, address);
      if (scripts.length === 0) throw new Error("the page has no script of the site");
      for (const script of scripts) {
        const loaded = await request(script);
        if (loaded.status !== 200 || loaded.body.length === 0) throw new Error(`its script ${script} gives HTTP ${loaded.status}`);
        if (loaded.type.includes("text/html")) throw new Error(`its script ${script} is an HTML page (missing file)`);
      }
    },
  });
  return [
    page("Player web loads", `${site}/`),
    page("Admin web loads", `${site}/admin/`),
    {
      name: `Web version is ${expected.web}`,
      run: async (request) => {
        const answer = await request(`${site}/version.json`);
        if (answer.status !== 200) throw new Error(`HTTP ${answer.status}`);
        const web = json(answer, "/version.json").web;
        if (web !== expected.web) throw new Error(`the site serves ${web}`);
      },
    },
    {
      name: `API answers with backend ${expected.backend}`,
      run: async (request) => {
        const answer = await request(`${site}/api/health`);
        if (answer.status !== 200) throw new Error(`HTTP ${answer.status}`);
        const health = json(answer, "/api/health");
        if (health.ok !== true) throw new Error("ok is not true");
        if (health.version !== expected.backend) throw new Error(`the API is ${health.version}`);
      },
    },
    {
      name: "Public tournaments list has data",
      run: async (request) => {
        const answer = await request(`${site}/api/public/tournaments`);
        if (answer.status !== 200) throw new Error(`HTTP ${answer.status}`);
        const tournaments = json(answer, "the list").tournaments;
        if (!Array.isArray(tournaments) || tournaments.length === 0) throw new Error("no tournaments");
        if (tournaments.some((tournament) => typeof tournament.id !== "string")) throw new Error("a tournament has no id");
      },
    },
    ...(appLinks ? [{
      name: "Android App Links file is served",
      run: async (request) => {
        const answer = await request(`${site}/.well-known/assetlinks.json`);
        if (answer.status !== 200) throw new Error(`HTTP ${answer.status}`);
        const links = json(answer, "assetlinks.json");
        if (!Array.isArray(links) || links.length === 0) throw new Error(`no app in it: ${answer.body.slice(0, 80)}`);
      },
    }] : []),
  ];
}

/** Runs every check, trying a failed one again until it passes or the time is over. Returns the results. */
export async function smoke({ site, expected, appLinks = true, fetchImpl = fetch, tries = 8, waitMs = 15000, timeoutMs = 20000, log = console.log }) {
  const request = (url) => get(fetchImpl, url, timeoutMs);
  const results = [];
  for (const check of checks(site.replace(/\/$/, ""), expected, { appLinks })) {
    let error = null;
    for (let attempt = 1; attempt <= tries; attempt++) {
      try {
        await check.run(request);
        error = null;
        break;
      } catch (failure) {
        error = failure;
        if (attempt < tries) await new Promise((resolve) => setTimeout(resolve, waitMs));
      }
    }
    results.push({ name: check.name, ok: error == null, error: error?.message });
    log(error == null ? `✅ ${check.name}` : `❌ ${check.name}: ${error.message}`);
  }
  return results;
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const site = process.env.MTS_SITE_URL ?? "https://mts.mahjongmadrid.com";
  const expected = await expectedVersions();
  console.log(`Smoke test of ${site} (web ${expected.web}, backend ${expected.backend})`);
  const results = await smoke({ site, expected, appLinks: process.env.MTS_SMOKE_APP_LINKS !== "off" });
  const failed = results.filter((result) => !result.ok);
  if (process.env.GITHUB_STEP_SUMMARY) {
    const lines = [`### Smoke test of ${site}`, "", ...results.map((r) => `- ${r.ok ? "✅" : "❌"} ${r.name}${r.ok ? "" : `: ${r.error}`}`)];
    await import("node:fs/promises").then((fs) => fs.appendFile(process.env.GITHUB_STEP_SUMMARY, lines.join("\n") + "\n"));
  }
  for (const result of failed) console.log(`::error::Smoke test: ${result.name}: ${result.error}`);
  process.exit(failed.length === 0 ? 0 : 1);
}
