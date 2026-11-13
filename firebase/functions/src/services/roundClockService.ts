import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { conflict, notFound } from "../api/httpError";
import { recordActivity } from "./activityLog";
import type { ChangeActor } from "./changeLog";
import { bumpGlobalDataVersion, tournamentVersionUpdate } from "./dataVersionsService";
import { closeRoundsEndedByChange } from "./matchTimeCloseService";
import { scheduleRoundCloses } from "./matchTimeTaskService";
import { readStoredMatchDuration } from "./tournamentSchedule";
import {
  applyRoundClockAction,
  readStoredRoundClocks,
  roundClockViews,
  type RoundClockAction,
  type RoundClockView,
} from "./roundClock";
import { HANDS_PER_TABLE } from "./tableManagerService";

type Data = Record<string, unknown>;

/** The clocks of a tournament with the time of the server, so a Timer can correct the clock of its device. */
export type RoundClocksAnswer = { roundClocks: RoundClockView[]; serverNowMs: number };

export async function getRoundClocks(tournamentId: string, nowMs: number = Date.now()): Promise<RoundClocksAnswer> {
  const snapshot = await db.doc(`tournaments/${tournamentId}`).get();
  if (!snapshot.exists) throw notFound("Tournament not found");
  return { roundClocks: roundClockViews(snapshot.data() ?? {}), serverNowMs: nowMs };
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
    const after = applyRoundClockAction(
      before,
      params.action,
      readStoredMatchDuration(tournament.matchDurationMinutes),
      nowMs,
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
  return { roundClocks: roundClockViews(result.tournament as Data), serverNowMs: result.nowMs };
}
