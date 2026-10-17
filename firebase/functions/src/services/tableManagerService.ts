import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { notFound } from "../api/httpError";
import { conflict } from "../api/httpError";
import {
  calculateTableCompletionStatus,
  hasFourValidScores,
  calculateHandSummary,
} from "./tournamentContentRules";
import { tablePointsOf } from "./handScoring";
import { globalVersionUpdate, tableVersionKey, tableVersionUpdate } from "./dataVersionsService";
import { prepareTableStats } from "./statisticsStore";
import { buildConflictChangeEntry, buildResetChangeEntry, buildSaveChangeEntry, type ChangeActor } from "./changeLog";

export type TableHand = {
  handId: number;
  playerWinnerId: string;
  playerLooserId: string;
  handScore: string;
  isChickenHand: boolean;
  isDone: boolean;
  playerEastPenalty: string;
  playerSouthPenalty: string;
  playerWestPenalty: string;
  playerNorthPenalty: string;
};

export type TableState = {
  version: number;
  roundId: number;
  tableId: number;
  playerIds: number[];
  playerEastId: string;
  playerSouthId: string;
  playerWestId: string;
  playerNorthId: string;
  playerEastScore: string;
  playerSouthScore: string;
  playerWestScore: string;
  playerNorthScore: string;
  playerEastPoints: string;
  playerSouthPoints: string;
  playerWestPoints: string;
  playerNorthPoints: string;
  manualPlayerEastScore: string;
  manualPlayerSouthScore: string;
  manualPlayerWestScore: string;
  manualPlayerNorthScore: string;
  isCompleted: boolean;
  useTotalsOnly: boolean;
};

type Data = Record<string, unknown>;

/** Reads a table from its stored fields. The stored document and a merged document in memory use the same code. */
export function tableStateFromData(data: Data): TableState {
  const text = (field: string) => String(data[field] ?? "");
  return {
    roundId: Number(data.roundId),
    tableId: Number(data.tableId),
    playerIds: (data.playerIds as unknown[] | undefined ?? []).map((x) => Number(x)),
    playerEastId: text("playerEastId"),
    playerSouthId: text("playerSouthId"),
    playerWestId: text("playerWestId"),
    playerNorthId: text("playerNorthId"),
    playerEastScore: text("playerEastScore"),
    playerSouthScore: text("playerSouthScore"),
    playerWestScore: text("playerWestScore"),
    playerNorthScore: text("playerNorthScore"),
    // The table points come only from the scores. A stored value is never used.
    ...tablePointsOf(data),
    manualPlayerEastScore: text("manualPlayerEastScore"),
    manualPlayerSouthScore: text("manualPlayerSouthScore"),
    manualPlayerWestScore: text("manualPlayerWestScore"),
    manualPlayerNorthScore: text("manualPlayerNorthScore"),
    isCompleted: Boolean(data.isCompleted ?? false),
    useTotalsOnly: Boolean(data.useTotalsOnly ?? false),
    version: Number(data.version ?? 0),
  };
}

export function handsFromData(list: Data[]): TableHand[] {
  return list
    .map((d) => ({
      handId: Number(d.handId),
      playerWinnerId: String(d.playerWinnerId ?? ""),
      playerLooserId: String(d.playerLooserId ?? ""),
      handScore: String(d.handScore ?? ""),
      isChickenHand: Boolean(d.isChickenHand ?? false),
      isDone: Boolean(d.isDone ?? false),
      playerEastPenalty: String(d.playerEastPenalty ?? ""),
      playerSouthPenalty: String(d.playerSouthPenalty ?? ""),
      playerWestPenalty: String(d.playerWestPenalty ?? ""),
      playerNorthPenalty: String(d.playerNorthPenalty ?? ""),
    }))
    .sort((a, b) => a.handId - b.handId);
}

export const HANDS_PER_TABLE = 16;

