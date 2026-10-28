import { db } from "../firebase";
import { conflict, notFound } from "../api/httpError";
import {
  ImportPlan,
  buildPlayerExportRecords,
  changedFields,
  normalizeEmaId,
  parsePlayerImportFile,
  planPlayerImport,
  type ParsedImport,
  type ChangedField,
  type PlannedRow,
  writePlayerExportXlsx,
} from "./playerImport";
import { EMAIL_PATTERN, loadAccountsByEmail } from "./playerAccountsService";
import { EMA_PLAYER_REGISTRY_COLLECTION } from "./playersService";
import {
  listTournamentPlayers,
  listTournamentTeams,
  overwriteTournamentPlayers,
  tournamentHasStartedTables,
  type TournamentPlayer,
} from "./tournamentContentService";

/** A row of the preview: the data of the file, its problems, and which data the import changes. */
export type PreviewRow = PlannedRow & { changes: ChangedField[] };

export type PlayerImportPreview = {
  rows: PreviewRow[];
  /** Players that sit in a slot now and that the file leaves out. The import removes them. */
  removed: Array<{ slotId: number; emaId: string }>;
  /** True when no row has an error. */
  valid: boolean;
  isTeams: boolean;
  /** True when a table has data. The import is then refused, because the results belong to the people of the slots. */
  tournamentStarted: boolean;
  /** How many slots hold a player now. The import overwrites them. */
  existingPlayers: number;
  /** How many slots hold a player after the import. */
  importedPlayers: number;
  hasEmailColumn: boolean;
  hasTeamColumn: boolean;
};

type Loaded = {
  players: TournamentPlayer[];
  teamNames: Map<number, string>;
  parsed: ParsedImport;
  plan: ImportPlan;
  isTeams: boolean;
  tournamentStarted: boolean;
  existingPlayers: number;
};

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

