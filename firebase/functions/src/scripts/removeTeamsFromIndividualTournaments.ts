import { FieldValue } from "firebase-admin/firestore";
import { db, firebaseProjectId } from "../firebase";
import { tournamentVersionUpdate } from "../services/dataVersionsService";

/**
 * Removes the team data from every tournament without teams: each slot gets team 0 and the team documents are deleted.
 * It previews by default. Add --apply to write.
 */
const applyChanges = process.argv.includes("--apply");

async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} team data removal for ${firebaseProjectId}.`);
  const tournaments = await db.collection("tournaments").where("isTeams", "==", false).get();
  let changed = 0;
  for (const tournament of tournaments.docs) {
    // eslint-disable-next-line no-await-in-loop
    const [players, teams] = await Promise.all([
      tournament.ref.collection("players").get(), tournament.ref.collection("teams").get(),
    ]);
    const slots = players.docs.filter((slot) => Number(slot.get("team") ?? 0) !== 0);
    if (slots.length === 0 && teams.empty) continue;
    changed += 1;
    console.log(`${tournament.id}: ${slots.length} slots to team 0, ${teams.size} team documents to delete`);
    if (!applyChanges) continue;
    const writes: Array<(batch: FirebaseFirestore.WriteBatch) => void> = [
      ...slots.map((slot) => (batch: FirebaseFirestore.WriteBatch) => batch.update(slot.ref, { team: 0, updatedAt: FieldValue.serverTimestamp() })),
      ...teams.docs.map((team) => (batch: FirebaseFirestore.WriteBatch) => batch.delete(team.ref)),
    ];
    for (let start = 0; start < writes.length; start += 400) {
      const batch = db.batch();
      writes.slice(start, start + 400).forEach((write) => write(batch));
      // eslint-disable-next-line no-await-in-loop
      await batch.commit();
    }
    // eslint-disable-next-line no-await-in-loop
    await tournament.ref.update({
      updatedAt: FieldValue.serverTimestamp(),
      ...tournamentVersionUpdate("players"),
      ...(teams.empty ? {} : tournamentVersionUpdate("teams")),
    });
  }
  console.log(`${changed} of ${tournaments.size} tournaments ${applyChanges ? "changed" : "would change"}.`);
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
