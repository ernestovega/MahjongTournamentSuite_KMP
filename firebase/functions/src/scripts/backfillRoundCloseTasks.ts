import { db, firebaseProjectId } from "../firebase";
import { roundCloseTasks, scheduleRoundCloses } from "../services/matchTimeTaskService";

/**
 * Plans the close tasks of the rounds of the tournaments that are not completed. Tournaments saved before the
 * tasks existed have none. A round whose end is in the past, unknown or more than 29 days ahead gets no task. The daily job plans the far rounds. A task that already exists
 * is dropped by Cloud Tasks, so it is safe to run the script again. It previews by default. Add --apply to enqueue.
 */
const applyChanges = process.argv.includes("--apply");

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} round close tasks for ${firebaseProjectId}.`);
  const tournaments = await db.collection("tournaments").where("isCompleted", "==", false).get();
  let total = 0;
  let planned = 0;
  for (const tournament of tournaments.docs) {
    const data = tournament.data();
    const tasks = roundCloseTasks(tournament.id, data, Date.now());
    if (tasks.length === 0) continue;
    total += tasks.length;
    console.log(`${tournament.id}: ${tasks.map((task) => `round ${task.roundId} at ${task.at.toISOString()}`).join(", ")}`);
    // eslint-disable-next-line no-await-in-loop
    if (applyChanges) planned += await scheduleRoundCloses(tournament.id, data);
  }
  console.log(applyChanges
    ? `${planned} of ${total} tasks enqueued for ${tournaments.size} open tournaments.`
    : `${total} tasks to enqueue for ${tournaments.size} open tournaments.`);
  if (applyChanges && planned < total) process.exitCode = 1;
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
