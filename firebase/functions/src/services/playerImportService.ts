import { db } from "../firebase";
import { notFound } from "../api/httpError";
import {
  ImportPlan,
  buildPlayerExportRecords,
  normalizeEmaId,
  parsePlayerImportFile,
  planPlayerImport,
  type ParsedImport,
  type PlannedRow,
  type SlotInfo,
  writePlayerExportXlsx,
} from "./playerImport";
import { createPlayerAccount, EMAIL_PATTERN, loadAccountsByEmail, loadAccountsByEmaId } from "./playerAccountsService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import {
  assignTournamentPlayer,
  listTournamentPlayers,
  listTournamentTeams,
  tournamentHasStartedTables,
  updateTournamentTeam,
  type TournamentPlayer,
} from "./tournamentContentService";

export type PlayerImportPreview = {
  rows: PlannedRow[];
  untouchedSlotIds: number[];
  teamRenames: ImportPlan["teamRenames"];
  isTeams: boolean;
  /** True when a table has data. Replacing a player then changes results that exist already. */
  tournamentStarted: boolean;
  hasEmailColumn: boolean;
  hasTeamColumn: boolean;
};

type Loaded = {
  parsed: ParsedImport;
  players: TournamentPlayer[];
  plan: ImportPlan;
  isTeams: boolean;
  tournamentStarted: boolean;
};

function slotInfoOf(player: TournamentPlayer): SlotInfo {
  return {
    id: player.id,
    team: player.team,
    emaId: player.assignedEmaId,
    email: player.email,
  };
}

/** Finds which of the EMA numbers are in the registry. */
async function registeredEmaIdsOf(emaIds: string[]): Promise<Set<string>> {
  const found = new Set<string>();
  for (let start = 0; start < emaIds.length; start += 300) {
    const refs = emaIds.slice(start, start + 300).map((emaId) => db.collection(EMA_PLAYER_REGISTRY_COLLECTION).doc(emaId));
    const snapshots = await db.getAll(...refs);
    snapshots.forEach((snapshot) => {
      if (snapshot.exists) found.add(snapshot.id);
    });
  }
  return found;
}

async function load(params: {
  tournamentId: string;
  fileName: string;
  buffer: Buffer;
  replaceExisting: boolean;
  /** False for the steps that only assign players. They need no account data. */
  withAccounts?: boolean;
}): Promise<Loaded> {
  const tournament = await db.collection("tournaments").doc(params.tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const isTeams = tournament.get("isTeams") === true;
  const parsed = await parsePlayerImportFile(params.fileName, params.buffer);
  const [players, teams] = await Promise.all([
    listTournamentPlayers(params.tournamentId, { withAccounts: params.withAccounts }),
    listTournamentTeams(params.tournamentId),
  ]);
  const emaIds = [...new Set(parsed.rows.map((row) => normalizeEmaId(row.emaId)).filter((id): id is string => id != null))];
  // The accounts decide what happens with the emails of the file.
  const accountEmailByEma = new Map<string, string | null>();
  const emailOwners = new Map<string, string>();
  if (params.withAccounts !== false && parsed.hasEmailColumn) {
    const accountsByEma = await loadAccountsByEmaId(emaIds);
    accountsByEma.forEach((account, emaId) => accountEmailByEma.set(emaId, account.email));
    const emails = parsed.rows.map((row) => row.email.toLowerCase()).filter((email) => EMAIL_PATTERN.test(email));
    (await loadAccountsByEmail(emails)).forEach((owner, email) => emailOwners.set(email, owner.emaId ?? ""));
  }
  const plan = planPlayerImport({
    rows: parsed.rows,
    slots: players.map(slotInfoOf),
    isTeams,
    teamNames: new Map(teams.map((team) => [team.id, team.name])),
    registeredEmaIds: await registeredEmaIdsOf(emaIds),
    accountEmailByEma,
    emailOwners,
    replaceExisting: params.replaceExisting,
  });
  return { parsed, players, plan, isTeams, tournamentStarted: tournamentHasStartedTables(tournament) };
}

/** Reads the file and says what an import would do. It changes nothing. */
export async function previewPlayerImport(params: {
  tournamentId: string;
  fileName: string;
  buffer: Buffer;
}): Promise<PlayerImportPreview> {
  const loaded = await load({ ...params, replaceExisting: true });
  return {
    rows: loaded.plan.rows,
    untouchedSlotIds: loaded.plan.untouchedSlotIds,
    teamRenames: loaded.plan.teamRenames,
    isTeams: loaded.isTeams,
    tournamentStarted: loaded.tournamentStarted,
    hasEmailColumn: loaded.parsed.hasEmailColumn,
    hasTeamColumn: loaded.parsed.hasTeamColumn,
  };
}

/** Writes the current players of the tournament in the format of the import file. */
export async function exportPlayers(tournamentId: string): Promise<{ fileName: string; buffer: Buffer }> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const isTeams = tournament.get("isTeams") === true;
  const [players, teams] = await Promise.all([listTournamentPlayers(tournamentId), listTournamentTeams(tournamentId)]);
  const records = buildPlayerExportRecords(
    players.map(slotInfoOf), isTeams, new Map(teams.map((team) => [team.id, team.name])),
  );
  return { fileName: "players.xlsx", buffer: await writePlayerExportXlsx(records) };
}

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : "Unexpected error";
}

