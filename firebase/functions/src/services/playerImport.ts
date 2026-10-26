import ExcelJS = require("exceljs");

import { badRequest } from "../api/httpError";
import { EMAIL_PATTERN } from "./playerAccountsService";

/** One data row of the import file. Every value is text, as the editor wrote it. */
export type ImportRow = {
  /** The line of the file, counted from 1. The header is line 1 or the first non-empty line. */
  row: number;
  id: string;
  emaId: string;
  email: string;
  team: string;
};

export type ParsedImport = {
  rows: ImportRow[];
  hasEmailColumn: boolean;
  hasTeamColumn: boolean;
};

export const MAX_IMPORT_ROWS = 1000;
/** A sheet can claim a million rows and 16 000 columns. The import reads only a sheet of a sensible size. */
const MAX_SHEET_ROWS = 5000;
const MAX_SHEET_COLUMNS = 40;
/** A zip file can hold much more data than its size says. The import refuses a file that unpacks to more than this. */
const MAX_UNPACKED_BYTES = 30 * 1024 * 1024;
export const MAX_TEAM_NAME_LENGTH = 80;

const HEADER_ALIASES: Record<"id" | "emaId" | "email" | "team", string[]> = {
  id: ["id", "playerid", "slot", "slotid", "number", "no", "n"],
  emaId: ["emaid", "ema", "emanumber", "emanum", "emaplayerid"],
  email: ["email", "mail", "emailaddress", "correo", "courriel"],
  team: ["team", "teamname", "equipo", "equipe", "squadra"],
};

function normalizeHeader(value: string): string {
  return value.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase().replace(/[^a-z0-9]/g, "");
}

/** Splits CSV text into records. It accepts comma, semicolon or tab, quoted values, and a byte order mark. */
export function parseCsv(input: string): string[][] {
  const text = input.replace(/^﻿/, "");
  const firstLine = text.split(/\r?\n/).find((line) => line.trim().length > 0) ?? "";
  const delimiter = [",", ";", "\t"]
    .map((candidate) => ({ candidate, count: countOutsideQuotes(firstLine, candidate) }))
    .sort((left, right) => right.count - left.count)[0];
  const separator = delimiter.count > 0 ? delimiter.candidate : ",";

  const records: string[][] = [];
  let record: string[] = [];
  let value = "";
  let inQuotes = false;
  const endValue = () => {
    record.push(value);
    value = "";
  };
  const endRecord = () => {
    endValue();
    records.push(record);
    record = [];
  };
  for (let index = 0; index < text.length; index++) {
    const char = text[index];
    if (inQuotes) {
      if (char === "\"" && text[index + 1] === "\"") {
        value += "\"";
        index++;
      } else if (char === "\"") {
        inQuotes = false;
      } else {
        value += char;
      }
    } else if (char === "\"") {
      inQuotes = true;
    } else if (char === separator) {
      endValue();
    } else if (char === "\n" || char === "\r") {
      if (char === "\r" && text[index + 1] === "\n") index++;
      endRecord();
    } else {
      value += char;
    }
  }
  if (value.length > 0 || record.length > 0) endRecord();
  return records;
}

function countOutsideQuotes(line: string, character: string): number {
  let inQuotes = false;
  let count = 0;
  for (const char of line) {
    if (char === "\"") inQuotes = !inQuotes;
    else if (char === character && !inQuotes) count++;
  }
  return count;
}

/** Turns the value of a spreadsheet cell into text. Numbers, links, rich text and formulas all become text. */
export function cellText(value: unknown): string {
  if (value == null) return "";
  if (typeof value === "string") return value;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  if (value instanceof Date) return value.toISOString();
  if (typeof value === "object") {
    const cell = value as Record<string, unknown>;
    if (Array.isArray(cell.richText)) {
      return cell.richText.map((part) => String((part as { text?: unknown }).text ?? "")).join("");
    }
    if (cell.result !== undefined) return cellText(cell.result);
    if (cell.text !== undefined) return cellText(cell.text);
    if (typeof cell.hyperlink === "string") return cell.hyperlink.replace(/^mailto:/i, "");
  }
  return "";
}

