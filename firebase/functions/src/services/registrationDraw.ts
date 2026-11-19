import { FieldValue } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest, conflict } from "../api/httpError";
import { bumpGlobalDataVersion } from "./dataVersionsService";
import { isValidPlayerCount, MIN_TOURNAMENT_PLAYERS } from "./tournamentContentRules";
import { overwriteTournamentPlayers } from "./tournamentContentService";
import { drawProblem, type DrawTable } from "./tournamentDraw";
import { commitContentWrites, getTournament, tournamentContentWrites, type Tournament } from "./tournamentsService";
import { listStored, loadTournament, TEAMS, type StoredRegistration } from "./registrationsService";
import { TEAM_SIZE } from "./registrationSettings";

export type DrawAssignment = { playerId: number; emaId: string };

/**
 * Checks that a draw fits the confirmed registrations, and gives the team of each slot. Pure, so a unit test
 * covers every rule. Returns the teams of the draw by their id, with the name of the registration team.
 * - Every confirmed person has exactly one slot, and every slot has one confirmed person.
 * - With teams, every person is in a team of exactly [TEAM_SIZE] confirmed people, and the slots of one team of the
 *   draw hold the people of one registration team.
 */
export function drawAssignmentProblem(params: {
  confirmed: StoredRegistration[];
  teamNames: Map<string, string>;
  isTeams: boolean;
  players: Array<{ id: number; team: number }>;
  assignments: DrawAssignment[];
}): { problem: string; code: string; details?: unknown } | { teamNames: Array<{ teamId: number; name: string }> } {
  const byEma = new Map(params.confirmed.map((registration) => [registration.emaId, registration]));
  const slotIds = new Set(params.players.map((player) => player.id));
  const seenSlots = new Set<number>();
  const seenEma = new Set<string>();
  for (const assignment of params.assignments) {
    if (!slotIds.has(assignment.playerId) || seenSlots.has(assignment.playerId)) {
      return { problem: `Slot ${assignment.playerId} is unknown or twice`, code: "BAD_ASSIGNMENTS" };
    }
    if (!byEma.has(assignment.emaId) || seenEma.has(assignment.emaId)) {
      return { problem: `EMA ${assignment.emaId} is not confirmed or twice`, code: "BAD_ASSIGNMENTS" };
    }
    seenSlots.add(assignment.playerId);
    seenEma.add(assignment.emaId);
  }
  if (seenEma.size !== byEma.size || seenSlots.size !== slotIds.size) {
    return { problem: "Every confirmed person needs one slot", code: "BAD_ASSIGNMENTS" };
  }
  if (!params.isTeams) return { teamNames: [] };
  const members = new Map<string, string[]>();
  params.confirmed.forEach((registration) => {
    if (registration.teamId == null) return;
    members.set(registration.teamId, [...(members.get(registration.teamId) ?? []), registration.emaId]);
  });
  const incomplete = params.confirmed
    .filter((registration) => registration.teamId == null || (members.get(registration.teamId)?.length ?? 0) !== TEAM_SIZE)
    .map((registration) => registration.emaId);
  if (incomplete.length > 0) {
    return { problem: `Every person needs a team of ${TEAM_SIZE}`, code: "INCOMPLETE_TEAMS", details: { emaIds: incomplete } };
  }
  const teamOfSlot = new Map(params.players.map((player) => [player.id, player.team]));
  const drawTeamToRegistrationTeam = new Map<number, string>();
  for (const assignment of params.assignments) {
    const drawTeam = teamOfSlot.get(assignment.playerId) ?? 0;
    const registrationTeam = byEma.get(assignment.emaId)?.teamId ?? "";
    const known = drawTeamToRegistrationTeam.get(drawTeam);
    if (known != null && known !== registrationTeam) {
      return { problem: `Draw team ${drawTeam} mixes registration teams`, code: "BAD_ASSIGNMENTS" };
    }
    drawTeamToRegistrationTeam.set(drawTeam, registrationTeam);
  }
  return {
    teamNames: [...drawTeamToRegistrationTeam.entries()]
      .sort(([left], [right]) => left - right)
      .map(([teamId, registrationTeam]) => ({ teamId, name: params.teamNames.get(registrationTeam) ?? `Team ${teamId}` })),
  };
}

/** The emails that the slots can keep: not shared with another person of the list, and not owned by another account. */
async function slotEmails(confirmed: StoredRegistration[]): Promise<Map<string, string | null>> {
  const result = new Map<string, string | null>();
  const firstOwner = new Set<string>();
  for (const registration of [...confirmed].sort((left, right) => left.order - right.order)) {
    const email = registration.email;
    if (email === "" || firstOwner.has(email)) {
      result.set(registration.emaId, null);
      continue;
    }
    firstOwner.add(email);
    // An account that owns this email and another EMA number keeps it: the import refuses the same case.
    let ownedByOther = false;
    try {
      const user = await auth.getUserByEmail(email);
      const emaId = (await db.doc(`users/${user.uid}`).get()).get("emaId");
      ownedByOther = emaId !== registration.emaId;
    } catch {
      ownedByOther = false;
    }
    result.set(registration.emaId, ownedByOther ? null : email);
  }
  return result;
}

