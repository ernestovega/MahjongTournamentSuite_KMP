import { db } from "../firebase";
import { matchPhaseAt, matchTimeOfTournament } from "./matchTime";
import { tableVersionKey } from "./dataVersionsService";
import { HANDS_PER_TABLE, saveTableStateInTransaction, type TableState } from "./tableManagerService";

type Data = Record<string, unknown>;

export type CloseExpiredResult = { tournaments: number; closed: number };

const SYSTEM_ACTOR = { uid: "system", name: "Match time", source: "system" as const };

/** Rounds of the tournament whose match time is over at an instant. Empty when the server cannot know it. */
function overRounds(tournament: Data, nowMs: number): number[] {
  const numRounds = Number(tournament.numRounds ?? 0);
  const rounds: number[] = [];
  for (let roundId = 1; roundId <= numRounds; roundId += 1) {
    const { rules, schedule } = matchTimeOfTournament(tournament, roundId);
    if (matchPhaseAt(schedule, rules, nowMs) === "OVER") rounds.push(roundId);
  }
  return rounds;
}

/**
 * Closes one table whose time is over. A table with played hands becomes completed. A table with no played hands
 * is only marked as checked, so the next runs skip it. The mark `matchTimeChecked` also stops the server from
 * closing a table again when an organizer opens it to add the missing data.
 */
async function closeTable(tournamentId: string, roundId: number, tableId: number): Promise<boolean> {
  const tableRef = db.doc(`tournaments/${tournamentId}/tables/${tableVersionKey(roundId, tableId)}`);
  return db.runTransaction(async (transaction) => {
    const tableSnap = await transaction.get(tableRef);
    if (!tableSnap.exists || tableSnap.get("isCompleted") === true || tableSnap.get("matchTimeChecked") === true) return false;
    const handSnaps = await transaction.getAll(
      ...Array.from({ length: HANDS_PER_TABLE }, (_, index) => tableRef.collection("hands").doc(String(index + 1))),
    );
    if (!handSnaps.some((snap) => snap.get("isDone") === true)) {
      transaction.update(tableRef, { matchTimeChecked: true });
      return false;
    }
    await saveTableStateInTransaction(transaction, {
      tournamentId,
      roundId,
      tableId,
      expectedVersion: Number(tableSnap.get("version") ?? 0),
      tablePatch: { isCompleted: true, matchTimeChecked: true } as Partial<TableState>,
      handPatches: [],
      actor: SYSTEM_ACTOR,
    });
    return true;
  });
}

/**
 * Marks as completed the tables with played hands whose match time is over. A tournament without time zone is
 * skipped, because the server cannot know the local time of its rounds. The job runs every few minutes.
 */
export async function closeExpiredTables(nowMs: number = Date.now()): Promise<CloseExpiredResult> {
  const tournaments = await db.collection("tournaments").where("isCompleted", "==", false).get();
  const result: CloseExpiredResult = { tournaments: 0, closed: 0 };
  for (const tournament of tournaments.docs) {
    const rounds = overRounds(tournament.data(), nowMs);
    if (rounds.length === 0) continue;
    const started = await tournament.ref.collection("tables").where("isCompleted", "==", false).where("hasProgress", "==", true).get();
    const candidates = started.docs.filter((doc) => rounds.includes(Number(doc.get("roundId"))) && doc.get("matchTimeChecked") !== true);
    if (candidates.length === 0) continue;
    result.tournaments += 1;
    for (const doc of candidates) {
      if (await closeTable(tournament.id, Number(doc.get("roundId")), Number(doc.get("tableId")))) result.closed += 1;
    }
  }
  return result;
}
