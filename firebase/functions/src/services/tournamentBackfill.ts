import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { calculateTableSummary, defaultHand, HANDS_PER_TABLE } from "./tableManagerService";

type Data = Record<string, unknown>;

export type TableForBackfill = {
  id: string;
  data: Data;
  hands: Data[];
};

export type TournamentBackfillPlan = {
  /** The number of tables that have data. It becomes `tablesWithProgress`. */
  tablesWithProgress: number;
  /** For each table that lacks hands: the numbers of the missing hands. The script creates them. */
  missingHands: Array<{ tableId: string; handIds: number[] }>;
};

/** Works out what an old tournament needs, without touching the database. */
export function planTournamentBackfill(tables: TableForBackfill[]): TournamentBackfillPlan {
  return {
    tablesWithProgress: tables.filter((table) => calculateTableSummary(table.data, table.hands).hasProgress).length,
    missingHands: tables
      .map((table) => {
        const present = new Set(table.hands.map((hand) => Number(hand.handId)));
        const handIds = Array.from({ length: HANDS_PER_TABLE }, (_, index) => index + 1).filter((id) => !present.has(id));
        return { tableId: table.id, handIds };
      })
      .filter((entry) => entry.handIds.length > 0),
  };
}

export type BackfillResult = TournamentBackfillPlan & {
  tableCount: number;
  applied: boolean;
};

/**
 * Prepares one tournament for the multi-editor features:
 * - creates every missing hand, so each table has hands 1 to 16,
 * - sets `tablesWithProgress`.
 *
 * Existing hands are never overwritten, so the function can run again and can run next to a save.
 * The counter can still be wrong if a save starts a table while the function runs. Run it when nobody edits.
 */
export async function backfillTournament(tournamentId: string, apply: boolean): Promise<BackfillResult> {
  const tournamentRef = db.collection("tournaments").doc(tournamentId);
  const tableDocs = (await tournamentRef.collection("tables").get()).docs;
  const tables = await Promise.all(tableDocs.map(async (table) => ({
    id: table.id,
    data: table.data(),
    hands: (await table.ref.collection("hands").get()).docs.map((hand) => hand.data()),
  })));
  const plan = planTournamentBackfill(tables);
  const result: BackfillResult = {
    ...plan,
    tableCount: tables.length,
    applied: apply,
  };
  if (!apply) return result;

  let batch = db.batch();
  let operations = 0;
  const flush = async () => {
    if (operations === 0) return;
    await batch.commit();
    batch = db.batch();
    operations = 0;
  };
  for (const { tableId, handIds } of plan.missingHands) {
    for (const handId of handIds) {
      // create() fails if a hand appeared meanwhile, so a running save is never overwritten.
      batch.create(tournamentRef.collection("tables").doc(tableId).collection("hands").doc(String(handId)), {
        ...defaultHand(handId),
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
      operations++;
      if (operations >= 450) await flush();
    }
  }
  batch.update(tournamentRef, { tablesWithProgress: plan.tablesWithProgress });
  operations++;
  await flush();
  return result;
}
