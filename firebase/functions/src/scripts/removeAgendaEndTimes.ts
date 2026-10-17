import { db, firebaseProjectId } from "../firebase";

/**
 * Deletes the removed end time from every agenda item of every tournament.
 * It previews by default. Add --apply to write. Run it when nobody edits the tournaments.
 */
const applyChanges = process.argv.includes("--apply");

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} agenda end time removal for ${firebaseProjectId}.`);
  const tournaments = await db.collection("tournaments").get();
  let changedCount = 0;
  for (const tournament of tournaments.docs) {
    const items = tournament.get("agendaItems");
    if (!Array.isArray(items)) continue;
    const stale = items.filter((item) => item != null && typeof item === "object" && "endTime" in item);
    if (stale.length === 0) continue;
    changedCount += 1;
    console.log(`${tournament.id}: ${stale.length} of ${items.length} agenda items have an end time`);
    if (!applyChanges) continue;
    const cleaned = items.map((item) => {
      if (item == null || typeof item !== "object") return item;
      const { endTime: _endTime, ...rest } = item as Record<string, unknown>;
      return rest;
    });
    // eslint-disable-next-line no-await-in-loop
    await tournament.ref.update({ agendaItems: cleaned });
  }
  console.log(`${changedCount} of ${tournaments.size} tournaments ${applyChanges ? "changed" : "would change"}.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
