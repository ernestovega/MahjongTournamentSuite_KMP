import { db } from "../firebase";
import { FieldValue } from "firebase-admin/firestore";

import { badRequest, notFound } from "../api/httpError";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "./dataVersionsService";
import { defaultHand, getTableWithHands, HANDS_PER_TABLE, type TableHand, type TableState } from "./tableManagerService";
import type { TournamentTable } from "./tournamentContentService";

/**
 * Hidden results: an organizer keeps the final ranking secret until the prize giving. From the round
 * [hideResultsFromRound] on, the public routes give the tables of the tournament without their hands and points, so
 * nobody can work the ranking out. The admins see everything. "Reveal" ([resultsRevealed]) shows them again.
 */

/** The first round whose results are hidden now, or null when everything is visible. */
export function hiddenFromRound(data: { hideResultsFromRound?: unknown; resultsRevealed?: unknown }): number | null {
  const round = Number(data.hideResultsFromRound);
  if (!Number.isInteger(round) || round <= 0) return null;
  return data.resultsRevealed === true ? null : round;
}

/** Reads the stored setting. Null or a missing field: the results are never hidden. */
export function readStoredHideResultsFromRound(value: unknown): number | null {
  const round = Number(value);
  return value != null && Number.isInteger(round) && round > 0 ? round : null;
}

/** The setting from a request: null or 0 turns it off, otherwise a round of the tournament. */
export function parseHideResultsFromRound(value: unknown, numRounds: number): number | null {
  if (value === null || value === 0) return null;
  const round = Number(value);
  if (typeof value !== "number" || !Number.isInteger(round) || round < 1 || round > Math.max(numRounds, 1)) {
    throw badRequest("hideResultsFromRound must be null or a round of the tournament", { numRounds });
  }
  return round;
}

export const isHiddenRound = (roundId: number, fromRound: number | null) => fromRound != null && roundId >= fromRound;

/**
 * A table of a hidden round as the public sees it: who sits where and whether it ended, but no score, no points and
 * no hands. The version stays, so the apps know when to read it again.
 */
export type MaskedTableState = TableState & { resultsHidden: true };

export function maskTable(table: TableState): { table: MaskedTableState; hands: TableHand[] } {
  return {
    table: {
      ...table,
      resultsHidden: true,
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
      useTotalsOnly: false,
      forcedZeroPlayerId: "",
    },
    hands: Array.from({ length: HANDS_PER_TABLE }, (_, index) => defaultHand(index + 1)),
  };
}

/** Masks the tables of the hidden rounds. */
export function maskTablesWithHands<T extends { table: TableState; hands: TableHand[] }>(
  tables: T[],
  fromRound: number | null,
): Array<{ table: TableState; hands: TableHand[] }> {
  if (fromRound == null) return tables;
  return tables.map((entry) => (isHiddenRound(entry.table.roundId, fromRound) ? maskTable(entry.table) : entry));
}

/** The summary of a table of a hidden round loses its best hands and chicken hands. */
export function maskTableSummaries(tables: TournamentTable[], fromRound: number | null): TournamentTable[] {
  if (fromRound == null) return tables;
  return tables.map((table) => (isHiddenRound(table.roundId, fromRound)
    ? { ...table, bestHandScores: [], chickenHandCount: 0, hasValidManualTotals: false, useTotalsOnly: false }
    : table));
}

/** The first hidden round of each tournament that hides its results now. */
export async function hiddenRoundsByTournament(): Promise<Map<string, number>> {
  const snapshot = await db.collection("tournaments").select("hideResultsFromRound", "resultsRevealed").get();
  const hidden = new Map<string, number>();
  snapshot.docs.forEach((doc) => {
    const round = hiddenFromRound({ hideResultsFromRound: doc.get("hideResultsFromRound"), resultsRevealed: doc.get("resultsRevealed") });
    if (round != null) hidden.set(doc.id, round);
  });
  return hidden;
}

/**
 * Reveals the hidden results, or hides them again after a reveal by mistake. The apps drop the tables they hold and
 * read them again, so everyone sees the change within a poll.
 */
export async function setResultsRevealed(tournamentId: string, revealed: boolean): Promise<void> {
  const ref = db.collection("tournaments").doc(tournamentId);
  const before = await ref.get();
  if (!before.exists) throw notFound("Tournament not found");
  if (readStoredHideResultsFromRound(before.get("hideResultsFromRound")) == null) {
    throw badRequest("This tournament does not hide its results");
  }
  if ((before.get("resultsRevealed") === true) === revealed) return;
  await ref.update({ resultsRevealed: revealed, updatedAt: FieldValue.serverTimestamp(), ...tournamentVersionUpdate("draw") });
  await bumpGlobalDataVersion("tournaments");
}

/**
 * The tables of the hidden rounds where the slot [playerId] plays, with their results. A player keeps seeing and
 * checking the own table while the public does not. Empty when the tournament hides nothing now.
 */
export async function ownHiddenTables(
  tournamentId: string,
  playerId: number,
): Promise<Array<{ table: TableState; hands: TableHand[] }>> {
  const snapshot = await db.collection("tournaments").doc(tournamentId).get();
  if (!snapshot.exists) throw notFound("Tournament not found");
  const fromRound = hiddenFromRound({ hideResultsFromRound: snapshot.get("hideResultsFromRound"), resultsRevealed: snapshot.get("resultsRevealed") });
  if (fromRound == null) return [];
  // A player sits at one table per round, so this reads a few documents, not the whole tournament.
  const mine = await snapshot.ref.collection("tables").where("playerIds", "array-contains", playerId).get();
  const rounds = mine.docs.map((doc) => Number(doc.get("roundId"))).filter((roundId) => roundId >= fromRound);
  const tables = await Promise.all(rounds.map((roundId) => {
    const doc = mine.docs.find((d) => Number(d.get("roundId")) === roundId);
    return getTableWithHands({ tournamentId, roundId, tableId: Number(doc?.get("tableId")) });
  }));
  return tables.sort((a, b) => a.table.roundId - b.table.roundId);
}

/** The results of a person without the hidden rounds of [hidden] (first hidden round by tournament). */
export function withoutHiddenRounds<T extends { tournaments: Array<{ tournamentId: string; results: Array<{ roundId: number }> }> }>(
  statistics: T,
  hidden: Map<string, number>,
): T {
  if (hidden.size === 0) return statistics;
  return {
    ...statistics,
    tournaments: statistics.tournaments
      .map((entry) => {
        const fromRound = hidden.get(entry.tournamentId);
        return fromRound == null ? entry : { ...entry, results: entry.results.filter((result) => result.roundId < fromRound) };
      })
      .filter((entry) => entry.results.length > 0),
  };
}