/** Adds the unpacked sizes that the central directory of a zip file declares. Returns null for a broken file. */
export function declaredUnpackedBytes(buffer: Buffer): number | null {
  // The end record is in the last 64 KiB: signature 0x06054b50, entry count at 10, directory offset at 16.
  const from = Math.max(0, buffer.length - 22 - 0xffff);
  let end = -1;
  for (let index = buffer.length - 22; index >= from; index--) {
    if (buffer.readUInt32LE(index) === 0x06054b50) {
      end = index;
      break;
    }
  }
  if (end < 0) return null;
  const entries = buffer.readUInt16LE(end + 10);
  let offset = buffer.readUInt32LE(end + 16);
  let total = 0;
  for (let entry = 0; entry < entries; entry++) {
    if (offset + 46 > buffer.length || buffer.readUInt32LE(offset) !== 0x02014b50) return null;
    total += buffer.readUInt32LE(offset + 24);
    offset += 46 + buffer.readUInt16LE(offset + 28) + buffer.readUInt16LE(offset + 30) + buffer.readUInt16LE(offset + 32);
  }
  return total;
}

async function readXlsx(buffer: Buffer): Promise<string[][]> {
  const unpacked = declaredUnpackedBytes(buffer);
  if (unpacked == null) throw badRequest("The file is not a valid Excel file");
  if (unpacked > MAX_UNPACKED_BYTES) throw badRequest("The Excel file is too large");
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer as unknown as ExcelJS.Buffer);
  } catch (_error) {
    throw badRequest("The file is not a valid Excel file");
  }
  const sheet = workbook.worksheets[0];
  if (sheet == null) throw badRequest("The Excel file has no sheet");
  if (sheet.rowCount > MAX_SHEET_ROWS || sheet.columnCount > MAX_SHEET_COLUMNS) {
    throw badRequest("The Excel sheet is too large");
  }
  const records: string[][] = [];
  const columns = sheet.columnCount;
  // Keep the line numbers of the sheet: empty lines stay as empty records.
  for (let rowNumber = 1; rowNumber <= sheet.rowCount; rowNumber++) {
    const row = sheet.getRow(rowNumber);
    records.push(Array.from({ length: columns }, (_, index) => cellText(row.getCell(index + 1).value)));
  }
  return records;
}

function isXlsx(fileName: string, buffer: Buffer): boolean {
  // An xlsx file is a zip file, which starts with "PK".
  return fileName.toLowerCase().endsWith(".xlsx") || (buffer.length > 1 && buffer[0] === 0x50 && buffer[1] === 0x4b);
}

/** Reads a CSV or Excel file with the columns id, ema id, email and team. Only id and ema id are required. */
export async function parsePlayerImportFile(fileName: string, buffer: Buffer): Promise<ParsedImport> {
  const records = isXlsx(fileName, buffer) ? await readXlsx(buffer) : parseCsv(buffer.toString("utf8"));
  const isBlank = (record: string[]) => record.every((value) => value.trim().length === 0);
  const headerIndex = records.findIndex((record) => !isBlank(record));
  if (headerIndex < 0) throw badRequest("The file is empty");

  const columnOf = (key: keyof typeof HEADER_ALIASES): number => records[headerIndex]
    .findIndex((header) => HEADER_ALIASES[key].includes(normalizeHeader(header)));
  const idColumn = columnOf("id");
  const emaColumn = columnOf("emaId");
  const emailColumn = columnOf("email");
  const teamColumn = columnOf("team");
  const missing = [idColumn < 0 ? "id" : null, emaColumn < 0 ? "ema id" : null].filter((name) => name != null);
  if (missing.length > 0) {
    throw badRequest(`The first line of the file needs the columns: ${missing.join(", ")}`, { missing });
  }

  const rows: ImportRow[] = [];
  records.slice(headerIndex + 1).forEach((record, offset) => {
    if (isBlank(record)) return;
    const at = (column: number) => (column < 0 ? "" : (record[column] ?? "").trim());
    rows.push({
      row: headerIndex + offset + 2,
      id: at(idColumn),
      emaId: at(emaColumn),
      email: at(emailColumn),
      team: at(teamColumn),
    });
  });
  if (rows.length === 0) throw badRequest("The file has no player rows");
  if (rows.length > MAX_IMPORT_ROWS) throw badRequest(`The file has more than ${MAX_IMPORT_ROWS} rows`);
  return { rows, hasEmailColumn: emailColumn >= 0, hasTeamColumn: teamColumn >= 0 };
}

/**
 * The rows of an export file, in the format that the import reads. The first row holds the column names.
 * Every slot has a row, also an empty slot, so the file works as a template. The team column exists only for team tournaments.
 */
