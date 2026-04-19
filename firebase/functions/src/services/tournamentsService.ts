import { FieldValue, Timestamp, type DocumentReference } from "firebase-admin/firestore";

import { db } from "../firebase";
import { Role } from "../models/role";
import { badRequest, notFound } from "../api/httpError";

export type Tournament = {
  id: string;
  name: string;
  isTeams: boolean;
  numPlayers: number;
  numRounds: number;
  numTries: number;
  isCompleted: boolean;
  createdByUid: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function toIsoString(value: unknown): string | null {
  if (value instanceof Timestamp) return value.toDate().toISOString();
  return null;
}

function mapTournamentDoc(d: FirebaseFirestore.DocumentSnapshot): Tournament {
  return {
    id: d.id,
    name: (d.get("name") as string) ?? "",
    isTeams: (d.get("isTeams") as boolean) ?? false,
    numPlayers: (d.get("numPlayers") as number) ?? 0,
    numRounds: (d.get("numRounds") as number) ?? 0,
    numTries: (d.get("numTries") as number) ?? 0,
    isCompleted: (d.get("isCompleted") as boolean) ?? false,
    createdByUid: (d.get("createdByUid") as string) ?? null,
    createdAt: toIsoString(d.get("createdAt")),
    updatedAt: toIsoString(d.get("updatedAt")),
  };
}

export async function createTournament(params: {
  name: string;
  isTeams: boolean;
  numPlayers: number;
  numRounds: number;
  numTries: number;
  players: Array<{ id: number; team: number; name?: string }>;
  tables: Array<{ roundId: number; tableId: number; playerIds: number[]; isCompleted?: boolean; useTotalsOnly?: boolean }>;
  createdByUid: string;
}): Promise<Tournament> {
  const numTablesPerRound = params.numPlayers / 4;
  const expectedTables = params.numRounds * numTablesPerRound;
  if (params.players.length !== params.numPlayers) {
    throw badRequest("Invalid players payload", { expected: params.numPlayers, received: params.players.length });
  }
  if (params.tables.length !== expectedTables) {
    throw badRequest("Invalid tables payload", { expected: expectedTables, received: params.tables.length });
  }

  const ref = db.collection("tournaments").doc();

  const tournamentDoc = {
    name: params.name,
    isTeams: params.isTeams,
    numPlayers: params.numPlayers,
    numRounds: params.numRounds,
    numTries: params.numTries,
    isCompleted: false,
    createdByUid: params.createdByUid,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  };

  await db.runTransaction(async (tx) => {
    tx.set(ref, tournamentDoc);

    const memberRef = ref.collection("members").doc(params.createdByUid);
    tx.set(memberRef, {
      uid: params.createdByUid,
      role: "ADMIN" satisfies Role,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  });

  // Persist the client-generated schedule payload (players/rounds/tables).
  // Hands are created lazily when a table is first opened to keep write volume manageable.
  const batchCommits: Array<Promise<FirebaseFirestore.WriteResult[]>> = [];
  let batch = db.batch();
  let opsInBatch = 0;

  const flushBatch = async () => {
    if (opsInBatch === 0) return;
    batchCommits.push(batch.commit());
    batch = db.batch();
    opsInBatch = 0;
  };

  const addSet = (docRef: FirebaseFirestore.DocumentReference, data: Record<string, unknown>) => {
    batch.set(docRef, data);
    opsInBatch++;
    if (opsInBatch >= 450) {
      // Keep a margin under 500 for safety if we add more writes later.
      return flushBatch();
    }
    return Promise.resolve();
  };

  for (const player of params.players) {
    const playerRef = ref.collection("players").doc(String(player.id));
    // eslint-disable-next-line no-await-in-loop
    await addSet(playerRef, {
      id: player.id,
      name: player.name ?? `Player ${player.id}`,
      team: player.team,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  for (let roundId = 1; roundId <= params.numRounds; roundId++) {
    const roundRef = ref.collection("rounds").doc(String(roundId));
    // eslint-disable-next-line no-await-in-loop
    await addSet(roundRef, {
      roundId,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  for (const table of params.tables) {
    const tableRef = ref.collection("tables").doc(`${table.roundId}_${table.tableId}`);
    // eslint-disable-next-line no-await-in-loop
    await addSet(tableRef, {
      roundId: table.roundId,
      tableId: table.tableId,
      playerIds: table.playerIds,
      playerEastId: "",
      playerSouthId: "",
      playerWestId: "",
      playerNorthId: "",
      playerEastScore: "",
      playerSouthScore: "",
      playerWestScore: "",
      playerNorthScore: "",
      playerEastPoints: "",
      playerSouthPoints: "",
      playerWestPoints: "",
      playerNorthPoints: "",
      manualPlayerEastScore: "",
      manualPlayerSouthScore: "",
      manualPlayerWestScore: "",
      manualPlayerNorthScore: "",
      manualPlayerEastPoints: "",
      manualPlayerSouthPoints: "",
      manualPlayerWestPoints: "",
      manualPlayerNorthPoints: "",
      isCompleted: Boolean(table.isCompleted ?? false),
      useTotalsOnly: Boolean(table.useTotalsOnly ?? false),
      usePointsCalculation: true,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  await flushBatch();
  await Promise.all(batchCommits);

  const snap = await ref.get();
  return mapTournamentDoc(snap);
}

export async function listAllTournaments(): Promise<Tournament[]> {
  const snap = await db.collection("tournaments").orderBy("updatedAt", "desc").get();
  return snap.docs.map((d) => mapTournamentDoc(d));
}

function isDocRef(value: DocumentReference | null): value is DocumentReference {
  return value != null;
}

export async function listTournamentsForUser(uid: string): Promise<Tournament[]> {
  const memberSnaps = await db.collectionGroup("members").where("uid", "==", uid).get();
  const tournamentRefs = memberSnaps.docs
    .map((m) => m.ref.parent.parent)
    .filter(isDocRef);

  if (tournamentRefs.length === 0) return [];

  const tournamentSnaps = await db.getAll(...tournamentRefs);
  return tournamentSnaps
    .filter((t) => t.exists)
    .map((d) => mapTournamentDoc(d));
}

export async function deleteTournament(tournamentId: string): Promise<void> {
  const ref = db.collection("tournaments").doc(tournamentId);
  const snap = await ref.get();
  if (!snap.exists) throw notFound("Tournament not found");

  const recursiveDelete = (db as unknown as { recursiveDelete?: (ref: DocumentReference) => Promise<void> })
    .recursiveDelete;
  if (typeof recursiveDelete === "function") {
    await recursiveDelete(ref);
    return;
  }

  // Fallback: delete the parent document (subcollections will remain).
  // This should be extremely rare; most firebase-admin builds expose recursiveDelete.
  await ref.delete();
}
