import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { conflict, notFound } from "../api/httpError";
import { recordActivity } from "./activityLog";
import type { ChangeActor } from "./changeLog";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "./dataVersionsService";
import { closeRoundsEndedByChange } from "./matchTimeCloseService";
import { matchTimeOfTournament } from "./matchTime";
import { scheduleRoundCloses } from "./matchTimeTaskService";
import { readStoredMatchDuration } from "./tournamentSchedule";
import {
  applyRoundClockAction,
  readStoredRoundClocks,
  roundClockViews,
  type RoundClockAction,
  type RoundClockView,
} from "./roundClock";
import { roundControlViews, type RoundControlChange, type RoundControlView, type RoundTableRef } from "./roundControl";
import { HANDS_PER_TABLE, saveTableStateInTransaction, type TableState } from "./tableManagerService";
import { tableVersionKey } from "./dataVersionsService";

type Data = Record<string, unknown>;

/** The clocks of a tournament with the time of the server, so a Timer can correct the clock of its device. */
export type RoundClocksAnswer = { roundClocks: RoundClockView[]; roundControls: RoundControlView[]; serverNowMs: number };

export async function getRoundClocks(tournamentId: string, nowMs: number = Date.now()): Promise<RoundClocksAnswer> {
  const snapshot = await db.doc(`tournaments/${tournamentId}`).get();
  if (!snapshot.exists) throw notFound("Tournament not found");
  const data = snapshot.data() ?? {};
  return { roundClocks: roundClockViews(data), roundControls: roundControlViews(data), serverNowMs: nowMs };
}

/** True when a table of the round has a saved hand. A reset of such a round needs a confirmation. */
async function roundHasHands(tournamentId: string, roundId: number): Promise<boolean> {
  const tables = await db.collection(`tournaments/${tournamentId}/tables`)
    .where("roundId", "==", roundId)
    .where("hasProgress", "==", true)
    .get();
  for (const table of tables.docs) {
    const hands = await db.getAll(
      ...Array.from({ length: HANDS_PER_TABLE }, (_, index) => table.ref.collection("hands").doc(String(index + 1))),
    );
    if (hands.some((hand) => hand.get("isDone") === true)) return true;
  }
  return false;
}

/**
 * Applies a Play, Pause, Resume, Reset or "Set time" of the Timer to the clock of a round. The server writes its own
 * time, so the clocks of the devices do not matter. Then it plans the close of the round at the new end.
 * A reset of a round with saved hands needs [force]: the hands stay, but the players cannot add more until the next Play.
 */
export async function updateRoundClock(params: {
  tournamentId: string;
  roundId: number;
  action: RoundClockAction;
  force?: boolean;
  actor: ChangeActor | null;
  nowMs?: number;
}): Promise<RoundClocksAnswer> {
  const ref = db.doc(`tournaments/${params.tournamentId}`);
  if (params.action.kind === "reset" && params.force !== true && await roundHasHands(params.tournamentId, params.roundId)) {
    throw conflict("This round has saved hands. Confirm to reset its clock", { code: "ROUND_HAS_HANDS" });
  }
  const result = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) throw notFound("Tournament not found");
    const tournament = snapshot.data() ?? {};
    if (tournament.isCompleted === true) throw conflict("This tournament is completed");
    const numRounds = Number(tournament.numRounds ?? 0);
    if (params.roundId < 1 || params.roundId > numRounds) throw notFound("Round not found");
    const nowMs = params.nowMs ?? Date.now();
    const before = readStoredRoundClocks(tournament).get(params.roundId) ?? null;
    const { rules } = matchTimeOfTournament(tournament, params.roundId);
    const after = applyRoundClockAction(
      before,
      params.action,
      readStoredMatchDuration(tournament.matchDurationMinutes),
      nowMs,
      rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60_000 : 0,
    );
    const changed = JSON.stringify(before) !== JSON.stringify(after);
    if (changed) {
      transaction.update(ref, {
        [`roundClocks.${params.roundId}`]: after ?? FieldValue.delete(),
        ...tournamentVersionUpdate("rounds"),
      });
    }
    const clocks = readStoredRoundClocks(tournament);
    if (after == null) clocks.delete(params.roundId);
    else clocks.set(params.roundId, after);
    return { changed, nowMs, before: tournament, tournament: { ...tournament, roundClocks: Object.fromEntries(clocks) } };
  });
  if (result.changed) {
    // The apps read the clocks with the tournament, so the shared revision of the tournaments changes too.
    await bumpGlobalDataVersion("tournaments");
    await scheduleRoundCloses(params.tournamentId, result.tournament, result.nowMs, params.roundId);
    // An end that is already past gets no task, so the server closes the round now.
    await closeRoundsEndedByChange({
      tournamentId: params.tournamentId, before: result.before, after: result.tournament, nowMs: result.nowMs, onlyRoundId: params.roundId,
    });
    await recordActivity({
      action: "round.clock",
      actor: params.actor,
      tournamentId: params.tournamentId,
      target: `round ${params.roundId}`,
      details: { ...params.action, force: params.force === true },
    });
  }
  return { roundClocks: roundClockViews(result.tournament as Data), roundControls: roundControlViews(result.tournament as Data), serverNowMs: result.nowMs };
}

/**
 * The plan B of the Timer: an admin opens or closes the hand entry and the seats of a round, or the hand entry of one
 * table, whatever the clock says, and opens again tables that the end of the time closed. The settings of the tournament
 * do not change. Answers the clocks and the controls, as the Timer reads them.
 */
