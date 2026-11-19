import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import type { DigestResult, DigestTable, HandRun } from "./statisticsDigest";
import type { Tournament } from "./tournamentsService";

/**
 * The statistics of any group of tournaments and players: the tournaments of a country or of some dates, the players
 * of a country, or both together.
 *
 * Each tournament keeps one summary, `statsTournamentSummaries/{tournamentId}`: the sums of its tables and the sums
 * and best values of every person in it. A summary is built from the table digests the first time someone asks for it
 * after the tournament changed, so a table save never waits for it. A filter reads one summary for each tournament
 * that it keeps, and the country of each person comes from the EMA registry when the filter runs, so a change of
 * country counts at once.
 */

/** Change this number when the summary rules change. A stored summary with another number is built again. */
export const SUMMARY_VERSION = 1;

const summariesCollection = () => db.collection("statsTournamentSummaries");

/** The sums of the tables of some tournaments. */
export type GroupTables = {
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
};

/** The sums and the best values of one person in some tournaments. */
export type GroupPerson = {
  emaId: string;
  tournaments: number;
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
  drawnHands: number;
  winningScoreSum: number;
  chickenHands: number;
  dealInScoreSum: number;
  lostToSelfDraws: number;
  otherWins: number;
  penaltyCount: number;
  penaltyPoints: number;
  /** The best values. A run counts inside one tournament. */
  bestHand: number;
  bestTableScore: number | null;
  mostHandsWonAtATable: number | null;
  maxPenalty: number;
  tableWinRun: number;
  handWinRun: number | null;
  noDealInRun: number | null;
};

type StoredSummary = {
  tournamentId: string;
  version: number;
  revision: number;
  tables: GroupTables;
  persons: GroupPerson[];
};

const SUM_FIELDS = [
  "tables", "points", "score", "place1", "place2", "place3", "place4", "tablesWithHands", "handsPlayed", "handsWon",
  "selfDraws", "dealIns", "drawnHands", "winningScoreSum", "chickenHands", "dealInScoreSum", "lostToSelfDraws",
  "otherWins", "penaltyCount", "penaltyPoints", "tournaments",
] as const;

const TABLE_FIELDS: Array<Exclude<keyof GroupTables, "highestHand">> = [
  "tablesCompleted", "tablesWithHands", "handsPlayed", "drawnHands", "winningHands", "selfDraws", "winningScoreSum",
  "chickenHands", "penaltyCount",
];

export const emptyTables = (): GroupTables => ({
  tablesCompleted: 0, tablesWithHands: 0, handsPlayed: 0, drawnHands: 0, winningHands: 0, selfDraws: 0,
  winningScoreSum: 0, chickenHands: 0, penaltyCount: 0, highestHand: 0,
});

const emptyPerson = (emaId: string): GroupPerson => ({
  emaId, tournaments: 0, tables: 0, points: 0, score: 0, place1: 0, place2: 0, place3: 0, place4: 0,
  tablesWithHands: 0, handsPlayed: 0, handsWon: 0, selfDraws: 0, dealIns: 0, drawnHands: 0, winningScoreSum: 0,
  chickenHands: 0, dealInScoreSum: 0, lostToSelfDraws: 0, otherWins: 0, penaltyCount: 0, penaltyPoints: 0,
  bestHand: 0, bestTableScore: null, mostHandsWonAtATable: null, maxPenalty: 0, tableWinRun: 0, handWinRun: null, noDealInRun: null,
});

const round6 = (value: number): number => Math.round(value * 1e6) / 1e6;
const maxOrNull = (a: number | null, b: number | null): number | null => (a == null ? b : b == null ? a : Math.max(a, b));

/** The longest run of true values. */
function longestRun(flags: boolean[]): number {
  let best = 0;
  let current = 0;
  flags.forEach((flag) => {
    current = flag ? current + 1 : 0;
    best = Math.max(best, current);
  });
  return best;
}

/** Joins the runs of tables in play order. A table that is one full run continues the run of the table before it. */
export function joinRuns(tables: Array<{ run: HandRun; hands: number }>): number {
  let best = 0;
  let open = 0;
  tables.forEach(({ run, hands }) => {
    if (run.best === hands) {
      open += hands;
    } else {
      best = Math.max(best, open + run.start, run.best);
      open = run.end;
    }
  });
  return Math.max(best, open);
}

type DigestDoc = { roundId: number; tableId: number; summary: DigestTable; results: DigestResult[] };

