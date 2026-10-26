import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { seatIdsOf } from "./handScoring";
import { readSubstitution, slotResultIgnored, substitutePlays } from "./playerSubstitution";
import { buildTableDigest, DIGEST_VERSION, type DigestResult, type DigestTable, type TableDigest } from "./statisticsDigest";

/**
 * The statistics store. The server keeps the statistics up to date when a table changes,
 * in the same transaction that saves the table. A request never builds statistics.
 *
 * - `statsTables/{tournamentId}_{round}_{table}`: the digest of one completed table.
 * - `statsPlayers/{emaId}`: the sums of one player over all tournaments, and the values of the leaderboards.
 * - `statsTournaments/{tournamentId}`: points and score of every slot, for the standings.
 * - `statsGlobal/overview-{n}`: sums of all tournaments, split in shards so that many saves do not wait for each other.
 */

type Data = Record<string, unknown>;

/** A leaderboard shows a rate only for a player with at least this many hands. Same value as the domain module. */
export const MIN_HANDS_FOR_LEADERBOARD = 8;
const GLOBAL_SHARDS = 8;
const LEADERBOARD_SIZE = 5;

const tablesCollection = () => db.collection("statsTables");
const playersCollection = () => db.collection("statsPlayers");
const tournamentsCollection = () => db.collection("statsTournaments");
const globalCollection = () => db.collection("statsGlobal");

function tableDocId(tournamentId: string, roundId: number, tableId: number): string {
  return `${tournamentId}_${roundId}_${tableId}`;
}

function shardOf(id: string): number {
  let hash = 0;
  for (let index = 0; index < id.length; index += 1) hash = (hash * 31 + id.charCodeAt(index)) >>> 0;
  return hash % GLOBAL_SHARDS;
}

type StoredTable = {
  tournamentId: string;
  roundId: number;
  tableId: number;
  version: number;
  emaIds: string[];
  bestHand: number;
  summary: DigestTable;
  results: DigestResult[];
};

/** The sums of one player. */
type PlayerTotals = {
  tables: number;
  points: number;
  score: number;
  place1: number;
  place2: number;
  place3: number;
  place4: number;
  tablesWithHands: number;
  handsPlayed: number;
  handsWon: number;
  selfDraws: number;
  dealIns: number;
  penaltyCount: number;
};

const TOTAL_FIELDS: Array<keyof PlayerTotals> = [
  "tables", "points", "score", "place1", "place2", "place3", "place4",
  "tablesWithHands", "handsPlayed", "handsWon", "selfDraws", "dealIns", "penaltyCount",
];

const emptyTotals = (): PlayerTotals => ({
  tables: 0, points: 0, score: 0, place1: 0, place2: 0, place3: 0, place4: 0,
  tablesWithHands: 0, handsPlayed: 0, handsWon: 0, selfDraws: 0, dealIns: 0, penaltyCount: 0,
});

const round6 = (value: number): number => Math.round(value * 1e6) / 1e6;

function totalsOf(data: Data | undefined): PlayerTotals {
  const totals = emptyTotals();
  TOTAL_FIELDS.forEach((field) => { totals[field] = Number(data?.[field] ?? 0) || 0; });
  return totals;
}

/** Adds ([sign] 1) or removes ([sign] -1) the result of one table seat. */
function addResult(totals: PlayerTotals, result: DigestResult, sign: 1 | -1): void {
  totals.tables += sign;
  totals.points = round6(totals.points + sign * result.points);
  totals.score += sign * result.score;
  const place = `place${Math.min(Math.max(result.position, 1), 4)}` as "place1" | "place2" | "place3" | "place4";
  totals[place] += sign;
  if (result.hasHands) {
    totals.tablesWithHands += sign;
    totals.handsPlayed += sign * result.handsPlayed;
    totals.handsWon += sign * result.handsWon;
    totals.selfDraws += sign * result.selfDraws;
    totals.dealIns += sign * result.dealIns;
    totals.penaltyCount += sign * result.penaltyCount;
  }
}

