"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const firebase_1 = require("../firebase");
const matchTimeTaskService_1 = require("../services/matchTimeTaskService");
/**
 * Plans the close tasks of the rounds of the tournaments that are not completed. Tournaments saved before the
 * tasks existed have none. A round whose end is in the past, unknown or more than 29 days ahead gets no task. The daily job plans the far rounds. A task that already exists
 * is dropped by Cloud Tasks, so it is safe to run the script again. It previews by default. Add --apply to enqueue.
 */
const applyChanges = process.argv.includes("--apply");
async function main() {
    console.log(`${applyChanges ? "Applying" : "Previewing"} round close tasks for ${firebase_1.firebaseProjectId}.`);
    const tournaments = await firebase_1.db.collection("tournaments").where("isCompleted", "==", false).get();
    let total = 0;
    let planned = 0;
    for (const tournament of tournaments.docs) {
        const data = tournament.data();
        const tasks = (0, matchTimeTaskService_1.roundCloseTasks)(tournament.id, data, Date.now());
        if (tasks.length === 0)
            continue;
        total += tasks.length;
        console.log(`${tournament.id}: ${tasks.map((task) => `round ${task.roundId} at ${task.at.toISOString()}`).join(", ")}`);
        // eslint-disable-next-line no-await-in-loop
        if (applyChanges)
            planned += await (0, matchTimeTaskService_1.scheduleRoundCloses)(tournament.id, data);
    }
    console.log(applyChanges
        ? `${planned} of ${total} tasks enqueued for ${tournaments.size} open tournaments.`
        : `${total} tasks to enqueue for ${tournaments.size} open tournaments.`);
    if (applyChanges && planned < total)
        process.exitCode = 1;
}
main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
//# sourceMappingURL=backfillRoundCloseTasks.js.map