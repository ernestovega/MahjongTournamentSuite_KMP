import { db, firebaseProjectId } from "../firebase";

/**
 * Lists the tournaments where two players sit together at more than one table. It only reads.
 * Usage: npm run audit:repeat-opponents
 */
async function main(): Promise<void> {
  const tournaments = await db.collection("tournaments").get();
  console.log(`Checking ${tournaments.size} tournaments in ${firebaseProjectId}.`);
  for (const tournament of tournaments.docs) {
    // eslint-disable-next-line no-await-in-loop
    const tables = await tournament.ref.collection("tables").get();
    const meetings = new Map<string, number>();
    tables.docs.forEach((doc) => {
      const ids = ["playerEastId", "playerSouthId", "playerWestId", "playerNorthId"]
        .map((field) => String(doc.get(field) ?? "").trim())
        .filter((id) => id !== "");
      for (let a = 0; a < ids.length; a += 1) {
        for (let b = a + 1; b < ids.length; b += 1) {
          const key = [ids[a], ids[b]].sort().join("-");
          meetings.set(key, (meetings.get(key) ?? 0) + 1);
        }
      }
    });
    const repeats = [...meetings.values()].filter((count) => count > 1).length;
    console.log(`${repeats > 0 ? "REPEATS" : "ok     "} ${tournament.id} rounds=${tournament.get("numRounds")} tables=${tables.size} repeatedPairs=${repeats}`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
