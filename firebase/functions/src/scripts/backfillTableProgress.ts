import { db, firebaseProjectId } from "../firebase";
import { backfillTournament } from "../services/tournamentBackfill";

/**
 * Prepares old tournaments for the multi-editor features. See `backfillTournament`.
 * Run it when nobody edits the tournaments. It previews by default. Add --apply to write.
 */
const applyChanges = process.argv.includes("--apply");

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} table progress backfill for ${firebaseProjectId}.`);
  const tournaments = await db.collection("tournaments").get();
  for (const tournament of tournaments.docs) {
    // eslint-disable-next-line no-await-in-loop
    const result = await backfillTournament(tournament.id, applyChanges);
    console.log(
      `${tournament.id}: ${result.tableCount} tables, ${result.tablesWithProgress} with data, ` +
        `${result.missingHands.length} tables with missing hands (${result.missingHands.reduce((sum, entry) => sum + entry.handIds.length, 0)} hands)`,
    );
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
