import { FieldValue, Timestamp, type DocumentReference } from "firebase-admin/firestore";
import { randomUUID } from "node:crypto";

import { db, storage } from "../firebase";
import { badRequest, notFound } from "../api/httpError";
import { isValidIsoDateRange } from "./tournamentDates";
import { getUserProfile } from "./usersService";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";
import {
  normalizeAgendaItems,
  normalizeRoundSchedules,
  readStoredAgendaItems,
  readStoredRoundSchedules,
  type TournamentAgendaItem,
  type TournamentRoundSchedule,
} from "./tournamentSchedule";

export type Tournament = {
  id: string;
  name: string;
  shortName: string;
  primaryColor: string;
  associationLogoUrl: string | null;
  hostCountry: string;
  hostCity: string;
  isTeams: boolean;
  numPlayers: number;
  numRounds: number;
  eventStartDate: string | null;
  eventEndDate: string | null;
  roundSchedules: TournamentRoundSchedule[];
  agendaItems: TournamentAgendaItem[];
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
  const numRounds = Number(d.get("numRounds") ?? 0);
  const eventStartDate = (d.get("eventStartDate") as string) ?? (d.get("eventDate") as string) ?? null;
  const eventEndDate = (d.get("eventEndDate") as string) ?? (d.get("eventDate") as string) ?? null;
  const safeStartDate = eventStartDate ?? "0000-01-01";
  const safeEndDate = eventEndDate ?? "9999-12-31";

  return {
    id: d.id,
    name: (d.get("name") as string) ?? "",
    shortName: String(d.get("shortName") ?? d.get("name") ?? "").trim().slice(0, 10),
    primaryColor: /^#[0-9A-F]{6}$/i.test(String(d.get("primaryColor") ?? ""))
      ? String(d.get("primaryColor")).toUpperCase()
      : "#02B16B",
    associationLogoUrl: typeof d.get("associationLogoUrl") === "string" ? d.get("associationLogoUrl") : null,
    hostCountry: String(d.get("hostCountry") ?? "").trim().toUpperCase(),
    hostCity: String(d.get("hostCity") ?? "").trim(),
    isTeams: (d.get("isTeams") as boolean) ?? false,
    numPlayers: (d.get("numPlayers") as number) ?? 0,
    numRounds,
    eventStartDate,
    eventEndDate,
    roundSchedules: readStoredRoundSchedules(d.get("roundSchedules"), numRounds, safeStartDate, safeEndDate),
    agendaItems: readStoredAgendaItems(d.get("agendaItems"), safeStartDate, safeEndDate),
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
  associationLogoSourceTournamentId?: string | null;
  eventStartDate: string;
  eventEndDate: string;
  hostCountry: string;
  hostCity: string;
  isTeams: boolean;
  numPlayers: number;
  numRounds: number;
  numTries: number;
  players: Array<{ id: number; team: number; name?: string; country?: string }>;
  tables: Array<{ roundId: number; tableId: number; playerIds: number[]; isCompleted?: boolean; useTotalsOnly?: boolean }>;
  roundSchedules?: unknown;
  agendaItems?: unknown;
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
  const roundSchedules = normalizeRoundSchedules(
    params.roundSchedules,
    params.numRounds,
    params.eventStartDate,
    params.eventEndDate,
  );
  const agendaItems = normalizeAgendaItems(params.agendaItems, params.eventStartDate, params.eventEndDate);

  const ref = db.collection("tournaments").doc();

  const logo = params.associationLogoContentType && params.associationLogoDataBase64
    ? await saveAssociationLogo(ref.id, params.associationLogoContentType, params.associationLogoDataBase64)
    : params.associationLogoSourceTournamentId
      ? await copyAssociationLogo(params.associationLogoSourceTournamentId, ref.id)
      : null;

  const tournamentDoc = {
    name: params.name,
    shortName: normalizeShortName(params.shortName),
    primaryColor: normalizePrimaryColor(params.primaryColor),
    associationLogoUrl: logo?.url ?? null,
    associationLogoPath: logo?.path ?? null,
    eventStartDate: params.eventStartDate,
    eventEndDate: params.eventEndDate,
    hostCountry: params.hostCountry.trim().toUpperCase(),
    hostCity: params.hostCity.trim(),
    isTeams: params.isTeams,
    numPlayers: params.numPlayers,
    numRounds: params.numRounds,
    roundSchedules,
    agendaItems,
    numTries: params.numTries,
    isCompleted: false,
    createdByUid: params.createdByUid,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
    dataVersions: {
      members: { revision: 0, changedAt: null },
      players: { revision: 1, changedAt: FieldValue.serverTimestamp() },
      teams: { revision: params.isTeams ? 1 : 0, changedAt: FieldValue.serverTimestamp() },
      rounds: { revision: 1, changedAt: FieldValue.serverTimestamp() },
      tables: { revision: 1, changedAt: FieldValue.serverTimestamp() },
    },
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
      hasProgress: Boolean(table.isCompleted ?? false),
      hasValidManualTotals: false,
      version: 0,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  await flushBatch();
  await Promise.all(batchCommits);

  const snap = await ref.get();
  await bumpGlobalDataVersion("tournaments");
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
  await bumpGlobalDataVersion("tournaments");
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

async function copyAssociationLogo(
  sourceTournamentId: string,
  targetTournamentId: string,
): Promise<{ path: string; url: string }> {
  const source = await db.collection("tournaments").doc(sourceTournamentId).get();
  if (!source.exists) throw notFound("Source tournament not found");
  const sourcePath = String(source.get("associationLogoPath") ?? "").trim();
  if (sourcePath.length === 0) throw badRequest("The selected tournament has no reusable logo");

  const sourceFile = storage.file(sourcePath);
  const [metadata] = await sourceFile.getMetadata();
  const contentType = String(metadata.contentType ?? "").trim();
  if (!(contentType in logoExtensions)) {
    throw badRequest("The selected tournament logo has an unsupported image type");
  }
  const [bytes] = await sourceFile.download();
  return saveAssociationLogo(targetTournamentId, contentType, bytes.toString("base64"));
}

export async function updateTournamentSettings(params: {
  tournamentId: string;
  name: string;
  shortName: string;
  primaryColor: string;
  eventStartDate: string;
  eventEndDate: string;
  hostCountry: string;
  hostCity: string;
  associationLogoContentType?: string | null;
  associationLogoDataBase64?: string | null;
  associationLogoSourceTournamentId?: string | null;
  removeAssociationLogo?: boolean;
  roundSchedules?: unknown;
  agendaItems?: unknown;
}): Promise<Tournament> {
  const normalizedName = params.name.trim();
  if (normalizedName.length === 0) throw badRequest("Tournament name is required");
  const shortName = normalizeShortName(params.shortName);
  const primaryColor = normalizePrimaryColor(params.primaryColor);
  if (!isValidIsoDateRange(params.eventStartDate, params.eventEndDate)) {
    throw badRequest("Tournament dates must use yyyy-MM-dd, and the end date must not be before the start date");
  }
  const ref = db.collection("tournaments").doc(params.tournamentId);
  const before = await ref.get();
  if (!before.exists) throw notFound("Tournament not found");
  const numRounds = Number(before.get("numRounds") ?? 0);
  const roundSchedules = params.roundSchedules === undefined
    ? readStoredRoundSchedules(before.get("roundSchedules"), numRounds, params.eventStartDate, params.eventEndDate)
    : normalizeRoundSchedules(params.roundSchedules, numRounds, params.eventStartDate, params.eventEndDate);
  const agendaItems = params.agendaItems === undefined
    ? readStoredAgendaItems(before.get("agendaItems"), params.eventStartDate, params.eventEndDate)
    : normalizeAgendaItems(params.agendaItems, params.eventStartDate, params.eventEndDate);

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
  } else if (params.associationLogoSourceTournamentId) {
    if (params.associationLogoSourceTournamentId === params.tournamentId) {
      throw badRequest("Select a different tournament logo to reuse");
    }
    logo = await copyAssociationLogo(params.associationLogoSourceTournamentId, params.tournamentId);
  }

  const update: Record<string, unknown> = {
    name: normalizedName,
    shortName,
    primaryColor,
    eventStartDate: params.eventStartDate,
    eventEndDate: params.eventEndDate,
    hostCountry: params.hostCountry.trim().toUpperCase(),
    hostCity: params.hostCity.trim(),
    roundSchedules,
    agendaItems,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (logo !== undefined) {
    update.associationLogoPath = logo?.path ?? null;
    update.associationLogoUrl = logo?.url ?? null;
  }
  await ref.update(update);
  await Promise.all([
    bumpGlobalDataVersion("tournaments"),
    bumpTournamentDataVersion(params.tournamentId, "rounds"),
  ]);

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

export async function deleteTournamentResources(params: {
  logoPath: string;
  deleteLogo: (logoPath: string) => Promise<void>;
  deleteTournamentTree: () => Promise<void>;
}): Promise<void> {
  const logoPath = params.logoPath.trim();
  if (logoPath.length > 0) {
    await params.deleteLogo(logoPath);
  }
  await params.deleteTournamentTree();
}

export async function deleteTournament(tournamentId: string): Promise<void> {
  const ref = db.collection("tournaments").doc(tournamentId);
  const snap = await ref.get();
  if (!snap.exists) throw notFound("Tournament not found");

  await deleteTournamentResources({
    logoPath: String(snap.get("associationLogoPath") ?? ""),
    deleteLogo: async (logoPath) => {
      await storage.file(logoPath).delete({ ignoreNotFound: true });
    },
    deleteTournamentTree: async () => {
      await db.recursiveDelete(ref);
    },
  });
  await bumpGlobalDataVersion("tournaments");
}
