import { FieldValue } from "firebase-admin/firestore";

import { db, firebaseProjectId } from "../firebase";
import { bumpTournamentDataVersion } from "../services/dataVersionsService";

const applyChanges = process.argv.includes("--apply");

/**
 * Removes the stored account links. The account of a slot is now the account that owns the EMA id of the slot,
 * so these two copies are retired:
 * - the `accountUid` field of every slot,
 * - every document of the `users/{uid}/playerTournaments` collections.
 * It previews by default. Add --apply to write.
 */
async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the cleanup for ${firebaseProjectId}.`);
  const slots = await db.collectionGroup("players").select("accountUid").get();
  const withLink = slots.docs.filter((slot) => slot.get("accountUid") !== undefined);
  const registrations = await db.collectionGroup("playerTournaments").get();
  console.log(`Slots with an accountUid: ${withLink.length}. Registration documents: ${registrations.size}.`);
  if (!applyChanges) {
    console.log("Run again with --apply to write.");
    return;
  }
  const writer = db.bulkWriter();
  withLink.forEach((slot) => { void writer.update(slot.ref, { accountUid: FieldValue.delete() }); });
  registrations.docs.forEach((doc) => { void writer.delete(doc.ref); });
  await writer.close();
  const tournamentIds = new Set(withLink.flatMap((slot) => (slot.ref.parent.parent == null ? [] : [slot.ref.parent.parent.id])));
  for (const id of tournamentIds) await bumpTournamentDataVersion(id, "players");
  console.log("Done.");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
