import { firebaseProjectId } from "../firebase";
import { backfillHandsCopy } from "../services/handsCopyBackfill";
import { runScript } from "./scriptRun";

const applyChanges = process.argv.includes("--apply");
const tournamentIds = process.argv.slice(2).filter((arg) => !arg.startsWith("--"));

/**
 * Gives every stored table a copy of its 16 hands, so the reads of many tables cost one document per table. A table
 * without the copy still works: the API reads its hand documents. Only completed tournaments really need this,
 * because any save writes the copy. Safe to run again. Pass tournament ids to limit it.
 */
async function backfill(): Promise<Record<string, number>> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the copies of the hands for ${firebaseProjectId}.`);
  const counts = await backfillHandsCopy({ tournamentIds: tournamentIds.length > 0 ? tournamentIds : undefined, apply: applyChanges });
  console.log(`${applyChanges ? "Copied" : "Would copy"} ${counts.copied} tables. ${counts.valid} already had a valid copy. ${counts["missing-hands"]} have missing hands.`);
  if (!applyChanges && counts.copied > 0) console.log("Run again with --apply to write them.");
  return counts;
}

runScript("backfillHandsCopy", backfill, applyChanges);
