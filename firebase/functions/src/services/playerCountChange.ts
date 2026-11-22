import { FieldValue } from "firebase-admin/firestore";

import { db } from "../firebase";
import { badRequest, conflict, notFound } from "../api/httpError";
import { bumpGlobalDataVersion, tableVersionKey, tournamentVersionUpdate } from "./dataVersionsService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import { slotEmails } from "./registrationDraw";
import { applyPlayerCountToRegistrations, listStored, loadTournament, type StoredRegistration } from "./registrationsService";
import { isValidPlayerCount } from "./tournamentContentRules";
import { drawProblem, type DrawPlayer, type DrawTable } from "./tournamentDraw";
import { getTournament, tournamentContentWrites, type ContentWrite, type Tournament } from "./tournamentsService";

/**
 * Change of the number of players of a tournament that has its draw and no table data yet: last minute
 * withdrawals take whole tables away, or late people add whole tables. The tables of every round are computed
 * again by the admin app, as at the creation, and the server writes them. See docs/player-count-change.md.
 *
 * - The people who stay keep their slot data (EMA player, name, country, pending email). Only the slot number,
 *   and in a team tournament the team number, change.
 * - Fewer players: the organizer says which slots leave, a multiple of 4. With teams, whole teams leave.
 * - More players: the new slots are empty ("Player N"), or take people of the waiting list of the registrations.
 *   With teams, every new team is a new empty team ("Team N").
 */

export type CurrentSlot = { id: number; team: number; emaId: string | null };

export type PlayerCountPlan = {
  /** For every slot of the new draw, the slot of the current draw it comes from, or null for a new slot. */
  sourceOf: Map<number, number | null>;
  /** For the new teams, the current team they come from, or null for a new team. */
  teamSourceOf: Map<number, number | null>;
  /** The people of the waiting list in the new slots, by new slot number. */
  fills: Map<number, string>;
  /** The current slots that leave. */
  removedSlotIds: number[];
};

export type PlanProblem = { problem: string; code: string };

const byId = (left: { id: number }, right: { id: number }) => left.id - right.id;

/**
 * Checks the change and maps the current slots to the slots of the new draw. Pure, so a unit test covers every
 * rule. The people who stay take the lowest slot numbers in their order, so "Player 9" after a removal of slots 5 to
 * 8 becomes slot 5. With teams, the teams that stay take the lowest team numbers in their order, and their people
 * keep their order inside the team.
 */
