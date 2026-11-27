import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { badRequest, notFound } from "../api/httpError";
import { conflict } from "../api/httpError";
import {
  calculateTableCompletionStatus,
  hasFourValidScores,
  calculateHandSummary,
} from "./tournamentContentRules";
import { canBeDone, seatIdsOf, tablePointsOf } from "./handScoring";
import { globalVersionUpdate, tableVersionKey, tableVersionUpdate } from "./dataVersionsService";
import { prepareTableStats } from "./statisticsStore";
import { buildConflictChangeEntry, buildResetChangeEntry, buildSaveChangeEntry, type ChangeActor } from "./changeLog";
import { historyExpireAt } from "./historyRetention";

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
  /** When the hand was marked done, in milliseconds since 1970. Null for a hand not done, or done before this was kept. */
  doneAtMs: number | null;
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
  /** Slot id of a substitute who left during this session. That seat gets 0 table points. Empty for most tables. */
  forcedZeroPlayerId: string;
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
    forcedZeroPlayerId: text("forcedZeroPlayerId"),
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
      doneAtMs: typeof d.doneAtMs === "number" ? d.doneAtMs : null,
    }))
    .sort((a, b) => a.handId - b.handId);
}

export const HANDS_PER_TABLE = 16;

/**
 * The time stamp of each hand that a save changes: a hand that becomes done gets [nowMs], a hand that stops being done
 * loses its stamp, and a hand that stays done keeps the one it had. The statistics measure the time per hand with it.
 */
export function doneStamps(
  handsBefore: Map<string, Data>,
  handsAfter: Map<string, Data>,
  handIds: number[],
  nowMs: number,
): Map<number, number | null> {
  const stamps = new Map<number, number | null>();
  handIds.forEach((handId) => {
    const before = handsBefore.get(String(handId)) ?? {};
    const after = handsAfter.get(String(handId)) ?? {};
    const wasDone = Boolean(before.isDone);
    const isDone = Boolean(after.isDone);
    if (isDone && !wasDone) stamps.set(handId, nowMs);
    else if (!isDone && wasDone) stamps.set(handId, null);
    else if (isDone && typeof before.doneAtMs === "number") stamps.set(handId, before.doneAtMs);
  });
  return stamps;
}

/**
 * The table document also keeps a copy of its 16 hands, so a read of many tables costs one document per table
 * instead of 17. The hand documents stay the source: every write reads and writes them in a transaction.
 * The copy counts only while its version is the version of the table. A writer that changes the hands without the
 * copy must raise the version of the table, and the readers then read the hand documents, as before the copy.
 */
export const HANDS_COPY_FIELD = "handsCopy";

/** The update that stores the copy of the hands for a table of [version]. */
export function handsCopyUpdate(version: number, hands: Data[]): Data {
  return { [HANDS_COPY_FIELD]: { version, hands: handsFromData(hands) } };
}

/** The hands kept in a table document, or null when there is no valid copy and the hand documents must be read. */
export function handsFromCopy(table: Data): TableHand[] | null {
  const copy = table[HANDS_COPY_FIELD] as { version?: unknown; hands?: unknown } | undefined;
  if (copy == null || !Array.isArray(copy.hands) || copy.hands.length !== HANDS_PER_TABLE) return null;
  if (Number(copy.version) !== Number(table.version ?? 0)) return null;
  return handsFromData(copy.hands as Data[]);
}

/** The update that keeps a valid copy valid when a writer raises the version without touching the hands. */
export function handsCopyVersionUpdate(table: Data, nextVersion: number): Data {
  return handsFromCopy(table) == null ? {} : { [`${HANDS_COPY_FIELD}.version`]: nextVersion };
}

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
    doneAtMs: null,
  };
}

