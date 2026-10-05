import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../firebase";
import { conflict, notFound } from "../api/httpError";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import {
  calculateBestHandScore,
  calculateTableCompletionStatus,
  countChickenHands,
  hasDuplicateEmaAssignments,
  hasFourValidScores,
  hasValidTablePoints,
} from "./tournamentContentRules";
import { bumpGlobalDataVersion, bumpTournamentDataVersion } from "./dataVersionsService";

export type TournamentPlayer = {
  id: number;
  name: string;
  team: number;
  country: string;
  assignedEmaId: string | null;
  nonMember: NonMemberPlayer | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export type NonMemberPlayer = { firstName: string; lastName: string; country: string };

function readNonMember(value: unknown): NonMemberPlayer | null {
  if (value == null || typeof value !== "object") return null;
  const data = value as Record<string, unknown>;
  const firstName = String(data.firstName ?? "").trim();
  const lastName = String(data.lastName ?? "").trim();
  const country = String(data.country ?? "").trim().toUpperCase();
  return firstName && lastName && country ? { firstName, lastName, country } : null;
}

async function refreshAssignmentCountries(tournamentId: string): Promise<void> {
  const players = await db.collection("tournaments").doc(tournamentId).collection("players").get();
  const emaIds = [...new Set(players.docs.map((player) => player.get("assignedEmaId"))
    .filter((value): value is string => typeof value === "string" && value.length > 0))];
  const registry = await Promise.all(emaIds.map((emaId) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()));
  const countries = new Map(registry.filter((player) => player.exists)
    .map((player) => [player.id, String(player.get("country") ?? "").trim().toUpperCase()]));
  const batch = db.batch();
  players.docs.forEach((player) => {
    const emaId = typeof player.get("assignedEmaId") === "string" ? String(player.get("assignedEmaId")) : null;
    const nonMember = readNonMember(player.get("nonMember"));
    batch.update(player.ref, { assignedCountry: emaId == null ? nonMember?.country ?? "" : countries.get(emaId) ?? "" });
  });
  await batch.commit();
}

export type TournamentTeam = {
  id: number;
  name: string;
  playerIds: number[];
};

function timestampToIso(value: unknown): string | null {
  return value instanceof Timestamp ? value.toDate().toISOString() : null;
}

export type TournamentRound = {
  roundId: number;
};

export type TournamentTable = {
  version: number;
  roundId: number;
  tableId: number;
  playerIds: number[];
  isCompleted: boolean;
  useTotalsOnly: boolean;
  usePointsCalculation: boolean;
  hasProgress: boolean;
  hasValidManualTotals: boolean;
  completionStatus: string;
  bestHandScore: number | null;
  chickenHandCount: number;
};

export async function listTournamentPlayers(tournamentId: string): Promise<TournamentPlayer[]> {
  const snap = await db.collection("tournaments").doc(tournamentId).collection("players").get();
  return snap.docs
    .map((d) => ({
      id: Number(d.get("id")),
      name: String(d.get("name") ?? ""),
      team: Number(d.get("team") ?? 0),
      country: String(d.get("country") ?? ""),
      assignedEmaId: typeof d.get("assignedEmaId") === "string" ? d.get("assignedEmaId") : null,
      nonMember: readNonMember(d.get("nonMember")),
      createdAt: timestampToIso(d.get("createdAt")),
      updatedAt: timestampToIso(d.get("updatedAt")),
    }))
    .sort((a, b) => a.id - b.id);
}

export async function listTournamentTeams(tournamentId: string): Promise<TournamentTeam[]> {
  const tournament = db.collection("tournaments").doc(tournamentId);
  const [tournamentSnapshot, players, teams] = await Promise.all([
    tournament.get(),
    tournament.collection("players").get(),
    tournament.collection("teams").get(),
  ]);
  if (!tournamentSnapshot.exists) throw notFound("Tournament not found");
  if (tournamentSnapshot.get("isTeams") !== true) return [];
  const names = new Map<number, string>();
  teams.docs.forEach((team) => {
    const id = Number(team.get("id") ?? team.id);
    if (Number.isInteger(id) && id > 0) {
      names.set(id, String(team.get("name") ?? "").trim());
    }
  });

  const playerIdsByTeam = new Map<number, number[]>();
  players.docs.forEach((player) => {
    const teamId = Number(player.get("team") ?? 0);
    const playerId = Number(player.get("id") ?? player.id);
    if (!Number.isInteger(teamId) || teamId <= 0 || !Number.isInteger(playerId)) return;
    playerIdsByTeam.set(teamId, [...(playerIdsByTeam.get(teamId) ?? []), playerId]);
  });

  return [...playerIdsByTeam.entries()]
    .map(([id, playerIds]) => ({
      id,
      name: names.get(id) || `Team ${id}`,
      playerIds: playerIds.sort((a, b) => a - b),
    }))
    .sort((a, b) => a.id - b.id);
}

/**
 * Updates one team and its fixed schedule slots.
 * Players selected from other teams exchange slots with displaced players.
 */
export async function updateTournamentTeam(params: {
  tournamentId: string;
  teamId: number;
  name: string;
  emaIds: Array<string | null>;
}): Promise<void> {
  const tournament = db.collection("tournaments").doc(params.tournamentId);
  const players = tournament.collection("players");
  const teams = tournament.collection("teams");
  const assignmentRefs = tournament.collection("emaPlayerAssignments");
  const tableSummaries = await listTournamentTables(params.tournamentId, null);
  const tournamentHasProgress = tableSummaries.some((table) => table.hasProgress);

  await db.runTransaction(async (transaction) => {
    const [tournamentSnapshot, playerSnapshot, teamSnapshot, currentTeam] = await Promise.all([
      transaction.get(tournament),
      transaction.get(players),
      transaction.get(teams),
      transaction.get(teams.doc(String(params.teamId))),
    ]);
    if (!tournamentSnapshot.exists) throw notFound("Tournament not found");
    if (tournamentSnapshot.get("isTeams") !== true) {
      throw conflict("This tournament does not use teams");
    }
    const playerDocs = playerSnapshot.docs.slice().sort((a, b) => {
      return Number(a.get("id") ?? a.id) - Number(b.get("id") ?? b.id);
    });
    const targetSlots = playerDocs.filter((player) => Number(player.get("team") ?? 0) === params.teamId);
    if (targetSlots.length === 0) throw notFound("Team not found");
    if (targetSlots.length > 4 || params.emaIds.length !== targetSlots.length) {
      throw conflict("Team assignments must match its schedule slots");
    }

    const desiredEmaIds = params.emaIds.map((emaId) => emaId?.trim() || null);
    if (hasDuplicateEmaAssignments(desiredEmaIds)) {
      throw conflict("A player cannot occupy two team slots");
    }
    const assignedDesired = desiredEmaIds.filter((emaId): emaId is string => emaId != null);

    const currentByRef = new Map<string, string | null>();
    const sourceByEma = new Map<string, FirebaseFirestore.QueryDocumentSnapshot>();
    playerDocs.forEach((player) => {
      const emaId = typeof player.get("assignedEmaId") === "string"
        ? String(player.get("assignedEmaId")).trim() || null
        : null;
      currentByRef.set(player.ref.path, emaId);
      if (emaId != null) sourceByEma.set(emaId, player);
    });

    const desiredSet = new Set(assignedDesired);
    const displaced = targetSlots
      .map((slot) => currentByRef.get(slot.ref.path) ?? null)
      .filter((emaId): emaId is string => emaId != null && !desiredSet.has(emaId));
    const finalByRef = new Map(currentByRef);

    targetSlots.forEach((slot, index) => {
      finalByRef.set(slot.ref.path, desiredEmaIds[index] ?? null);
    });

    const vacatedSourceSlots: FirebaseFirestore.QueryDocumentSnapshot[] = [];
    desiredEmaIds.forEach((emaId) => {
      if (emaId == null) return;
      const source = sourceByEma.get(emaId);
      if (source != null && Number(source.get("team") ?? 0) !== params.teamId) {
        vacatedSourceSlots.push(source);
      }
    });
    vacatedSourceSlots.forEach((source, index) => {
      finalByRef.set(source.ref.path, displaced[index] ?? null);
    });

    const membershipChanged = playerDocs.some((player) => {
      return (currentByRef.get(player.ref.path) ?? null) !== (finalByRef.get(player.ref.path) ?? null);
    });
    if (membershipChanged && tournamentHasProgress) {
      throw conflict("Team players cannot change after table results have started");
    }

    const namesByTeam = new Map<number, string>();
    playerDocs.forEach((player) => {
      const teamId = Number(player.get("team") ?? 0);
      if (Number.isInteger(teamId) && teamId > 0) namesByTeam.set(teamId, `Team ${teamId}`);
    });
    teamSnapshot.docs.forEach((team) => {
      const teamId = Number(team.get("id") ?? team.id);
      if (Number.isInteger(teamId) && teamId > 0) {
        namesByTeam.set(teamId, String(team.get("name") ?? `Team ${teamId}`).trim());
      }
    });
    const duplicateName = [...namesByTeam.entries()].some(([teamId, name]) => {
      return teamId !== params.teamId && name.localeCompare(params.name, undefined, { sensitivity: "accent" }) === 0;
    });
    if (duplicateName) throw conflict("Team name is already in use");

    const affectedEmaIds = new Set<string>();
    playerDocs.forEach((player) => {
      const previous = currentByRef.get(player.ref.path) ?? null;
      const next = finalByRef.get(player.ref.path) ?? null;
      if (previous === next) return;
      if (previous != null) affectedEmaIds.add(previous);
      if (next != null) affectedEmaIds.add(next);
      transaction.update(player.ref, {
        assignedEmaId: next,
        ...(next == null ? {} : { nonMember: null }),
        updatedAt: FieldValue.serverTimestamp(),
      });
    });

    affectedEmaIds.forEach((emaId) => {
      const owner = playerDocs.find((player) => finalByRef.get(player.ref.path) === emaId);
      if (owner == null) {
        transaction.delete(assignmentRefs.doc(emaId));
      } else {
        transaction.set(assignmentRefs.doc(emaId), {
          playerId: Number(owner.get("id") ?? owner.id),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    });

    transaction.set(teams.doc(String(params.teamId)), {
      id: params.teamId,
      name: params.name,
      ...(currentTeam.exists ? {} : { createdAt: FieldValue.serverTimestamp() }),
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true });
  });
  await refreshAssignmentCountries(params.tournamentId);
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "teams"),
    bumpTournamentDataVersion(params.tournamentId, "players"),
    bumpGlobalDataVersion("tournaments"),
  ]);
}

export async function assignTournamentPlayer(params: {
  tournamentId: string;
  playerId: number;
  emaId: string | null;
  nonMember: NonMemberPlayer | null;
}): Promise<void> {
  const playerRef = db.collection("tournaments").doc(params.tournamentId)
    .collection("players").doc(String(params.playerId));
  const players = playerRef.parent;
  const assignmentRefs = db.collection("tournaments").doc(params.tournamentId)
    .collection("emaPlayerAssignments");
  const tableSummaries = await listTournamentTables(params.tournamentId, null);
  const tournamentHasProgress = tableSummaries.some((table) => table.hasProgress);

  await db.runTransaction(async (transaction) => {
    const player = await transaction.get(playerRef);
    if (!player.exists) {
      throw new Error("Player not found");
    }

    const previousEmaId = typeof player.get("assignedEmaId") === "string"
      ? String(player.get("assignedEmaId"))
      : null;
    const previousNonMember = readNonMember(player.get("nonMember"));
    const assignmentChanged = previousEmaId !== params.emaId
      || JSON.stringify(previousNonMember) !== JSON.stringify(params.nonMember);
    if ((previousEmaId != null || previousNonMember != null) && assignmentChanged && tournamentHasProgress) {
      throw conflict("Players cannot change after table results have started");
    }
    const nextAssignmentRef = params.emaId == null ? null : assignmentRefs.doc(params.emaId);
    const nextAssignment = nextAssignmentRef == null ? null : await transaction.get(nextAssignmentRef);
    const existingAssignments = params.emaId == null
      ? null
      : await transaction.get(players.where("assignedEmaId", "==", params.emaId));

    if (params.emaId != null && nextAssignmentRef != null) {
      const mappedPlayerId = nextAssignment?.exists ? Number(nextAssignment.get("playerId")) : null;
      if (mappedPlayerId != null && mappedPlayerId !== params.playerId) {
        throw conflict("EMA player is already assigned in this tournament");
      }
      const assignedElsewhere = existingAssignments?.docs.some((document) => document.id !== playerRef.id) ?? false;
      if (assignedElsewhere) {
        throw conflict("EMA player is already assigned in this tournament");
      }
    }

    let assignedCountry = params.nonMember?.country ?? "";
    if (params.emaId != null) {
      const registryPlayer = await transaction.get(db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(params.emaId));
      if (!registryPlayer.exists) throw notFound("EMA player not found");
      assignedCountry = String(registryPlayer.get("country") ?? "").trim().toUpperCase();
    }

    if (previousEmaId != null && previousEmaId !== params.emaId) {
      transaction.delete(assignmentRefs.doc(previousEmaId));
    }
    if (nextAssignmentRef != null) {
      transaction.set(nextAssignmentRef, {
        playerId: params.playerId,
        updatedAt: FieldValue.serverTimestamp(),
      });
    }
    transaction.update(playerRef, {
      assignedEmaId: params.emaId,
      nonMember: params.nonMember,
      assignedCountry,
      updatedAt: FieldValue.serverTimestamp(),
    });
  });
  await refreshAssignmentCountries(params.tournamentId);
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "players"),
    bumpGlobalDataVersion("tournaments"),
  ]);
}