export function planPlayerCountChange(params: {
  current: CurrentSlot[];
  isTeams: boolean;
  newPlayers: DrawPlayer[];
  removedSlotIds: number[];
  waitingEmaIds: string[];
}): PlayerCountPlan | PlanProblem {
  const current = [...params.current].sort(byId);
  const target = params.newPlayers.length;
  if (!isValidPlayerCount(target)) return { problem: `Invalid number of players ${target}`, code: "BAD_PLAYER_COUNT" };
  if (target === current.length) return { problem: "The number of players is the same", code: "SAME_PLAYER_COUNT" };
  const newPlayers = [...params.newPlayers].sort(byId);
  if (new Set(newPlayers.map((player) => player.id)).size !== target) return { problem: "Two new slots have the same id", code: "BAD_DRAW" };
  const currentIds = new Set(current.map((slot) => slot.id));
  const removed = [...new Set(params.removedSlotIds)];
  if (removed.length !== params.removedSlotIds.length || removed.some((id) => !currentIds.has(id))) {
    return { problem: "A removed slot is unknown or twice", code: "BAD_REMOVED_SLOTS" };
  }
  const waiting = params.waitingEmaIds;
  if (new Set(waiting).size !== waiting.length) return { problem: "A person of the waiting list is twice", code: "BAD_WAITING" };
  if (target < current.length) {
    if (removed.length !== current.length - target) {
      return { problem: `Remove exactly ${current.length - target} slots`, code: "BAD_REMOVED_SLOTS" };
    }
    if (waiting.length > 0) return { problem: "Fewer players take nobody of the waiting list", code: "BAD_WAITING" };
  } else {
    if (removed.length > 0) return { problem: "More players remove no slot", code: "BAD_REMOVED_SLOTS" };
    if (waiting.length > target - current.length) return { problem: "More people of the waiting list than new slots", code: "BAD_WAITING" };
  }
  const assigned = new Set(current.flatMap((slot) => (slot.emaId == null ? [] : [slot.emaId])));
  if (waiting.some((emaId) => assigned.has(emaId))) return { problem: "A person of the waiting list has a slot", code: "BAD_WAITING" };

  const removedSet = new Set(removed);
  const kept = current.filter((slot) => !removedSet.has(slot.id));
  const sourceOf = new Map<number, number | null>();
  const teamSourceOf = new Map<number, number | null>();
  const fills = new Map<number, string>();
  let newSlots: number[];

  if (!params.isTeams) {
    newPlayers.forEach((player, index) => sourceOf.set(player.id, kept[index]?.id ?? null));
    newSlots = newPlayers.slice(kept.length).map((player) => player.id);
  } else {
    const membersOf = (slots: Array<{ id: number; team: number }>) => {
      const teams = new Map<number, number[]>();
      slots.forEach((slot) => teams.set(slot.team, [...(teams.get(slot.team) ?? []), slot.id]));
      teams.forEach((ids) => ids.sort((left, right) => left - right));
      return teams;
    };
    const currentTeams = membersOf(current);
    const newTeams = membersOf(newPlayers);
    if ([...newTeams.keys()].some((team) => team <= 0) || [...newTeams.values()].some((ids) => ids.length !== 4)) {
      return { problem: "Every new team needs 4 slots", code: "BAD_DRAW" };
    }
    const removedTeams = new Set(current.filter((slot) => removedSet.has(slot.id)).map((slot) => slot.team));
    for (const team of removedTeams) {
      if ((currentTeams.get(team) ?? []).some((id) => !removedSet.has(id))) {
        return { problem: `Team ${team} must leave whole`, code: "PARTIAL_TEAM" };
      }
    }
    const keptTeams = [...currentTeams.keys()].filter((team) => !removedTeams.has(team)).sort((left, right) => left - right);
    const targetTeams = [...newTeams.keys()].sort((left, right) => left - right);
    newSlots = [];
    targetTeams.forEach((team, index) => {
      const source = keptTeams[index] ?? null;
      teamSourceOf.set(team, source);
      const sourceSlots = source == null ? [] : currentTeams.get(source) ?? [];
      (newTeams.get(team) ?? []).forEach((slotId, position) => {
        const from = sourceSlots[position] ?? null;
        sourceOf.set(slotId, from);
        if (from == null) newSlots.push(slotId);
      });
    });
  }
  waiting.forEach((emaId, index) => fills.set(newSlots[index], emaId));
  return { sourceOf, teamSourceOf, fills, removedSlotIds: removed.sort((left, right) => left - right) };
}

/** A default name follows its slot number, a real name stays. */
export function slotNameFor(name: unknown, newId: number): string {
  const text = typeof name === "string" ? name.trim() : "";
  return text === "" || /^Player \d+$/.test(text) ? `Player ${newId}` : text;
}

/** A default team name follows its team number, a real name stays. */
export function teamNameFor(name: unknown, newId: number): string {
  const text = typeof name === "string" ? name.trim() : "";
  return text === "" || /^Team \d+$/.test(text) ? `Team ${newId}` : text;
}

export type PlayerCountResult = {
  tournament: Tournament;
  previousPlayers: number;
  removed: Array<{ slotId: number; emaId: string | null; name: string }>;
  promoted: string[];
};

const WRITES_PER_BATCH = 400;

type Op = { kind: "set"; ref: FirebaseFirestore.DocumentReference; data: Record<string, unknown> } | { kind: "delete"; ref: FirebaseFirestore.DocumentReference };

async function commitOps(ops: Op[]): Promise<void> {
  for (let start = 0; start < ops.length; start += WRITES_PER_BATCH) {
    const batch = db.batch();
    ops.slice(start, start + WRITES_PER_BATCH).forEach((op) => {
      if (op.kind === "set") batch.set(op.ref, op.data);
      else batch.delete(op.ref);
    });
    await batch.commit();
  }
}

const asSet = (writes: ContentWrite[]): Op[] => writes.map((write) => ({ kind: "set", ref: write.ref, data: write.data }));

/**
 * Writes the new draw. Refused while any table has data, for a tournament still waiting for its registration draw,
 * and for a finished tournament. When a write fails, the documents of the old draw are written back.
 */