/** The stored player document: the sums and the fields that the leaderboard queries sort by. */
function playerDocument(emaId: string, totals: PlayerTotals): Data {
  const eligible = totals.handsPlayed >= MIN_HANDS_FOR_LEADERBOARD;
  return {
    emaId,
    ...totals,
    // A field that is missing keeps the player out of the query of the leaderboard.
    ...(eligible ? {
      leaderWinRate: totals.handsWon / totals.handsPlayed,
      leaderDealInRate: totals.dealIns / totals.handsPlayed,
    } : {}),
    updatedAt: FieldValue.serverTimestamp(),
  };
}

function stableJson(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(stableJson).join(",")}]`;
  if (value != null && typeof value === "object") {
    const record = value as Record<string, unknown>;
    return `{${Object.keys(record).sort().map((key) => `${JSON.stringify(key)}:${stableJson(record[key])}`).join(",")}}`;
  }
  return JSON.stringify(value) ?? "null";
}

const intOf = (value: string): number | null => (/^-?\d+$/.test(value.trim()) ? Number(value.trim()) : null);

export type TableStatsParams = {
  tournamentId: string;
  roundId: number;
  tableId: number;
  /** The table after the change. Null when the table is removed. */
  table: Data | null;
  hands: Data[];
};

/** The writes of one statistics update. The reads are already done. */
export type TableStatsPlan = { commit: (transaction: FirebaseFirestore.Transaction) => void };

/**
 * Reads what the update needs and works out the writes. A transaction must finish all reads before the first write,
 * so the caller runs this before its own writes and then calls `commit`. It returns null when nothing changes.
 * A table that is not complete and has no stored statistics returns null after one read.
 */
export async function prepareTableStats(
  transaction: FirebaseFirestore.Transaction,
  params: TableStatsParams,
): Promise<TableStatsPlan | null> {
  const ref = tablesCollection().doc(tableDocId(params.tournamentId, params.roundId, params.tableId));
  const oldSnapshot = await transaction.get(ref);
  const old = oldSnapshot.exists ? (oldSnapshot.data() as StoredTable) : null;
  const completed = params.table != null && Boolean(params.table.isCompleted);
  if (!completed && old == null) return null;

  let next: TableDigest | null = null;
  if (completed && params.table != null) {
    const slotIds = (seatIdsOf(params.table) ?? []).map(intOf);
    const emaBySlot = new Map<number, string>();
    const uncountedSlots = new Set<number>();
    const zeroedSlots = new Set<number>();
    if (slotIds.length === 4 && slotIds.every((id) => id != null)) {
      const slotSnapshots = await transaction.getAll(
        ...(slotIds as number[]).map((id) => db.collection("tournaments").doc(params.tournamentId).collection("players").doc(String(id))),
      );
      slotSnapshots.forEach((snapshot, index) => {
        const ema = snapshot.get("assignedEmaId");
        const slotId = (slotIds as number[])[index];
        const substitution = readSubstitution(snapshot.get("substitution"));
        // A substitute is not recorded. So the person has no personal statistics from the rounds of the substitute.
        // An excluded player has no personal statistics at all.
        const recorded = !substitutePlays(substitution, params.roundId) && substitution?.excludedFromEma !== true;
        emaBySlot.set(slotId, typeof ema === "string" && recorded ? ema.trim() : "");
        if (slotResultIgnored(substitution, params.roundId)) uncountedSlots.add(slotId);
        // A disqualified player gets 0 table points and 0 score. These zeros count for the statistics.
        if (substitution?.reason === "DISQUALIFIED") zeroedSlots.add(slotId);
      });
    }
    const digest = buildTableDigest({
      tournamentId: params.tournamentId, table: params.table, hands: params.hands, emaBySlot, uncountedSlots, zeroedSlots,
    });
    next = digest.results.length === 4 ? digest : null;
  }
  if (old == null && next == null) return null;
  if (old != null && next != null && old.version === DIGEST_VERSION
    && stableJson(old.results) === stableJson(next.results) && stableJson(old.summary) === stableJson(next.summary)) {
    return null;
  }

  const oldResults = old?.results ?? [];
  const newResults = next?.results ?? [];
  const emaIds = [...new Set([...oldResults, ...newResults].map((result) => result.emaId).filter((ema) => ema !== ""))];
  const playerSnapshots = emaIds.length === 0
    ? []
    : await transaction.getAll(...emaIds.map((ema) => playersCollection().doc(ema)));
  const players = new Map<string, PlayerTotals>(
    playerSnapshots.map((snapshot, index) => [emaIds[index], totalsOf(snapshot.data())] as [string, PlayerTotals]),
  );
  oldResults.filter((result) => result.emaId !== "").forEach((result) => addResult(players.get(result.emaId)!, result, -1));
  newResults.filter((result) => result.emaId !== "").forEach((result) => addResult(players.get(result.emaId)!, result, 1));

  const slotDelta = new Map<number, { points: number; score: number }>();
  const addSlot = (result: DigestResult, sign: 1 | -1) => {
    if (result.countsForSlot === false) return;
    const entry = slotDelta.get(result.slotId) ?? { points: 0, score: 0 };
    entry.points += sign * result.points;
    entry.score += sign * result.score;
    slotDelta.set(result.slotId, entry);
  };
  oldResults.forEach((result) => addSlot(result, -1));
  newResults.forEach((result) => addSlot(result, 1));

  const global = { tablesCompleted: 0, tablesWithHands: 0, handsPlayed: 0, drawnHands: 0, winningHands: 0, selfDraws: 0, winningScoreSum: 0, chickenHands: 0, penaltyCount: 0 };
  const addGlobal = (stored: { summary: DigestTable; results: DigestResult[] } | null, sign: 1 | -1) => {
    if (stored == null) return;
    global.tablesCompleted += sign;
    if (!stored.summary.hasHands) return;
    global.tablesWithHands += sign;
    global.handsPlayed += sign * stored.summary.handsPlayed;
    global.drawnHands += sign * stored.summary.drawnHands;
    global.winningHands += sign * stored.summary.winningHands;
    global.selfDraws += sign * (stored.summary.selfDraws ?? 0);
    global.winningScoreSum += sign * stored.summary.winningScoreSum;
    global.chickenHands += sign * stored.summary.chickenHands;
    global.penaltyCount += sign * stored.results.reduce((sum, result) => sum + result.penaltyCount, 0);
  };
  addGlobal(old, -1);
  addGlobal(next, 1);

  return {
    commit: (tx) => {
      if (next == null) {
        tx.delete(ref);
      } else {
        const stored: StoredTable = {
          tournamentId: params.tournamentId,
          roundId: params.roundId,
          tableId: params.tableId,
          version: DIGEST_VERSION,
          emaIds: [...new Set(next.results.map((result) => result.emaId).filter((ema) => ema !== ""))],
          bestHand: next.summary.bestHand,
          summary: next.summary,
          results: next.results,
        };
        tx.set(ref, { ...stored, updatedAt: FieldValue.serverTimestamp() });
      }
      players.forEach((totals, ema) => tx.set(playersCollection().doc(ema), playerDocument(ema, totals)));
      if (slotDelta.size > 0) {
        const totals: Record<string, { points: FieldValue; score: FieldValue }> = {};
        slotDelta.forEach((delta, slotId) => {
          totals[String(slotId)] = { points: FieldValue.increment(round6(delta.points)), score: FieldValue.increment(delta.score) };
        });
        tx.set(tournamentsCollection().doc(params.tournamentId), { totals }, { merge: true });
      }
      tx.set(globalCollection().doc(`overview-${shardOf(ref.id)}`), {
        tablesCompleted: FieldValue.increment(global.tablesCompleted),
        tablesWithHands: FieldValue.increment(global.tablesWithHands),
        handsPlayed: FieldValue.increment(global.handsPlayed),
        drawnHands: FieldValue.increment(global.drawnHands),
        winningHands: FieldValue.increment(global.winningHands),
        selfDraws: FieldValue.increment(global.selfDraws),
        winningScoreSum: FieldValue.increment(global.winningScoreSum),
        chickenHands: FieldValue.increment(global.chickenHands),
        penaltyCount: FieldValue.increment(global.penaltyCount),
      }, { merge: true });
    },
  };
}

/** Works out the statistics of one table from the stored table and its hands. Used to repair and to backfill. */
export async function refreshTableStats(tournamentId: string, tableDocument: string): Promise<void> {
  const tableRef = db.collection("tournaments").doc(tournamentId).collection("tables").doc(tableDocument);
  await db.runTransaction(async (transaction) => {
    const [tableSnapshot, handsSnapshot] = await Promise.all([transaction.get(tableRef), transaction.get(tableRef.collection("hands"))]);
    const data = tableSnapshot.exists ? (tableSnapshot.data() ?? {}) : null;
    const [roundId, tableId] = tableDocument.split("_").map(Number);
    const plan = await prepareTableStats(transaction, {
      tournamentId,
      roundId,
      tableId,
      table: data == null ? null : { ...data, roundId, tableId },
      hands: handsSnapshot.docs.map((doc) => doc.data()),
    });
    plan?.commit(transaction);
  });
}

/** Works out the statistics of every table of a tournament, one table at a time. */
export async function refreshTournamentStats(tournamentId: string): Promise<number> {
  const tables = await db.collection("tournaments").doc(tournamentId).collection("tables").select().get();
  for (const table of tables.docs) await refreshTableStats(tournamentId, table.id);
  return tables.size;
}

/** Removes the statistics of a tournament and takes its numbers out of the sums. */
export async function removeTournamentStats(tournamentId: string): Promise<void> {
  const stored = await tablesCollection().where("tournamentId", "==", tournamentId).select("roundId", "tableId").get();
  for (const doc of stored.docs) {
    await db.runTransaction(async (transaction) => {
      const plan = await prepareTableStats(transaction, {
        tournamentId,
        roundId: Number(doc.get("roundId")),
        tableId: Number(doc.get("tableId")),
        table: null,
        hands: [],
      });
      plan?.commit(transaction);
    });
  }
  await tournamentsCollection().doc(tournamentId).delete();
}

export type OverviewPlayer = PlayerTotals & { emaId: string };

export type Overview = {
  tournaments: number;
  tablesCompleted: number;
  tablesWithHands: number;
  handsPlayed: number;
  drawnHands: number;
  winningHands: number;
  selfDraws: number;
  winningScoreSum: number;
  chickenHands: number;
  penaltyCount: number;
  highestHand: number;
  /** The number of players with a statistics document. */
  playerCount: number;
  /** The players of the three leaderboards, each once. */
  players: OverviewPlayer[];
};

/** The statistics of all tournaments. It reads a fixed number of documents, whatever the number of tournaments. */
export async function loadOverview(): Promise<Overview> {
  const [shards, highest, tournamentCount, playerCount, winRate, dealInRate, selfDraws] = await Promise.all([
    globalCollection().get(),
    tablesCollection().orderBy("bestHand", "desc").limit(1).select("bestHand").get(),
    tournamentsCollection().count().get(),
    playersCollection().count().get(),
    playersCollection().orderBy("leaderWinRate", "desc").limit(LEADERBOARD_SIZE).get(),
    playersCollection().orderBy("leaderDealInRate", "asc").limit(LEADERBOARD_SIZE).get(),
    playersCollection().where("selfDraws", ">", 0).orderBy("selfDraws", "desc").limit(LEADERBOARD_SIZE).get(),
  ]);
  const sum = (field: string) => shards.docs.reduce((total, doc) => total + (Number(doc.get(field) ?? 0) || 0), 0);
  const players = new Map<string, OverviewPlayer>();
  [winRate, dealInRate, selfDraws].forEach((snapshot) => snapshot.docs.forEach((doc) => {
    players.set(doc.id, { emaId: doc.id, ...totalsOf(doc.data()) });
  }));
  return {
    tournaments: tournamentCount.data().count,
    tablesCompleted: sum("tablesCompleted"),
    tablesWithHands: sum("tablesWithHands"),
    handsPlayed: sum("handsPlayed"),
    drawnHands: sum("drawnHands"),
    winningHands: sum("winningHands"),
    selfDraws: sum("selfDraws"),
    winningScoreSum: sum("winningScoreSum"),
    chickenHands: sum("chickenHands"),
    penaltyCount: sum("penaltyCount"),
    highestHand: Number(highest.docs[0]?.get("bestHand") ?? 0) || 0,
    playerCount: playerCount.data().count,
    players: [...players.values()],
  };
}

export type PersonStatistics = {
  emaId: string;
  tournaments: Array<{ tournamentId: string; results: DigestResult[] }>;
};

/**
 * The results of one player in all tournaments. [allowedTournamentIds] limits the tournaments, or null for all.
 * It reads only the tables of this player. The final places come from [loadStandings], for the tournaments on screen.
 */
export async function loadPersonStatistics(emaId: string, allowedTournamentIds: Set<string> | null): Promise<PersonStatistics> {
  const snapshot = await tablesCollection().where("emaIds", "array-contains", emaId).get();
  const byTournament = new Map<string, DigestResult[]>();
  snapshot.docs.forEach((doc) => {
    const stored = doc.data() as StoredTable;
    if (allowedTournamentIds != null && !allowedTournamentIds.has(stored.tournamentId)) return;
    const mine = stored.results.filter((result) => result.emaId === emaId);
    byTournament.set(stored.tournamentId, [...(byTournament.get(stored.tournamentId) ?? []), ...mine]);
  });
  return {
    emaId,
    tournaments: [...byTournament.entries()].map(([tournamentId, results]) => ({
      tournamentId,
      results: results.sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId),
    })),
  };
}

/** The most tournaments that one standings request can ask for. */
export const MAX_STANDINGS_PER_REQUEST = 25;

export type PersonStanding = {
  tournamentId: string;
  /** Place of the player in the tournament and the number of players with a result. */
  rank: number;
  of: number;
  /** The points and the score of the player in the tournament. */
  points: number;
  score: number;
};

/** The final place of one player in each of the given tournaments. A tournament without a result of the player is left out. */
export async function loadStandings(emaId: string, tournamentIds: string[]): Promise<PersonStanding[]> {
  const ids = [...new Set(tournamentIds)].slice(0, MAX_STANDINGS_PER_REQUEST);
  const standings = await Promise.all(ids.map(async (tournamentId): Promise<PersonStanding | null> => {
    const [slots, totalsSnapshot] = await Promise.all([
      db.collection("tournaments").doc(tournamentId).collection("players").where("assignedEmaId", "==", emaId).limit(1).get(),
      tournamentsCollection().doc(tournamentId).get(),
    ]);
    if (slots.empty) return null;
    const totals = (totalsSnapshot.get("totals") ?? {}) as Record<string, { points?: number; score?: number }>;
    const rows = Object.entries(totals).map(([slot, value]) => ({
      slot: Number(slot), points: round6(Number(value.points ?? 0)), score: Number(value.score ?? 0),
    }));
    const mine = rows.find((row) => row.slot === Number(slots.docs[0].id));
    if (mine == null) return null;
    const better = rows.filter((row) => row.points > mine.points || (row.points === mine.points && row.score > mine.score)).length;
    return { tournamentId, rank: better + 1, of: rows.length, points: mine.points, score: mine.score };
  }));
  return standings.filter((standing): standing is PersonStanding => standing != null);
}