export async function listTournamentRounds(tournamentId: string): Promise<TournamentRound[]> {
  const snap = await db.collection("tournaments").doc(tournamentId).collection("rounds").get();
  return snap.docs
    .map((d) => ({
      roundId: Number(d.get("roundId")),
    }))
    .sort((a, b) => a.roundId - b.roundId);
}

export async function listTournamentTables(
  tournamentId: string,
  roundId: number | null,
): Promise<TournamentTable[]> {
  const collection = db.collection("tournaments").doc(tournamentId).collection("tables");
  const snap = roundId == null
    ? await collection.get()
    : await collection.where("roundId", "==", roundId).get();

  const tables = await Promise.all(snap.docs.map(async (d) => {
    const storedHasProgress = d.get("hasProgress");
    const storedHasValidManualTotals = d.get("hasValidManualTotals");
    const storedCompletionStatus = d.get("completionStatus");
    const storedChickenHandCount = d.get("chickenHandCount");
    const storedBestHandScore = d.get("bestHandScore");
    if (
      typeof storedHasProgress === "boolean"
      && typeof storedHasValidManualTotals === "boolean"
      && typeof storedCompletionStatus === "string"
      && typeof storedChickenHandCount === "number"
      && (storedBestHandScore === null || typeof storedBestHandScore === "number")
    ) {
      return {
        version: Number(d.get("version") ?? 0),
        roundId: Number(d.get("roundId")),
        tableId: Number(d.get("tableId")),
        playerIds: (d.get("playerIds") as unknown[] | undefined ?? []).map((x) => Number(x)),
        isCompleted: Boolean(d.get("isCompleted") ?? false),
        useTotalsOnly: Boolean(d.get("useTotalsOnly") ?? true),
        usePointsCalculation: Boolean(d.get("usePointsCalculation") ?? true),
        hasProgress: storedHasProgress,
        hasValidManualTotals: storedHasValidManualTotals,
        completionStatus: storedCompletionStatus,
        bestHandScore: storedBestHandScore,
        chickenHandCount: storedChickenHandCount,
      };
    }
    // Old documents use this fallback until the summary backfill runs.
    const hands = await d.ref.collection("hands").get();
    const hasHandProgress = hands.docs.some((hand) => {
      const value = hand.data();
      return [
        value.playerWinnerId,
        value.playerLooserId,
        value.handScore,
        value.playerEastPenalty,
        value.playerSouthPenalty,
        value.playerWestPenalty,
        value.playerNorthPenalty,
      ].some((field) => String(field ?? "").trim().length > 0)
        || Boolean(value.isChickenHand)
        || Boolean(value.isDone);
    });
    const hasTableProgress = [
      d.get("playerEastId"),
      d.get("playerSouthId"),
      d.get("playerWestId"),
      d.get("playerNorthId"),
      d.get("playerEastScore"),
      d.get("playerSouthScore"),
      d.get("playerWestScore"),
      d.get("playerNorthScore"),
      d.get("playerEastPoints"),
      d.get("playerSouthPoints"),
      d.get("playerWestPoints"),
      d.get("playerNorthPoints"),
    ].some((field) => String(field ?? "").trim().length > 0);
    const hasValidManualScores = hasFourValidScores([
      d.get("manualPlayerEastScore") || d.get("playerEastScore"),
      d.get("manualPlayerSouthScore") || d.get("playerSouthScore"),
      d.get("manualPlayerWestScore") || d.get("playerWestScore"),
      d.get("manualPlayerNorthScore") || d.get("playerNorthScore"),
    ]);
    const hasValidManualPoints = hasValidTablePoints([
      d.get("manualPlayerEastPoints") || d.get("playerEastPoints"),
      d.get("manualPlayerSouthPoints") || d.get("playerSouthPoints"),
      d.get("manualPlayerWestPoints") || d.get("playerWestPoints"),
      d.get("manualPlayerNorthPoints") || d.get("playerNorthPoints"),
    ]);
    const useTotalsOnly = Boolean(d.get("useTotalsOnly") ?? true);
    const usePointsCalculation = Boolean(d.get("usePointsCalculation") ?? true);

    const hasValidManualTotals = useTotalsOnly ? hasValidManualScores : !usePointsCalculation && hasValidManualPoints;
    return {
      version: Number(d.get("version") ?? 0),
      roundId: Number(d.get("roundId")),
      tableId: Number(d.get("tableId")),
      playerIds: (d.get("playerIds") as unknown[] | undefined ?? []).map((x) => Number(x)),
      isCompleted: Boolean(d.get("isCompleted") ?? false),
      useTotalsOnly,
      usePointsCalculation,
      hasProgress: hasTableProgress || hasHandProgress || Boolean(d.get("isCompleted") ?? false),
      hasValidManualTotals,
      completionStatus: calculateTableCompletionStatus({
        hasData: hasTableProgress || hasHandProgress,
        seatIds: [d.get("playerEastId"), d.get("playerSouthId"), d.get("playerWestId"), d.get("playerNorthId")],
        useTotalsOnly,
        hasValidTotals: useTotalsOnly ? hasValidManualScores : hasFourValidScores([
          d.get("playerEastScore"), d.get("playerSouthScore"), d.get("playerWestScore"), d.get("playerNorthScore"),
        ]),
      }),
      bestHandScore: calculateBestHandScore(hands.docs.map((hand) => hand.data())),
      chickenHandCount: countChickenHands(hands.docs.map((hand) => hand.data())),
    };
  }));

  return tables
    .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