export function buildPlayerExportRecords(slots: SlotInfo[], isTeams: boolean, teamNames: Map<number, string>): string[][] {
  const header = ["id", "ema id", "email", ...(isTeams ? ["team"] : [])];
  const rows = [...slots].sort((a, b) => a.id - b.id).map((slot) => [
    String(slot.id),
    slot.emaId ?? "",
    slot.email ?? "",
    ...(isTeams ? [teamNames.get(slot.team) ?? ""] : []),
  ]);
  return [header, ...rows];
}

/** Writes the records as an Excel file. Every cell is text, so Excel keeps the zeros at the start of an EMA number. */
export async function writePlayerExportXlsx(records: string[][]): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  const sheet = workbook.addWorksheet("Players");
  records.forEach((record) => sheet.addRow(record));
  sheet.columns.forEach((column) => { column.width = 18; });
  sheet.eachRow((row) => row.eachCell((cell) => { cell.numFmt = "@"; }));
  sheet.getRow(1).font = { bold: true };
  return Buffer.from(await workbook.xlsx.writeBuffer());
}

export type IssueCode =
  // Errors: the row is skipped.
  | "INVALID_ID"
  | "UNKNOWN_SLOT"
  | "DUPLICATE_SLOT"
  | "MISSING_EMA_ID"
  | "INVALID_EMA_ID"
  | "UNKNOWN_EMA_ID"
  | "DUPLICATE_EMA_ID"
  | "EMA_ASSIGNED_ELSEWHERE"
  | "INVALID_EMAIL"
  | "TEAM_NAME_TOO_LONG"
  | "TEAM_NAME_CONFLICT"
  | "TEAM_NAME_IN_USE"
  // Warnings: the row is applied.
  | "REPLACES_PLAYER"
  /** The account of the player has another email. The account email stays and the email of the file is not saved. */
  | "EMAIL_KEPT_ACCOUNT"
  /** The email belongs to another account, or to an earlier row of the file. The email is not saved. */
  | "EMAIL_IN_USE"
  | "TEAM_IGNORED";

export type RowIssue = {
  code: IssueCode;
  severity: "ERROR" | "WARNING";
  /** Values for the text of the issue, such as the old and the new email. */
  args: string[];
};

export type RowStatus = "NEW" | "REPLACE" | "UNCHANGED" | "ERROR";

export type SlotInfo = {
  id: number;
  team: number;
  emaId: string | null;
  accountUid: string | null;
  /** The email of the account linked to the slot. The email lives only in the account. */
  email: string | null;
};

export type PlannedRow = {
  row: number;
  slotId: number | null;
  emaId: string | null;
  email: string | null;
  team: string | null;
  status: RowStatus;
  issues: RowIssue[];
  /** True when applying the row creates the account of the player, because the email is new. */
  createsAccount: boolean;
  /** What the slot holds now. Null when the slot does not exist. */
  current: { emaId: string | null; email: string | null; teamName: string | null } | null;
};

export type ImportPlan = {
  rows: PlannedRow[];
  /** Slots that no row of the file names. They stay as they are. */
  untouchedSlotIds: number[];
  /** New team names by team id. Only teams whose name changes. */
  teamRenames: Array<{ teamId: number; name: string }>;
};

export type PlanInput = {
  rows: ImportRow[];
  slots: SlotInfo[];
  isTeams: boolean;
  teamNames: Map<number, string>;
  registeredEmaIds: ReadonlySet<string>;
  /** The email of the account that owns each EMA id. An EMA id with no account is missing. */
  accountEmailByEma: ReadonlyMap<string, string | null>;
  /** The accounts that own an email, by email in lower case. The value is the EMA id of the account, or "" when it has none. */
  emailOwners: ReadonlyMap<string, string>;
  /** True when the editor accepts to replace players that are in a slot already. */
  replaceExisting: boolean;
};

/** An EMA number as the server stores it: digits only, 8 digits long. Null when the text is not a number. */
export function normalizeEmaId(value: string): string | null {
  const text = value.trim();
  if (!/^\d{1,8}$/.test(text)) return null;
  return text.padStart(8, "0");
}

function sameText(left: string, right: string): boolean {
  return left.localeCompare(right, undefined, { sensitivity: "accent" }) === 0;
}

/**
 * Checks every row of the file against the tournament and says what the import would do.
 * It changes nothing. The apply step uses the same plan, so the preview and the result agree.
 */
