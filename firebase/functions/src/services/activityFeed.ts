import { FieldPath, Timestamp, type DocumentData, type Query, type QueryDocumentSnapshot } from "firebase-admin/firestore";

import { badRequest } from "../api/httpError";
import { db } from "../firebase";
import { ACTIVITY_COLLECTION } from "./activityLog";

/**
 * The activity screen of the admin app reads one list, newest first, from the two write logs: the activity log
 * (settings, players, teams, editors, accounts, registry) and the change log of every tournament (table saves).
 * Each log keeps its own place in the cursor, so the pages of the merged list never skip or repeat an entry.
 */

export const CHANGES_COLLECTION = "changes";

/** `tables` keeps only table saves, `other` keeps only the activity log. */
export type FeedSource = "all" | "tables" | "other";
export const FEED_SOURCES: readonly FeedSource[] = ["all", "tables", "other"];

export type FeedFilter = { tournamentId?: string; uid?: string; source: FeedSource };

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
  };
}

type LogRead = { docs: QueryDocumentSnapshot[]; place: LogPlace; isGroup: boolean };

function newestFirst(query: Query): Query {
  return query.orderBy("at", "desc").orderBy(FieldPath.documentId(), "desc");
}

function after(query: Query, place: LogPlace): Query {
  return place && place !== "end" ? query.startAfter(new Timestamp(place.s, place.n), place.p) : query;
}

async function readActivity(filter: FeedFilter, place: LogPlace, limit: number): Promise<LogRead> {
  if (place === "end" || filter.source === "tables") return { docs: [], place: "end", isGroup: false };
  let query: Query = db.collection(ACTIVITY_COLLECTION);
  if (filter.tournamentId) query = query.where("tournamentId", "==", filter.tournamentId);
  if (filter.uid) query = query.where("uid", "==", filter.uid);
  const snapshot = await after(newestFirst(query), place).limit(limit + 1).get();
  return { docs: snapshot.docs, place, isGroup: false };
}

async function readTables(filter: FeedFilter, place: LogPlace, limit: number): Promise<LogRead> {
  if (place === "end" || filter.source === "other") return { docs: [], place: "end", isGroup: true };
  const isGroup = !filter.tournamentId;
  let query: Query = isGroup
    ? db.collectionGroup(CHANGES_COLLECTION)
    : db.collection("tournaments").doc(filter.tournamentId as string).collection(CHANGES_COLLECTION);
  if (filter.uid) query = query.where("uid", "==", filter.uid);
  const snapshot = await after(newestFirst(query), place).limit(limit + 1).get();
  return { docs: snapshot.docs, place, isGroup };
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
    const ended = mine.length === read.docs.length && read.docs.length <= limit;
    if (ended) return "end";
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

/** One page of the merged write log, newest first. Pass `nextCursor` back as [cursorText] to read the next page. */
export async function listActivityFeed(filter: FeedFilter, limit: number, cursorText?: unknown): Promise<FeedPage> {
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
  const names = await tournamentNames(ids);
  for (const entry of entries) {
    if (!entry.tournamentId) continue;
    // The record of a delete keeps the name of the tournament as its target.
    entry.tournamentName = names.get(entry.tournamentId) ?? (entry.action === "tournament.delete" ? entry.target : null);
  }
  const done = next.a === "end" && next.t === "end";
  return { entries, nextCursor: done ? null : encodeFeedCursor(next) };
}
