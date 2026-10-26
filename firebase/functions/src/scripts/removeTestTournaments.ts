import { mkdirSync, writeFileSync } from "node:fs";

import { db, firebaseProjectId } from "../firebase";
import { deleteTournament } from "../services/tournamentsService";

const applyChanges = process.argv.includes("--apply");

/**
 * Deletes every tournament that is not a legacy tournament or a production tournament of the seed script.
 * It removes the statistics of each tournament, its logo and its data, and the entries in the lists of the player accounts.
 * It previews by default. Add --apply to delete. Before it deletes, it saves the tournament documents (without tables,
 * players and hands) in output/removed-tournaments-backup-<date>.json.
 */
const KEEP_PREFIX = "legacy-";
const KEEP_IDS = new Set(["madrid-open-2026", "madrid-spring-open-2027", "spanish-team-cup-2027"]);

const keeps = (id: string) => id.startsWith(KEEP_PREFIX) || KEEP_IDS.has(id);

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the tournament cleanup for ${firebaseProjectId}.`);
  const snapshot = await db.collection("tournaments").get();
  const removed = snapshot.docs.filter((doc) => !keeps(doc.id));
  const kept = snapshot.docs.filter((doc) => keeps(doc.id));
  console.log(`Keeping ${kept.length}: ${kept.map((doc) => doc.id).join(", ")}`);
  console.log(`Deleting ${removed.length}:`);
  removed.forEach((doc) => console.log(`  ${doc.id} | ${String(doc.get("name") ?? "")}`));
  if (!applyChanges) {
    console.log("Run again with --apply to delete.");
    return;
  }

  mkdirSync("output", { recursive: true });
  const file = `output/removed-tournaments-backup-${new Date().toISOString().slice(0, 10)}.json`;
  writeFileSync(file, JSON.stringify(removed.map((doc) => ({ id: doc.id, ...doc.data() })), null, 2));
  console.log(`Backup of the tournament documents: ${file}`);

  // The id of a link document is the id of its tournament, so no index is needed.
  const userRefs = (await db.collection("users").select().get()).docs.map((user) => user.ref);
  for (const doc of removed) {
    await deleteTournament(doc.id);
    const links = await Promise.all(userRefs.map((user) => user.collection("playerTournaments").doc(doc.id).get()));
    const existing = links.filter((link) => link.exists);
    await Promise.all(existing.map((link) => link.ref.delete()));
    console.log(`  deleted ${doc.id} (${existing.length} account links)`);
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
