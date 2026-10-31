import { createHash } from "node:crypto";

import { FieldPath, FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { badRequest } from "../api/httpError";

/**
 * The error log of the apps. Every app sends the errors that it caught and the crashes of the last run.
 * Equal errors share one group, so the list shows each problem once with a count. A group keeps one sample
 * report. The newest reports of the group sit in the subcollection "reports".
 * The reports hold no e-mail addresses and no EMA ids. The text is cleaned here, even if an app forgets it.
 * Firestore deletes old documents by the field "expireAt". See the field overrides in firestore.indexes.json.
 */
export const ERROR_GROUPS_COLLECTION = "errorGroups";
const REPORTS_SUBCOLLECTION = "reports";

const REPORT_DAYS = 30;
const GROUP_DAYS = 90;
const DAY_MS = 86_400_000;

const MAX_MESSAGE = 500;
const MAX_STACK = 8000;
const MAX_SHORT = 120;

export const APPS = ["admin", "player"] as const;
export const PLATFORMS = ["android", "web", "desktop"] as const;
export const KINDS = ["crash", "handled"] as const;
const ENVIRONMENTS = ["prod", "dev", "debug"] as const;
const ROLES = ["admin", "editor", "player"] as const;

export type ErrorReport = {
  app: (typeof APPS)[number];
  platform: (typeof PLATFORMS)[number];
  kind: (typeof KINDS)[number];
  env: (typeof ENVIRONMENTS)[number] | "unknown";
  role: (typeof ROLES)[number] | null;
  appVersion: string;
  errorType: string;
  area: string;
  message: string;
  stackTrace: string;
  /** Why a request failed, for example "server_error". Empty for a crash. */
  reason: string;
  httpStatus: number | null;
  language: string;
  /** The id that the app also sends in the Analytics event of this error. It joins the log and Analytics. */
  reportId: string;
  /** A random id of one app run. It links the reports of one run. It is not an account id. */
  sessionId: string;
};

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)+/g;
const LONG_NUMBER = /\b\d{6,}\b/g;
const BEARER = /\b(Bearer\s+)[A-Za-z0-9._~+/=-]+/gi;
const JWT = /\beyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]*/g;

/** Removes what must not stay in a log: e-mail addresses, long numbers such as EMA ids, and tokens. */
export function redact(text: string): string {
  return text.replace(JWT, "<token>").replace(BEARER, "$1<token>").replace(EMAIL, "<email>").replace(LONG_NUMBER, "<number>");
}

function text(value: unknown, max: number): string {
  return typeof value === "string" ? redact(value.trim()).slice(0, max) : "";
}

function oneOf<T extends string>(value: unknown, allowed: readonly T[]): T | null {
  return typeof value === "string" && (allowed as readonly string[]).includes(value) ? (value as T) : null;
}

/** Checks the body of a request and keeps only the known fields, cut to a safe length. */
export function parseErrorReport(body: unknown): ErrorReport {
  const data = body != null && typeof body === "object" ? (body as Record<string, unknown>) : {};
  const app = oneOf(data.app, APPS);
  const platform = oneOf(data.platform, PLATFORMS);
  const kind = oneOf(data.kind, KINDS);
  if (!app || !platform || !kind) throw badRequest("app, platform and kind must be valid");
  const errorType = text(data.errorType, MAX_SHORT);
  if (!errorType) throw badRequest("errorType is required");
  const status = Number(data.httpStatus);
  return {
    app,
    platform,
    kind,
    env: oneOf(data.env, ENVIRONMENTS) ?? "unknown",
    role: oneOf(data.role, ROLES),
    appVersion: text(data.appVersion, 30),
    errorType,
    area: text(data.area, MAX_SHORT),
    message: text(data.message, MAX_MESSAGE),
    stackTrace: text(data.stackTrace, MAX_STACK),
    reason: text(data.reason, 40),
    httpStatus: Number.isInteger(status) && status >= 100 && status <= 599 ? status : null,
    language: text(data.language, 8),
    reportId: text(data.reportId, 40),
    sessionId: text(data.sessionId, 40),
  };
}

/**
 * The id of a group. Equal errors give equal ids on every platform run: it uses the type, the area and the first
 * three lines of the stack, without line numbers and addresses.
 */
export function fingerprint(report: Pick<ErrorReport, "errorType" | "area" | "stackTrace">): string {
  const frames = report.stackTrace
    .split("\n")
    .map((line) => line.trim().replace(/0x[0-9a-f]+/gi, "").replace(/[:(]\d+(:\d+)?\)?/g, "").replace(/\d+/g, "#"))
    .filter(Boolean)
    .slice(0, 3);
  return createHash("sha1").update([report.errorType, report.area, ...frames].join("|")).digest("hex").slice(0, 20);
}