export async function changePlayerCount(params: {
  tournamentId: string;
  numTries: number;
  players: DrawPlayer[];
  tables: DrawTable[];
  removedSlotIds: number[];
  waitingEmaIds: string[];
  uid: string | null;
}): Promise<PlayerCountResult> {
  const ref = db.collection("tournaments").doc(params.tournamentId);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw notFound("Tournament not found");
  if (snapshot.get("drawPending") === true) throw conflict("The tournament has no draw yet", { code: "DRAW_PENDING" });
  if (snapshot.get("isCompleted") === true) throw conflict("The tournament is finished", { code: "TOURNAMENT_COMPLETED" });
  const startedTables = await ref.collection("tables").where("hasProgress", "==", true).limit(1).get();
  if (Number(snapshot.get("tablesWithProgress") ?? 0) > 0 || !startedTables.empty) {
    throw conflict("The tournament has table data", { code: "TOURNAMENT_STARTED" });
  }
  const isTeams = snapshot.get("isTeams") === true;
  const numRounds = Number(snapshot.get("numRounds") ?? 0);
  const target = params.players.length;
  const players = isTeams ? params.players : params.players.map((player) => ({ ...player, team: 0 }));
  if (params.tables.length !== numRounds * (target / 4)) {
    throw badRequest("The draw does not fit the number of players", { code: "BAD_DRAW" });
  }
  const problem = drawProblem(players, params.tables, numRounds, target / 4, isTeams);
  if (problem) throw badRequest("Invalid draw", { code: "BAD_DRAW", problem });

  const [slotDocs, teamDocs, assignmentDocs, tableDocs] = await Promise.all([
    ref.collection("players").get(),
    ref.collection("teams").get(),
    ref.collection("emaPlayerAssignments").get(),
    ref.collection("tables").get(),
  ]);
  const slotById = new Map(slotDocs.docs.map((doc) => [Number(doc.get("id") ?? doc.id), doc]));
  const current: CurrentSlot[] = [...slotById.entries()].map(([id, doc]) => ({
    id,
    team: isTeams ? Number(doc.get("team") ?? 0) : 0,
    emaId: typeof doc.get("assignedEmaId") === "string" && doc.get("assignedEmaId") !== "" ? String(doc.get("assignedEmaId")) : null,
  }));
  const plan = planPlayerCountChange({ current, isTeams, newPlayers: players, removedSlotIds: params.removedSlotIds, waitingEmaIds: params.waitingEmaIds });
  if ("problem" in plan) throw conflict(plan.problem, { code: plan.code });

  // The people of the waiting list must be waiting in the registrations of this tournament.
  const hasRegistrations = snapshot.get("registration") != null;
  let waitingByEma = new Map<string, StoredRegistration>();
  if (params.waitingEmaIds.length > 0) {
    if (!hasRegistrations) throw conflict("This tournament has no registrations", { code: "NO_REGISTRATIONS" });
    const stored = await listStored(await loadTournament(params.tournamentId));
    waitingByEma = new Map(stored.filter((registration) => registration.status === "WAITING").map((registration) => [registration.emaId, registration]));
    if (params.waitingEmaIds.some((emaId) => !waitingByEma.has(emaId))) {
      throw conflict("A person is not on the waiting list", { code: "NOT_WAITING" });
    }
  }
  const registry = await Promise.all(params.waitingEmaIds.map((emaId) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId).get()));
  const registryCountry = new Map(registry.filter((doc) => doc.exists).map((doc) => [doc.id, String(doc.get("country") ?? "").trim().toUpperCase()]));
  const emails = await slotEmails([...waitingByEma.values()].filter((registration) => params.waitingEmaIds.includes(registration.emaId)));

  const now = FieldValue.serverTimestamp();
  const newOps: Op[] = [];
  const finalOwner = new Map<string, number>();
  for (const player of players) {
    const source = plan.sourceOf.get(player.id) ?? null;
    const fill = plan.fills.get(player.id);
    const slotRef = ref.collection("players").doc(String(player.id));
    if (source != null) {
      const { substitution: _substitution, ...data } = slotById.get(source)?.data() ?? {};
      const emaId = typeof data.assignedEmaId === "string" && data.assignedEmaId !== "" ? data.assignedEmaId : null;
      if (emaId != null) finalOwner.set(emaId, player.id);
      newOps.push({ kind: "set", ref: slotRef, data: { ...data, id: player.id, team: player.team, name: slotNameFor(data.name, player.id), updatedAt: now } });
    } else if (fill != null) {
      const registration = waitingByEma.get(fill);
      finalOwner.set(fill, player.id);
      const email = emails.get(fill) ?? null;
      newOps.push({ kind: "set", ref: slotRef, data: {
        id: player.id, team: player.team, name: registration?.name || `Player ${player.id}`, country: registration?.country ?? "",
        assignedEmaId: fill, assignedCountry: registryCountry.get(fill) ?? "", ...(email == null ? {} : { email }),
        createdAt: now, updatedAt: now,
      } });
    } else {
      newOps.push({ kind: "set", ref: slotRef, data: { id: player.id, team: player.team, name: `Player ${player.id}`, country: "", createdAt: now, updatedAt: now } });
    }
  }
  const newIds = new Set(players.map((player) => player.id));
  slotDocs.docs.filter((doc) => !newIds.has(Number(doc.get("id") ?? doc.id))).forEach((doc) => newOps.push({ kind: "delete", ref: doc.ref }));

  // Teams: a team that stays keeps its name. A new team of people of the waiting list of one registration team
  // takes the name of that team.
  if (isTeams) {
    const teamName = new Map(teamDocs.docs.map((doc) => [Number(doc.get("id") ?? doc.id), doc.get("name")]));
    const registrationTeamNames = new Map<string, string>();
    if (params.waitingEmaIds.length > 0) {
      const registrationTeams = await ref.collection("registrationTeams").get();
      registrationTeams.docs.forEach((doc) => registrationTeamNames.set(doc.id, String(doc.get("name") ?? "")));
    }
    plan.teamSourceOf.forEach((source, teamId) => {
      let name = source == null ? `Team ${teamId}` : teamNameFor(teamName.get(source), teamId);
      if (source == null) {
        const fillTeams = players.filter((player) => player.team === teamId).map((player) => {
          const fill = plan.fills.get(player.id);
          return fill == null ? null : waitingByEma.get(fill)?.teamId ?? null;
        });
        if (fillTeams.length === 4 && fillTeams[0] != null && fillTeams.every((team) => team === fillTeams[0])) {
          name = registrationTeamNames.get(fillTeams[0]) || name;
        }
      }
      newOps.push({ kind: "set", ref: ref.collection("teams").doc(String(teamId)), data: { id: teamId, name, createdAt: now, updatedAt: now } });
    });
    teamDocs.docs.filter((doc) => !plan.teamSourceOf.has(Number(doc.get("id") ?? doc.id))).forEach((doc) => newOps.push({ kind: "delete", ref: doc.ref }));
  }

  assignmentDocs.docs.filter((doc) => !finalOwner.has(doc.id)).forEach((doc) => newOps.push({ kind: "delete", ref: doc.ref }));
  finalOwner.forEach((playerId, emaId) => newOps.push({ kind: "set", ref: ref.collection("emaPlayerAssignments").doc(emaId), data: { playerId, updatedAt: now } }));

  // The tables of every round are new. A table that keeps its key raises its version, so an editor who has the old
  // table open gets a conflict instead of writing on the new one.
  const oldVersion = new Map(tableDocs.docs.map((doc) => [doc.id, Number(doc.get("version") ?? 0)]));
  const tableWrites = tournamentContentWrites(ref, { players: [], tables: params.tables, numRounds: 0, isTeams: false });
  const tableVersions: Record<string, number> = {};
  tableWrites.forEach((write) => {
    if (write.ref.parent.id !== "tables") return;
    const version = (oldVersion.get(write.ref.id) ?? -1) + 1;
    write.data.version = version;
    const [roundId, tableId] = write.ref.id.split("_").map(Number);
    if (version > 0) tableVersions[tableVersionKey(roundId, tableId)] = version;
  });
  newOps.push(...asSet(tableWrites));
  const newTableKeys = new Set(params.tables.map((table) => `${table.roundId}_${table.tableId}`));
  const removedTables = tableDocs.docs.filter((doc) => !newTableKeys.has(doc.id));

  const previousPlayers = slotById.size;
  const removedPeople = plan.removedSlotIds.map((slotId) => {
    const doc = slotById.get(slotId);
    const emaId = typeof doc?.get("assignedEmaId") === "string" && doc.get("assignedEmaId") !== "" ? String(doc.get("assignedEmaId")) : null;
    return { slotId, emaId, name: String(doc?.get("name") ?? `Player ${slotId}`) };
  });

  try {
    await commitOps(newOps);
    await Promise.all(removedTables.map((doc) => db.recursiveDelete(doc.ref)));
    const log = ref.collection("playerLog");
    const logBatch = db.batch();
    logBatch.set(log.doc(), {
      kind: "playerCount", slotId: 0, previous: { numPlayers: previousPlayers }, next: { numPlayers: target }, uid: params.uid, at: now,
    });
    removedPeople.filter((person) => person.emaId != null).forEach((person) => logBatch.set(log.doc(), {
      kind: "clear", slotId: person.slotId, previous: { emaId: person.emaId }, next: { emaId: null }, uid: params.uid, at: now,
    }));
    plan.fills.forEach((emaId, slotId) => logBatch.set(log.doc(), {
      kind: "replace", slotId, previous: { emaId: null }, next: { emaId }, uid: params.uid, at: now,
    }));
    await logBatch.commit();
    await ref.update({
      numPlayers: target,
      numTries: params.numTries,
      tablesWithProgress: 0,
      "dataVersions.tableVersions": tableVersions,
      ...tournamentVersionUpdate("players"),
      ...tournamentVersionUpdate("teams"),
      ...tournamentVersionUpdate("tables"),
      ...tournamentVersionUpdate("rounds"),
      updatedAt: now,
    });
  } catch (error) {
    await restoreOldDraw(ref, { slotDocs, teamDocs, assignmentDocs, tableDocs, newOps }).catch(() => undefined);
    throw error;
  }
  if (hasRegistrations) {
    await applyPlayerCountToRegistrations({
      tournamentId: params.tournamentId,
      removedEmaIds: removedPeople.flatMap((person) => (person.emaId == null ? [] : [person.emaId])),
      promotedEmaIds: params.waitingEmaIds,
      numPlayers: target,
    });
  }
  await bumpGlobalDataVersion("tournaments");
  const tournament = await getTournament(params.tournamentId);
  if (tournament == null) throw conflict("The tournament disappeared");
  return { tournament, previousPlayers, removed: removedPeople, promoted: params.waitingEmaIds };
}

