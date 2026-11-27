import { removeTournamentStats } from "./statisticsStore";
import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { db, storage } from "../firebase";
import { DEFAULT_PRIMARY_COLOR, organizersOf, readOrganizerIds, type OrganizerView } from "./organizationsService";
import { badRequest, notFound } from "../api/httpError";
import { isValidIsoDateRange } from "./tournamentDates";
import { drawProblem } from "./tournamentDraw";
import { readRegistrationSettings, type RegistrationSettingsView } from "./registrationSettings";
import { getUserProfile } from "./usersService";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";
import {
  normalizeMatchExtraMinutes,
  normalizeTimeZone,
  readStoredMatchExtraMinutes,
  readStoredTimeZone,
} from "./matchTime";
import { closeRoundsEndedByChange } from "./matchTimeCloseService";
import { scheduleRoundCloses } from "./matchTimeTaskService";
import { roundClockViews, type RoundClockView } from "./roundClock";
import { roundControlViews, type RoundControlView } from "./roundControl";
import { defaultHand, HANDS_PER_TABLE, handsCopyUpdate } from "./tableManagerService";
import {
  normalizeAgendaItems,
  normalizeMatchDuration,
  normalizeMatchEndMode,
  normalizeRoundSchedules,
  readStoredAgendaItems,
  readStoredMatchDuration,
  readStoredMatchEndMode,
  readStoredRoundSchedules,
  requireCompleteRoundSchedules,
  type MatchEndMode,
  type TournamentAgendaItem,
  type TournamentRoundSchedule,
} from "./tournamentSchedule";

