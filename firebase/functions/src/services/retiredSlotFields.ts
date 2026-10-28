import { FieldValue } from "firebase-admin/firestore";

/**
 * Fields of a slot that the account rules retired. The slot keeps only a pending `email`, which the import writes.
 * `notifiedAt` went away with the invitation states.
 */
const RETIRED_SLOT_FIELDS = ["invitedEmail", "invitationStatus", "invitedAt", "notifiedAt"] as const;

type Data = Record<string, unknown>;

/**
 * The update for one slot, or null when the slot needs none.
 * A non-member slot becomes an open slot: no person, "Player N" as name, no country.
 */
export function slotCleanupOf(data: Data, slotId: number): Data | null {
  const update: Data = {};
  if (data.nonMember != null || "nonMember" in data) {
    update.nonMember = FieldValue.delete();
    if (data.nonMember != null) {
      update.name = `Player ${slotId}`;
      update.country = "";
      update.assignedCountry = "";
    }
  }
  for (const field of RETIRED_SLOT_FIELDS) {
    if (field in data) update[field] = FieldValue.delete();
  }
  return Object.keys(update).length === 0 ? null : { ...update, updatedAt: FieldValue.serverTimestamp() };
}