/** Works out the summary of one tournament from the digests of its tables. */
export function summaryOf(digests: DigestDoc[]): { tables: GroupTables; persons: GroupPerson[] } {
  const tables = emptyTables();
  const byPerson = new Map<string, DigestResult[]>();
  [...digests].sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId).forEach((digest) => {
    tables.tablesCompleted += 1;
    tables.highestHand = Math.max(tables.highestHand, digest.summary.bestHand ?? 0);
    if (digest.summary.hasHands) {
      tables.tablesWithHands += 1;
      tables.handsPlayed += digest.summary.handsPlayed;
      tables.drawnHands += digest.summary.drawnHands;
      tables.winningHands += digest.summary.winningHands;
      tables.selfDraws += digest.summary.selfDraws ?? 0;
      tables.winningScoreSum += digest.summary.winningScoreSum;
      tables.chickenHands += digest.summary.chickenHands;
      tables.penaltyCount += digest.results.reduce((sum, result) => sum + result.penaltyCount, 0);
    }
    digest.results.filter((result) => result.emaId !== "").forEach((result) => {
      byPerson.set(result.emaId, [...(byPerson.get(result.emaId) ?? []), result]);
    });
  });
  const persons = [...byPerson.entries()].map(([emaId, results]) => {
    const person = emptyPerson(emaId);
    person.tournaments = 1;
    results.forEach((result) => {
      person.tables += 1;
      person.points = round6(person.points + result.points);
      person.score += result.score;
      const place = `place${Math.min(Math.max(result.position, 1), 4)}` as "place1" | "place2" | "place3" | "place4";
      person[place] += 1;
      person.bestTableScore = maxOrNull(person.bestTableScore, result.score);
      if (!result.hasHands) return;
      person.tablesWithHands += 1;
      person.handsPlayed += result.handsPlayed;
      person.handsWon += result.handsWon;
      person.selfDraws += result.selfDraws;
      person.dealIns += result.dealIns;
      person.drawnHands += result.drawnHands;
      person.winningScoreSum += result.winningScoreSum;
      person.chickenHands += result.chickenHands;
      person.dealInScoreSum += result.dealInScoreSum;
      person.lostToSelfDraws += result.lostToSelfDraws;
      person.otherWins += result.otherWins;
      person.penaltyCount += result.penaltyCount;
      person.penaltyPoints += result.penaltyPoints;
      person.bestHand = Math.max(person.bestHand, result.bestHand);
      person.maxPenalty = Math.max(person.maxPenalty, result.maxPenalty);
      person.mostHandsWonAtATable = maxOrNull(person.mostHandsWonAtATable, result.handsWon);
    });
    person.tableWinRun = longestRun(results.map((result) => result.position === 1));
    const withHands = results.filter((result) => result.hasHands);
    if (withHands.length > 0) {
      person.handWinRun = joinRuns(withHands.map((result) => ({ run: result.winRun, hands: result.handsPlayed })));
      person.noDealInRun = joinRuns(withHands.map((result) => ({ run: result.noDealInRun, hands: result.handsPlayed })));
    }
    return person;
  });
  return { tables, persons };
}

/** Adds the numbers of [from] to [into]. Sums add up and best values keep the best. */
export function addPerson(into: GroupPerson, from: GroupPerson): void {
  SUM_FIELDS.forEach((field) => { into[field] = round6(into[field] + from[field]); });
  into.bestHand = Math.max(into.bestHand, from.bestHand);
  into.maxPenalty = Math.max(into.maxPenalty, from.maxPenalty);
  into.tableWinRun = Math.max(into.tableWinRun, from.tableWinRun);
  into.bestTableScore = maxOrNull(into.bestTableScore, from.bestTableScore);
  into.mostHandsWonAtATable = maxOrNull(into.mostHandsWonAtATable, from.mostHandsWonAtATable);
  into.handWinRun = maxOrNull(into.handWinRun, from.handWinRun);
  into.noDealInRun = maxOrNull(into.noDealInRun, from.noDealInRun);
}

/** The revision of the statistics of a tournament. Every change of a table digest raises it. */
const revisionOf = (snapshot: FirebaseFirestore.DocumentSnapshot): number => Number(snapshot.get("revision") ?? 0) || 0;