export async function updateRoundControl(params: {
  tournamentId: string;
  roundId: number;
  change: RoundControlChange;
  actor: ChangeActor | null;
  nowMs?: number;
}): Promise<RoundClocksAnswer & { reopened: number; completed: number }> {
  const ref = db.doc(`tournaments/${params.tournamentId}`);
  const { change } = params;
  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    if (!snapshot.exists) throw notFound("Tournament not found");
    const data = snapshot.data() ?? {};
    // A completed tournament takes a control too: the close of the last round completes the tournament by itself,
    // and opening its tables again is exactly what an admin may need.
    const numRounds = Number(data.numRounds ?? 0);
    if (params.roundId < 1 || params.roundId > numRounds) throw notFound("Round not found");
    const path = `roundControls.${params.roundId}`;
    const update: Data = {};
    const value = (mode: string) => (mode === "auto" ? FieldValue.delete() : mode);
    if (change.hands !== undefined) update[`${path}.hands`] = value(change.hands);
    if (change.seats !== undefined) update[`${path}.seats`] = value(change.seats);
    if (change.table !== undefined) update[`${path}.tables.${change.table.tableId}`] = value(change.table.hands);
    if (Object.keys(update).length > 0) transaction.update(ref, { ...update, ...tournamentVersionUpdate("rounds") });
  });
  let reopened = 0;
  for (const tableId of change.reopenTables ?? []) {
    if (await reopenTable(params.tournamentId, params.roundId, tableId, params.actor)) reopened += 1;
  }
  let completed = 0;
  for (const tableId of change.completeTables ?? []) {
    if (await setTableCompleted(params.tournamentId, params.roundId, tableId, true, params.actor)) completed += 1;
  }
  const changedControls = change.hands !== undefined || change.seats !== undefined || change.table !== undefined;
  // The apps read the controls with the tournament and the clocks, so the shared revision changes too.
  if (changedControls) await bumpGlobalDataVersion("tournaments");
  await recordActivity({
    action: "round.control",
    actor: params.actor,
    tournamentId: params.tournamentId,
    target: change.table != null ? `round ${params.roundId}, table ${change.table.tableId}` : `round ${params.roundId}`,
    details: { ...change, reopened, completed },
  });
  const answer = await getRoundClocks(params.tournamentId, params.nowMs);
  return { ...answer, reopened, completed };
}

/**
 * Marks as completed many tables of several rounds at once: the button "Close the open tables of the finished rounds"
 * of the round control. The admin chose the tables in the app, after a confirmation. A table that is completed
 * already is skipped. One activity entry tells which tables closed.
 */
export async function completeTables(params: {
  tournamentId: string;
  tables: RoundTableRef[];
  actor: ChangeActor | null;
}): Promise<{ completed: number }> {
  const tournament = await db.doc(`tournaments/${params.tournamentId}`).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const numRounds = Number(tournament.get("numRounds") ?? 0);
  const outside = params.tables.find((table) => table.roundId > numRounds);
  if (outside != null) throw notFound(`Round ${outside.roundId} not found`);
  const closed: RoundTableRef[] = [];
  for (const table of params.tables) {
    if (await setTableCompleted(params.tournamentId, table.roundId, table.tableId, true, params.actor)) closed.push(table);
  }
  if (closed.length > 0) {
    const rounds = [...new Set(closed.map((table) => table.roundId))];
    await recordActivity({
      action: "round.control",
      actor: params.actor,
      tournamentId: params.tournamentId,
      target: rounds.map((roundId) => `round ${roundId}`).join(", "),
      details: {
        completeOpenTables: rounds.map((roundId) => ({
          roundId,
          tables: closed.filter((table) => table.roundId === roundId).map((table) => table.tableId),
        })),
        completed: closed.length,
      },
    });
  }
  return { completed: closed.length };
}

/** Opens a completed table again, so its players can add the hands they miss. False when it was open already. */
function reopenTable(tournamentId: string, roundId: number, tableId: number, actor: ChangeActor | null): Promise<boolean> {
  return setTableCompleted(tournamentId, roundId, tableId, false, actor);
}

/**
 * Marks a table as completed or not, like the Completed switch of the table editor. False when it already was.
 * The mark of the close stays set, so the end of the time never closes a table that an admin opened again.
 */
async function setTableCompleted(
  tournamentId: string,
  roundId: number,
  tableId: number,
  completed: boolean,
  actor: ChangeActor | null,
): Promise<boolean> {
  const tableRef = db.doc(`tournaments/${tournamentId}/tables/${tableVersionKey(roundId, tableId)}`);
  return db.runTransaction(async (transaction) => {
    const table = await transaction.get(tableRef);
    if (!table.exists) throw notFound(`Table ${tableId} not found`);
    if ((table.get("isCompleted") === true) === completed) return false;
    const saved = await saveTableStateInTransaction(transaction, {
      tournamentId,
      roundId,
      tableId,
      expectedVersion: Number(table.get("version") ?? 0),
      tablePatch: { isCompleted: completed, matchTimeChecked: true } as Partial<TableState>,
      handPatches: [],
      actor: actor ?? { uid: "system", name: "Round control", source: "system" },
    });
    // A table with every hand done is always completed. Say so instead of keeping it completed in silence.
    if (!completed && saved.table.isCompleted) {
      throw conflict(`Table ${tableId} has every hand done, so it stays completed`, { code: "TABLE_HAS_ALL_HANDS", tableId });
    }
    return true;
  });
}
