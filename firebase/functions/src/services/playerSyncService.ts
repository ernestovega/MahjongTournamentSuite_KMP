import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { createHash, randomUUID } from "node:crypto";
import { readFile } from "node:fs/promises";
import { join } from "node:path";

import { db, storage } from "../firebase";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { decodePlayerNameEntities, normalizePlayerName } from "./playerName";

const baseUrl = `${process.env.EMA_SOURCE_BASE_URL ?? "https://mahjong-europe.org"}/ranking/`;
const EMA_RANKING_TOURNAMENT_RESULT_INDEX = "emaRankingTournamentResultIndex";

export type SyncChange = { emaId: string; name: string; country: string };
export type EmaPlayerRegistrySyncReport = {
  id: string;
  mode: "seed" | "incremental";
  startedAt: string;
  completedAt: string;
  additions: SyncChange[];
  updates: SyncChange[];
  noLongerRanked: SyncChange[];
  sourceUrls: string[];
};

type EmaPlayer = { emaId: string; name: string; country: string; sourceUrl: string; photoSourceUrl: string | null };

async function mapWithConcurrency<T, R>(items: T[], worker: (item: T, index: number) => Promise<R>, concurrency = 6): Promise<R[]> {
  const results = new Array<R>(items.length);
  let next = 0;
  async function consume(): Promise<void> {
    while (true) {
      const index = next++;
      if (index >= items.length) return;
      results[index] = await worker(items[index], index);
    }
  }
  await Promise.all(Array.from({ length: Math.min(concurrency, items.length) }, consume));
  return results;
}

/** EMA number is the registry primary key across MCR, Riichi, and tournament results. */
function uniqueByEmaId(players: Iterable<EmaPlayer>): EmaPlayer[] {
  return [...new Map([...players].map((player) => [player.emaId, player])).values()];
}

async function syncPhoto(player: EmaPlayer, existing: FirebaseFirestore.DocumentData | undefined): Promise<{ url: string | null; etag: string | null }> {
  if (!player.photoSourceUrl) return { url: null, etag: null };
  const headers: Record<string, string> = { "User-Agent": "MahjongTournamentSuite player sync" };
  const priorEtag = existing?.photoSourceEtag as string | undefined;
  if (priorEtag) headers["If-None-Match"] = priorEtag;
  const localSourceDir = process.env.EMA_LOCAL_SOURCE_DIR?.trim();
  let bytes: Buffer;
  let contentType = "image/jpeg";
  let etag: string | null;
  if (localSourceDir) {
    bytes = await readLocalBytes(localSourceDir, player.photoSourceUrl);
    etag = `"${createHash("sha256").update(bytes).digest("hex")}"`;
    if (priorEtag === etag) return { url: typeof existing?.photoUrl === "string" ? existing.photoUrl : null, etag };
  } else {
    const response = await fetch(player.photoSourceUrl, { headers });
    if (response.status === 304) return { url: typeof existing?.photoUrl === "string" ? existing.photoUrl : null, etag: priorEtag ?? null };
    if (!response.ok) throw new Error(`EMA photo request failed (${response.status}): ${player.emaId}`);
    contentType = response.headers.get("content-type") ?? "image/jpeg";
    if (!contentType.startsWith("image/")) throw new Error(`EMA photo is not an image: ${player.emaId}`);
    bytes = Buffer.from(await response.arrayBuffer());
    etag = response.headers.get("etag");
  }
  const file = storage.file(`playerPhotos/${player.emaId}.jpg`);
  const token = randomUUID();
  await file.save(bytes, {
    contentType,
    metadata: {
      cacheControl: "public, max-age=604800",
      metadata: {
        sourceUrl: player.photoSourceUrl,
        sourceEtag: etag ?? "",
        firebaseStorageDownloadTokens: token,
      },
    },
  });
  const objectName = encodeURIComponent(file.name);
  return { url: `https://firebasestorage.googleapis.com/v0/b/${storage.name}/o/${objectName}?alt=media&token=${token}`, etag };
}

