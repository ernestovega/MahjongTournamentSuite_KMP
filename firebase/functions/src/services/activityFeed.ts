import { FieldPath, Timestamp, type DocumentData, type Query, type QueryDocumentSnapshot } from "firebase-admin/firestore";

import { badRequest } from "../api/httpError";
import { db } from "../firebase";
import { ACTIVITY_COLLECTION } from "./activityLog";
import { tableVersionKey } from "./dataVersionsService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";

/**
 * The activity screen of the admin app reads one list, newest first, from the two write logs: the activity log
 * (settings, players, teams, editors, accounts, registry) and the change log of every tournament (table saves).
 * Each log keeps its own place in the cursor, so the pages of the merged list never skip or repeat an entry.
 */

export const CHANGES_COLLECTION = "changes";

/** `tables` keeps only table saves, `other` keeps only the activity log. */
export type FeedSource = "all" | "tables" | "other";
export const FEED_SOURCES: readonly FeedSource[] = ["all", "tables", "other"];

/** Where a change came from: the admin app, the player app, or the server and the maintenance scripts. */
export type FeedOrigin = "admin" | "player" | "system";
export const FEED_ORIGINS: readonly FeedOrigin[] = ["admin", "player", "system"];

/** The uid of the activity records of the maintenance scripts. */
export const SCRIPT_UID = "script";

export type FeedFilter = {
  tournamentId?: string;
  uid?: string;
  source: FeedSource;
  origin?: FeedOrigin;
  /** Milliseconds since the epoch. Keeps the records written at this time or later. */
  fromMs?: number;
  /** Milliseconds since the epoch. Keeps the records written before this time. */
  toMs?: number;
  /** Free text. Keeps the entries that hold it anywhere: person, tournament, target, values. Case and accents do not count. */
  text?: string;
};

/** One value of a change, as text. Null when the field had no value. */
export type FeedFieldChange = { field: string; from: string | null; to: string | null; handId?: number };

export type FeedEntry = {
  /** The path of the record, unique across both logs. */
  id: string;
  /** `activity` for the activity log, `table` for a table save. */
  kind: "activity" | "table";
  /** Milliseconds since the epoch. */
  at: number;
  /** The action of the activity log, or `table.save`, `table.reset` or `table.conflict`. */
  action: string;
  uid: string | null;
  name: string | null;
  /** Where a table save came from: `editor`, `player` or `system`. Null for the activity log. */
  source: string | null;
  tournamentId: string | null;
  tournamentName: string | null;
  target: string | null;
  /** The short values of an activity record, as text. */
  details: Array<{ key: string; value: string }>;
  roundId: number | null;
  tableId: number | null;
  version: number | null;
  changes: FeedFieldChange[];
  /**
   * The names of the players: the four players of the table of a table save, or the player that an activity record
   * names as its target (`player 5`). Empty when the player has no EMA player assigned.
   */
  players: string[];
};

export type FeedPage = { entries: FeedEntry[]; nextCursor: string | null };

/** The place of one log: the last entry read, or `end` when the log has nothing more. */
type LogPlace = { s: number; n: number; p: string } | "end" | null;
type FeedCursor = { a: LogPlace; t: LogPlace };

export function encodeFeedCursor(cursor: FeedCursor): string {
  return Buffer.from(JSON.stringify(cursor), "utf8").toString("base64url");
}

function isPlace(value: unknown): value is LogPlace {
  if (value === null || value === "end") return true;
  if (typeof value !== "object") return false;
  const place = value as Record<string, unknown>;
  return Number.isInteger(place.s) && Number.isInteger(place.n) && typeof place.p === "string" && place.p !== "";
}

export function decodeFeedCursor(text: unknown): FeedCursor {
  if (text === undefined || text === "") return { a: null, t: null };
  try {
    if (typeof text !== "string") throw new Error("not text");
    const value = JSON.parse(Buffer.from(text, "base64url").toString("utf8")) as Record<string, unknown>;
    if (isPlace(value.a) && isPlace(value.t)) return { a: value.a, t: value.t };
  } catch {
    // Falls through to the error below.
  }
  throw badRequest("cursor is not valid");
}

const MAX_TEXT = 200;

/** Shows a stored value as short text. */
export function valueText(value: unknown): string | null {
  if (value === null || value === undefined || value === "") return null;
  const text = typeof value === "string" ? value : JSON.stringify(value);
  return text.length > MAX_TEXT ? `${text.slice(0, MAX_TEXT)}…` : text;
}

function millisOf(value: unknown): number {
  return value instanceof Timestamp ? value.toMillis() : 0;
}

function textOrNull(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}

function intOrNull(value: unknown): number | null {
  return typeof value === "number" && Number.isInteger(value) ? value : null;
}