/**
 * Makes the draw of a tournament with registrations, when they are closed. The app computes the draw for the
 * number of confirmed people, as it does when it creates a tournament, and says which person sits in which slot.
 * The server writes the slots, the teams, the rounds and the tables, and the tournament is a normal one from then.
 * The slots keep the email of the registration as the pending email, so the Invitations dialog can invite them.
 */
export async function makeDrawFromRegistrations(params: {
  tournamentId: string;
  numTries: number;
  players: Array<{ id: number; team: number }>;
  tables: DrawTable[];
  assignments: DrawAssignment[];
  uid: string | null;
}): Promise<Tournament> {
  const tournament = await loadTournament(params.tournamentId);
  if (!tournament.drawPending) throw conflict("The draw is already made", { code: "DRAW_MADE" });
  if (tournament.settings.status === "OPEN") throw conflict("Close the registrations first", { code: "REGISTRATION_OPEN" });
  const confirmed = (await listStored(tournament)).filter((registration) => registration.status === "CONFIRMED");
  const numPlayers = confirmed.length;
  if (!isValidPlayerCount(numPlayers)) {
    throw conflict(`The confirmed players must be a multiple of 4 and at least ${MIN_TOURNAMENT_PLAYERS}`, { code: "BAD_PLAYER_COUNT", confirmed: numPlayers });
  }
  const numRounds = Number(tournament.data.numRounds ?? 0);
  const tablesPerRound = numPlayers / 4;
  if (params.players.length !== numPlayers || params.tables.length !== numRounds * tablesPerRound) {
    throw badRequest("The draw does not fit the confirmed players", { code: "BAD_DRAW", confirmed: numPlayers });
  }
  const players = tournament.isTeams ? params.players : params.players.map((player) => ({ ...player, team: 0 }));
  const problem = drawProblem(players, params.tables, numRounds, tablesPerRound, tournament.isTeams);
  if (problem) throw badRequest("Invalid draw", { code: "BAD_DRAW", problem });
  const teamDocs = await tournament.ref.collection(TEAMS).get();
  const teamNames = new Map(teamDocs.docs.map((doc) => [doc.id, String(doc.get("name") ?? "")]));
  const checked = drawAssignmentProblem({ confirmed, teamNames, isTeams: tournament.isTeams, players, assignments: params.assignments });
  if ("problem" in checked) throw conflict(checked.problem, { code: checked.code, ...(checked.details as object | undefined) });

  const emaOfSlot = new Map(params.assignments.map((assignment) => [assignment.playerId, assignment.emaId]));
  const byEma = new Map(confirmed.map((registration) => [registration.emaId, registration]));
  const writes = tournamentContentWrites(tournament.ref, {
    players: players.map((player) => {
      const registration = byEma.get(emaOfSlot.get(player.id) ?? "");
      return { id: player.id, team: player.team, name: registration?.name, country: registration?.country };
    }),
    tables: params.tables,
    numRounds,
    isTeams: tournament.isTeams,
  });
  const emails = await slotEmails(confirmed);
  try {
    await commitContentWrites(writes);
    await tournament.ref.update({
      numPlayers,
      numTries: params.numTries,
      drawPending: false,
      "registration.status": "CLOSED",
      tablesWithProgress: 0,
      updatedAt: FieldValue.serverTimestamp(),
    });
    await overwriteTournamentPlayers({
      tournamentId: tournament.ref.id,
      slots: params.assignments.map((assignment) => ({
        slotId: assignment.playerId,
        emaId: assignment.emaId,
        email: emails.get(assignment.emaId) ?? null,
      })),
      teamNames: checked.teamNames,
      uid: params.uid,
    });
  } catch (error) {
    // The tournament goes back to its state before the draw, so the organizer can try again.
    await Promise.all(["players", "teams", "tables", "emaPlayerAssignments", "playerLog"].map((name) =>
      db.recursiveDelete(tournament.ref.collection(name)).catch(() => undefined)));
    await tournament.ref.update({ numPlayers: 0, drawPending: true, updatedAt: FieldValue.serverTimestamp() }).catch(() => undefined);
    throw error;
  }
  await bumpGlobalDataVersion("tournaments");
  const result = await getTournament(tournament.ref.id);
  if (result == null) throw conflict("The tournament disappeared");
  return result;
}