export async function recordErrorReport(report: ErrorReport, now: number = Date.now()): Promise<string> {
  const id = fingerprint(report);
  const groupRef = db.collection(ERROR_GROUPS_COLLECTION).doc(id);
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(groupRef);
    const common = {
      lastSeen: now,
      lastKind: report.kind,
      lastApp: report.app,
      lastPlatform: report.platform,
      lastVersion: report.appVersion,
      lastEnv: report.env,
      count: FieldValue.increment(1),
      platforms: { [report.platform]: FieldValue.increment(1) },
      apps: { [report.app]: FieldValue.increment(1) },
      expireAt: new Date(now + GROUP_DAYS * DAY_MS),
    };
    if (snapshot.exists) {
      transaction.set(groupRef, common, { merge: true });
    } else {
      transaction.set(groupRef, {
        ...common,
        firstSeen: now,
        errorType: report.errorType,
        area: report.area,
        message: report.message,
        stackTrace: report.stackTrace,
      });
    }
    transaction.set(groupRef.collection(REPORTS_SUBCOLLECTION).doc(), {
      ...report,
      createdAt: now,
      expireAt: new Date(now + REPORT_DAYS * DAY_MS),
    });
  });
  return id;
}

export type ErrorGroupSummary = {
  id: string;
  errorType: string;
  area: string;
  message: string;
  count: number;
  firstSeen: number;
  lastSeen: number;
  lastKind: string;
  lastApp: string;
  lastPlatform: string;
  lastVersion: string;
  lastEnv: string;
  platforms: Record<string, number>;
  apps: Record<string, number>;
};

/** Where the next page starts: the time of the last entry of the page and its document id. */
export type PageCursor = { at: number; id: string };

export type ErrorGroupPage = { groups: ErrorGroupSummary[]; nextCursor: PageCursor | null };

/**
 * One page of groups, newest first. [since] keeps the groups seen at that time or later (milliseconds).
 * The answer has a nextCursor while more groups are left. Pass it back as [cursor] to read the next page.
 */
export async function listErrorGroups(options: { limit: number; since?: number; cursor?: PageCursor }): Promise<ErrorGroupPage> {
  let query = db.collection(ERROR_GROUPS_COLLECTION).orderBy("lastSeen", "desc").orderBy(FieldPath.documentId(), "desc");
  if (options.since !== undefined) query = query.where("lastSeen", ">=", options.since);
  if (options.cursor) query = query.startAfter(options.cursor.at, options.cursor.id);
  const snapshot = await query.limit(options.limit + 1).get();
  const docs = snapshot.docs.slice(0, options.limit);
  const groups = docs.map((doc) => {
    const data = doc.data();
    return {
      id: doc.id,
      errorType: data.errorType ?? "",
      area: data.area ?? "",
      message: data.message ?? "",
      count: data.count ?? 0,
      firstSeen: data.firstSeen ?? 0,
      lastSeen: data.lastSeen ?? 0,
      lastKind: data.lastKind ?? "",
      lastApp: data.lastApp ?? "",
      lastPlatform: data.lastPlatform ?? "",
      lastVersion: data.lastVersion ?? "",
      lastEnv: data.lastEnv ?? "",
      platforms: data.platforms ?? {},
      apps: data.apps ?? {},
    };
  });
  const last = groups[groups.length - 1];
  const hasMore = snapshot.docs.length > options.limit;
  return { groups, nextCursor: hasMore && last ? { at: last.lastSeen, id: last.id } : null };
}

/**
 * One group with its sample stack and one page of its reports, newest first. Null when the group does not exist.
 * The answer has a nextReportCursor while more reports are left. Pass it back as [cursor].
 */
export async function loadErrorGroup(id: string, reportLimit: number, cursor?: PageCursor) {
  const ref = db.collection(ERROR_GROUPS_COLLECTION).doc(id);
  let reportQuery = ref.collection(REPORTS_SUBCOLLECTION).orderBy("createdAt", "desc").orderBy(FieldPath.documentId(), "desc");
  if (cursor) reportQuery = reportQuery.startAfter(cursor.at, cursor.id);
  const [group, reports] = await Promise.all([ref.get(), reportQuery.limit(reportLimit + 1).get()]);
  if (!group.exists) return null;
  const { expireAt: _groupExpire, ...data } = group.data() ?? {};
  const page = reports.docs.slice(0, reportLimit);
  const last = page[page.length - 1];
  const hasMore = reports.docs.length > reportLimit;
  return {
    id,
    ...data,
    reports: page.map((doc) => {
      const { expireAt: _expire, ...report } = doc.data();
      return report;
    }),
    nextReportCursor: hasMore && last ? { at: last.data().createdAt ?? 0, id: last.id } : null,
  };
}