export function activityEntryFrom(id: string, data: DocumentData): FeedEntry {
  const details = data.details && typeof data.details === "object" ? data.details as Record<string, unknown> : {};
  return {
    id,
    kind: "activity",
    at: millisOf(data.at),
    action: String(data.action ?? ""),
    uid: textOrNull(data.uid),
    name: textOrNull(data.name),
    source: null,
    tournamentId: textOrNull(data.tournamentId),
    tournamentName: null,
    target: textOrNull(data.target),
    details: Object.entries(details)
      .map(([key, value]) => ({ key, value: valueText(value) ?? "–" }))
      .sort((a, b) => a.key.localeCompare(b.key)),
    roundId: null,
    tableId: null,
    version: null,
    changes: [],
    players: [],
  };
}

export function tableEntryFrom(id: string, tournamentId: string, data: DocumentData): FeedEntry {
  const tableChanges = Array.isArray(data.tableChanges) ? data.tableChanges as Array<Record<string, unknown>> : [];
  const handChanges = Array.isArray(data.handChanges) ? data.handChanges as Array<Record<string, unknown>> : [];
  return {
    id,
    kind: "table",
    at: millisOf(data.at),
    action: `table.${String(data.kind ?? "save")}`,
    uid: textOrNull(data.uid),
    name: textOrNull(data.name),
    source: textOrNull(data.source),
    tournamentId,
    tournamentName: null,
    target: null,
    details: [],
    roundId: intOrNull(data.roundId),
    tableId: intOrNull(data.tableId),
    version: intOrNull(data.version),
    changes: [
      ...tableChanges.map((change) => ({ field: String(change.field ?? ""), from: valueText(change.from), to: valueText(change.to) })),
      ...handChanges.map((change) => ({
        field: String(change.field ?? ""),
        from: valueText(change.from),
        to: valueText(change.to),
        handId: intOrNull(change.handId) ?? 0,
      })),
    ],
    players: [],
  };
}

/**
 * [docs] are the records that pass the filters, [raw] all the records that the query read. A filter that the query
 * cannot apply removes records from [docs] only, so the place of the log still moves past them.
 */
type LogRead = { docs: QueryDocumentSnapshot[]; raw: QueryDocumentSnapshot[]; place: LogPlace; isGroup: boolean };

const NO_READ = (isGroup: boolean): LogRead => ({ docs: [], raw: [], place: "end", isGroup });

function newestFirst(query: Query): Query {
  return query.orderBy("at", "desc").orderBy(FieldPath.documentId(), "desc");
}

function after(query: Query, place: LogPlace): Query {
  return place && place !== "end" ? query.startAfter(new Timestamp(place.s, place.n), place.p) : query;
}

function inPeriod(query: Query, filter: FeedFilter): Query {
  let result = query;
  if (filter.fromMs !== undefined) result = result.where("at", ">=", Timestamp.fromMillis(filter.fromMs));
  if (filter.toMs !== undefined) result = result.where("at", "<", Timestamp.fromMillis(filter.toMs));
  return result;
}

/**
 * The activity log holds the changes made in the admin app and the runs of the maintenance scripts. The player app
 * writes only table saves, so it has no records here.
 */
async function readActivity(filter: FeedFilter, place: LogPlace, limit: number): Promise<LogRead> {
  if (place === "end" || filter.source === "tables" || filter.origin === "player") return NO_READ(false);
  const uid = filter.origin === "system" ? SCRIPT_UID : filter.uid;
  if (filter.origin === "system" && filter.uid && filter.uid !== SCRIPT_UID) return NO_READ(false);
  let query: Query = db.collection(ACTIVITY_COLLECTION);
  if (filter.tournamentId) query = query.where("tournamentId", "==", filter.tournamentId);
  if (uid) query = query.where("uid", "==", uid);
  const snapshot = await after(newestFirst(inPeriod(query, filter)), place).limit(limit + 1).get();
  // Firestore cannot leave out one uid and keep the order by time, so the script runs leave here.
  const docs = filter.origin === "admin" ? snapshot.docs.filter((doc) => doc.get("uid") !== SCRIPT_UID) : snapshot.docs;
  return { docs, raw: snapshot.docs, place, isGroup: false };
}

/** The origin of a table save as the change log stores it. */
const TABLE_SOURCE: Record<FeedOrigin, string> = { admin: "editor", player: "player", system: "system" };