/** Writes the documents of the old draw back and deletes the new ones that it did not have. Best effort. */
async function restoreOldDraw(
  ref: FirebaseFirestore.DocumentReference,
  old: {
    slotDocs: FirebaseFirestore.QuerySnapshot;
    teamDocs: FirebaseFirestore.QuerySnapshot;
    assignmentDocs: FirebaseFirestore.QuerySnapshot;
    tableDocs: FirebaseFirestore.QuerySnapshot;
    newOps: Op[];
  },
): Promise<void> {
  const oldDocs = [...old.slotDocs.docs, ...old.teamDocs.docs, ...old.assignmentDocs.docs, ...old.tableDocs.docs];
  const oldPaths = new Set(oldDocs.map((doc) => doc.ref.path));
  const ops: Op[] = oldDocs.map((doc) => ({ kind: "set", ref: doc.ref, data: doc.data() }));
  old.newOps.forEach((op) => {
    if (op.kind === "set" && op.ref.parent.id !== "hands" && !oldPaths.has(op.ref.path)) ops.push({ kind: "delete", ref: op.ref });
  });
  // The old tables had no data, so their hands are the empty ones of a new draw.
  const oldTables = old.tableDocs.docs.map((doc) => ({
    roundId: Number(doc.get("roundId")), tableId: Number(doc.get("tableId")), playerIds: (doc.get("playerIds") as number[]) ?? [],
  }));
  ops.push(...asSet(tournamentContentWrites(ref, { players: [], tables: oldTables, numRounds: 0, isTeams: false })
    .filter((write) => write.ref.parent.id === "hands")));
  await commitOps(ops);
  const newTableKeys = old.newOps.filter((op) => op.kind === "set" && op.ref.parent.id === "tables" && !oldPaths.has(op.ref.path));
  await Promise.all(newTableKeys.map((op) => db.recursiveDelete(op.ref)));
}
