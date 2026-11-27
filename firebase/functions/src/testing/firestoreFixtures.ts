import { randomUUID } from "node:crypto";

import { db } from "../firebase";
import { forgetOrganizations } from "../services/organizationsService";

/** Integration tests need the Firestore emulator. `npm run test:integration` starts it. */
export function requireEmulator(): void {
  if (!process.env.FIRESTORE_EMULATOR_HOST) {
    throw new Error("Run these tests with the Firestore emulator: npm run test:integration");
  }
}

export type SeedTable = {
  roundId: number;
  tableId: number;
  version?: number;
  isCompleted?: boolean;
  /** Seats already taken, such as `{ playerSouthId: "1" }`. A hand can be done only when the four seats are set. */
  seats?: Partial<Record<"playerEastId" | "playerSouthId" | "playerWestId" | "playerNorthId", string>>;
};

export function newTournamentId(): string {
  return `test-${randomUUID()}`;
}

/** Creates a tournament with tables and 16 empty hands per table, as the app does after creation. */
export async function seedTournament(params: {
  tournamentId?: string;
  tables: SeedTable[];
  withHands?: boolean;
  isCompleted?: boolean;
  /** The host country, as a two-letter code. */
  hostCountry?: string;
  /**
   * The organizations that own the tournament. They decide which club admins manage it. Without them the tournament
   * belongs to `org-<country>`, for example `org-es`, see seedOrganization.
   */
  organizerIds?: string[];
  eventEndDate?: string;
}): Promise<string> {
  const tournamentId = params.tournamentId ?? newTournamentId();
  const tournamentRef = db.collection("tournaments").doc(tournamentId);
  const hostCountry = params.hostCountry ?? "ES";
  await tournamentRef.set({
    isCompleted: params.isCompleted ?? false,
    hostCountry,
    organizerIds: params.organizerIds ?? [`org-${hostCountry.toLowerCase()}`],
    ...(params.eventEndDate ? { eventEndDate: params.eventEndDate } : {}),
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
      useTotalsOnly: false,
      version: table.version ?? 0,
      ...table.seats,
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

/** Writes an organization (a club or a federation) as the API stores it, and returns its id. */
export async function seedOrganization(params: { id: string; name?: string; country?: string; primaryColor?: string }): Promise<string> {
  await db.doc(`organizations/${params.id}`).set({
    name: params.name ?? params.id,
    type: "CLUB",
    country: params.country ?? "ES",
    primaryColor: params.primaryColor ?? "#02B16B",
    logoUrl: null,
    logoPath: null,
    logoHash: null,
  });
  forgetOrganizations();
  return params.id;
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