async function readTables(filter: FeedFilter, place: LogPlace, limit: number): Promise<LogRead> {
  if (place === "end" || filter.source === "other") return NO_READ(true);
  const isGroup = !filter.tournamentId;
  let query: Query = isGroup
    ? db.collectionGroup(CHANGES_COLLECTION)
    : db.collection("tournaments").doc(filter.tournamentId as string).collection(CHANGES_COLLECTION);
  if (filter.origin) query = query.where("source", "==", TABLE_SOURCE[filter.origin]);
  if (filter.uid) query = query.where("uid", "==", filter.uid);
  const snapshot = await after(newestFirst(inPeriod(query, filter)), place).limit(limit + 1).get();
  return { docs: snapshot.docs, raw: snapshot.docs, place, isGroup };
}

/** A collection group query needs the full path of the document as its cursor, a collection query only its id. */
function placeOf(doc: QueryDocumentSnapshot, isGroup: boolean): LogPlace {
  const at = doc.get("at");
  const timestamp = at instanceof Timestamp ? at : new Timestamp(0, 0);
  return { s: timestamp.seconds, n: timestamp.nanoseconds, p: isGroup ? doc.ref.path : doc.id };
}

function compareNewestFirst(a: QueryDocumentSnapshot, b: QueryDocumentSnapshot): number {
  return millisOf(b.get("at")) - millisOf(a.get("at"));
}

/**
 * Takes the newest [limit] records of the two logs. The place of a log moves to the last record taken from it.
 * A log ends when the page took every record that it still had.
 */
export function mergeLogs(activity: LogRead, tables: LogRead, limit: number) {
  const tagged = [
    ...activity.docs.map((doc) => ({ doc, log: "a" as const })),
    ...tables.docs.map((doc) => ({ doc, log: "t" as const })),
  ];
  // A stable sort keeps the order of each log for records with the same time.
  tagged.sort((x, y) => compareNewestFirst(x.doc, y.doc));
  const taken = tagged.slice(0, limit);
  const nextPlace = (read: LogRead, log: "a" | "t"): LogPlace => {
    if (read.place === "end") return "end";
    const mine = taken.filter((item) => item.log === log);
    if (mine.length === read.docs.length) {
      // Every record that passed the filters is on the page, so the log moves past everything it read.
      if (read.raw.length <= limit) return "end";
      return placeOf(read.raw[read.raw.length - 1], read.isGroup);
    }
    const last = mine[mine.length - 1];
    return last ? placeOf(last.doc, read.isGroup) : read.place;
  };
  const cursor: FeedCursor = { a: nextPlace(activity, "a"), t: nextPlace(tables, "t") };
  return { taken, cursor };
}

async function tournamentNames(ids: string[]): Promise<Map<string, string>> {
  const names = new Map<string, string>();
  if (ids.length === 0) return names;
  const snapshots = await db.getAll(...ids.map((id) => db.collection("tournaments").doc(id)));
  for (const snapshot of snapshots) {
    if (snapshot.exists) names.set(snapshot.id, String(snapshot.get("name") ?? ""));
  }
  return names;
}

/** Lower case and without accents, so "Jose" finds "José". */
export function searchKey(text: string): string {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase().trim();
}

/** Whether [key] (from [searchKey]) is in any text of the entry. */
export function entryHasText(entry: FeedEntry, key: string): boolean {
  const texts: Array<string | null> = [
    entry.action, entry.uid, entry.name, entry.source, entry.tournamentId, entry.tournamentName, entry.target,
    entry.roundId === null ? null : `round ${entry.roundId}`,
    entry.tableId === null ? null : `table ${entry.tableId}`,
    ...entry.details.flatMap((detail) => [detail.key, detail.value]),
    ...entry.changes.flatMap((change) => [change.field, change.from, change.to]),
    ...entry.players,
  ];
  return texts.some((text) => text !== null && searchKey(text).includes(key));
}

/** A text search reads at most this many pages of the logs in one request; `nextCursor` goes on from there. */
const MAX_SEARCH_PAGES = 10;

/**
 * One page of the merged write log, newest first. Pass `nextCursor` back as [cursorText] to read the next page.
 * With a text, the server reads page after page until it has [limit] matches, reads [MAX_SEARCH_PAGES] pages or the
 * logs end. A page can then be shorter than [limit] and still have a `nextCursor`.
 */
export async function listActivityFeed(filter: FeedFilter, limit: number, cursorText?: unknown): Promise<FeedPage> {
  const key = filter.text ? searchKey(filter.text) : "";
  const names = new PlayerNames();
  if (!key) return readFeedPage(filter, limit, cursorText, names);
  const entries: FeedEntry[] = [];
  let cursor: unknown = cursorText;
  for (let pages = 0; pages < MAX_SEARCH_PAGES; pages++) {
    const page = await readFeedPage(filter, limit, cursor, names);
    entries.push(...page.entries.filter((entry) => entryHasText(entry, key)));
    cursor = page.nextCursor;
    if (page.nextCursor === null || entries.length >= limit) break;
  }
  return { entries, nextCursor: typeof cursor === "string" ? cursor : null };
}

