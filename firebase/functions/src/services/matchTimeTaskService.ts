import { getFunctions } from "firebase-admin/functions";

import { matchTimeOfTournament } from "./matchTime";
import { clockEndMs } from "./roundClock";

type Data = Record<string, unknown>;

/** Region and name of the task function. They must match the function in `index.ts`. */
export const CLOSE_ROUND_TASK_REGION = "europe-west1";
export const CLOSE_ROUND_TASK_NAME = "closeRoundTablesTask";

export type RoundCloseTask = { id: string; roundId: number; at: Date };

export type RoundCloseEnqueuer = (tournamentId: string, task: RoundCloseTask) => Promise<void>;

/**
 * The instant in which the match time of a round is over, extra time included. Null while the round has not started
 * or its clock is paused: the server cannot know the end then.
 */
export function roundOverMs(tournament: Data, roundId: number): number | null {
  const { rules, clock } = matchTimeOfTournament(tournament, roundId);
  if (clock == null) return null;
  const end = clockEndMs(clock, rules.durationMinutes);
  if (end == null) return null;
  const extra = rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60_000 : 0;
  return end + extra;
}

/**
 * The tasks that close the rounds of a tournament, one for each running round whose end is in the future.
 * The Play of the Timer starts a round, so an end is never more than a few hours ahead.
 * The id has the end instant, so the same clock gives the same id and Cloud Tasks drops the copy.
 */
export function roundCloseTasks(tournamentId: string, tournament: Data, nowMs: number, onlyRoundId?: number): RoundCloseTask[] {
  const numRounds = Number(tournament.numRounds ?? 0);
  const tasks: RoundCloseTask[] = [];
  for (let roundId = 1; roundId <= numRounds; roundId += 1) {
    if (onlyRoundId != null && roundId !== onlyRoundId) continue;
    const overMs = roundOverMs(tournament, roundId);
    if (overMs == null || overMs <= nowMs) continue;
    tasks.push({ id: `${tournamentId}_r${roundId}_${overMs}`, roundId, at: new Date(overMs) });
  }
  return tasks;
}

const enqueueWithCloudTasks: RoundCloseEnqueuer = async (tournamentId, task) => {
  // With the Firestore emulator the code runs in a test. It must never reach the real Cloud Tasks.
  if (process.env.FIRESTORE_EMULATOR_HOST && !process.env.CLOUD_TASKS_EMULATOR_HOST) return;
  const queue = getFunctions().taskQueue(`locations/${CLOSE_ROUND_TASK_REGION}/functions/${CLOSE_ROUND_TASK_NAME}`);
  try {
    await queue.enqueue({ tournamentId, roundId: task.roundId }, { id: task.id, scheduleTime: task.at });
  } catch (error) {
    // The same task already exists. That is the expected result of saving a tournament without a schedule change.
    if ((error as { code?: string }).code === "functions/task-already-exists") return;
    throw error;
  }
};

let enqueuer: RoundCloseEnqueuer = enqueueWithCloudTasks;

/** Replaces the code that enqueues the tasks. Tests use it. Call it without argument to restore Cloud Tasks. */
export function setRoundCloseEnqueuer(replacement?: RoundCloseEnqueuer): void {
  enqueuer = replacement ?? enqueueWithCloudTasks;
}

/**
 * Plans the close of the running rounds of a tournament, or of one round. The Timer calls it after each change of a
 * clock, and a settings save calls it because the match duration can change. A task made before an earlier change
 * stays in the queue. It does nothing when it runs, because it checks the time again. A failure is logged and never
 * stops the save.
 */
export async function scheduleRoundCloses(
  tournamentId: string,
  tournament: Data,
  nowMs: number = Date.now(),
  onlyRoundId?: number,
): Promise<number> {
  let planned = 0;
  for (const task of roundCloseTasks(tournamentId, tournament, nowMs, onlyRoundId)) {
    try {
      await enqueuer(tournamentId, task);
      planned += 1;
    } catch (error) {
      console.error(`Could not plan the close of round ${task.roundId} of tournament ${tournamentId}`, error);
    }
  }
  return planned;
}
