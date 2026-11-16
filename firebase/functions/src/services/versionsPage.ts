/**
 * The page /version: for each product, the latest version (on main when this backend was built) next to the
 * published one. The web comes from /version.json of the site, the backend is this one, and the Android app is read
 * from Google Play. The same table as `python3 scripts/versions.py status`. See docs/versioning.md.
 */
import { builtVersions } from "../backendVersion";

export const ANDROID_PACKAGE = "com.etologic.mahjongtournamentsuite";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const PLAY_API = `https://androidpublisher.googleapis.com/androidpublisher/v3/applications/${ANDROID_PACKAGE}`;
/** Google Play is asked again after this long, so a reload of the page does not call it each time. */
const PLAY_CACHE_MS = 5 * 60_000;

export const TRACK_LABELS: Record<string, string> = {
  internal: "Internal testing",
  alpha: "Closed testing",
  beta: "Open testing",
  production: "Production",
};

export interface PlayRelease {
  track: string;
  name: string;
  versionCodes: string[];
  status: string;
}

export interface PlayAnswer {
  releases?: PlayRelease[];
  error?: string;
}

export interface VersionRow {
  product: string;
  latest: string;
  published: Array<{ text: string; upToDate: boolean }>;
}

function siteUrl(): string {
  return (process.env.PLAYER_APP_URL ?? "https://mts.mahjongmadrid.com").replace(/\/$/, "");
}

async function publishedWeb(): Promise<string> {
  try {
    const response = await fetch(`${siteUrl()}/version.json`, {
      headers: { "Cache-Control": "no-cache" },
      signal: AbortSignal.timeout(5_000),
    });
    if (!response.ok) {
      return `unknown (HTTP ${response.status})`;
    }
    return String(((await response.json()) as { web?: string }).web ?? "unknown");
  } catch (error) {
    return `unknown (${(error as Error).message})`;
  }
}

/** The releases on each track of the Android app. Reading them needs an edit, which is deleted at once. */
async function readPlay(): Promise<PlayAnswer> {
  const { GoogleAuth } = require("google-auth-library") as typeof import("google-auth-library");
  const auth = new GoogleAuth({ scopes: [PLAY_SCOPE] });
  let account = "the service account of the functions";
  try {
    account = (await auth.getCredentials()).client_email ?? account;
    const client = await auth.getClient();
    const edit = await client.request<{ id: string }>({ url: `${PLAY_API}/edits`, method: "POST", data: {} });
    try {
      const listed = await client.request<{ tracks?: Array<{ track: string; releases?: Array<Partial<PlayRelease>> }> }>({
        url: `${PLAY_API}/edits/${edit.data.id}/tracks`,
      });
      const releases = (listed.data.tracks ?? []).flatMap((track) =>
        (track.releases ?? []).map((release) => ({
          track: track.track,
          name: release.name ?? "",
          versionCodes: release.versionCodes ?? [],
          status: release.status ?? "",
        })),
      );
      return { releases };
    } finally {
      await client.request({ url: `${PLAY_API}/edits/${edit.data.id}`, method: "DELETE" }).catch(() => undefined);
    }
  } catch (error) {
    const status = (error as { status?: number; response?: { status?: number } }).response?.status;
    if (status === 401 || status === 403) {
      return { error: `no access to Google Play. Invite ${account} in Play Console (Users and permissions) with "View app information".` };
    }
    return { error: `could not read Google Play (${(error as Error).message})` };
  }
}

let playCache: { at: number; answer: PlayAnswer } | undefined;

async function publishedAndroid(now = Date.now()): Promise<PlayAnswer> {
  if (!playCache || now - playCache.at > PLAY_CACHE_MS) {
    playCache = { at: now, answer: await readPlay() };
  }
  return playCache.answer;
}

/** The rows of the table. Pure, so the tests give it the answers. */
export function versionRows(
  built: { version: string; web?: string; android?: string; androidCode?: string },
  web: string,
  play: PlayAnswer,
): VersionRow[] {
  const order = Object.keys(TRACK_LABELS);
  const rank = (track: string) => (order.includes(track) ? order.indexOf(track) : order.length);
  const releases = [...(play.releases ?? [])].sort((a, b) => rank(a.track) - rank(b.track));
  const android = play.error
    ? [{ text: play.error, upToDate: false }]
    : releases.length === 0
      ? [{ text: "nothing on Google Play", upToDate: false }]
      : releases.map((release) => ({
          text:
            `${TRACK_LABELS[release.track] ?? release.track}: ${release.name || release.versionCodes.join(", ")}` +
            (release.status === "completed" ? "" : ` [${release.status}]`),
          upToDate: release.status === "completed" && !!built.androidCode && release.versionCodes.includes(built.androidCode),
        }));
  return [
    { product: "Web", latest: built.web ?? "?", published: [{ text: web, upToDate: web === built.web }] },
    // The API that answers this page is the published backend.
    { product: "Backend", latest: built.version, published: [{ text: built.version, upToDate: true }] },
    {
      product: "Android app",
      latest: built.android ? `${built.android} (${built.androidCode})` : "?",
      published: android,
    },
  ];
}

function escape(text: string): string {
  return text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);
}

export function renderVersionsPage(rows: VersionRow[]): string {
  const body = rows
    .map((row) => {
      const published = row.published
        .map((line) => `<div class="${line.upToDate ? "ok" : "behind"}">${escape(line.text)}${line.upToDate ? " ✓" : ""}</div>`)
        .join("");
      return `<tr><th scope="row">${escape(row.product)}</th><td>${escape(row.latest)}</td><td>${published}</td></tr>`;
    })
    .join("\n");
  return `<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex"><title>MTS versions</title>
<style>
:root{color-scheme:light dark;--fg:#1b1b1b;--bg:#fff;--muted:#666;--ok:#1b7f3b;--line:#ddd}
@media (prefers-color-scheme:dark){:root{--fg:#eee;--bg:#121212;--muted:#aaa;--ok:#5fd38a;--line:#333}}
body{font:16px/1.4 system-ui,sans-serif;color:var(--fg);background:var(--bg);margin:0;padding:24px 16px}
h1{font-size:20px;margin:0 0 16px}table{border-collapse:collapse;width:100%;max-width:720px}
th,td{text-align:left;vertical-align:top;padding:10px 12px 10px 0;border-bottom:1px solid var(--line)}
thead th{color:var(--muted);font-weight:500;font-size:14px}.ok{color:var(--ok)}
</style></head><body>
<h1>Mahjong Tournament Suite versions</h1>
<table><thead><tr><th>Product</th><th>Latest (main)</th><th>Published</th></tr></thead><tbody>
${body}
</tbody></table>
</body></html>`;
}

export async function versionsPage(): Promise<string> {
  const [web, play] = await Promise.all([publishedWeb(), publishedAndroid()]);
  return renderVersionsPage(versionRows(builtVersions(), web, play));
}
