import { randomUUID } from "node:crypto";

import { db } from "../firebase";

/** Integration tests need the Firestore emulator. `npm run test:integration` starts it. */
export function requireEmulator(): void {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Run these tests with the Firestore emulator: npm run test:integration");
  }
}

export type SeedTable = { roundId: number; tableId: number; version?: number; isCompleted?: boolean };

export function newTournamentId(): string {
  return `test-${randomUUID()}`;
}

/** Creates a tournament with tables and 16 empty hands per table, as the app does after creation. */
export async function seedTournament(params: {
  tournamentId?: string;
  tables: SeedTable[];
  withHands?: boolean;
  isCompleted?: boolean;
}): Promise<string> {
  const tournamentId = params.tournamentId ?? newTournamentId();
  const tournamentRef = db.collection("tournaments").doc(tournamentId);
  await tournamentRef.set({
    isCompleted: params.isCompleted ?? false,
    tablesWithProgress: 0,
    dataVersions: { tables: { revision: 1, changedAt: null } },
  });
  for (const table of params.tables) {
    const tableRef = tournamentRef.collection("tables").doc(`${table.roundId}_${table.tableId}`);
    await tableRef.set({
      roundId: table.roundId,
      tableId: table.tableId,
      playerIds: [1, 2, 3, 4],
      isCompleted: table.isCompleted ?? false,
      useTotalsOnly: true,
      usePointsCalculation: true,
      version: table.version ?? 0,
    });
    if (params.withHands === false) continue;
    const batch = db.batch();
    for (let handId = 1; handId <= 16; handId++) {
      batch.set(tableRef.collection("hands").doc(String(handId)), {
        handId,
        playerWinnerId: "",
        playerLooserId: "",
        handScore: "",
        isChickenHand: false,
        isDone: false,
        playerEastPenalty: "",
        playerSouthPenalty: "",
        playerWestPenalty: "",
        playerNorthPenalty: "",
      });
    }
    await batch.commit();
  }
  return tournamentId;
}

export async function readTournament(tournamentId: string): Promise<Record<string, unknown>> {
  const snapshot = await db.collection("tournaments").doc(tournamentId).get();
  return snapshot.data() ?? {};
}

export async function readTable(tournamentId: string, roundId: number, tableId: number): Promise<Record<string, unknown>> {
  const snapshot = await db.collection("tournaments").doc(tournamentId)
    .collection("tables").doc(`${roundId}_${tableId}`).get();
  return snapshot.data() ?? {};
}

export async function readHand(
  tournamentId: string,
  roundId: number,
  tableId: number,
  handId: number,
): Promise<Record<string, unknown>> {
  const snapshot = await db.collection("tournaments").doc(tournamentId)
    .collection("tables").doc(`${roundId}_${tableId}`)
    .collection("hands").doc(String(handId)).get();
  return snapshot.data() ?? {};
}

export async function readChanges(tournamentId: string): Promise<Array<Record<string, unknown>>> {
  const snapshot = await db.collection("tournaments").doc(tournamentId).collection("changes").get();
  return snapshot.docs
    .map((doc) => doc.data())
    .sort((a, b) => Number(a.version) - Number(b.version));
}

export async function readGlobalTournamentsRevision(): Promise<number> {
  const snapshot = await db.collection("_meta").doc("dataVersions").get();
  return Number(snapshot.get("resources.tournaments.revision") ?? 0);
}

export async function countHandDocs(tournamentId: string, roundId: number, tableId: number): Promise<number> {
  const snapshot = await db.collection("tournaments").doc(tournamentId)
    .collection("tables").doc(`${roundId}_${tableId}`).collection("hands").get();
  return snapshot.size;
}