export type Tournament = {
  id: string;
  name: string;
  shortName: string;
  /** The organizations that own the tournament. The first one is the main organizer. */
  organizerIds: string[];
  organizers: OrganizerView[];
  /** The color of the main organizer. A tournament has no color of its own. */
  primaryColor: string;
  /** The logo of the main organizer. A tournament has no logo of its own. */
  associationLogoUrl: string | null;
  /** SHA-256 of the logo file. */
  associationLogoHash: string | null;
  hostCountry: string;
  hostCity: string;
  venueName: string;
  venueAddress: string;
  venueMapUrl: string;
  contactEmail: string;
  isTeams: boolean;
  /** When false, the app does not count or show the best hands. Old documents count them. */
  countBestHands: boolean;
  /** When false, the app does not count or show the chicken hands. Old documents count them. */
  countChickenHands: boolean;
  numPlayers: number;
  numRounds: number;
  eventStartDate: string | null;
  eventEndDate: string | null;
  roundSchedules: TournamentRoundSchedule[];
  /** The clocks of the rounds that started. The Play of the Timer starts a round. */
  roundClocks: RoundClockView[];
  /** The manual controls of the rounds (the plan B of the Timer). A round without one follows its clock. */
  roundControls: RoundControlView[];
  agendaItems: TournamentAgendaItem[];
  /** Minutes that a match lasts. The end of a table is the start of its round plus this time. */
  matchDurationMinutes: number;
  /** STOP: the match ends at the end time. FINISH_HAND: the players finish the hand in play. */
  matchEndMode: MatchEndMode;
  /** With FINISH_HAND, minutes after the end time in which the players can record the hand in play. */
  matchExtraMinutes: number;
  /** IANA time zone of the venue. The round times are local times of this zone. Null while it is not set. */
  timeZone: string | null;
  numTries: number;
  isCompleted: boolean;
  /** True when any table has data (a hand, totals or a completed mark). The draw cannot change then. */
  hasTableData: boolean;
  /** Everyone sees a public tournament without signing in. An admin hides test tournaments. Old documents are public. */
  isPublic: boolean;
  /** The player app shows the differences button on the table screen. Old documents show it. */
  showTableDiffs: boolean;
  /** True while a tournament with registrations has no draw yet: no players, no tables. */
  drawPending: boolean;
  /** The registrations of the tournament, or null for a tournament that never had them. */
  registration: RegistrationSettingsView | null;
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

export async function mapTournamentDoc(d: FirebaseFirestore.DocumentSnapshot): Promise<Tournament> {
  const createdByUid = (d.get("createdByUid") as string) ?? null;
  const createdByName = (d.get("createdByName") as string) ?? await resolveCreatedByName(createdByUid);
  const numRounds = Number(d.get("numRounds") ?? 0);
  const eventStartDate = (d.get("eventStartDate") as string) ?? null;
  const eventEndDate = (d.get("eventEndDate") as string) ?? null;
  const safeStartDate = eventStartDate ?? "0000-01-01";
  const safeEndDate = eventEndDate ?? "9999-12-31";
  const organizerIds = readOrganizerIds(d.get("organizerIds"));
  const organizers = await organizersOf(organizerIds);
  const mainOrganizer = organizers[0];

  return {
    id: d.id,
    name: (d.get("name") as string) ?? "",
    shortName: String(d.get("shortName") ?? d.get("name") ?? "").trim().slice(0, 10),
    organizerIds,
    organizers,
    primaryColor: mainOrganizer?.primaryColor ?? DEFAULT_PRIMARY_COLOR,
    associationLogoUrl: mainOrganizer?.logoUrl ?? null,
    associationLogoHash: mainOrganizer?.logoHash ?? null,
    hostCountry: String(d.get("hostCountry") ?? "").trim().toUpperCase(),
    hostCity: String(d.get("hostCity") ?? "").trim(),
    venueName: String(d.get("venueName") ?? ""),
    venueAddress: String(d.get("venueAddress") ?? ""),
    venueMapUrl: String(d.get("venueMapUrl") ?? ""),
    contactEmail: String(d.get("contactEmail") ?? ""),
    isTeams: (d.get("isTeams") as boolean) ?? false,
    countBestHands: d.get("countBestHands") !== false,
    countChickenHands: d.get("countChickenHands") !== false,
    numPlayers: (d.get("numPlayers") as number) ?? 0,
    numRounds,
    eventStartDate,
    eventEndDate,
    roundSchedules: readStoredRoundSchedules(d.get("roundSchedules"), numRounds, safeStartDate, safeEndDate),
    roundClocks: roundClockViews({ numRounds, roundClocks: d.get("roundClocks") }),
    roundControls: roundControlViews({ numRounds, roundControls: d.get("roundControls") }),
    agendaItems: readStoredAgendaItems(d.get("agendaItems")),
    matchDurationMinutes: readStoredMatchDuration(d.get("matchDurationMinutes")),
    matchEndMode: readStoredMatchEndMode(d.get("matchEndMode")),
    matchExtraMinutes: readStoredMatchExtraMinutes(d.get("matchExtraMinutes")),
    timeZone: readStoredTimeZone(d.get("timeZone")),
    numTries: (d.get("numTries") as number) ?? 0,
    isCompleted: (d.get("isCompleted") as boolean) ?? false,
    hasTableData: Number(d.get("tablesWithProgress") ?? 0) > 0,
    isPublic: d.get("isPublic") !== false,
    showTableDiffs: d.get("showTableDiffs") !== false,
    drawPending: d.get("drawPending") === true,
    registration: readRegistrationSettings(d.get("registration")),
    createdByUid,
    createdByName,
    createdAt: toIsoString(d.get("createdAt")),
    updatedAt: toIsoString(d.get("updatedAt")),
  };
}

/** Firestore allows 500 writes in a batch. Keep a margin. */
const MAX_WRITES_PER_BATCH = 450;

export async function createTournament(params: {
  name: string;
  shortName: string;
  /** Checked ids of existing organizations. The first one is the main organizer. The route requires one. */
  organizerIds?: string[];
  eventStartDate: string;
  eventEndDate: string;
  hostCountry: string;
  hostCity: string;
  venueName?: string;
  venueAddress?: string;
  venueMapUrl?: string;
  contactEmail?: string;
  isTeams: boolean;
  countBestHands?: boolean;
  countChickenHands?: boolean;
  numPlayers: number;
  numRounds: number;
  numTries: number;
  players: Array<{ id: number; team: number; name?: string; country?: string }>;
  tables: Array<{ roundId: number; tableId: number; playerIds: number[]; isCompleted?: boolean; useTotalsOnly?: boolean }>;
  roundSchedules?: unknown;
  agendaItems?: unknown;
  matchDurationMinutes?: unknown;
  matchEndMode?: unknown;
  matchExtraMinutes?: unknown;
  timeZone?: unknown;
  createdByUid: string;
  /**
   * The registrations of a tournament that opens them before the draw. The tournament then has no players, no tables
   * and no draw: the organizer makes the draw when the registrations close (registrationsService).
   */
  registration?: Record<string, unknown>;
}): Promise<Tournament> {
  const withRegistration = params.registration != null;
  const numTablesPerRound = params.numPlayers / 4;
  const expectedTables = params.numRounds * numTablesPerRound;
  if (withRegistration) {
    if (params.players.length > 0 || params.tables.length > 0) throw badRequest("A tournament with registrations has no draw yet");
  } else if (params.players.length !== params.numPlayers) {
    throw badRequest("Invalid players payload", { expected: params.numPlayers, received: params.players.length });
  }
  if (!withRegistration && params.tables.length !== expectedTables) {
    throw badRequest("Invalid tables payload", { expected: expectedTables, received: params.tables.length });
  }
  // A tournament without teams has no team data. Every slot gets team 0.
  const players = params.isTeams ? params.players : params.players.map((player) => ({ ...player, team: 0 }));
  const problem = withRegistration ? null : drawProblem(players, params.tables, params.numRounds, numTablesPerRound, params.isTeams);
  if (problem) throw badRequest("Invalid draw", { problem });
  const roundSchedules = normalizeRoundSchedules(
    params.roundSchedules,
    params.numRounds,
    params.eventStartDate,
    params.eventEndDate,
  );
  requireCompleteRoundSchedules(roundSchedules, params.numRounds);
  const agendaItems = normalizeAgendaItems(params.agendaItems);
  const matchDurationMinutes = normalizeMatchDuration(params.matchDurationMinutes);
  const matchEndMode = normalizeMatchEndMode(params.matchEndMode);
  const matchExtraMinutes = normalizeMatchExtraMinutes(params.matchExtraMinutes);
  const timeZone = normalizeTimeZone(params.timeZone);

  const ref = db.collection("tournaments").doc();

  const tournamentDoc = {
    name: params.name,
    shortName: normalizeShortName(params.shortName),
    organizerIds: params.organizerIds ?? [],
    eventStartDate: params.eventStartDate,
    eventEndDate: params.eventEndDate,
    hostCountry: params.hostCountry.trim().toUpperCase(),
    hostCity: params.hostCity.trim(),
    venueName: params.venueName ?? "",
    venueAddress: params.venueAddress ?? "",
    venueMapUrl: params.venueMapUrl ?? "",
    contactEmail: params.contactEmail ?? "",
    isTeams: params.isTeams,
    countBestHands: params.countBestHands ?? true,
    countChickenHands: params.countChickenHands ?? true,
    numPlayers: withRegistration ? 0 : params.numPlayers,
    numRounds: params.numRounds,
    ...(withRegistration ? { drawPending: true, registration: params.registration } : {}),
    roundSchedules,
    agendaItems,
    matchDurationMinutes,
    matchEndMode,
    matchExtraMinutes,
    timeZone,
    numTries: params.numTries,
    isCompleted: false,
    // The number of tables with data. Saves keep it current. Imported tables can arrive completed.
    tablesWithProgress: params.tables.filter((table) => Boolean(table.isCompleted ?? false)).length,
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

  // The children are written first and the tournament document last. A tournament that is missing its
  // document is invisible, so a failure in the middle never shows a half-built tournament.
  // If anything fails, everything written so far is deleted.
  const writes = tournamentContentWrites(ref, { players, tables: params.tables, numRounds: params.numRounds, isTeams: params.isTeams });
  try {
    await commitContentWrites(writes);
    await ref.set(tournamentDoc);
  } catch (error) {
    await db.recursiveDelete(ref).catch(() => undefined);
    throw error;
  }

  const snap = await ref.get();
  await bumpGlobalDataVersion("tournaments");
  return mapTournamentDoc(snap);
}

export type ContentWrite = { ref: FirebaseFirestore.DocumentReference; data: Record<string, unknown> };

/**
 * The documents under a tournament that the draw makes: the player slots, the teams, the rounds, the tables and
 * their empty hands. A tournament with registrations gets them when the organizer makes the draw at the close.
 */
export function tournamentContentWrites(
  ref: FirebaseFirestore.DocumentReference,
  params: {
    players: Array<{ id: number; team: number; name?: string; country?: string }>;
    tables: Array<{ roundId: number; tableId: number; playerIds: number[]; isCompleted?: boolean; useTotalsOnly?: boolean }>;
    numRounds: number;
    isTeams: boolean;
  },
): ContentWrite[] {
  const players = params.players;
  const writes: ContentWrite[] = [];
  const addSet = (docRef: FirebaseFirestore.DocumentReference, data: Record<string, unknown>) => {
    writes.push({ ref: docRef, data });
  };

  for (const player of players) {
    const playerRef = ref.collection("players").doc(String(player.id));
    addSet(playerRef, {
      id: player.id,
      name: player.name ?? `Player ${player.id}`,
      team: player.team,
      country: player.country ?? "",
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  if (params.isTeams) {
    const teamIds = [...new Set(players.map((player) => player.team))]
      .filter((teamId) => Number.isInteger(teamId) && teamId > 0)
      .sort((a, b) => a - b);
    for (const teamId of teamIds) {
      const teamRef = ref.collection("teams").doc(String(teamId));
      addSet(teamRef, {
        id: teamId,
        name: `Team ${teamId}`,
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  }

  for (let roundId = 1; roundId <= params.numRounds; roundId++) {
    const roundRef = ref.collection("rounds").doc(String(roundId));
    addSet(roundRef, {
      roundId,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
  }

  for (const table of params.tables) {
    const tableRef = ref.collection("tables").doc(`${table.roundId}_${table.tableId}`);
    addSet(tableRef, {
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
      isCompleted: Boolean(table.isCompleted ?? false),
      useTotalsOnly: Boolean(table.useTotalsOnly ?? false),
      hasProgress: Boolean(table.isCompleted ?? false),
      hasValidManualTotals: false,
      completionStatus: "empty",
      bestHandScores: [],
      chickenHandCount: 0,
      ...handsCopyUpdate(0, Array.from({ length: HANDS_PER_TABLE }, (_, index) => defaultHand(index + 1))),
      version: 0,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    for (let handId = 1; handId <= HANDS_PER_TABLE; handId++) {
      addSet(tableRef.collection("hands").doc(String(handId)), {
        ...defaultHand(handId),
        createdAt: FieldValue.serverTimestamp(),
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
  }

  return writes;
}

/** Writes the documents in parallel batches. Firestore allows 500 writes in a batch. */
export async function commitContentWrites(writes: ContentWrite[]): Promise<void> {
  const chunks: ContentWrite[][] = [];
  for (let index = 0; index < writes.length; index += MAX_WRITES_PER_BATCH) {
    chunks.push(writes.slice(index, index + MAX_WRITES_PER_BATCH));
  }
  const results = await Promise.allSettled(chunks.map((chunk) => {
    const batch = db.batch();
    chunk.forEach(({ ref: docRef, data }) => batch.set(docRef, data));
    return batch.commit();
  }));
  const failed = results.find((result): result is PromiseRejectedResult => result.status === "rejected");
  if (failed) throw failed.reason;
}

/** One tournament, or null when it does not exist. */
export async function getTournament(tournamentId: string): Promise<Tournament | null> {
  const snap = await db.collection("tournaments").doc(tournamentId).get();
  return tournamentFromSnapshot(snap);
}

/** The tournament of a document that was already read, or null when it does not exist. */
export async function tournamentFromSnapshot(snap: FirebaseFirestore.DocumentSnapshot): Promise<Tournament | null> {
  return snap.exists ? mapTournamentDoc(snap) : null;
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

function normalizeShortName(value: string): string {
  const shortName = value.trim();
  if (shortName.length === 0) throw badRequest("Tournament short name is required");
  if (shortName.length > 10) throw badRequest("Tournament short name must contain at most 10 characters");
  return shortName;
}

export async function updateTournamentSettings(params: {
  tournamentId: string;
  name: string;
  shortName: string;
  /** Checked ids of existing organizations, only when they change. */
  organizerIds?: string[];
  eventStartDate: string;
  eventEndDate: string;
  hostCountry: string;
  hostCity: string;
  venueName?: string;
  venueAddress?: string;
  venueMapUrl?: string;
  contactEmail?: string;
  countBestHands?: boolean;
  countChickenHands?: boolean;
  isPublic?: boolean;
  showTableDiffs?: boolean;
  roundSchedules?: unknown;
  agendaItems?: unknown;
  matchDurationMinutes?: unknown;
  matchEndMode?: unknown;
  matchExtraMinutes?: unknown;
  timeZone?: unknown;
}): Promise<Tournament> {
  const normalizedName = params.name.trim();
  if (normalizedName.length === 0) throw badRequest("Tournament name is required");
  const shortName = normalizeShortName(params.shortName);
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
  // An old tournament may lack some of them. Its next edit must complete them, even when it does not send them.
  requireCompleteRoundSchedules(roundSchedules, numRounds);
  const agendaItems = params.agendaItems === undefined
    ? readStoredAgendaItems(before.get("agendaItems"))
    : normalizeAgendaItems(params.agendaItems);

  const update: Record<string, unknown> = {
    name: normalizedName,
    shortName,
    eventStartDate: params.eventStartDate,
    eventEndDate: params.eventEndDate,
    hostCountry: params.hostCountry.trim().toUpperCase(),
    hostCity: params.hostCity.trim(),
    venueName: params.venueName ?? "",
    venueAddress: params.venueAddress ?? "",
    venueMapUrl: params.venueMapUrl ?? "",
    contactEmail: params.contactEmail ?? "",
    roundSchedules,
    agendaItems,
    updatedAt: FieldValue.serverTimestamp(),
  };
  if (params.matchDurationMinutes !== undefined) update.matchDurationMinutes = normalizeMatchDuration(params.matchDurationMinutes);
  if (params.matchEndMode !== undefined) update.matchEndMode = normalizeMatchEndMode(params.matchEndMode);
  if (params.matchExtraMinutes !== undefined) update.matchExtraMinutes = normalizeMatchExtraMinutes(params.matchExtraMinutes);
  if (params.timeZone !== undefined) update.timeZone = normalizeTimeZone(params.timeZone);
  if (params.countBestHands !== undefined) update.countBestHands = params.countBestHands;
  if (params.countChickenHands !== undefined) update.countChickenHands = params.countChickenHands;
  if (params.isPublic !== undefined) update.isPublic = params.isPublic;
  if (params.showTableDiffs !== undefined) update.showTableDiffs = params.showTableDiffs;
  if (params.organizerIds !== undefined) {
    if (params.organizerIds.length === 0) throw badRequest("A tournament needs at least one organizer");
    update.organizerIds = params.organizerIds;
  }
  await ref.update(update);
  await Promise.all([
    bumpGlobalDataVersion("tournaments"),
    bumpTournamentDataVersion(params.tournamentId, "rounds"),
  ]);

  const after = await ref.get();
  // A new match duration moves the end of a running round.
  const nowMs = Date.now();
  await scheduleRoundCloses(params.tournamentId, after.data() ?? {}, nowMs);
  // A shorter match can end a running round at once. No task runs for an end that is past.
  await closeRoundsEndedByChange({ tournamentId: params.tournamentId, before: before.data() ?? {}, after: after.data() ?? {}, nowMs });
  return mapTournamentDoc(after);
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
      // The public link of its registrations goes away with it.
      const slug = String((snap.get("registration") as Record<string, unknown> | undefined)?.slug ?? "");
      if (slug !== "") {
        const link = db.collection("registrationSlugs").doc(slug);
        if ((await link.get()).get("tournamentId") === tournamentId) await link.delete();
      }
      // The numbers of the tournament leave the sums before its tables are deleted.
      await removeTournamentStats(tournamentId);
      await db.recursiveDelete(ref);
    },
  });
  await bumpGlobalDataVersion("tournaments");
}