/** The summaries of some tournaments. A summary that is missing or older than its tournament is built again and stored. */
export async function loadTournamentSummaries(tournamentIds: string[]): Promise<StoredSummary[]> {
  if (tournamentIds.length === 0) return [];
  const [states, stored] = await Promise.all([
    db.getAll(...tournamentIds.map((id) => db.collection("statsTournaments").doc(id)), { fieldMask: ["revision"] }),
    db.getAll(...tournamentIds.map((id) => summariesCollection().doc(id))),
  ]);
  return Promise.all(tournamentIds.map(async (tournamentId, index) => {
    const revision = revisionOf(states[index]);
    const summary = stored[index].exists ? (stored[index].data() as StoredSummary) : null;
    if (summary != null && summary.version === SUMMARY_VERSION && summary.revision === revision) return summary;
    const digests = await db.collection("statsTables").where("tournamentId", "==", tournamentId).get();
    const built: StoredSummary = {
      tournamentId, version: SUMMARY_VERSION, revision,
      ...summaryOf(digests.docs.map((doc) => doc.data() as DigestDoc)),
    };
    await summariesCollection().doc(tournamentId).set({ ...built, updatedAt: FieldValue.serverTimestamp() });
    return built;
  }));
}

/** Removes the summary of a tournament. */
export async function removeTournamentSummary(tournamentId: string): Promise<void> {
  await summariesCollection().doc(tournamentId).delete();
}

/** One tournament that a filter can keep. */
export type GroupTournament = { id: string; country: string; startDate: string | null };

/** The fields of a tournament that the filters read. */
export function groupTournament(tournament: Pick<Tournament, "id" | "hostCountry" | "eventStartDate">): GroupTournament {
  return { id: tournament.id, country: String(tournament.hostCountry ?? "").toUpperCase(), startDate: tournament.eventStartDate ?? null };
}

/** What a group keeps. A field left out keeps everything. */
export type GroupFilter = {
  tournamentIds?: string[];
  /** Two-letter ISO code of the country where the tournaments were played. */
  tournamentCountry?: string;
  /** EMA code of the country of the players. An empty code keeps the players without a country. */
  playerCountry?: string;
  /** ISO dates. A tournament counts when its first day is between them. */
  from?: string;
  to?: string;
};

export type GroupStatistics = {
  /** The tournaments that the filter keeps and that have a completed table. */
  tournamentIds: string[];
  /** The sums of all tables of those tournaments, whoever played them. */
  tables: GroupTables;
  /** The people that the filter keeps, with their numbers in those tournaments. */
  persons: GroupPerson[];
};

/** The tournaments of [tournaments] that [filter] keeps. */
export function tournamentsOf(tournaments: GroupTournament[], filter: GroupFilter): GroupTournament[] {
  const ids = filter.tournamentIds == null ? null : new Set(filter.tournamentIds);
  return tournaments.filter((tournament) => {
    if (ids != null && !ids.has(tournament.id)) return false;
    if (filter.tournamentCountry != null && tournament.country.toUpperCase() !== filter.tournamentCountry) return false;
    const start = tournament.startDate ?? "";
    if (filter.from != null && (start === "" || start < filter.from)) return false;
    if (filter.to != null && (start === "" || start > filter.to)) return false;
    return true;
  });
}

/** The EMA numbers of the people of one country in the registry. */
async function emaIdsOfCountry(country: string): Promise<Set<string>> {
  const snapshot = await db.collection(EMA_PLAYER_REGISTRY_COLLECTION).where("country", "==", country).select().get();
  return new Set(snapshot.docs.map((doc) => doc.id));
}

/**
 * The statistics of a group. [tournaments] are the tournaments that the caller may see. [hidden] are people who asked
 * not to be shown: their numbers count in the table sums, but they are not listed.
 */
export async function loadGroupStatistics(
  tournaments: GroupTournament[],
  filter: GroupFilter,
  hidden: Set<string> = new Set(),
): Promise<GroupStatistics> {
  const kept = tournamentsOf(tournaments, filter);
  const [summaries, countryPeople] = await Promise.all([
    loadTournamentSummaries(kept.map((tournament) => tournament.id)),
    filter.playerCountry == null ? Promise.resolve(null) : emaIdsOfCountry(filter.playerCountry),
  ]);
  const tables = emptyTables();
  const persons = new Map<string, GroupPerson>();
  const withData: string[] = [];
  summaries.forEach((summary) => {
    if (summary.tables.tablesCompleted === 0) return;
    withData.push(summary.tournamentId);
    TABLE_FIELDS.forEach((field) => { tables[field] += summary.tables[field]; });
    tables.highestHand = Math.max(tables.highestHand, summary.tables.highestHand);
    summary.persons.forEach((person) => {
      if (hidden.has(person.emaId)) return;
      if (countryPeople != null && !countryPeople.has(person.emaId)) return;
      const sum = persons.get(person.emaId) ?? emptyPerson(person.emaId);
      addPerson(sum, person);
      persons.set(person.emaId, sum);
    });
  });
  return { tournamentIds: withData, tables, persons: [...persons.values()] };
}