export async function getTableWithHands(params: {
  tournamentId: string;
  roundId: number;
  tableId: number;
}): Promise<{ table: TableState; hands: TableHand[] }> {
  const tableDocId = `${params.roundId}_${params.tableId}`;
  const tableRef = db.collection("tournaments").doc(params.tournamentId).collection("tables").doc(tableDocId);
  const tableSnap = await tableRef.get();
  if (!tableSnap.exists) throw notFound("Table not found");
  const data = tableSnap.data() as Data;
  const hands = handsFromCopy(data) ?? handsFromData((await tableRef.collection("hands").get()).docs.map((doc) => doc.data()));
  return { table: tableStateFromData(data), hands };
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

// Firestore reads at most this many documents well in one batch call.
const HAND_READ_BATCH = 500;

/**
 * Reads the hands of many tables by document id, in a few batch calls instead of one query per table.
 * Every table has the hands 1 to 16, so the ids are known. A hand that does not exist is left out, like a query does.
 */
async function readTablesWithHands(
  tableDocs: FirebaseFirestore.QueryDocumentSnapshot[],
): Promise<{ table: TableState; hands: TableHand[] }[]> {
  // Most tables keep a valid copy of their hands. Only the others need their hand documents.
  const copies = new Map(tableDocs.map((tableSnap) => [tableSnap.ref.path, handsFromCopy(tableSnap.data())]));
  const handRefs = tableDocs.filter((tableSnap) => copies.get(tableSnap.ref.path) == null).flatMap((tableSnap) => handRefsOf(tableSnap.ref));
  const batches: FirebaseFirestore.DocumentReference[][] = [];
  for (let start = 0; start < handRefs.length; start += HAND_READ_BATCH) {
    batches.push(handRefs.slice(start, start + HAND_READ_BATCH));
  }
  const handSnaps = (await Promise.all(batches.map((refs) => db.getAll(...refs)))).flat();
  const handsByTable = new Map<string, Array<Record<string, unknown>>>();
  for (const handSnap of handSnaps) {
    const tablePath = handSnap.ref.parent.parent?.path;
    if (!handSnap.exists || tablePath == null) continue;
    const hands = handsByTable.get(tablePath) ?? [];
    hands.push(handSnap.data() as Data);
    handsByTable.set(tablePath, hands);
  }
  const tables = tableDocs.map((tableSnap) => ({
    table: tableStateFromData(tableSnap.data()),
    hands: copies.get(tableSnap.ref.path) ?? handsFromData(handsByTable.get(tableSnap.ref.path) ?? []),
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
/**
 * The writes of a table save to documents that every table shares: the tournament (the versions that polling clients
 * compare, the counters, the end of the tournament) and the global versions. In the transaction, the saves of all the
 * tables of a tournament waited for each other on the tournament document, and with 60 tables Firestore aborted some
 * of them ("cross-transaction contention": 509 errors 500 in the large game day). [runTableTransaction] writes them
 * right after the transaction instead.
 *
 * The writes are blind (increments and fields of one table), so their order between tables does not matter. When one
 * still fails after its retries, the next save of that table writes its version again.
 */
const sharedWrites = new WeakMap<FirebaseFirestore.Transaction, Array<() => Promise<unknown>>>();

/** A transaction that saves tables, with the writes to the shared documents after it. See [sharedWrites]. */
export async function runTableTransaction<T>(work: (transaction: FirebaseFirestore.Transaction) => Promise<T>): Promise<T> {
  let writes: Array<() => Promise<unknown>> = [];
  const result = await db.runTransaction(async (transaction) => {
    // A transaction that Firestore runs again starts with no writes: only the attempt that commits counts.
    writes = [];
    sharedWrites.set(transaction, writes);
    return work(transaction);
  });
  await Promise.all(writes.map((write) => writeWithRetries(write)));
  return result;
}

async function writeWithRetries(write: () => Promise<unknown>, attempts = 5): Promise<void> {
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      await write();
      return;
    } catch (error) {
      if (attempt === attempts) {
        console.error("A shared write after a table save failed", { error });
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 100 * 2 ** attempt + Math.random() * 100));
    }
  }
}

/**
 * Sets whether the tournament is complete from its open tables, after a save that completed a table or opened it again.
 * It runs after the commit of that save. The settles of one tournament wait for each other on the tournament document,
 * and each one looks at the tables after its own save committed, so the last one sees every save before it: of two
 * last tables that complete at the same moment, the second one completes the tournament. The query of the open tables
 * is not part of the transaction, so it locks no table and the hand saves of the other tables go on.
 */
export async function settleTournamentCompletion(tournamentRef: FirebaseFirestore.DocumentReference): Promise<void> {
  await db.runTransaction(async (transaction) => {
    const tournament = await transaction.get(tournamentRef);
    if (!tournament.exists) return;
    const open = await tournamentRef.collection("tables").where("isCompleted", "==", false).limit(1).get();
    const complete = open.empty;
    if (Boolean(tournament.get("isCompleted")) !== complete) {
      transaction.update(tournamentRef, { isCompleted: complete, updatedAt: FieldValue.serverTimestamp() });
    }
  });
}

/** Writes to a shared document after the transaction when [runTableTransaction] runs it, or in it otherwise. */
function writeShared(
  transaction: FirebaseFirestore.Transaction,
  ref: FirebaseFirestore.DocumentReference,
  data: Record<string, unknown>,
  kind: "update" | "merge",
  inTransaction = false,
): void {
  const later = sharedWrites.get(transaction);
  if (later == null || inTransaction) {
    if (kind === "update") transaction.update(ref, data);
    else transaction.set(ref, data, { merge: true });
    return;
  }
  later.push(() => (kind === "update" ? ref.update(data) : ref.set(data, { merge: true })));
}

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
    handSnapshots.map((snapshot) => [snapshot.id, snapshot.data() as Data] as [string, Data]),
  );
  if (params.handPatches.some((item) => !handsBefore.has(String(item.handId)))) {
    throw notFound("Hand not found");
  }

  const tableBefore = tableSnapshot.data() as Data;
  const mergedTable: Data = { ...tableBefore, ...params.tablePatch };
  const handsAfter = new Map(handsBefore);
  params.handPatches.forEach((item) => {
    const key = String(item.handId);
    handsAfter.set(key, { ...handsBefore.get(key), ...item.patch, handId: item.handId });
  });
  // A hand can be done only with a result that counts (0 for a draw) and complete penalties. Only the hands of this
  // save are checked, so an old hand saved before the rule does not block other changes of the table.
  const seats = seatIdsOf(mergedTable);
  params.handPatches.forEach((item) => {
    const hand = handsAfter.get(String(item.handId)) as Data;
    if (Boolean(hand.isDone) && (seats == null || !canBeDone(hand, seats))) {
      throw badRequest(`Hand ${item.handId} cannot be done: it needs a valid result and complete penalties`);
    }
  });
  const stamps = doneStamps(handsBefore, handsAfter, params.handPatches.map((item) => item.handId), Date.now());
  stamps.forEach((doneAtMs, handId) => {
    const key = String(handId);
    handsAfter.set(key, { ...handsAfter.get(key), doneAtMs });
  });
  // A table with every hand done is completed, whether the hands come from the editor or from a phone. The organizer
  // does not need the Completed switch for it. A totals-only table has no hands to count.
  const everyHandDone = !Boolean(mergedTable.useTotalsOnly) && [...handsAfter.values()].every((hand) => Boolean(hand.isDone));
  const tablePatch = everyHandDone && !Boolean(mergedTable.isCompleted)
    ? { ...params.tablePatch, isCompleted: true }
    : params.tablePatch;
  if (everyHandDone) mergedTable.isCompleted = true;
  const previousSummary = calculateTableSummary(tableBefore, [...handsBefore.values()]);
  const summary = calculateTableSummary(mergedTable, [...handsAfter.values()]);
  // The table points are written on every save, from the scores of the merged table.
  const derivedPoints = tablePointsOf(mergedTable);
  const wasComplete = Boolean(tableBefore.isCompleted);
  const isComplete = Boolean(mergedTable.isCompleted);

  // Only a table that becomes complete can finish the tournament. Other saves skip this query,
  // so editors of different tables do not lock each other. In a transaction of [runTableTransaction] the query runs
  // after the commit instead (see [settleTournamentCompletion]): in the transaction it locked the first open tables of
  // the tournament, which are the tables in play in the last rounds, and their hand saves were aborted.
  const deferCompletion = sharedWrites.has(transaction);
  let tournamentIsComplete = false;
  if (isComplete && !deferCompletion) {
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
    ...tablePatch,
    ...derivedPoints,
    ...summary,
    ...editedBy,
    ...handsCopyUpdate(nextVersion, [...handsAfter.values()]),
    version: nextVersion,
    updatedAt: FieldValue.serverTimestamp(),
  });
  params.handPatches.forEach((item) => {
    transaction.update(tableRef.collection("hands").doc(String(item.handId)), {
      ...item.patch,
      ...(stamps.has(item.handId) ? { doneAtMs: stamps.get(item.handId) ?? null } : {}),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  statsPlan?.commit(transaction);
  const startedChange = progressDelta(previousSummary.hasProgress, summary.hasProgress);
  // The end of the tournament can change only when the last table completes or a completed table opens again. Then the
  // tournament is written in the transaction, with the query of the open tables above, so two such saves cannot cross.
  // Every other save leaves the end of the tournament as it is and writes the rest after the transaction.
  const completionChanges = !deferCompletion && (tournamentIsComplete || (wasComplete && !isComplete));
  if (deferCompletion && wasComplete !== isComplete) {
    // The tournament list changes its version once the end of the tournament is settled.
    sharedWrites.get(transaction)?.push(async () => {
      await settleTournamentCompletion(tournamentRef);
      await db.collection("_meta").doc("dataVersions").set(globalVersionUpdate("tournaments"), { merge: true });
    });
  }
  writeShared(transaction, tournamentRef, {
    ...(completionChanges ? { isCompleted: tournamentIsComplete } : {}),
    updatedAt: FieldValue.serverTimestamp(),
    ...(startedChange === 0 ? {} : { tablesWithProgress: FieldValue.increment(startedChange) }),
    ...tableVersionUpdate(params.roundId, params.tableId, nextVersion),
  }, "update", completionChanges);
  // The tournament list shows only whether the tournament is complete. That can change only when a table changes.
  if (wasComplete !== isComplete && !deferCompletion) {
    writeShared(transaction, db.collection("_meta").doc("dataVersions"), globalVersionUpdate("tournaments"), "merge");
  }
  transaction.set(tournamentRef.collection("changes").doc(), {
    ...buildSaveChangeEntry({
      roundId: params.roundId,
      tableId: params.tableId,
      version: nextVersion,
      actor: params.actor ?? null,
      tableBefore,
      tablePatch,
      handsBefore,
      handPatches: params.handPatches,
    }),
    at: FieldValue.serverTimestamp(),
    expireAt: historyExpireAt(),
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
      expireAt: historyExpireAt(),
    });
  } catch {
    // The usage report is a best-effort record.
  }
}

export async function saveTableState(params: SaveTableStateParams): Promise<{ table: TableState; hands: TableHand[] }> {
  try {
    return await runTableTransaction((transaction) => saveTableStateInTransaction(transaction, params));
  } catch (error) {
    if (error instanceof StaleTableVersion) {
      await logTableConflict({ ...params, expectedVersion: error.expectedVersion, actor: params.actor ?? null });
      throw conflict("Table data changed on the server", {
        code: "TABLE_CHANGED",
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
    const tableBefore = tableSnap.data() as Data;
    const nextVersion = Number(tableBefore.version ?? 0) + 1;
    const previous = calculateTableSummary(tableBefore, handSnapshots.map((hand) => hand.data() as Data));
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
      ...handsCopyUpdate(nextVersion, handSnapshots.map((hand) => defaultHand(Number(hand.get("handId") ?? hand.id)))),
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
      expireAt: historyExpireAt(),
    });
  });
}