/** The slot that an activity record names as its target, such as `player 5`. */
function targetSlot(entry: FeedEntry): number | null {
  const match = /^player (\d+)$/.exec(entry.target ?? "");
  return match ? Number(match[1]) : null;
}

/**
 * Reads the names of the players of the entries, once per request: the tables, then the slots, then the EMA players.
 * The slot shows the EMA player assigned now, the same as the apps show it.
 */
export class PlayerNames {
  private readonly tables = new Map<string, number[]>();
  private readonly slots = new Map<string, string | null>();
  private readonly names = new Map<string, string>();

  async fill(entries: FeedEntry[]): Promise<void> {
    const tableKey = (entry: FeedEntry) => `${entry.tournamentId}/${tableVersionKey(entry.roundId ?? 0, entry.tableId ?? 0)}`;
    const tableEntries = entries.filter((entry) => entry.kind === "table" && entry.tournamentId && entry.roundId !== null && entry.tableId !== null);
    const missingTables = [...new Set(tableEntries.map(tableKey))].filter((key) => !this.tables.has(key));
    if (missingTables.length > 0) {
      const snapshots = await db.getAll(...missingTables.map((key) => db.collection("tournaments").doc(key.split("/")[0]).collection("tables").doc(key.split("/")[1])));
      snapshots.forEach((snapshot, index) => {
        const ids = snapshot.exists && Array.isArray(snapshot.get("playerIds")) ? (snapshot.get("playerIds") as unknown[]).map(Number) : [];
        this.tables.set(missingTables[index], ids.filter((id) => Number.isInteger(id)));
      });
    }
    const slotsOf = new Map<FeedEntry, string[]>();
    for (const entry of entries) {
      if (!entry.tournamentId) continue;
      const ids = entry.kind === "table" ? this.tables.get(tableKey(entry)) ?? [] : [targetSlot(entry)].filter((id): id is number => id !== null);
      if (ids.length > 0) slotsOf.set(entry, ids.map((id) => `${entry.tournamentId}/${id}`));
    }
    const missingSlots = [...new Set([...slotsOf.values()].flat())].filter((key) => !this.slots.has(key));
    if (missingSlots.length > 0) {
      const snapshots = await db.getAll(...missingSlots.map((key) => db.collection("tournaments").doc(key.split("/")[0]).collection("players").doc(key.split("/")[1])));
      snapshots.forEach((snapshot, index) => {
        const emaId = snapshot.exists ? snapshot.get("assignedEmaId") : null;
        this.slots.set(missingSlots[index], typeof emaId === "string" && emaId !== "" ? emaId : null);
      });
    }
    const missingPlayers = [...new Set([...this.slots.values()])].filter((id): id is string => id !== null && !this.names.has(id));
    if (missingPlayers.length > 0) {
      const snapshots = await db.getAll(...missingPlayers.map((id) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(id)));
      snapshots.forEach((snapshot, index) => {
        const name = snapshot.exists ? `${String(snapshot.get("firstName") ?? "")} ${String(snapshot.get("lastName") ?? "")}`.trim() : "";
        this.names.set(missingPlayers[index], name);
      });
    }
    for (const [entry, keys] of slotsOf) {
      entry.players = keys
        .map((key) => this.slots.get(key))
        .map((emaId) => (emaId ? this.names.get(emaId) ?? "" : ""))
        .filter((name) => name !== "");
    }
  }
}

async function readFeedPage(filter: FeedFilter, limit: number, cursorText?: unknown, names = new PlayerNames()): Promise<FeedPage> {
  const cursor = decodeFeedCursor(cursorText);
  const [activity, tables] = await Promise.all([
    readActivity(filter, cursor.a, limit),
    readTables(filter, cursor.t, limit),
  ]);
  const { taken, cursor: next } = mergeLogs(activity, tables, limit);
  const entries = taken.map(({ doc, log }) => (log === "a"
    ? activityEntryFrom(doc.ref.path, doc.data())
    : tableEntryFrom(doc.ref.path, doc.ref.parent.parent?.id ?? "", doc.data())));
  const ids = [...new Set(entries.map((entry) => entry.tournamentId).filter((id): id is string => !!id))];
  const tournaments = await tournamentNames(ids);
  for (const entry of entries) {
    if (!entry.tournamentId) continue;
    // The record of a delete keeps the name of the tournament as its target.
    entry.tournamentName = tournaments.get(entry.tournamentId) ?? (entry.action === "tournament.delete" ? entry.target : null);
  }
  await names.fill(entries);
  const done = next.a === "end" && next.t === "end";
  return { entries, nextCursor: done ? null : encodeFeedCursor(next) };
}
