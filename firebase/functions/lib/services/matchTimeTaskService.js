"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.CLOSE_ROUND_TASK_NAME = exports.CLOSE_ROUND_TASK_REGION = void 0;
exports.roundOverMs = roundOverMs;
exports.roundCloseTasks = roundCloseTasks;
exports.setRoundCloseEnqueuer = setRoundCloseEnqueuer;
exports.scheduleRoundCloses = scheduleRoundCloses;
exports.planRoundCloses = planRoundCloses;
const functions_1 = require("firebase-admin/functions");
const firebase_1 = require("../firebase");
const matchTime_1 = require("./matchTime");
/** Region and name of the task function. They must match the function in `index.ts`. */
exports.CLOSE_ROUND_TASK_REGION = "europe-west1";
exports.CLOSE_ROUND_TASK_NAME = "closeRoundTablesTask";
/** Cloud Tasks accepts a task at most 30 days ahead. The daily job plans the later rounds when they come near. */
const MAX_TASK_AHEAD_MS = 29 * 24 * 60 * 60000;
/** The instant in which the match time of a round is over. Null when the server cannot know it. */
function roundOverMs(tournament, roundId) {
    const { rules, schedule } = (0, matchTime_1.matchTimeOfTournament)(tournament, roundId);
    const start = (0, matchTime_1.roundStartMs)(schedule, rules.timeZone);
    if (start == null)
        return null;
    const extra = rules.endMode === "FINISH_HAND" ? rules.extraMinutes * 60000 : 0;
    return start + rules.durationMinutes * 60000 + extra;
}
/**
 * The tasks that close the rounds of a tournament, one for each round whose end is in the next 29 days.
 * The id has the end instant, so the same schedule gives the same id and Cloud Tasks drops the copy.
 */
function roundCloseTasks(tournamentId, tournament, nowMs) {
    const numRounds = Number(tournament.numRounds ?? 0);
    const tasks = [];
    for (let roundId = 1; roundId <= numRounds; roundId += 1) {
        const overMs = roundOverMs(tournament, roundId);
        if (overMs == null || overMs <= nowMs || overMs > nowMs + MAX_TASK_AHEAD_MS)
            continue;
        tasks.push({ id: `${tournamentId}_r${roundId}_${overMs}`, roundId, at: new Date(overMs) });
    }
    return tasks;
}
const enqueueWithCloudTasks = async (tournamentId, task) => {
    // With the Firestore emulator the code runs in a test. It must never reach the real Cloud Tasks.
    if (process.env.FIRESTORE_EMULATOR_HOST && !process.env.CLOUD_TASKS_EMULATOR_HOST)
        return;
    const queue = (0, functions_1.getFunctions)().taskQueue(`locations/${exports.CLOSE_ROUND_TASK_REGION}/functions/${exports.CLOSE_ROUND_TASK_NAME}`);
    try {
        await queue.enqueue({ tournamentId, roundId: task.roundId }, { id: task.id, scheduleTime: task.at });
    }
    catch (error) {
        // The same task already exists. That is the expected result of saving a tournament without a schedule change.
        if (error.code === "functions/task-already-exists")
            return;
        throw error;
    }
};
let enqueuer = enqueueWithCloudTasks;
/** Replaces the code that enqueues the tasks. Tests use it. Call it without argument to restore Cloud Tasks. */
function setRoundCloseEnqueuer(replacement) {
    enqueuer = replacement ?? enqueueWithCloudTasks;
}
/**
 * Plans the close of every round of a tournament. A task made before an earlier schedule stays in the queue.
 * It does nothing when it runs, because it checks the time again. A failure is logged and never stops the save.
 */
async function scheduleRoundCloses(tournamentId, tournament, nowMs = Date.now()) {
    let planned = 0;
    for (const task of roundCloseTasks(tournamentId, tournament, nowMs)) {
        try {
            await enqueuer(tournamentId, task);
            planned += 1;
        }
        catch (error) {
            console.error(`Could not plan the close of round ${task.roundId} of tournament ${tournamentId}`, error);
        }
    }
    return planned;
}
/**
 * Plans the close tasks of every tournament that is not completed. The daily job runs it. It plans the rounds
 * that entered the 30 days window, and it repairs a task that failed to enqueue when the organizer saved.
 */
async function planRoundCloses(nowMs = Date.now()) {
    const tournaments = await firebase_1.db.collection("tournaments").where("isCompleted", "==", false).get();
    let planned = 0;
    for (const tournament of tournaments.docs) {
        planned += await scheduleRoundCloses(tournament.id, tournament.data(), nowMs);
    }
    return { tournaments: tournaments.size, planned };
}
//# sourceMappingURL=matchTimeTaskService.js.map