export function defaultHand(handId: number): TableHand {
  return {
    handId,
    playerWinnerId: "",
    playerLooserId: "",
    handScore: "",
    isChickenHand: false,
    isDone: false,
    playerEastPenalty: "",
    playerSouthPenalty: "",
    playerWestPenalty: "",
    playerNorthPenalty: "",
  };
}

export async function getTableWithHands(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
}): Promise<{ table: TableState; hands: TableHand[] }> {
  const tableDocId = `${params.roundId}_${params.tableId}`;
  const tableRef = db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
  // The table and its hands are independent reads, so run them together.
  const [tableSnap, handsSnap] = await Promise.all([tableRef.get(), tableRef.collection("hands").get()]);
  if (!tableSnap.exists) throw notFound("Table not found");
  return {
    table: tableStateFromData(tableSnap.data() ?? {}),
    hands: handsFromData(handsSnap.docs.map((doc) => doc.data())),
  };
}

/** Reads every table of a round with its hands in one call, so the client needs one request instead of one per table. */
export async function getRoundTablesWithHands(params: {
  tournamentId: string;
  roundId: number;
}): Promise<{ table: TableState; hands: TableHand[] }[]> {
  const tablesSnap = await db.collection("tournaments").doc(params.tournamentId).collection("tables")
    .where("roundId", "==", params.roundId)
    .get();
  return readTablesWithHands(tablesSnap.docs);
}

/** Reads every table of the tournament with its hands in one call. The ranking needs all of them. */
export async function getAllTablesWithHands(params: {
  tournamentId: string;
}): Promise<{ table: TableState; hands: TableHand[] }[]> {
  const tablesSnap = await db.collection("tournaments").doc(params.tournamentId).collection("tables").get();
  return readTablesWithHands(tablesSnap.docs);
}

async function readTablesWithHands(
  tableDocs: FirebaseFirestore.QueryDocumentSnapshot[],
): Promise<{ table: TableState; hands: TableHand[] }[]> {
  const tables = await Promise.all(tableDocs.map(async (tableSnap) => {
    const handsSnap = await tableSnap.ref.collection("hands").get();
    return {
      table: tableStateFromData(tableSnap.data()),
      hands: handsFromData(handsSnap.docs.map((doc) => doc.data())),
    };
  }));
  return tables.sort((a, b) => a.table.roundId - b.table.roundId || a.table.tableId - b.table.tableId);
}

export function calculateTableSummary(table: Record<string, unknown>, hands: Array<Record<string, unknown>>): {
  hasProgress: boolean;
  hasValidManualTotals: boolean;
  completionStatus: string;
  bestHandScores: number[];
  chickenHandCount: number;
} {
  const hasTableProgress = [
    "playerEastId", "playerSouthId", "playerWestId", "playerNorthId",
    "playerEastScore", "playerSouthScore", "playerWestScore", "playerNorthScore",
    "playerEastPoints", "playerSouthPoints", "playerWestPoints", "playerNorthPoints",
  ].some((field) => String(table[field] ?? "").trim().length > 0);
  const hasHandProgress = hands.some((hand) => [
    "playerWinnerId", "playerLooserId", "handScore", "playerEastPenalty",
    "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty",
  ].some((field) => String(hand[field] ?? "").trim().length > 0)
    || Boolean(hand.isChickenHand) || Boolean(hand.isDone));
  const useTotalsOnly = Boolean(table.useTotalsOnly ?? false);
  const validScores = hasFourValidScores([
    table.manualPlayerEastScore || table.playerEastScore,
    table.manualPlayerSouthScore || table.playerSouthScore,
    table.manualPlayerWestScore || table.playerWestScore,
    table.manualPlayerNorthScore || table.playerNorthScore,
  ]);
  const hasValidManualTotals = useTotalsOnly && validScores;
  return {
    hasProgress: hasTableProgress || hasHandProgress || Boolean(table.isCompleted),
    hasValidManualTotals,
    completionStatus: calculateTableCompletionStatus({
      hasData: hasTableProgress || hasHandProgress,
      seatIds: [table.playerEastId, table.playerSouthId, table.playerWestId, table.playerNorthId],
      useTotalsOnly,
      hasValidTotals: useTotalsOnly ? validScores : hasFourValidScores([
        table.playerEastScore, table.playerSouthScore, table.playerWestScore, table.playerNorthScore,
      ]),
    }),
    ...calculateHandSummary(hands),
  };
}

