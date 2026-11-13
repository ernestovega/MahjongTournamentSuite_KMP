import { db } from "../firebase";
import { HANDS_PER_TABLE, handsCopyUpdate, handsFromCopy } from "./tableManagerService";

export type HandsCopyResult = "copied" | "valid" | "missing-hands";

/**
 * Writes the copy of the hands into one table that has no valid copy yet. It reads the table and its 16 hands in a
 * transaction, so a save at the same time is never lost: the transaction runs again on the new data. The version of
 * the table stays the same, because nothing that the apps see changes. When [apply] is false it only reports.
 */
export async function copyHandsIntoTable(tableRef: FirebaseFirestore.DocumentReference, apply: boolean): Promise<HandsCopyResult> {
  return db.runTransaction(async (transaction) => {
    const table = await transaction.get(tableRef);
    const data = table.data() ?? {};
    if (handsFromCopy(data) != null) return "valid";
    const hands = await transaction.getAll(
      ...Array.from({ length: HANDS_PER_TABLE }, (_, index) => tableRef.collection("hands").doc(String(index + 1))),
    );
    if (hands.some((hand) => !hand.exists)) return "missing-hands";
    if (apply) {
      transaction.update(tableRef, handsCopyUpdate(Number(data.version ?? 0), hands.map((hand) => hand.data() ?? {})));
    }
    return "copied";
  });
}

/** Gives every table of the tournaments a copy of its hands. Tables that have a valid copy are only counted. */
export async function backfillHandsCopy(params: { tournamentIds?: string[]; apply: boolean }): Promise<Record<HandsCopyResult, number>> {
  const counts: Record<HandsCopyResult, number> = { copied: 0, valid: 0, "missing-hands": 0 };
  const ids = params.tournamentIds ?? (await db.collection("tournaments").select().get()).docs.map((doc) => doc.id);
  for (const tournamentId of ids) {
    // eslint-disable-next-line no-await-in-loop
    const tables = await db.collection(`tournaments/${tournamentId}/tables`).get();
    for (const table of tables.docs) {
      // eslint-disable-next-line no-await-in-loop
      counts[await copyHandsIntoTable(table.ref, params.apply)] += 1;
    }
  }
  return counts;
}
