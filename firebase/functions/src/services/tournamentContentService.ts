import { refreshTournamentStats } from "./statisticsStore";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { db } from "../firebase";
import { badRequest, conflict, notFound } from "../api/httpError";
import { tablePointsOf } from "./handScoring";
import { readSubstitution, SUBSTITUTION_REASONS, type PlayerSubstitution } from "./playerSubstitution";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { loadAccountsByEmaId, type AccountInfo } from "./playerAccountsService";
import {
  hasDuplicateEmaAssignments,
} from "./tournamentContentRules";
import {
  bumpGlobalDataVersion,
  bumpTournamentDataVersion,
  globalVersionUpdate,
  tableVersionUpdate,
  tournamentVersionUpdate,
} from "./dataVersionsService";

export type TournamentPlayer = {
  id: number;
  name: string;
  team: number;
  country: string;
  assignedEmaId: string | null;
  accountUid: string | null;
  /** True when an account owns the EMA id of the slot. */
  hasAccount: boolean;
  /**
   * The email of the account when the slot has one. Otherwise the pending email of the import, which "Invite" uses
   * to create the account. Only admins read it: the routes hide it from everyone else.
   */
  email: string | null;
  /** Set when a substitute plays for this person from a round on. The substitute is not recorded. */
  substitution: PlayerSubstitution | null;
  createdAt: string | null;
  updatedAt: string | null;
};

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
    batch.update(player.ref, { assignedCountry: emaId == null ? "" : countries.get(emaId) ?? "" });
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
  hasProgress: boolean;
  hasValidManualTotals: boolean;
  completionStatus: string;
  bestHandScores: number[];
  chickenHandCount: number;
};

/**
 * Lists the slots of a tournament. Whether a slot has an account comes from the accounts, so the list reads
 * the accounts of the slots. Pass `withAccounts: false` when the caller needs only the slots, for example to assign
 * players in a batch.
 */
export async function listTournamentPlayers(
  tournamentId: string,
  options: { withAccounts?: boolean } = {},
): Promise<TournamentPlayer[]> {
  const snap = await db.collection("tournaments").doc(tournamentId).collection("players").get();
  // The account of a slot is the account that owns the EMA id of the slot.
  const accounts = options.withAccounts === false ? new Map<string, AccountInfo>() : await loadAccountsByEmaId(
    snap.docs.flatMap((d) => (typeof d.get("assignedEmaId") === "string" ? [String(d.get("assignedEmaId"))] : [])),
  );
  return snap.docs
    .map((d) => readPlayer(d, accounts))
    .sort((a, b) => a.id - b.id);
}

