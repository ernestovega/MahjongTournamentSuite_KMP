import { db } from "../firebase";
import { matchPhaseAt, matchTimeOfTournament } from "./matchTime";
import { tableVersionKey } from "./dataVersionsService";
import { HANDS_PER_TABLE, saveTableStateInTransaction, type TableState } from "./tableManagerService";

type Data = Record<string, unknown>;

export type CloseExpiredResult = { tournaments: number; closed: number };

const SYSTEM_ACTOR = { uid: "system", name: "Match time", source: "system" as const };

/** Rounds of the tournament whose match time is over at an instant. A round without a clock has not started. */
function overRounds(tournament: Data, nowMs: number): number[] {
  const numRounds = Number(tournament.numRounds ?? 0);
  const rounds: number[] = [];
  for (let roundId = 1; roundId <= numRounds; roundId += 1) {
    const { rules, clock } = matchTimeOfTournament(tournament, roundId);
    if (matchPhaseAt(clock, rules, nowMs) === "OVER") rounds.push(roundId);
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
 * Marks as completed the tables of one round that have played hands, when the match time of the round is over.
 * A task runs it at the end of the round. It checks the clock again, so a task made before a pause, a reset or
 * "Set time" does nothing.
 */
export async function closeRoundTables(tournamentId: string, roundId: number, nowMs: number = Date.now()): Promise<CloseExpiredResult> {
  const result: CloseExpiredResult = { tournaments: 0, closed: 0 };
  const tournament = await db.doc(`tournaments/${tournamentId}`).get();
  if (!tournament.exists || tournament.get("isCompleted") === true) return result;
  if (!overRounds(tournament.data() ?? {}, nowMs).includes(roundId)) return result;
  const started = await tournament.ref.collection("tables")
    .where("roundId", "==", roundId)
    .where("isCompleted", "==", false)
    .where("hasProgress", "==", true)
    .get();
  const candidates = started.docs.filter((doc) => doc.get("matchTimeChecked") !== true);
  if (candidates.length === 0) return result;
  result.tournaments = 1;
  for (const doc of candidates) {
    if (await closeTable(tournamentId, roundId, Number(doc.get("tableId")))) result.closed += 1;
  }
  return result;
}

/**
 * Closes at once the rounds that a change ended: a "Set time" to zero, a "started N minutes ago" longer than the match,
 * a shorter match duration in the settings. No task closes them, because a task runs only at an end in the future.
 * Only rounds that were not over before the change count, so a save of the settings never closes the tables that an
 * organizer fills later in an old round. A failure is logged and never stops the change.
 */
export async function closeRoundsEndedByChange(params: {
  tournamentId: string;
  before: Data;
  after: Data;
  nowMs: number;
  onlyRoundId?: number;
}): Promise<number> {
  let closed = 0;
  const numRounds = Number(params.after.numRounds ?? 0);
  for (let roundId = 1; roundId <= numRounds; roundId += 1) {
    if (params.onlyRoundId != null && roundId !== params.onlyRoundId) continue;
    const phase = (tournament: Data) => {
      const { rules, clock } = matchTimeOfTournament(tournament, roundId);
      return matchPhaseAt(clock, rules, params.nowMs);
    };
    if (phase(params.before) === "OVER" || phase(params.after) !== "OVER") continue;
    try {
      closed += (await closeRoundTables(params.tournamentId, roundId, params.nowMs)).closed;
    } catch (error) {
      console.error(`Could not close round ${roundId} of tournament ${params.tournamentId}`, error);
    }
  }
  return closed;
}