async function load(params: { tournamentId: string; fileName: string; buffer: Buffer }): Promise<Loaded> {
  const tournament = await db.collection("tournaments").doc(params.tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const isTeams = tournament.get("isTeams") === true;
  const parsed = await parsePlayerImportFile(params.fileName, params.buffer);
  const players = await listTournamentPlayers(params.tournamentId, { withAccounts: false });
  const emaIds = [...new Set(parsed.rows.map((row) => normalizeEmaId(row.emaId)).filter((id): id is string => id != null))];
  // An email that an account owns must belong to the EMA id of the row. Otherwise "Invite" would fail later.
  const emailOwners = new Map<string, string>();
  const emails = parsed.rows.map((row) => row.email.toLowerCase()).filter((email) => EMAIL_PATTERN.test(email));
  (await loadAccountsByEmail(emails)).forEach((owner, email) => emailOwners.set(email, owner.emaId ?? ""));
  const teamNames = new Map((await listTournamentTeams(params.tournamentId)).map((team) => [team.id, team.name]));
  const plan = planPlayerImport({
    rows: parsed.rows,
    slots: players.map((player) => ({ id: player.id, team: player.team, emaId: player.assignedEmaId })),
    isTeams,
    hasTeamColumn: parsed.hasTeamColumn,
    registeredEmaIds: await registeredEmaIdsOf(emaIds),
    emailOwners,
  });
  return {
    players,
    teamNames,
    parsed,
    plan,
    isTeams,
    tournamentStarted: tournamentHasStartedTables(tournament),
    existingPlayers: players.filter((player) => player.assignedEmaId != null).length,
  };
}

/** Reads the file and says what an import would do. It changes nothing. */
export async function previewPlayerImport(params: {
  tournamentId: string;
  fileName: string;
  buffer: Buffer;
}): Promise<PlayerImportPreview> {
  const loaded = await load(params);
  const slots = new Map(loaded.players.map((player) => [player.id, player]));
  const rows = loaded.plan.rows.map((row): PreviewRow => {
    const slot = row.slotId == null ? undefined : slots.get(row.slotId);
    if (slot == null) return { ...row, changes: [] };
    const current = { emaId: slot.assignedEmaId, email: slot.email, teamName: loaded.isTeams ? loaded.teamNames.get(slot.team) ?? null : null };
    return {
      ...row,
      changes: changedFields(row, current, { isTeams: loaded.isTeams, hasTeamColumn: loaded.parsed.hasTeamColumn }),
    };
  });
  const emptied = new Set(loaded.plan.emptiedSlotIds);
  return {
    rows,
    removed: loaded.players.flatMap((player) => (emptied.has(player.id) && player.assignedEmaId != null
      ? [{ slotId: player.id, emaId: player.assignedEmaId }] : [])),
    valid: loaded.plan.valid,
    isTeams: loaded.isTeams,
    tournamentStarted: loaded.tournamentStarted,
    existingPlayers: loaded.existingPlayers,
    importedPlayers: loaded.plan.rows.filter((row) => row.emaId != null && row.slotId != null).length,
    hasEmailColumn: loaded.parsed.hasEmailColumn,
    hasTeamColumn: loaded.parsed.hasTeamColumn,
  };
}

/**
 * Writes the current players of the tournament in the format of the import file.
 * The email column holds the account email, or the pending email when the slot has no account.
 * Only admins may read emails, so the other callers get an empty column.
 */
export async function exportPlayers(tournamentId: string, options: { includeEmails: boolean }): Promise<{ fileName: string; buffer: Buffer }> {
  const tournament = await db.collection("tournaments").doc(tournamentId).get();
  if (!tournament.exists) throw notFound("Tournament not found");
  const isTeams = tournament.get("isTeams") === true;
  const [players, teams] = await Promise.all([
    listTournamentPlayers(tournamentId, { withAccounts: options.includeEmails }),
    listTournamentTeams(tournamentId),
  ]);
  const records = buildPlayerExportRecords(
    players.map((player) => ({
      id: player.id, team: player.team, emaId: player.assignedEmaId, email: options.includeEmails ? player.email : null,
    })),
    isTeams,
    new Map(teams.map((team) => [team.id, team.name])),
  );
  return { fileName: "players.xlsx", buffer: await writePlayerExportXlsx(records) };
}

export type PlayerImportResult = {
  /** Slots that hold a player after the import. */
  players: number;
  /** Slots whose data changed. */
  changedSlots: number;
  /** Slots that the file does not list, now empty. */
  emptiedSlots: number;
};

/**
 * Overwrites all players and teams of the tournament with the file. It creates no account.
 * The server reads and checks the file again, so it never trusts what the client showed in the preview.
 * A file with any error, or a tournament with table data, changes nothing.
 */
export async function applyPlayerImport(params: {
  tournamentId: string;
  fileName: string;
  buffer: Buffer;
  uid: string;
}): Promise<PlayerImportResult> {
  const loaded = await load(params);
  if (loaded.tournamentStarted) throw conflict("The tournament has table data", { code: "TOURNAMENT_STARTED" });
  if (!loaded.plan.valid) throw conflict("The file has errors", { code: "FILE_HAS_ERRORS" });
  const rowOfSlot = new Map(loaded.plan.rows.map((row) => [row.slotId!, row]));
  const slotIds = (await listTournamentPlayers(params.tournamentId, { withAccounts: false })).map((player) => player.id);
  const { changedSlots } = await overwriteTournamentPlayers({
    tournamentId: params.tournamentId,
    slots: slotIds.map((slotId) => ({
      slotId, emaId: rowOfSlot.get(slotId)?.emaId ?? null, email: rowOfSlot.get(slotId)?.email ?? null,
    })),
    teamNames: loaded.plan.teamNames,
    uid: params.uid,
  });
  return {
    players: loaded.plan.rows.filter((row) => row.emaId != null).length,
    changedSlots,
    emptiedSlots: loaded.plan.emptiedSlotIds.length,
  };
}
