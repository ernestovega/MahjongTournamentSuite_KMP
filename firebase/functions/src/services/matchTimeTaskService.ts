import { getFunctions } from "firebase-admin/functions";

import { db } from "../firebase";
import { roundStartMs, matchTimeOfTournament } from "./matchTime";

type Data = Record<string, unknown>;

/** Region and name of the task function. They must match the function in `index.ts`. */
export const CLOSE_ROUND_TASK_REGION = "europe-west1";
export const CLOSE_ROUND_TASK_NAME = "closeRoundTablesTask";

/** Cloud Tasks accepts a task at most 30 days ahead. The daily job plans the later rounds when they come near. */
const MAX_TASK_AHEAD_MS = 29 * 24 * 60 * 60_000;

export type RoundCloseTask = { id: string; roundId: number; at: Date };

export type RoundCloseEnqueuer = (tournamentId: string, task: RoundCloseTask) => Promise<void>;

/** The instant in which the match time of a round is over. Null when the server cannot know it. */
export function roundOverMs(tournament: Data, roundId: number): number | null {
  const { rules, schedule } = matchTimeOfTournament(tournament, roundId);
  const start = roundStartMs(schedule, rules.timeZone);
  if (start == null) return null;
  const extra = rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60_000 : 0;
  return start + rules.durationMinutes * 60_000 + extra;
}

/**
 * The tasks that close the rounds of a tournament, one for each round whose end is in the next 29 days.
 * The id has the end instant, so the same schedule gives the same id and Cloud Tasks drops the copy.
 */
export function roundCloseTasks(tournamentId: string, tournament: Data, nowMs: number): RoundCloseTask[] {
  const numRounds = Number(tournament.numRounds ?? 0);
  const tasks: RoundCloseTask[] = [];
  for (let roundId = 1; roundId <= numRounds; roundId += 1) {
    const overMs = roundOverMs(tournament, roundId);
    if (overMs == null || overMs <= nowMs || overMs > nowMs + MAX_TASK_AHEAD_MS) continue;
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
 * Plans the close of every round of a tournament. A task made before an earlier schedule stays in the queue.
 * It does nothing when it runs, because it checks the time again. A failure is logged and never stops the save.
 */
export async function scheduleRoundCloses(tournamentId: string, tournament: Data, nowMs: number = Date.now()): Promise<number> {
  let planned = 0;
  for (const task of roundCloseTasks(tournamentId, tournament, nowMs)) {
    try {
      await enqueuer(tournamentId, task);
      planned += 1;
    } catch (error) {
      console.error(`Could not plan the close of round ${task.roundId} of tournament ${tournamentId}`, error);
    }
  }
  return planned;
}

/**
 * Plans the close tasks of every tournament that is not completed. The daily job runs it. It plans the rounds
 * that entered the 30 days window, and it repairs a task that failed to enqueue when the organizer saved.
 */
export async function planRoundCloses(nowMs: number = Date.now()): Promise<{ tournaments: number; planned: number }> {
  const tournaments = await db.collection("tournaments").where("isCompleted", "==", false).get();
  let planned = 0;
  for (const tournament of tournaments.docs) {
    planned += await scheduleRoundCloses(tournament.id, tournament.data(), nowMs);
  }
  return { tournaments: tournaments.size, planned };
}