async function getHtml(url: string): Promise<string> {
  const localSourceDir = process.env.EMA_LOCAL_SOURCE_DIR?.trim();
  if (localSourceDir) return readLocalText(localSourceDir, url);
  const response = await fetch(url, { headers: { "User-Agent": "MahjongTournamentSuite player sync" } });
  if (!response.ok) throw new Error(`EMA request failed (${response.status}): ${url}`);
  return response.text();
}

function localSourceCandidates(sourceDir: string, url: string): string[] {
  const pathname = new URL(url).pathname;
  return [
    join(sourceDir, pathname),
    join(sourceDir, "mahjong-europe.org", pathname),
  ];
}

async function readLocalBytes(sourceDir: string, url: string): Promise<Buffer> {
  const candidates = localSourceCandidates(sourceDir, url);
  let lastError: unknown;
  for (const candidate of candidates) {
    try {
      return await readFile(candidate);
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

async function readLocalText(sourceDir: string, url: string): Promise<string> {
  const candidates = localSourceCandidates(sourceDir, url);
  let lastError: unknown;
  for (const candidate of candidates) {
    try {
      return await readFile(candidate, "utf8");
    } catch (error) {
      lastError = error;
    }
  }
  throw lastError;
}

function text(value: string): string {
  return decodePlayerNameEntities(value.replace(/<[^>]*>/g, " ")).replace(/\s+/g, " ").trim();
}

/** Lists all known tournament result pages through EMA's all-years index. */
async function getTournamentResultPaths(): Promise<Set<string>> {
  const index = await getHtml(baseUrl + "Tournament/Tournaments_all.html");
  const yearPaths = new Set([...index.matchAll(/href="(Tournaments_[0-9]{4}\.html)"/gi)].map((match) => match[1]));
  const pages = await Promise.all([...yearPaths].map((path) => getHtml(baseUrl + "Tournament/" + path)));
  const tournamentPaths = new Set<string>();
  for (const page of [index, ...pages]) {
    for (const match of page.matchAll(/href="(TR(?:_RCR)?_[0-9]+\.html)"/gi)) tournamentPaths.add(match[1]);
  }
  return tournamentPaths;
}

async function markTournamentPaths(paths: Iterable<string>): Promise<void> {
  let batch = db.batch();
  let count = 0;
  for (const path of paths) {
    batch.set(db.collection(EMA_RANKING_TOURNAMENT_RESULT_INDEX).doc(path), { firstSeenAt: FieldValue.serverTimestamp(), sourceUrl: baseUrl + "Tournament/" + path });
    count += 1;
    if (count === 500) {
      await batch.commit();
      batch = db.batch();
      count = 0;
    }
  }
  if (count > 0) await batch.commit();
}

async function getPlayersFromTournamentPaths(tournamentPaths: Iterable<string>): Promise<EmaPlayer[]> {
  const ids = new Set<string>();
  for (const path of tournamentPaths) {
    const html = await getHtml(baseUrl + "Tournament/" + path);
    for (const match of html.matchAll(/Players\/([0-9]+)\.html/gi)) ids.add(match[1]);
  }
  console.log(`EMA tournament pages found ${ids.size} unique players.`);
  const players = await mapWithConcurrency([...ids], async (emaId, index) => {
    if ((index + 1) % 50 === 0) console.log(`Read ${index + 1}/${ids.size} tournament player pages.`);
    return getPlayer(emaId);
  });
  return players.filter((player): player is EmaPlayer => player != null);
}

/** Initial-only crawl. Tournament result pages link all recorded participants. */
/** Weekly runs inspect only tournament pages not processed by an earlier successful sync. */
async function getNewTournamentPlayers(): Promise<{ players: EmaPlayer[]; paths: string[] }> {
  const paths = await getTournamentResultPaths();
  const known = await db.collection(EMA_RANKING_TOURNAMENT_RESULT_INDEX).get();
  const knownPaths = new Set(known.docs.map((document) => document.id));
  const newPaths = [...paths].filter((path) => !knownPaths.has(path));
  const players = await getPlayersFromTournamentPaths(newPaths);
  return { players, paths: newPaths };
}

async function getPlayer(emaId: string): Promise<EmaPlayer | null> {
  const sourceUrl = `${baseUrl}Players/${emaId}.html`;
  const html = await getHtml(sourceUrl);
  const nameMatch = html.match(/Name\s*:<\/td>\s*<td[^>]*>([\s\S]*?)<\/td>/i);
  const countryMatch = html.match(/Country\s*:<\/td>\s*<td[^>]*>[\s\S]*?Img\/flag\/16\/([a-z]{2})\.png/i);
  const photoMatch = html.match(/<img[^>]+src="(?:\.\.\/Players\/)?photo\/([^"?#]+)"/i);
  const name = nameMatch ? normalizePlayerName(text(nameMatch[1])) : "";
  if (!name) return null;
  const photoName = photoMatch?.[1];
  const hasPhoto = photoName != null && !/^vide\.jpe?g$/i.test(photoName);
  return { emaId, name, country: countryMatch?.[1]?.toUpperCase() ?? "", sourceUrl, photoSourceUrl: hasPhoto ? `${baseUrl}Players/photo/${photoName}` : null };
}

export async function runEmaPlayerRegistrySync(mode: "seed" | "incremental"): Promise<EmaPlayerRegistrySyncReport> {
  const startedAt = new Date().toISOString();
  const reportRef = db.collection("emaPlayerRegistrySyncReports").doc();
  const newTournaments = mode === "seed"
    ? await (async () => {
      const paths = [...await getTournamentResultPaths()];
      return { players: await getPlayersFromTournamentPaths(paths), paths };
    })()
    : await getNewTournamentPlayers();
  const crawled = newTournaments.players;
  const players = uniqueByEmaId(crawled);
  const current = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).get();
  console.log(`Syncing ${players.length} unique EMA players. Existing records: ${current.size}.`);
  const existing = new Map(current.docs.map((document) => [document.id, document.data()]));
  const additions: SyncChange[] = [];
  const updates: SyncChange[] = [];
  const noLongerRanked: SyncChange[] = [];

  // Preserve a complete recoverable revision before any update.
  const backup = db.batch();
  for (const document of current.docs) backup.set(reportRef.collection("backupPlayers").doc(document.id), document.data());
  await backup.commit();

  for (const player of players) {
    const progress = players.indexOf(player) + 1;
    const before = existing.get(player.emaId);
    const photo = await syncPhoto(player, before);
    const change = { emaId: player.emaId, name: player.name, country: player.country };
    if (!before) additions.push(change);
    else if (before.name !== player.name || before.country !== player.country) updates.push(change);
    await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(player.emaId).set({
      ...player,
      photoUrl: photo.url ?? before?.photoUrl ?? null,
      photoSourceEtag: photo.etag,
      lastSeenInTournamentAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
      ...(before ? {} : { createdAt: FieldValue.serverTimestamp() }),
    }, { merge: true });
    if (progress % 25 === 0 || progress === players.length) console.log(`Stored ${progress}/${players.length} EMA player records.`);
  }
  // Mark source pages only after player updates complete. A failed run will retry them.
  await markTournamentPaths(newTournaments.paths);
  const completedAt = new Date().toISOString();
  const report: EmaPlayerRegistrySyncReport = { id: reportRef.id, mode, startedAt, completedAt, additions, updates, noLongerRanked, sourceUrls: [baseUrl + "Tournament/Tournaments_all.html"] };
  await reportRef.set({ ...report, createdAt: Timestamp.now(), backupCount: current.size, newTournamentPaths: newTournaments.paths });
  await db.doc("emaPlayerRegistrySyncState/current").set({ lastSuccessfulSyncAt: Timestamp.now(), lastReportId: reportRef.id }, { merge: true });
  return report;
}
