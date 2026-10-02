import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";

export type ForcedPointsMigrationResult = {
  patch: Record<string, string | boolean> | null;
  reason?: string;
};

const SCORE_FIELDS = [
  "east", "south", "west", "north",
] as const;

function text(value: unknown): string {
  return String(value ?? "").trim();
}

function pointsForScores(scores: string[]): string[] | null {
  if (scores.some((score) => score.length === 0)) return null;
  const ranked = scores.map((score, index) => ({ index, score: Number(score) }));
  if (ranked.some((item) => !Number.isSafeInteger(item.score))) return null;
  const sorted = [...ranked].sort((left, right) => right.score - left.score);
  const values = (() => {
    const a = sorted.map((item) => item.score);
    if (a[0] === a[1] && a[1] === a[2] && a[2] === a[3]) return ["1,75", "1,75", "1,75", "1,75"];
    if (a[0] === a[1] && a[1] === a[2]) return ["2,33", "2,33", "2,33", "0"];
    if (a[1] === a[2] && a[2] === a[3]) return ["4", "1", "1", "1"];
    if (a[0] === a[1] && a[2] === a[3]) return ["3", "3", "0,5", "0,5"];
    if (a[0] === a[1]) return ["3", "3", "1", "0"];
    if (a[1] === a[2]) return ["4", "1,5", "1,5", "0"];
    if (a[2] === a[3]) return ["4", "2", "0,5", "0,5"];
    return ["4", "2", "1", "0"];
  })();
  const bySeat = Array<string>(4);
  sorted.forEach((item, index) => { bySeat[item.index] = values[index]; });
  return bySeat;
}

/** Converts one legacy Manual Points table without changing any hand document. */
export function convertForcedPointsTable(data: Record<string, unknown>): ForcedPointsMigrationResult {
  if (Boolean(data.usePointsCalculation ?? true)) return { patch: null };

  const scores = SCORE_FIELDS.map((seat) => {
    const manual = text(data[`manualPlayer${seat[0].toUpperCase()}${seat.slice(1)}Score`]);
    return manual || text(data[`player${seat[0].toUpperCase()}${seat.slice(1)}Score`]);
  });
  const points = pointsForScores(scores);
  if (points == null) {
    return {
      patch: null,
      reason: "The table has no four integer scores. Manual Points were not changed.",
    };
  }

  const patch: Record<string, string | boolean> = {
    useTotalsOnly: true,
    usePointsCalculation: true,
  };
  SCORE_FIELDS.forEach((seat, index) => {
    const capitalized = `${seat[0].toUpperCase()}${seat.slice(1)}`;
    patch[`manualPlayer${capitalized}Score`] = scores[index];
    patch[`player${capitalized}Score`] = scores[index];
    patch[`manualPlayer${capitalized}Points`] = points[index];
    patch[`player${capitalized}Points`] = points[index];
  });
  return { patch };
}

type AuditEntry = {
  tournamentPath: string;
  path: string;
  roundId: number;
  tableId: number;
  action: "convert" | "skip";
  reason?: string;
  before: Record<string, unknown>;
  patch: Record<string, string | boolean> | null;
  hands: Array<Record<string, unknown>>;
};

function argumentValue(name: string): string | null {
  const prefix = `--${name}=`;
  const inline = process.argv.find((argument) => argument.startsWith(prefix));
  if (inline) return inline.slice(prefix.length).trim();
  const index = process.argv.indexOf(`--${name}`);
  const value = index >= 0 ? process.argv[index + 1] : undefined;
  return value && !value.startsWith("--") ? value.trim() : null;
}

async function main(): Promise<void> {
  const shouldApply = process.argv.includes("--apply");
  const project = argumentValue("project") ?? firebaseProjectId;
  if (shouldApply && argumentValue("confirm-project") !== project) {
    throw new Error(`Apply mode requires --confirm-project=${project}`);
  }

  const tournaments = await db.collection("tournaments").get();
  const entries: AuditEntry[] = [];
  for (const tournament of tournaments.docs) {
    const tables = await tournament.ref.collection("tables").get();
    for (const table of tables.docs) {
      const before = table.data();
      if (Boolean(before.usePointsCalculation ?? true)) continue;
      const converted = convertForcedPointsTable(before);
      const hands = (await table.ref.collection("hands").get()).docs.map((hand) => hand.data());
      entries.push({
        tournamentPath: tournament.ref.path,
        path: table.ref.path,
        roundId: Number(before.roundId),
        tableId: Number(before.tableId),
        action: converted.patch ? "convert" : "skip",
        reason: converted.reason,
        before,
        patch: converted.patch,
        hands,
      });
    }
  }

  const auditDirectory = resolve(process.cwd(), "migration-audits");
  await mkdir(auditDirectory, { recursive: true });
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const auditPath = resolve(auditDirectory, `table-scoring-${project}-${timestamp}.json`);
  await writeFile(auditPath, `${JSON.stringify({ project, createdAt: new Date().toISOString(), entries }, null, 2)}\n`, {
    encoding: "utf8",
    flag: "wx",
    mode: 0o600,
  });

  if (shouldApply) {
    let convertedCount = 0;
    const touchedTournaments = new Set<string>();
    for (const entry of entries) {
      if (!entry.patch) continue;
      await db.doc(entry.path).update({
        ...entry.patch,
        hasValidManualTotals: true,
        updatedAt: FieldValue.serverTimestamp(),
        version: FieldValue.increment(1),
      });
      convertedCount++;
      touchedTournaments.add(entry.tournamentPath);
    }
    for (const tournamentPath of touchedTournaments) {
      await db.doc(tournamentPath).update({
        updatedAt: FieldValue.serverTimestamp(),
        "dataVersions.tables.revision": FieldValue.increment(1),
        "dataVersions.tables.changedAt": FieldValue.serverTimestamp(),
      });
    }
    console.log(`Applied ${convertedCount} table conversions. Audit: ${auditPath}`);
  } else {
    console.log(`Report only. Found ${entries.length} forced points tables. Audit: ${auditPath}`);
    console.log("Run with --apply --confirm-project=<project> to write the listed table patches.");
  }
}

if (process.argv[1]?.endsWith("migrateTableScoringSource.js")) {
  main().catch((error: unknown) => {
    console.error("Table scoring migration failed.", error);
    process.exitCode = 1;
  });
}
