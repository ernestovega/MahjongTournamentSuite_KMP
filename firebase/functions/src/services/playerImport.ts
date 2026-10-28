import ExcelJS = require("exceljs");
import JSZip = require("jszip");

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

async function loadWorkbook(buffer: Buffer): Promise<ExcelJS.Workbook | null> {
  const workbook = new ExcelJS.Workbook();
  try {
    await workbook.xlsx.load(buffer as unknown as ExcelJS.Buffer);
    return workbook;
  } catch (_error) {
    return null;
  }
}

/**
 * Some tools, such as the Open XML SDK, write the XML with a prefix on every tag (`<x:row>`) and with absolute paths
 * in the relations (`/xl/worksheets/sheet1.xml`). Excel reads these files, but ExcelJS does not.
 * This removes the prefix and makes the paths relative. It returns null when the file is not a zip file.
 */
async function normalizeXlsx(buffer: Buffer): Promise<Buffer | null> {
  try {
    const zip = await JSZip.loadAsync(buffer);
    for (const name of Object.keys(zip.files)) {
      if (!/\.(xml|rels)$/i.test(name)) continue;
      const text = (await zip.file(name)!.async("string")).replace(/^\uFEFF/, "")
        .replace(/<(\/?)x:/g, "<$1")
        .replace(/xmlns:x=/g, "xmlns=")
        .replace(/Target="\/xl\//g, 'Target="');
      zip.file(name, text);
    }
    return await zip.generateAsync({ type: "nodebuffer" });
  } catch (_error) {
    return null;
  }
}

async function readXlsx(buffer: Buffer): Promise<string[][]> {
  const unpacked = declaredUnpackedBytes(buffer);
  if (unpacked == null) throw badRequest("The file is not a valid Excel file", { code: "NOT_EXCEL" });
  if (unpacked > MAX_UNPACKED_BYTES) throw badRequest("The Excel file is too large", { code: "TOO_LARGE" });
  let workbook = await loadWorkbook(buffer);
  // Some tools write valid files that ExcelJS does not read. Rewrite the file in the usual form and read it again.
  if (workbook == null || workbook.worksheets.length === 0) {
    const normalized = await normalizeXlsx(buffer);
    workbook = normalized == null ? workbook : await loadWorkbook(normalized);
  }
  if (workbook == null) throw badRequest("The file is not a valid Excel file", { code: "NOT_EXCEL" });
  const sheet = workbook.worksheets[0];
  if (sheet == null) throw badRequest("The Excel file has no sheet", { code: "NO_SHEET" });
  if (sheet.rowCount > MAX_SHEET_ROWS || sheet.columnCount > MAX_SHEET_COLUMNS) {
    throw badRequest("The Excel sheet is too large", { code: "SHEET_TOO_LARGE" });
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
  if (headerIndex < 0) throw badRequest("The file is empty", { code: "EMPTY" });

  const columnOf = (key: keyof typeof HEADER_ALIASES): number => records[headerIndex]
    .findIndex((header) => HEADER_ALIASES[key].includes(normalizeHeader(header)));
  const idColumn = columnOf("id");
  const emaColumn = columnOf("emaId");
  const emailColumn = columnOf("email");
  const teamColumn = columnOf("team");
  const missing = [idColumn < 0 ? "id" : null, emaColumn < 0 ? "ema id" : null].filter((name) => name != null);
  if (missing.length > 0) {
    throw badRequest(`The first line of the file needs the columns: ${missing.join(", ")}`, { code: "MISSING_COLUMNS", missing });
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
  if (rows.length === 0) throw badRequest("The file has no player rows", { code: "NO_ROWS" });
  if (rows.length > MAX_IMPORT_ROWS) throw badRequest(`The file has more than ${MAX_IMPORT_ROWS} rows`, { code: "TOO_MANY_ROWS", max: MAX_IMPORT_ROWS });
  return { rows, hasEmailColumn: emailColumn >= 0, hasTeamColumn: teamColumn >= 0 };
}

/** A slot as the export writes it. The email is empty when the caller may not read emails. */
export type ExportSlot = SlotInfo & { email: string | null };

/**
 * The rows of an export file, in the format that the import reads. The first row holds the column names.
 * Every slot has a row, also an empty slot, so the file works as a template. The team column exists only for team tournaments.
 */
export function buildPlayerExportRecords(slots: ExportSlot[], isTeams: boolean, teamNames: Map<number, string>): string[][] {
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
  // Errors: the file cannot be applied.
  | "INVALID_ID"
  | "UNKNOWN_SLOT"
  | "DUPLICATE_SLOT"
  | "MISSING_EMA_ID"
  | "INVALID_EMA_ID"
  | "UNKNOWN_EMA_ID"
  | "DUPLICATE_EMA_ID"
  | "INVALID_EMAIL"
  /** Two rows have the same email. One email belongs to one account. */
  | "DUPLICATE_EMAIL"
  /** An account owns the email, and the account has another EMA id or none. */
  | "EMAIL_OWNED_BY_OTHER_ACCOUNT"
  | "TEAM_NAME_TOO_LONG"
  | "TEAM_NAME_CONFLICT"
  | "TEAM_NAME_IN_USE"
  // Warnings: the row is applied.
  | "TEAM_IGNORED";

export type RowIssue = {
  code: IssueCode;
  severity: "ERROR" | "WARNING";
  /** Values for the text of the issue, such as the email or the slot number. */
  args: string[];
};

export type SlotInfo = {
  id: number;
  team: number;
  emaId: string | null;
};

export type PlannedRow = {
  row: number;
  slotId: number | null;
  emaId: string | null;
  email: string | null;
  team: string | null;
  issues: RowIssue[];
};

export type ImportPlan = {
  rows: PlannedRow[];
  /** True when no row has an error. Only then can the import apply. */
  valid: boolean;
  /** Slots that the file leaves without a player. The import empties them. */
  emptiedSlotIds: number[];
  /** The final name of every team, by team id. Empty when the file has no team column or the tournament has no teams. */
  teamNames: Array<{ teamId: number; name: string }>;
};

export type PlanInput = {
  rows: ImportRow[];
  slots: SlotInfo[];
  isTeams: boolean;
  /** True when the file has a team column. Without it, the team names stay as they are. */
  hasTeamColumn: boolean;
  registeredEmaIds: ReadonlySet<string>;
  /** The accounts that own an email, by email in lower case. The value is the EMA id of the account, or "" when it has none. */
  emailOwners: ReadonlyMap<string, string>;
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
 * Checks every row of the file against the tournament. It changes nothing.
 * The import overwrites all players and teams, so any error means that the whole file is refused.
 * The apply step uses the same plan, so the preview and the result agree.
 */
export function planPlayerImport(input: PlanInput): ImportPlan {
  const slotsById = new Map(input.slots.map((slot) => [slot.id, slot]));
  const issuesByRow = new Map<number, RowIssue[]>();
  const addIssue = (row: number, code: IssueCode, severity: "ERROR" | "WARNING", args: string[] = []) => {
    const issues = issuesByRow.get(row) ?? [];
    if (!issues.some((issue) => issue.code === code)) issues.push({ code, severity, args });
    issuesByRow.set(row, issues);
  };

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
      // A row with an id and nothing else is an empty slot, as in the template of the export.
      if (row.email.length > 0) addIssue(row.row, "MISSING_EMA_ID", "ERROR");
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

  const rowsBy = <K>(keyOf: (row: ImportRow) => K | undefined): Map<K, ImportRow[]> => {
    const groups = new Map<K, ImportRow[]>();
    for (const row of input.rows) {
      const key = keyOf(row);
      if (key !== undefined) groups.set(key, [...(groups.get(key) ?? []), row]);
    }
    return groups;
  };
  rowsBy((row) => slotIdOf.get(row.row)).forEach((rows, slotId) => {
    if (rows.length > 1) rows.forEach((row) => addIssue(row.row, "DUPLICATE_SLOT", "ERROR", [String(slotId)]));
  });
  rowsBy((row) => emaOf.get(row.row)).forEach((rows, emaId) => {
    if (rows.length > 1) rows.forEach((row) => addIssue(row.row, "DUPLICATE_EMA_ID", "ERROR", [emaId]));
  });
  rowsBy((row) => emailOf.get(row.row)).forEach((rows, email) => {
    if (rows.length > 1) rows.forEach((row) => addIssue(row.row, "DUPLICATE_EMAIL", "ERROR", [email]));
  });
  // An account that owns the email must own the EMA id of the row too. Then the email is the email of that player.
  for (const row of input.rows) {
    const email = emailOf.get(row.row);
    const emaId = emaOf.get(row.row);
    const owner = email == null ? undefined : input.emailOwners.get(email);
    if (email != null && owner !== undefined && owner !== emaId) addIssue(row.row, "EMAIL_OWNED_BY_OTHER_ACCOUNT", "ERROR", [email]);
  }

  // The rows of one team must agree on its name.
  const rowsByTeam = rowsBy((row) => {
    const slotId = slotIdOf.get(row.row);
    return slotId == null || !teamOf.has(row.row) ? undefined : slotsById.get(slotId)!.team;
  });
  const namedTeams = new Map<number, string>();
  rowsByTeam.forEach((rows, teamId) => {
    const first = teamOf.get(rows[0].row)!;
    if (rows.some((row) => !sameText(teamOf.get(row.row)!, first))) {
      rows.forEach((row) => addIssue(row.row, "TEAM_NAME_CONFLICT", "ERROR"));
    } else {
      namedTeams.set(teamId, first);
    }
  });
  // No two teams can end with the same name. A team that the file does not name gets the default name.
  const finalNames = new Map<number, string>();
  if (input.isTeams && input.hasTeamColumn) {
    new Set(input.slots.map((slot) => slot.team)).forEach((teamId) => finalNames.set(teamId, namedTeams.get(teamId) ?? `Team ${teamId}`));
    rowsByTeam.forEach((rows, teamId) => {
      const name = finalNames.get(teamId)!;
      const clash = [...finalNames.entries()].some(([otherId, otherName]) => otherId !== teamId && sameText(otherName, name));
      if (clash) rows.forEach((row) => addIssue(row.row, "TEAM_NAME_IN_USE", "ERROR", [teamOf.get(row.row)!]));
    });
  }

  const planned: PlannedRow[] = input.rows.map((row) => ({
    row: row.row,
    slotId: slotIdOf.get(row.row) ?? null,
    emaId: emaOf.get(row.row) ?? null,
    email: emailOf.get(row.row) ?? null,
    team: teamOf.get(row.row) ?? null,
    issues: issuesByRow.get(row.row) ?? [],
  }));
  const filledSlots = new Set(input.rows.flatMap((row) => (slotIdOf.has(row.row) && emaOf.has(row.row) ? [slotIdOf.get(row.row)!] : [])));
  return {
    rows: planned,
    valid: !planned.some((row) => row.issues.some((issue) => issue.severity === "ERROR")),
    emptiedSlotIds: input.slots.map((slot) => slot.id).filter((id) => !filledSlots.has(id)).sort((a, b) => a - b),
    teamNames: [...finalNames.entries()].sort((a, b) => a[0] - b[0]).map(([teamId, name]) => ({ teamId, name })),
  };
}
