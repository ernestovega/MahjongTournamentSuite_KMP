import { FieldValue, Timestamp, type DocumentReference } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";

import { db, storage } from "../firebase";
import { badRequest, notFound } from "../api/httpError";
import { getUserProfile } from "./usersService";

export type Tournament = {
  id: string;
  name: string;
  shortName: string;
  primaryColor: string;
  associationLogoUrl: string | null;
  isTeams: boolean;
  numPlayers: number;
  numRounds: number;
  eventStartDate: string | null;
  eventEndDate: string | null;
  numTries: number;
  isCompleted: boolean;
  createdByUid: string | null;
  createdByName: string | null;
  createdAt: string | null;
  updatedAt: string | null;
};

function toIsoString(value: unknown): string | null {
  if (value instanceof Timestamp) return value.toDate().toISOString();
  return null;
}

function formatDisplayName(email: string): string {
  const rawName = email
    .split("@", 1)[0]
    .split(".").join(" ")
    .split("_").join(" ")
    .split("-").join(" ")
    .trim()
    .split(" ")
    .filter((part) => part.trim().length > 0)
    .map((part: string) => {
      const lower = part.toLowerCase();
      return lower.charAt(0).toUpperCase() + lower.slice(1);
    })
    .join(" ");

  return rawName.length > 0 ? rawName : email;
}

async function resolveCreatedByName(uid: string | null): Promise<string | null> {
  if (uid == null || uid.trim().length === 0) return null;

  try {
    const profile = await getUserProfile(uid);
    return formatDisplayName(profile.email);
  } catch (_error) {
    return null;
  }
}

async function mapTournamentDoc(d: FirebaseFirestore.DocumentSnapshot): Promise<Tournament> {
  const createdByUid = (d.get("createdByUid") as string) ?? (d.get("createdBy") as string) ?? null;
  const createdByName = (d.get("createdByName") as string) ?? await resolveCreatedByName(createdByUid);

  return {
    id: d.id,
    name: (d.get("name") as string) ?? "",
    shortName: String(d.get("shortName") ?? d.get("name") ?? "").trim().slice(0, 10),
    primaryColor: /^#[0-9A-F]{6}$/i.test(String(d.get("primaryColor") ?? ""))
      ? String(d.get("primaryColor")).toUpperCase()
      : "#02B16B",
    associationLogoUrl: typeof d.get("associationLogoUrl") === "string" ? d.get("associationLogoUrl") : null,
    isTeams: (d.get("isTeams") as boolean) ?? false,
    numPlayers: (d.get("numPlayers") as number) ?? 0,
    numRounds: (d.get("numRounds") as number) ?? 0,
    eventStartDate: (d.get("eventStartDate") as string) ?? (d.get("eventDate") as string) ?? null,
    eventEndDate: (d.get("eventEndDate") as string) ?? (d.get("eventDate") as string) ?? null,
    numTries: (d.get("numTries") as number) ?? 0,
    isCompleted: (d.get("isCompleted") as boolean) ?? false,
    createdByUid,
    createdByName,
    createdAt: toIsoString(d.get("createdAt")),
    updatedAt: toIsoString(d.get("updatedAt")),
  };
}