function readPlayer(
  d: FirebaseFirestore.DocumentSnapshot,
  accountsByEmaId: ReadonlyMap<string, AccountInfo>,
): TournamentPlayer {
  const assignedEmaId = typeof d.get("assignedEmaId") === "string" ? d.get("assignedEmaId") : null;
  const account = assignedEmaId == null ? undefined : accountsByEmaId.get(assignedEmaId);
  const accountUid = account?.uid ?? null;
  const pendingEmail = typeof d.get("email") === "string" && d.get("email") !== "" ? String(d.get("email")) : null;
  return {
    id: Number(d.get("id")),
    name: String(d.get("name") ?? ""),
    team: Number(d.get("team") ?? 0),
    country: String(d.get("country") ?? ""),
    assignedEmaId,
    accountUid,
    hasAccount: accountUid != null,
    email: account?.email ?? pendingEmail,
    substitution: readSubstitution(d.get("substitution")),
    createdAt: timestampToIso(d.get("createdAt")),
    updatedAt: timestampToIso(d.get("updatedAt")),
  };
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

/** Tables with data are counted on the tournament document by every save and reset. */
export function tournamentHasStartedTables(tournament: { get(field: string): unknown }): boolean {
  return Number(tournament.get("tablesWithProgress") ?? 0) > 0;
}

/**
 * Sets or removes the substitute seat of a table, and stores the table points again.
 * It raises the version of the table, so an editor who has the table open gets a conflict.
 * Returns the update for the version list of the tournament document.
 */
function changeTable(
  transaction: FirebaseFirestore.Transaction,
  table: FirebaseFirestore.QueryDocumentSnapshot,
  forcedZeroPlayerId: string | null,
): Record<string, unknown> {
  const version = Number(table.get("version") ?? 0) + 1;
  const points = tablePointsOf({ ...table.data(), forcedZeroPlayerId: forcedZeroPlayerId ?? "" });
  transaction.update(table.ref, {
    forcedZeroPlayerId: forcedZeroPlayerId ?? FieldValue.delete(),
    ...points,
    version,
    updatedAt: FieldValue.serverTimestamp(),
  });
  return tableVersionUpdate(Number(table.get("roundId")), Number(table.get("tableId")), version);
}

/** Appends one entry to the log of player changes. The log is write-only for now. */
function logPlayerChange(
  transaction: FirebaseFirestore.Transaction,
  tournamentRef: FirebaseFirestore.DocumentReference,
  entry: { kind: "replace" | "clear" | "substitute" | "restore"; slotId: number; previous: unknown; next: unknown; uid: string | null },
): void {
  transaction.set(tournamentRef.collection("playerLog").doc(), { ...entry, at: FieldValue.serverTimestamp() });
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
    if (membershipChanged && tournamentHasStartedTables(tournamentSnapshot)) {
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

    // The pending email travels with the person, so a player who moves to another slot keeps it.
    const pendingEmailByEma = new Map<string, string>();
    playerDocs.forEach((player) => {
      const emaId = currentByRef.get(player.ref.path);
      const email = player.get("email");
      if (emaId != null && typeof email === "string" && email !== "") pendingEmailByEma.set(emaId, email);
    });
    const affectedEmaIds = new Set<string>();
    playerDocs.forEach((player) => {
      const previous = currentByRef.get(player.ref.path) ?? null;
      const next = finalByRef.get(player.ref.path) ?? null;
      if (previous === next) return;
      if (previous != null) affectedEmaIds.add(previous);
      if (next != null) affectedEmaIds.add(next);
      transaction.update(player.ref, {
        assignedEmaId: next,
        email: (next == null ? undefined : pendingEmailByEma.get(next)) ?? FieldValue.delete(),
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
    // The versions change in the same commit as the data. If the server stops after the commit, caches still see it.
    transaction.update(tournament, { ...tournamentVersionUpdate("teams"), ...tournamentVersionUpdate("players") });
    transaction.set(db.collection("_meta").doc("dataVersions"), globalVersionUpdate("tournaments"), { merge: true });
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
  uid?: string | null;
}): Promise<void> {
  const playerRef = db.collection("tournaments").doc(params.tournamentId)
    .collection("players").doc(String(params.playerId));
  const players = playerRef.parent;
  const assignmentRefs = db.collection("tournaments").doc(params.tournamentId)
    .collection("emaPlayerAssignments");
  const tournamentRef = db.collection("tournaments").doc(params.tournamentId);
  let statisticsChanged = false;
  await db.runTransaction(async (transaction) => {
    const tournament = await transaction.get(tournamentRef);
    const tournamentHasProgress = tournamentHasStartedTables(tournament);
    const player = await transaction.get(playerRef);
    if (!player.exists) {
      throw new Error("Player not found");
    }

    const previousEmaId = typeof player.get("assignedEmaId") === "string"
      ? String(player.get("assignedEmaId"))
      : null;
    const assignmentChanged = previousEmaId !== params.emaId;
    // A slot with data can change. The tables, hands and team stay on the slot number.
    // The statistics follow the EMA id of the slot, so a cleared slot adds nothing to any person.
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

    // A new person starts without the substitution of the old person.
    const hadSubstitution = assignmentChanged && player.get("substitution") != null;
    const forcedTables = hadSubstitution
      ? await transaction.get(tournamentRef.collection("tables").where("forcedZeroPlayerId", "==", String(params.playerId)))
      : null;

    let assignedCountry = "";
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
    // Tables that are complete hold the EMA number of the slot in their statistics.
    statisticsChanged = previousEmaId !== params.emaId && tournamentHasProgress;
    if (assignmentChanged) {
      logPlayerChange(transaction, tournamentRef, {
        kind: params.emaId == null ? "clear" : "replace",
        slotId: params.playerId,
        previous: { emaId: previousEmaId },
        next: { emaId: params.emaId },
        uid: params.uid ?? null,
      });
    }
    transaction.update(playerRef, {
      assignedEmaId: params.emaId,
      assignedCountry,
      updatedAt: FieldValue.serverTimestamp(),
      ...(hadSubstitution ? { substitution: FieldValue.delete() } : {}),
      // The pending email belongs to the old person. The account of the slot follows the EMA id.
      ...(assignmentChanged ? { email: FieldValue.delete() } : {}),
    });
    const tableVersions: Record<string, unknown> = {};
    forcedTables?.docs.forEach((table) => {
      Object.assign(tableVersions, changeTable(transaction, table, null));
    });
    transaction.update(tournamentRef, { ...tournamentVersionUpdate("players"), ...tableVersions });
    transaction.set(db.collection("_meta").doc("dataVersions"), globalVersionUpdate("tournaments"), { merge: true });
    statisticsChanged = statisticsChanged || hadSubstitution;
  });
  await refreshAssignmentCountries(params.tournamentId);
  if (statisticsChanged) await refreshTournamentStats(params.tournamentId);
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "players"),
    bumpGlobalDataVersion("tournaments"),
  ]);
}

export type SlotOverwrite = { slotId: number; emaId: string | null; email: string | null };

const WRITES_PER_BATCH = 400;

/**
 * Overwrites the person of every slot, and the names of the teams. The import uses it.
 * Every slot must be in `slots`: a slot with no EMA id becomes empty. A slot that does not change is not written.
 * A new person starts with no substitution. `teamNames` is empty when the team names stay as they are.
 * It refuses a tournament with table data, because the tables, hands and statistics belong to the people of the slots.
 * The writes go in batches, so the caller must validate the data before. The same call again finishes a stopped import.
 */
export async function overwriteTournamentPlayers(params: {
  tournamentId: string;
  slots: SlotOverwrite[];
  teamNames: Array<{ teamId: number; name: string }>;
  uid: string | null;
}): Promise<{ changedSlots: number }> {
  const tournamentRef = db.collection("tournaments").doc(params.tournamentId);
  const [tournament, slotDocs, assignmentDocs] = await Promise.all([
    tournamentRef.get(),
    tournamentRef.collection("players").get(),
    tournamentRef.collection("emaPlayerAssignments").get(),
  ]);
  if (!tournament.exists) throw notFound("Tournament not found");
  if (tournamentHasStartedTables(tournament)) {
    throw conflict("The tournament has table data", { code: "TOURNAMENT_STARTED" });
  }
  const wanted = new Map(params.slots.map((slot) => [slot.slotId, slot]));
  const emaIds = [...new Set(params.slots.flatMap((slot) => (slot.emaId == null ? [] : [slot.emaId])))];
  const registry = await Promise.all(emaIds.map((emaId) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()));
  const countries = new Map(registry.filter((player) => player.exists)
    .map((player) => [player.id, String(player.get("country") ?? "").trim().toUpperCase()]));
  if (emaIds.some((emaId) => !countries.has(emaId))) throw notFound("EMA player not found");

  const writes: Array<(batch: FirebaseFirestore.WriteBatch) => void> = [];
  let changedSlots = 0;
  const finalOwner = new Map<string, number>();
  slotDocs.docs.forEach((slot) => {
    const slotId = Number(slot.get("id") ?? slot.id);
    const next = wanted.get(slotId) ?? { slotId, emaId: null, email: null };
    if (next.emaId != null) finalOwner.set(next.emaId, slotId);
    const previousEmaId = typeof slot.get("assignedEmaId") === "string" ? String(slot.get("assignedEmaId")) : null;
    const previousEmail = typeof slot.get("email") === "string" && slot.get("email") !== "" ? String(slot.get("email")) : null;
    if (previousEmaId === next.emaId && previousEmail === next.email) return;
    changedSlots++;
    const personChanged = previousEmaId !== next.emaId;
    writes.push((batch) => {
      batch.update(slot.ref, {
        assignedEmaId: next.emaId,
        assignedCountry: next.emaId == null ? "" : countries.get(next.emaId) ?? "",
        email: next.email ?? FieldValue.delete(),
        updatedAt: FieldValue.serverTimestamp(),
        ...(personChanged ? { substitution: FieldValue.delete() } : {}),
      });
      if (personChanged) {
        batch.set(tournamentRef.collection("playerLog").doc(), {
          kind: next.emaId == null ? "clear" : "replace",
          slotId,
          previous: { emaId: previousEmaId },
          next: { emaId: next.emaId },
          uid: params.uid,
          at: FieldValue.serverTimestamp(),
        });
      }
    });
  });
  assignmentDocs.docs.forEach((assignment) => {
    if (!finalOwner.has(assignment.id)) writes.push((batch) => batch.delete(assignment.ref));
  });
  finalOwner.forEach((slotId, emaId) => {
    const current = assignmentDocs.docs.find((assignment) => assignment.id === emaId);
    if (current != null && Number(current.get("playerId")) === slotId) return;
    writes.push((batch) => batch.set(tournamentRef.collection("emaPlayerAssignments").doc(emaId), {
      playerId: slotId,
      updatedAt: FieldValue.serverTimestamp(),
    }));
  });
  params.teamNames.forEach((team) => {
    writes.push((batch) => batch.set(tournamentRef.collection("teams").doc(String(team.teamId)), {
      id: team.teamId,
      name: team.name,
      updatedAt: FieldValue.serverTimestamp(),
    }, { merge: true }));
  });
  writes.push((batch) => batch.update(tournamentRef, {
    ...tournamentVersionUpdate("players"),
    ...(params.teamNames.length > 0 ? tournamentVersionUpdate("teams") : {}),
  }));
  for (let start = 0; start < writes.length; start += WRITES_PER_BATCH) {
    const batch = db.batch();
    writes.slice(start, start + WRITES_PER_BATCH).forEach((write) => write(batch));
    await batch.commit();
  }
  await Promise.all([
    bumpTournamentDataVersion(params.tournamentId, "players"),
    ...(params.teamNames.length > 0 ? [bumpTournamentDataVersion(params.tournamentId, "teams")] : []),
    bumpGlobalDataVersion("tournaments"),
  ]);
  return { changedSlots };
}

/**
 * Sets or removes the substitution of a player who played some rounds. By the EMA rules the player gets no result
 * for the rounds that the substitute plays. When the substitute leaves the table after the session started
 * (`duringSession`), the table of `fromRound` gives 0 table points to the seat of the player.
 * The change is one transaction, so an editor who has the table open gets a conflict and sees the new points.
 */
export async function setPlayerSubstitution(params: {
  tournamentId: string;
  playerId: number;
  substitution: PlayerSubstitution | null;
  uid: string | null;
}): Promise<void> {
  const tournamentRef = db.collection("tournaments").doc(params.tournamentId);
  const playerRef = tournamentRef.collection("players").doc(String(params.playerId));
  const next = params.substitution;
  if (next != null) {
    if (!SUBSTITUTION_REASONS.includes(next.reason)) throw badRequest("Unknown substitution reason");
    if (next.excludedFromEma && next.reason !== "ILLNESS") {
      throw badRequest("Only a player who is ill can be excluded from the EMA ranking");
    }
    if (next.duringSession && next.reason === "LATE") {
      throw badRequest("A late player has no session in progress");
    }
  }
  await db.runTransaction(async (transaction) => {
    const [tournament, player, forcedTables] = await Promise.all([
      transaction.get(tournamentRef),
      transaction.get(playerRef),
      transaction.get(tournamentRef.collection("tables").where("forcedZeroPlayerId", "==", String(params.playerId))),
    ]);
    if (!tournament.exists) throw notFound("Tournament not found");
    if (!player.exists) throw notFound("Player not found");
    const assigned = typeof player.get("assignedEmaId") === "string";
    if (!assigned) throw conflict("Only an assigned player can be substituted");
    let targetTable: FirebaseFirestore.QueryDocumentSnapshot | null = null;
    if (next != null) {
      if (!Number.isInteger(next.fromRound) || next.fromRound < 1 || next.fromRound > Number(tournament.get("numRounds") ?? 0)) {
        throw badRequest("The round is not part of the tournament");
      }
      if (next.duringSession) {
        const tables = await transaction.get(tournamentRef.collection("tables").where("roundId", "==", next.fromRound));
        targetTable = tables.docs.find((table) => (table.get("playerIds") as unknown[] | undefined ?? [])
          .map(Number).includes(params.playerId)) ?? null;
        if (targetTable == null) throw conflict("The player has no table in this round");
      }
    }
    const tableVersions: Record<string, unknown> = {};
    forcedTables.docs.forEach((table) => {
      if (table.id !== targetTable?.id) {
        Object.assign(tableVersions, changeTable(transaction, table, null));
      }
    });
    if (targetTable != null) {
      Object.assign(tableVersions, changeTable(transaction, targetTable, String(params.playerId)));
    }
    transaction.update(playerRef, {
      substitution: next ?? FieldValue.delete(),
      updatedAt: FieldValue.serverTimestamp(),
    });
    logPlayerChange(transaction, tournamentRef, {
      kind: next == null ? "restore" : "substitute",
      slotId: params.playerId,
      previous: readSubstitution(player.get("substitution")),
      next,
      uid: params.uid,
    });
    transaction.update(tournamentRef, { ...tournamentVersionUpdate("players"), ...tableVersions });
  });
  await refreshTournamentStats(params.tournamentId);
  await bumpTournamentDataVersion(params.tournamentId, "players");
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

  // Every save keeps the summary on the table document, so a list needs no read of the hands.
  const tables = snap.docs.map((d) => ({
    version: Number(d.get("version") ?? 0),
    roundId: Number(d.get("roundId")),
    tableId: Number(d.get("tableId")),
    playerIds: (d.get("playerIds") as unknown[] | undefined ?? []).map((x) => Number(x)),
    isCompleted: Boolean(d.get("isCompleted") ?? false),
    useTotalsOnly: Boolean(d.get("useTotalsOnly") ?? false),
    hasProgress: Boolean(d.get("hasProgress") ?? false),
    hasValidManualTotals: Boolean(d.get("hasValidManualTotals") ?? false),
    completionStatus: String(d.get("completionStatus") ?? "empty"),
    bestHandScores: (d.get("bestHandScores") as number[] | undefined) ?? [],
    chickenHandCount: Number(d.get("chickenHandCount") ?? 0),
  }));

  return tables
    .sort((a, b) => a.roundId - b.roundId || a.tableId - b.tableId);
}