export function planPlayerImport(input: PlanInput): ImportPlan {
  const slotsById = new Map(input.slots.map((slot) => [slot.id, slot]));
  const issuesByRow = new Map<number, RowIssue[]>();
  const addIssue = (row: number, code: IssueCode, severity: "ERROR" | "WARNING", args: string[] = []) => {
    const issues = issuesByRow.get(row) ?? [];
    if (!issues.some((issue) => issue.code === code)) issues.push({ code, severity, args });
    issuesByRow.set(row, issues);
  };
  const hasError = (row: number) => (issuesByRow.get(row) ?? []).some((issue) => issue.severity === "ERROR");

  const slotIdOf = new Map<number, number>();
  const emaOf = new Map<number, string>();
  const emailOf = new Map<number, string>();
  const teamOf = new Map<number, string>();

  for (const row of input.rows) {
    if (!/^\d+$/.test(row.id) || Number(row.id) <= 0) {
      addIssue(row.row, "INVALID_ID", "ERROR");
    } else if (!slotsById.has(Number(row.id))) {
      addIssue(row.row, "UNKNOWN_SLOT", "ERROR", [row.id]);
    } else {
      slotIdOf.set(row.row, Number(row.id));
    }

    if (row.emaId.length === 0) {
      addIssue(row.row, "MISSING_EMA_ID", "ERROR");
    } else {
      const emaId = normalizeEmaId(row.emaId);
      if (emaId == null) addIssue(row.row, "INVALID_EMA_ID", "ERROR", [row.emaId]);
      else if (!input.registeredEmaIds.has(emaId)) addIssue(row.row, "UNKNOWN_EMA_ID", "ERROR", [emaId]);
      else emaOf.set(row.row, emaId);
    }

    if (row.email.length > 0) {
      const email = row.email.toLowerCase();
      if (EMAIL_PATTERN.test(email)) emailOf.set(row.row, email);
      else addIssue(row.row, "INVALID_EMAIL", "ERROR", [row.email]);
    }

    if (row.team.length > 0) {
      if (!input.isTeams) addIssue(row.row, "TEAM_IGNORED", "WARNING");
      else if (row.team.length > MAX_TEAM_NAME_LENGTH) addIssue(row.row, "TEAM_NAME_TOO_LONG", "ERROR", [row.team]);
      else teamOf.set(row.row, row.team);
    }
  }

  const rowsBySlot = new Map<number, ImportRow[]>();
  const rowsByEma = new Map<string, ImportRow[]>();
  for (const row of input.rows) {
    const slotId = slotIdOf.get(row.row);
    if (slotId != null) rowsBySlot.set(slotId, [...(rowsBySlot.get(slotId) ?? []), row]);
    const emaId = emaOf.get(row.row);
    if (emaId != null) rowsByEma.set(emaId, [...(rowsByEma.get(emaId) ?? []), row]);
  }
  rowsBySlot.forEach((rows, slotId) => {
    if (rows.length > 1) rows.forEach((row) => addIssue(row.row, "DUPLICATE_SLOT", "ERROR", [String(slotId)]));
  });
  rowsByEma.forEach((rows, emaId) => {
    if (rows.length > 1) rows.forEach((row) => addIssue(row.row, "DUPLICATE_EMA_ID", "ERROR", [emaId]));
  });

  // The rows of one team must agree on its name.
  const rowsByTeam = new Map<number, ImportRow[]>();
  for (const row of input.rows) {
    const slotId = slotIdOf.get(row.row);
    if (slotId == null || !teamOf.has(row.row)) continue;
    const teamId = slotsById.get(slotId)!.team;
    rowsByTeam.set(teamId, [...(rowsByTeam.get(teamId) ?? []), row]);
  }
  rowsByTeam.forEach((rows) => {
    const first = teamOf.get(rows[0].row)!;
    if (rows.some((row) => !sameText(teamOf.get(row.row)!, first))) {
      rows.forEach((row) => addIssue(row.row, "TEAM_NAME_CONFLICT", "ERROR"));
    }
  });
  // No two teams can end with the same name.
  const finalTeamNames = () => {
    const names = new Map(input.teamNames);
    rowsByTeam.forEach((rows, teamId) => {
      const active = rows.filter((row) => !hasError(row.row));
      if (active.length > 0) names.set(teamId, teamOf.get(active[0].row)!);
    });
    return names;
  };
  const names = finalTeamNames();
  rowsByTeam.forEach((rows, teamId) => {
    const name = names.get(teamId) ?? "";
    const clash = [...names.entries()].some(([otherId, otherName]) => otherId !== teamId && sameText(otherName, name));
    if (clash) rows.forEach((row) => addIssue(row.row, "TEAM_NAME_IN_USE", "ERROR", [teamOf.get(row.row)!]));
  });

  const replacesPlayer = (row: ImportRow): boolean => {
    const slot = slotsById.get(slotIdOf.get(row.row)!)!;
    const emaId = emaOf.get(row.row);
    return emaId != null && slot.emaId != null && slot.emaId !== emaId;
  };
  // A row is applied when it has no error, and when it replaces a player only if the editor accepted it.
  const isActive = (row: ImportRow) => !hasError(row.row) && (input.replaceExisting || !replacesPlayer(row));

  // An EMA number cannot end in two slots. The slots that no active row changes keep their player.
  let changed = true;
  while (changed) {
    changed = false;
    const finalBySlot = new Map<number, string | null>(input.slots.map((slot) => [slot.id, slot.emaId]));
    for (const row of input.rows) {
      if (slotIdOf.has(row.row) && emaOf.has(row.row) && isActive(row)) finalBySlot.set(slotIdOf.get(row.row)!, emaOf.get(row.row)!);
    }
    for (const row of input.rows) {
      if (!slotIdOf.has(row.row) || !emaOf.has(row.row) || !isActive(row)) continue;
      const slotId = slotIdOf.get(row.row)!;
      const emaId = emaOf.get(row.row)!;
      const holder = [...finalBySlot.entries()].find(([otherSlot, otherEma]) => otherSlot !== slotId && otherEma === emaId);
      if (holder != null) {
        addIssue(row.row, "EMA_ASSIGNED_ELSEWHERE", "ERROR", [String(holder[0])]);
        changed = true;
      }
    }
  }

  // The email of an account never changes here. A new email creates an account only when no account owns the email.
  const emailsToCreate = new Map<string, string>();
  const planned: PlannedRow[] = input.rows.map((row) => {
    const slotId = slotIdOf.get(row.row) ?? null;
    const slot = slotId == null ? null : slotsById.get(slotId)!;
    const emaId = emaOf.get(row.row) ?? null;
    const email = emailOf.get(row.row) ?? null;
    const team = teamOf.get(row.row) ?? null;
    const current = slot == null ? null : {
      emaId: slot.emaId,
      email: slot.email,
      teamName: input.isTeams ? input.teamNames.get(slot.team) ?? null : null,
    };
    let createsAccount = false;
    if (slot != null && !hasError(row.row)) {
      if (replacesPlayer(row)) addIssue(row.row, "REPLACES_PLAYER", "WARNING");
      if (email != null && emaId != null && isActive(row)) {
        const accountEmail = input.accountEmailByEma.get(emaId);
        const owner = input.emailOwners.get(email);
        const earlier = emailsToCreate.get(email);
        if (accountEmail !== undefined) {
          if (accountEmail !== email) addIssue(row.row, "EMAIL_KEPT_ACCOUNT", "WARNING", [accountEmail ?? "", email]);
        } else if (owner !== undefined) {
          addIssue(row.row, "EMAIL_IN_USE", "WARNING", [owner]);
        } else if (earlier !== undefined) {
          addIssue(row.row, "EMAIL_IN_USE", "WARNING", [earlier]);
        } else {
          emailsToCreate.set(email, emaId);
          createsAccount = true;
        }
      }
    }
    const issues = issuesByRow.get(row.row) ?? [];
    let status: RowStatus;
    if (issues.some((issue) => issue.severity === "ERROR")) {
      status = "ERROR";
    } else if (replacesPlayer(row)) {
      status = "REPLACE";
    } else {
      const teamChanges = team != null && input.isTeams && !sameText(team, input.teamNames.get(slot!.team) ?? "");
      status = slot!.emaId !== emaId || createsAccount || teamChanges ? "NEW" : "UNCHANGED";
    }
    return { row: row.row, slotId, emaId, email, team, status, issues, createsAccount, current };
  });

  const finalNames = finalTeamNames();
  const activeTeamIds = new Set<number>();
  input.rows.forEach((row) => {
    const slotId = slotIdOf.get(row.row);
    if (slotId != null && teamOf.has(row.row) && isActive(row)) activeTeamIds.add(slotsById.get(slotId)!.team);
  });
  const teamRenames = [...activeTeamIds]
    .filter((teamId) => !sameText(finalNames.get(teamId) ?? "", input.teamNames.get(teamId) ?? ""))
    .sort((left, right) => left - right)
    .map((teamId) => ({ teamId, name: finalNames.get(teamId)! }));

  const namedSlots = new Set(slotIdOf.values());
  return {
    rows: planned,
    untouchedSlotIds: input.slots.map((slot) => slot.id).filter((id) => !namedSlots.has(id)).sort((a, b) => a - b),
    teamRenames,
  };
}
