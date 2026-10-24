import { db, firebaseProjectId } from "../firebase";
import { refreshTournamentStats } from "../services/statisticsStore";

const applyChanges = process.argv.includes("--apply");

/**
 * Rebuilds the statistics store from the tables of every tournament. Run it after a change of the statistics
 * rules, and after tables are written without the app, as the test seed does. It works one table at a time,
 * so the memory use does not depend on the number of tournaments. It is safe to run again:
 * a table that did not change adds nothing a second time.
 */
async function backfill(): Promise<void> {
  const tournaments = await db.collection("tournaments").select().get();
  console.log(`${applyChanges ? "Applying" : "Previewing"} the statistics rebuild for ${firebaseProjectId}: ${tournaments.size} tournaments.`);
  let tables = 0;
  for (const [index, tournament] of tournaments.docs.entries()) {
    if (applyChanges) {
      tables += await refreshTournamentStats(tournament.id);
    } else {
      tables += (await tournament.ref.collection("tables").select().get()).size;
    }
    if ((index + 1) % 10 === 0) console.log(`${index + 1} of ${tournaments.size} tournaments done.`);
  }
  console.log(`${applyChanges ? "Processed" : "Found"} ${tables} tables.`);
  if (!applyChanges) console.log("Run again with --apply to write the statistics.");
}

backfill().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