export async function createTournament(params: {
  name: string;
  shortName: string;
  primaryColor: string;
  associationLogoContentType?: string | null;
  associationLogoDataBase64?: string | null;
  eventStartDate: string;
  eventEndDate: string;
  isTeams: boolean;
  numPlayers: number;
  numRounds: number;
  numTries: number;
  players: Array<{ id: number; team: number; name?: string; country?: string }>;
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

  const logo = params.associationLogoContentType && params.associationLogoDataBase64
    ? await saveAssociationLogo(ref.id, params.associationLogoContentType, params.associationLogoDataBase64)
    : null;

  const tournamentDoc = {
    name: params.name,
    shortName: normalizeShortName(params.shortName),
    primaryColor: normalizePrimaryColor(params.primaryColor),
    associationLogoUrl: logo?.url ?? null,
    associationLogoPath: logo?.path ?? null,
    eventStartDate: params.eventStartDate,
    eventEndDate: params.eventEndDate,
    isTeams: params.isTeams,
    numPlayers: params.numPlayers,
    numRounds: params.numRounds,
    numTries: params.numTries,
    isCompleted: false,
    createdByUid: params.createdByUid,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  };

  await ref.set(tournamentDoc);

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
      country: player.country ?? "",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  if (params.isTeams) {
    const teamIds = [...new Set(params.players.map((player) => player.team))]
      .filter((teamId) => Number.isInteger(teamId) && teamId > 0)
      .sort((a, b) => a - b);
    for (const teamId of teamIds) {
      const teamRef = ref.collection("teams").doc(String(teamId));
      // eslint-disable-next-line no-await-in-loop
      await addSet(teamRef, {
        id: teamId,
        name: `Team ${teamId}`,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
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
      useTotalsOnly: Boolean(table.useTotalsOnly ?? true),
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
  return Promise.all(snap.docs.map((d) => mapTournamentDoc(d)));
}

export async function renameTournament(tournamentId: string, name: string): Promise<void> {
  const normalizedName = name.trim();
  if (normalizedName.length === 0) throw badRequest("Tournament name is required");

  const ref = db.collection("tournaments").doc(tournamentId);
  const snap = await ref.get();
  if (!snap.exists) throw notFound("Tournament not found");

  await ref.update({
    name: normalizedName,
    updatedAt: FieldValue.serverTimestamp(),
  });
}

const logoExtensions: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
};

function normalizeShortName(value: string): string {
  const shortName = value.trim();
  if (shortName.length === 0) throw badRequest("Tournament short name is required");
  if (shortName.length > 10) throw badRequest("Tournament short name must contain at most 10 characters");
  return shortName;
}

function normalizePrimaryColor(value: string): string {
  const color = value.trim().toUpperCase();
  if (!/^#[0-9A-F]{6}$/.test(color)) throw badRequest("Primary color must use #RRGGBB format");
  return color;
}

function hasValidLogoSignature(bytes: Buffer, contentType: string): boolean {
  if (contentType === "image/jpeg") {
    return bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff;
  }
  return bytes.length >= 8
    && bytes.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]));
}

async function saveAssociationLogo(
  tournamentId: string,
  contentType: string,
  dataBase64: string,
): Promise<{ path: string; url: string }> {
  const extension = logoExtensions[contentType];
  if (!extension) throw badRequest("Association logo must be a JPEG or PNG image");
  const bytes = Buffer.from(dataBase64, "base64");
  if (bytes.length === 0) throw badRequest("Association logo is empty");
  if (bytes.length > 2 * 1024 * 1024) throw badRequest("Association logo must be 2 MB or smaller");
  if (!hasValidLogoSignature(bytes, contentType)) {
    throw badRequest("Association logo data does not match its image type");
  }
  const path = `tournamentLogos/${tournamentId}.${extension}`;
  const file = storage.file(path);
  const token = randomUUID();
  await file.save(bytes, {
    contentType,
    metadata: {
      cacheControl: "public, max-age=604800",
      metadata: { firebaseStorageDownloadTokens: token },
    },
  });
  const objectName = encodeURIComponent(path);
  return {
    path,
    url: `https://firebasestorage.googleapis.com/v0/b/${storage.name}/o/${objectName}?alt=media&token=${token}`,
  };
}

export async function updateTournamentSettings(params: {
  tournamentId: string;
  name: string;
  shortName: string;
  primaryColor: string;
  associationLogoContentType?: string | null;
  associationLogoDataBase64?: string | null;
  removeAssociationLogo?: boolean;
}): Promise<Tournament> {
  const normalizedName = params.name.trim();
  if (normalizedName.length === 0) throw badRequest("Tournament name is required");
  const shortName = normalizeShortName(params.shortName);
  const primaryColor = normalizePrimaryColor(params.primaryColor);
  const ref = db.collection("tournaments").doc(params.tournamentId);
  const before = await ref.get();
  if (!before.exists) throw notFound("Tournament not found");

  const oldLogoPath = String(before.get("associationLogoPath") ?? "").trim();
  let logo: { path: string; url: string } | null | undefined;
  if (params.removeAssociationLogo === true) {
    logo = null;
  } else if (params.associationLogoContentType && params.associationLogoDataBase64) {
    logo = await saveAssociationLogo(
      params.tournamentId,
      params.associationLogoContentType,
      params.associationLogoDataBase64,
    );
  }

  const update: Record<string, unknown> = {
    name: normalizedName,
    shortName,
    primaryColor,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (logo !== undefined) {
    update.associationLogoPath = logo?.path ?? null;
    update.associationLogoUrl = logo?.url ?? null;
  }
  await ref.update(update);

  if (oldLogoPath.length > 0 && (logo === null || (logo != null && logo.path !== oldLogoPath))) {
    await storage.file(oldLogoPath).delete({ ignoreNotFound: true }).catch(() => undefined);
  }
  return mapTournamentDoc(await ref.get());
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
  return Promise.all(
    tournamentSnaps
      .filter((t) => t.exists)
      .map((d) => mapTournamentDoc(d)),
  );
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
