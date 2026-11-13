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

/** How many tables are read at the same time. Each one is its own small transaction. */
const PARALLEL_TABLES = 20;

/**
 * Gives every table of the tournaments a copy of its hands. Tables that have a valid copy are only counted.
 * [onTournament] hears about each tournament when it is done, so a long run shows its progress.
 */
export async function backfillHandsCopy(params: {
  tournamentIds?: string[];
  apply: boolean;
  onTournament?: (progress: { tournamentId: string; index: number; total: number; tables: number }) => void;
}): Promise<Record<HandsCopyResult, number>> {
  const counts: Record<HandsCopyResult, number> = { copied: 0, valid: 0, "missing-hands": 0 };
  const ids = params.tournamentIds ?? (await db.collection("tournaments").select().get()).docs.map((doc) => doc.id);
  for (const [index, tournamentId] of ids.entries()) {
    // eslint-disable-next-line no-await-in-loop
    const tables = await db.collection(`tournaments/${tournamentId}/tables`).select("version", "handsCopy").get();
    for (let start = 0; start < tables.docs.length; start += PARALLEL_TABLES) {
      const batch = tables.docs.slice(start, start + PARALLEL_TABLES);
      // eslint-disable-next-line no-await-in-loop
      const results = await Promise.all(batch.map((table) => copyHandsIntoTable(table.ref, params.apply)));
      results.forEach((result) => { counts[result] += 1; });
    }
    params.onTournament?.({ tournamentId, index: index + 1, total: ids.length, tables: tables.size });
  }
  return counts;
}