/**
 * Returns the tournament completion state after the current table changes.
 * The query contains every table that was incomplete when the transaction read it.
 */
export function isTournamentCompleteAfterTableSave(params: {
  currentTablePath: string;
  currentTableIsComplete: boolean;
  incompleteTablePaths: string[];
}): boolean {
  if (!params.currentTableIsComplete) return false;
  return params.incompleteTablePaths.every((path) => path === params.currentTablePath);
}

function handRefsOf(tableRef: FirebaseFirestore.DocumentReference): FirebaseFirestore.DocumentReference[] {
  return Array.from({ length: HANDS_PER_TABLE }, (_, index) => tableRef.collection("hands").doc(String(index + 1)));
}

export class StaleTableVersion extends Error {
  constructor(
    readonly expectedVersion: number,
    readonly currentVersion: number,
  ) {
    super("Stale table version");
  }
}

/** A table counts as started when it has any data. The tournament keeps the number of started tables. */
function progressDelta(before: boolean, after: boolean): number {
  return Number(after) - Number(before);
}

export type SaveTableStateParams = {
  tournamentId: string;
  roundId: number;
  tableId: number;
  expectedVersion: number;
  tablePatch: Partial<Omit<TableState, "roundId" | "tableId" | "playerIds" | "version">>;
  handPatches: Array<{ handId: number; patch: Partial<Omit<TableHand, "handId">> }>;
  actor?: ChangeActor | null;
};

/**
 * Runs a save inside a transaction that the caller owns. The caller must finish its own reads before it calls this,
 * because this function writes. It throws [StaleTableVersion] when the expected version is old.
 */
