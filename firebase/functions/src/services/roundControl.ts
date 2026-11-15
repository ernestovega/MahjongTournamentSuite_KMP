import { badRequest } from "../api/httpError";

/**
 * The manual control of a round: the plan B of the Timer. An admin opens or closes what the players can do, whatever
 * the clock says, for a whole round or for one table. Without a control (automatic) the clock decides, as always.
 * It lives in the tournament document, apart from the settings, under `roundControls.{roundId}`.
 */
export type ControlMode = "open" | "closed";

export type RoundControl = {
  /** Hand entry of the players at every table of the round. */
  hands: ControlMode | null;
  /** The seats of the tables, before their first hand. */
  seats: ControlMode | null;
  /** Hand entry at one table, by table id. It wins over [hands]. */
  tables: Map<number, ControlMode>;
};

/** A control as the apps receive it. Automatic is "auto". */
export type RoundControlView = {
  roundId: number;
  hands: ControlMode | "auto";
  seats: ControlMode | "auto";
  tables: Array<{ tableId: number; hands: ControlMode }>;
};

type Data = Record<string, unknown>;

const modeOf = (value: unknown): ControlMode | null => (value === "open" || value === "closed" ? value : null);

/** The stored controls of a tournament by round. Damaged values count as automatic. */
export function readStoredRoundControls(tournament: Data): Map<number, RoundControl> {
  const numRounds = Number(tournament.numRounds ?? 0);
  const raw = tournament.roundControls != null && typeof tournament.roundControls === "object" ? tournament.roundControls as Data : {};
  const controls = new Map<number, RoundControl>();
  for (const [key, value] of Object.entries(raw)) {
    const roundId = Number(key);
    if (!Number.isInteger(roundId) || roundId < 1 || (numRounds > 0 && roundId > numRounds)) continue;
    if (value == null || typeof value !== "object") continue;
    const data = value as Data;
    const tables = new Map<number, ControlMode>();
    if (data.tables != null && typeof data.tables === "object") {
      for (const [tableKey, mode] of Object.entries(data.tables as Data)) {
        const tableId = Number(tableKey);
        const parsed = modeOf(mode);
        if (Number.isInteger(tableId) && tableId > 0 && parsed != null) tables.set(tableId, parsed);
      }
    }
    const control = { hands: modeOf(data.hands), seats: modeOf(data.seats), tables };
    if (control.hands != null || control.seats != null || tables.size > 0) controls.set(roundId, control);
  }
  return controls;
}

/** The controls sorted by round and table, as the apps receive them. Rounds without a control are left out. */
export function roundControlViews(tournament: Data): RoundControlView[] {
  return [...readStoredRoundControls(tournament).entries()]
    .sort(([a], [b]) => a - b)
    .map(([roundId, control]) => ({
      roundId,
      hands: control.hands ?? "auto",
      seats: control.seats ?? "auto",
      tables: [...control.tables.entries()].sort(([a], [b]) => a - b).map(([tableId, hands]) => ({ tableId, hands })),
    }));
}

/** The manual hand entry of one table: its own control, else the control of its round, else null (the clock decides). */
export function manualHands(tournament: Data, roundId: number, tableId: number): ControlMode | null {
  const control = readStoredRoundControls(tournament).get(roundId);
  return control?.tables.get(tableId) ?? control?.hands ?? null;
}

/** The manual seats of a round, or null (the clock and the planned start decide). */
export function manualSeats(tournament: Data, roundId: number): ControlMode | null {
  return readStoredRoundControls(tournament).get(roundId)?.seats ?? null;
}

/** What an admin asks to change. A missing field keeps its value. "auto" removes the control. */
export type RoundControlChange = {
  hands?: ControlMode | "auto";
  seats?: ControlMode | "auto";
  table?: { tableId: number; hands: ControlMode | "auto" };
  /** Tables to open again after the end of the time closed them, so their players can go on. */
  reopenTables?: number[];
};

const parseMode = (value: unknown, field: string): ControlMode | "auto" => {
  if (value === "open" || value === "closed" || value === "auto") return value;
  throw badRequest(`${field} must be auto, open or closed`);
};

/** Reads the change of a request body. */
export function parseRoundControlChange(body: unknown): RoundControlChange {
  const data = body != null && typeof body === "object" ? body as Data : {};
  const change: RoundControlChange = {};
  if (data.hands !== undefined) change.hands = parseMode(data.hands, "hands");
  if (data.seats !== undefined) change.seats = parseMode(data.seats, "seats");
  if (data.table !== undefined) {
    const table = data.table != null && typeof data.table === "object" ? data.table as Data : {};
    const tableId = Number(table.tableId);
    if (!Number.isInteger(tableId) || tableId <= 0) throw badRequest("table.tableId must be a positive integer");
    change.table = { tableId, hands: parseMode(table.hands, "table.hands") };
  }
  if (data.reopenTables !== undefined) {
    if (!Array.isArray(data.reopenTables)) throw badRequest("reopenTables must be a list of table ids");
    const ids = data.reopenTables.map(Number);
    if (ids.some((id) => !Number.isInteger(id) || id <= 0)) throw badRequest("reopenTables must be a list of table ids");
    change.reopenTables = [...new Set(ids)];
  }
  if (change.hands === undefined && change.seats === undefined && change.table === undefined && change.reopenTables === undefined) {
    throw badRequest("Nothing to change");
  }
  return change;
}
