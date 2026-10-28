import { db, firebaseProjectId } from "../firebase";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "../services/dataVersionsService";
import { slotCleanupOf } from "../services/retiredSlotFields";
import { deleteTournament } from "../services/tournamentsService";

const applyChanges = process.argv.includes("--apply");

/** The test tournament that held guests with special names. The seed script now writes `test-special-names`. */
const RETIRED_TEST_TOURNAMENT = "test-guests-unicode";

/**
 * Removes the retired models from the stored data:
 * - the `nonMember` data of slots (the slot becomes an open slot),
 * - the `invitedEmail`, `invitationStatus`, `invitedAt` and `notifiedAt` fields of slots,
 * - every document of the `playerInvitations` collections,
 * - the retired test tournament `test-guests-unicode`.
 * It previews by default. Add --apply to write.
 */
async function main(): Promise<void> {
  console.log(`${applyChanges ? "Applying" : "Previewing"} the cleanup for ${firebaseProjectId}.`);
  const tournaments = await db.collection("tournaments").get();
  const touched = new Set<string>();
  let slotCount = 0;
  let nonMemberCount = 0;
  const writer = db.bulkWriter();
  for (const tournament of tournaments.docs) {
    if (tournament.id === RETIRED_TEST_TOURNAMENT) continue;
    const slots = await tournament.ref.collection("players").get();
    for (const slot of slots.docs) {
      const update = slotCleanupOf(slot.data(), Number(slot.get("id") ?? slot.id));
      if (update == null) continue;
      slotCount += 1;
      if (slot.get("nonMember") != null) nonMemberCount += 1;
      touched.add(tournament.id);
      if (applyChanges) void writer.update(slot.ref, update);
    }
  }
  const invitations = await db.collectionGroup("playerInvitations").get();
  console.log(`Slots to clean: ${slotCount} (${nonMemberCount} non-members) in ${touched.size} tournaments.`);
  console.log(`Invitation documents to delete: ${invitations.size}.`);
  const retired = tournaments.docs.some((doc) => doc.id === RETIRED_TEST_TOURNAMENT);
  console.log(`Retired test tournament ${RETIRED_TEST_TOURNAMENT}: ${retired ? "found, will be deleted" : "not found"}.`);
  if (!applyChanges) {
    console.log("Run again with --apply to write.");
    return;
  }

  invitations.docs.forEach((doc) => { void writer.delete(doc.ref); });
  await writer.close();
  for (const id of touched) await bumpTournamentDataVersion(id, "players");
  if (retired) await deleteTournament(RETIRED_TEST_TOURNAMENT);
  await bumpGlobalDataVersion("tournaments");
  console.log("Done.");
}

if (require.main === module) {
  main().catch((error) => {
    console.error(error);
    process.exitCode = 1;
  });
}