export async function saveTableStateInTransaction(
  transaction: FirebaseFirestore.Transaction,
  params: SaveTableStateParams,
): Promise<{ table: TableState; hands: TableHand[] }> {
  const tournamentRef = db.collection("tournaments").doc(params.tournamentId);
  const tableRef = tournamentRef.collection("tables").doc(tableVersionKey(params.roundId, params.tableId));
  const tableSnapshot = await transaction.get(tableRef);
  if (!tableSnapshot.exists) throw notFound("Table not found");
  const currentVersion = Number(tableSnapshot.get("version") ?? 0);
  if (params.expectedVersion !== currentVersion) {
    throw new StaleTableVersion(params.expectedVersion, currentVersion);
  }
  // The hands are read by document, not by query. A query locks a whole range, and then editors of
  // different tables would wait for each other.
  const handSnapshots = await transaction.getAll(...handRefsOf(tableRef));
  if (handSnapshots.some((snapshot) => !snapshot.exists)) throw notFound("Hands not found");
  const handsBefore = new Map<string, Data>(
    handSnapshots.map((snapshot) => [snapshot.id, snapshot.data() ?? {}] as [string, Data]),
  );
  if (params.handPatches.some((item) => !handsBefore.has(String(item.handId)))) {
    throw notFound("Hand not found");
  }

  const tableBefore = tableSnapshot.data() ?? {};
  const mergedTable: Data = { ...tableBefore, ...params.tablePatch };
  const handsAfter = new Map(handsBefore);
  params.handPatches.forEach((item) => {
    const key = String(item.handId);
    handsAfter.set(key, { ...(handsBefore.get(key) ?? {}), ...item.patch, handId: item.handId });
  });
  const previousSummary = calculateTableSummary(tableBefore, [...handsBefore.values()]);
  const summary = calculateTableSummary(mergedTable, [...handsAfter.values()]);
  // The table points are written on every save, from the scores of the merged table.
  const derivedPoints = tablePointsOf(mergedTable);
  const wasComplete = Boolean(tableBefore.isCompleted);
  const isComplete = Boolean(mergedTable.isCompleted);

  // Only a table that becomes complete can finish the tournament. Other saves skip this query,
  // so editors of different tables do not lock each other.
  let tournamentIsComplete = false;
  if (isComplete) {
    const incompleteTables = await transaction.get(
      tournamentRef.collection("tables").where("isCompleted", "==", false).limit(2),
    );
    tournamentIsComplete = isTournamentCompleteAfterTableSave({
      currentTablePath: tableRef.path,
      currentTableIsComplete: true,
      incompleteTablePaths: incompleteTables.docs.map((document) => document.ref.path),
    });
  }

  // The statistics change only for a table that is or was complete. Other saves read nothing more.
  const statsPlan = wasComplete || isComplete
    ? await prepareTableStats(transaction, {
      tournamentId: params.tournamentId,
      roundId: params.roundId,
      tableId: params.tableId,
      table: { ...mergedTable, ...derivedPoints, roundId: params.roundId, tableId: params.tableId },
      hands: [...handsAfter.values()],
    })
    : null;

  const nextVersion = currentVersion + 1;
  const editedBy = params.actor == null
    ? {}
    : { lastEditedByUid: params.actor.uid, lastEditedByName: params.actor.name, lastEditedAt: FieldValue.serverTimestamp() };
  transaction.update(tableRef, {
    ...params.tablePatch,
    ...derivedPoints,
    ...summary,
    ...editedBy,
    version: nextVersion,
    updatedAt: FieldValue.serverTimestamp(),
  });
  params.handPatches.forEach((item) => {
    transaction.update(tableRef.collection("hands").doc(String(item.handId)), {
      ...item.patch,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  statsPlan?.commit(transaction);
  const startedChange = progressDelta(previousSummary.hasProgress, summary.hasProgress);
  transaction.update(tournamentRef, {
    isCompleted: tournamentIsComplete,
    updatedAt: FieldValue.serverTimestamp(),
    ...(startedChange === 0 ? {} : { tablesWithProgress: FieldValue.increment(startedChange) }),
    ...tableVersionUpdate(params.roundId, params.tableId, nextVersion),
  });
  // The tournament list shows only whether the tournament is complete. That can change only when a table changes.
  if (wasComplete !== isComplete) {
    transaction.set(db.collection("_meta").doc("dataVersions"), globalVersionUpdate("tournaments"), { merge: true });
  }
  transaction.set(tournamentRef.collection("changes").doc(), {
    ...buildSaveChangeEntry({
      roundId: params.roundId,
      tableId: params.tableId,
      version: nextVersion,
      actor: params.actor ?? null,
      tableBefore,
      tablePatch: params.tablePatch,
      handsBefore,
      handPatches: params.handPatches,
    }),
    at: FieldValue.serverTimestamp(),
  });

  // The answer comes from the data that the transaction wrote, so the client needs no second read.
  return {
    table: tableStateFromData({ ...mergedTable, ...derivedPoints, ...summary, version: nextVersion }),
    hands: handsFromData([...handsAfter.values()]),
  };
}

/** Keeps a record of a refused save, so the usage report can count conflicts. A failure here must not hide the conflict. */
export async function logTableConflict(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
  expectedVersion: number;
  actor: ChangeActor | null;
}): Promise<void> {
  try {
    await db.collection("tournaments").doc(params.tournamentId).collection("changes").add({
      ...buildConflictChangeEntry({
        roundId: params.roundId,
        tableId: params.tableId,
        version: params.expectedVersion,
        actor: params.actor,
      }),
      at: FieldValue.serverTimestamp(),
    });
  } catch {
    // The usage report is a best-effort record.
  }
}

export async function saveTableState(params: SaveTableStateParams): Promise<{ table: TableState; hands: TableHand[] }> {
  try {
    return await db.runTransaction((transaction) => saveTableStateInTransaction(transaction, params));
  } catch (error) {
    if (error instanceof StaleTableVersion) {
      await logTableConflict({ ...params, expectedVersion: error.expectedVersion, actor: params.actor ?? null });
      throw conflict("Table data changed on the server", {
        expectedVersion: error.expectedVersion,
        currentVersion: error.currentVersion,
        current: await getTableWithHands(params),
      });
    }
    throw error;
  }
}

const EMPTY_TABLE_FIELDS = {
  playerEastId: "",
  playerSouthId: "",
  playerWestId: "",
  playerNorthId: "",
  playerEastScore: "",
  playerSouthScore: "",
  playerWestScore: "",
  playerNorthScore: "",
  playerEastPoints: "",
  playerSouthPoints: "",
  playerWestPoints: "",
  playerNorthPoints: "",
  manualPlayerEastScore: "",
  manualPlayerSouthScore: "",
  manualPlayerWestScore: "",
  manualPlayerNorthScore: "",
  isCompleted: false,
  useTotalsOnly: false,
  hasProgress: false,
  hasValidManualTotals: false,
  completionStatus: "empty",
  bestHandScores: [] as number[],
  chickenHandCount: 0,
};

export async function resetTable(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
  actor?: ChangeActor | null;
}): Promise<void> {
  const tournamentRef = db.collection("tournaments").doc(params.tournamentId);
  const tableRef = tournamentRef.collection("tables").doc(tableVersionKey(params.roundId, params.tableId));
  // A transaction makes the reset atomic with saves. A save based on the old version fails after it.
  await db.runTransaction(async (transaction) => {
    const tableSnap = await transaction.get(tableRef);
    if (!tableSnap.exists) throw notFound("Table not found");
    const handSnapshots = await transaction.getAll(...handRefsOf(tableRef));
    if (handSnapshots.some((snapshot) => !snapshot.exists)) throw notFound("Hands not found");
    const tableBefore = tableSnap.data() ?? {};
    const nextVersion = Number(tableBefore.version ?? 0) + 1;
    const previous = calculateTableSummary(tableBefore, handSnapshots.map((hand) => hand.data() ?? {}));
    const statsPlan = Boolean(tableBefore.isCompleted)
      ? await prepareTableStats(transaction, {
        tournamentId: params.tournamentId,
        roundId: params.roundId,
        tableId: params.tableId,
        table: null,
        hands: [],
      })
      : null;
    statsPlan?.commit(transaction);

    transaction.update(tableRef, {
      ...EMPTY_TABLE_FIELDS,
      version: nextVersion,
      updatedAt: FieldValue.serverTimestamp(),
    });
    handSnapshots.forEach((hand) => {
      transaction.update(hand.ref, {
        ...defaultHand(Number(hand.get("handId") ?? hand.id)),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });
    const startedChange = progressDelta(previous.hasProgress, false);
    transaction.update(tournamentRef, {
      isCompleted: false,
      updatedAt: FieldValue.serverTimestamp(),
      ...(startedChange === 0 ? {} : { tablesWithProgress: FieldValue.increment(startedChange) }),
      ...tableVersionUpdate(params.roundId, params.tableId, nextVersion),
    });
    if (Boolean(tableBefore.isCompleted)) {
      transaction.set(db.collection("_meta").doc("dataVersions"), globalVersionUpdate("tournaments"), { merge: true });
    }
    transaction.set(tournamentRef.collection("changes").doc(), {
      ...buildResetChangeEntry({
        roundId: params.roundId,
        tableId: params.tableId,
        version: nextVersion,
        actor: params.actor ?? null,
      }),
      at: FieldValue.serverTimestamp(),
    });
  });
}