/**
 * The steps of an import. A long import would not fit in one request, so the client runs the steps one by one:
 * CLEAR once, ASSIGN for batches of rows, FINISH once. Each step plans the file again, so a step does not depend
 * on the memory of an earlier request.
 */
export type ImportPhase = "CLEAR" | "ASSIGN" | "FINISH";

export const IMPORT_BATCH_SIZE = 25;

export type PlayerImportResult = {
  assigned: number;
  emailsSaved: number;
  teamsRenamed: number;
  /** Rows with an error, and rows that replace a player when the editor did not accept it. Set by FINISH. */
  skipped: number;
  /** Rows whose email was not saved, because the account has another email or another account owns the email. Set by FINISH. */
  emailsRefused: number;
  failures: Array<{ row: number | null; slotId: number | null; message: string }>;
};

/**
 * Runs one step of the import and returns its part of the result.
 * CLEAR empties every slot whose player the file puts in another slot, so an EMA number is never in two slots.
 * The file decides the positions: a player may move to any slot. Only a player that the file places nowhere is lost.
 * ASSIGN puts the players of the rows in `rows` in their slots. FINISH saves the emails and renames the teams.
 * One failed row does not stop the others.
 */
export async function applyPlayerImport(params: {
  tournamentId: string;
  fileName: string;
  buffer: Buffer;
  replaceExisting: boolean;
  uid: string;
  phase: ImportPhase;
  /** Lines of the file for ASSIGN. */
  rows?: number[];
}): Promise<PlayerImportResult> {
  const { plan, players } = await load({ ...params, withAccounts: params.phase === "FINISH" });
  const slots = new Map(players.map((player) => [player.id, player]));
  const result: PlayerImportResult = { assigned: 0, emailsSaved: 0, emailsRefused: 0, teamsRenamed: 0, skipped: 0, failures: [] };
  const fail = (row: PlannedRow, error: unknown) => {
    result.failures.push({ row: row.row, slotId: row.slotId, message: messageOf(error) });
  };
  // Rows that the import applies. After ASSIGN a row is UNCHANGED or NEW, so the status alone does not say it.
  const applicable = plan.rows.filter((row) =>
    row.status === "NEW" || row.status === "UNCHANGED" || (row.status === "REPLACE" && params.replaceExisting));

  if (params.phase === "CLEAR") {
    const cleared = new Set<number>();
    for (const row of applicable) {
      const holder = players.find((player) => player.assignedEmaId === row.emaId && player.id !== row.slotId);
      if (holder == null || cleared.has(holder.id)) continue;
      cleared.add(holder.id);
      try {
        await assignTournamentPlayer({
          tournamentId: params.tournamentId, playerId: holder.id, emaId: null, uid: params.uid,
        });
      } catch (error) {
        fail(row, error);
      }
    }
    return result;
  }

  if (params.phase === "ASSIGN") {
    const wanted = new Set(params.rows ?? []);
    for (const row of applicable.filter((candidate) => wanted.has(candidate.row))) {
      const slot = slots.get(row.slotId!)!;
      if (slot.assignedEmaId === row.emaId) continue;
      try {
        await assignTournamentPlayer({
          tournamentId: params.tournamentId, playerId: row.slotId!, emaId: row.emaId, uid: params.uid,
        });
        result.assigned++;
      } catch (error) {
        fail(row, error);
      }
    }
    return result;
  }

  // FINISH. The email of an account never changes. A new email creates an account without a password.
  const emailRows = applicable.filter((row) => {
    if (!row.createsAccount) return false;
    // A row whose player is not in the slot failed in ASSIGN.
    return slots.get(row.slotId!)!.assignedEmaId === row.emaId;
  });
  for (const row of emailRows) {
    try {
      await createPlayerAccount({ email: row.email!, emaId: row.emaId! });
      result.emailsSaved++;
    } catch (error) {
      fail(row, error);
    }
  }
  result.emailsRefused = applicable.filter((row) =>
    row.issues.some((issue) => issue.code === "EMAIL_KEPT_ACCOUNT" || issue.code === "EMAIL_IN_USE")).length;

  if (plan.teamRenames.length > 0) {
    for (const rename of plan.teamRenames) {
      const emaIds = players.filter((player) => player.team === rename.teamId).map((player) => player.assignedEmaId);
      try {
        await updateTournamentTeam({
          tournamentId: params.tournamentId, teamId: rename.teamId, name: rename.name, emaIds,
        });
        result.teamsRenamed++;
      } catch (error) {
        const row = applicable.find((candidate) => slots.get(candidate.slotId!)?.team === rename.teamId);
        result.failures.push({ row: row?.row ?? null, slotId: row?.slotId ?? null, message: messageOf(error) });
      }
    }
  }
  result.skipped = plan.rows.filter((row) => row.status === "ERROR" || (row.status === "REPLACE" && !params.replaceExisting)).length;
  return result;
}
